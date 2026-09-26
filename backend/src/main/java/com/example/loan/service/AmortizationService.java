package com.example.loan.service;

import com.example.loan.api.ComparisonResult;
import com.example.loan.api.DiffSummary;
import com.example.loan.api.PlanResult;
import com.example.loan.api.PlanSummary;
import com.example.loan.api.RatePart;
import com.example.loan.api.RateSegmentInput;
import com.example.loan.api.ScheduleRow;
import com.example.loan.domain.RepaymentMethod;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 还款计划与提前还款对比计算。全部金额使用 BigDecimal：
 * <ul>
 *   <li>金额保留 2 位小数，HALF_UP 四舍五入；</li>
 *   <li>每期按 [起始日, 起始日+1个月) 的实际天数计息；</li>
 *   <li>期未跨调息日：利息 = 期初余额 × 年利率 / 12（月利率保留 12 位小数）；</li>
 *   <li>期跨调息日：按实际占用天数分段，利息 = 期初余额 × Σ(年利率ᵢ × 天数ᵢ) / (12 × 当期天数)，
 *       整期汇总后一次性四舍五入到分，避免跨段累计误差；</li>
 *   <li>等额本息遇利率调整：自调整生效后首个完整期起按新利率对剩余本金、剩余期数重算月供，
 *       跨调息日的当期仍按原月供还款、仅利息分段；</li>
 *   <li>提前还款在计划起始日计息前冲减本金；调息日与提前还款日重合时，
 *       事件顺序为：新利率段生效 → 冲减本金 → 按新利率计第一期利息；</li>
 *   <li>末期自动结清：末期本金 = 当期期初剩余本金，保证尾期后余额恰好为 0，不出现负余额。</li>
 * </ul>
 */
@Service
public class AmortizationService {

    private static final int MONEY_SCALE = 2;
    private static final int RATE_SCALE = 12;
    private static final RoundingMode ROUND = RoundingMode.HALF_UP;
    /** 模拟计算的安全上限，防止异常参数导致死循环。 */
    private static final int MAX_PERIODS = 1200;

    /**
     * 对同一笔提前还款，分别生成「缩短期限」与「降低月供」两种方案并对比。
     *
     * @param method             还款方式（等额本息 / 等额本金）
     * @param rateSchedule       分段利率时间表（按生效日排列；校验失败抛 IllegalArgumentException）
     * @param scheduleStart      计划起始日（第 1 期计息起始日，提前还款于当日计息前冲减本金）
     * @param remainingPrincipal 当前剩余本金
     * @param remainingPeriods   当前剩余期数（月）
     * @param prepaymentAmount   提前还款金额（直接冲减本金）
     * @param fee                提前还款手续费（一次性成本，不冲减本金）
     */
    public ComparisonResult compare(RepaymentMethod method,
                                    List<RateSegmentInput> rateSchedule,
                                    LocalDate scheduleStart,
                                    BigDecimal remainingPrincipal,
                                    int remainingPeriods,
                                    BigDecimal prepaymentAmount,
                                    BigDecimal fee) {
        validate(method, remainingPrincipal, remainingPeriods, prepaymentAmount, fee);
        RateTimeline timeline = RateTimeline.of(rateSchedule, scheduleStart);

        BigDecimal newPrincipal = money(remainingPrincipal.subtract(prepaymentAmount));

        // 基准：不提前还款，原合同继续执行的汇总
        List<ScheduleRow> baselineRows = schedule(method, timeline, scheduleStart, remainingPrincipal, remainingPeriods);
        PlanSummary baseline = summarize(method, baselineRows, BigDecimal.ZERO);

        // 方案二：降低月供 —— 期限不变，按提前还款后的本金重新计算计划
        List<ScheduleRow> reduceRows = schedule(method, timeline, scheduleStart, newPrincipal, remainingPeriods);
        PlanResult reducePayment = new PlanResult(
                "REDUCE_PAYMENT", "降低月供（期限不变）",
                summarize(method, reduceRows, fee), reduceRows);

        // 方案一：缩短期限 —— 月供水平保持不变，期数相应减少
        List<ScheduleRow> shortenRows = switch (method) {
            // 等额本息：保持原月供金额不变
            case EQUAL_INSTALLMENT -> shortenByFixedPayment(timeline, scheduleStart, newPrincipal,
                    baselineRows.get(0).payment());
            // 等额本金：保持每月偿还本金不变
            case EQUAL_PRINCIPAL -> shortenByFixedPrincipal(timeline, scheduleStart, newPrincipal,
                    monthlyPrincipal(remainingPrincipal, remainingPeriods));
        };
        PlanResult shortenTerm = new PlanResult(
                "SHORTEN_TERM", "缩短期限（月供不变）",
                summarize(method, shortenRows, fee), shortenRows);

        DiffSummary diff = new DiffSummary(
                reducePayment.summary().periods() - shortenTerm.summary().periods(),
                subtractNullable(reducePayment.summary().monthlyPayment(), shortenTerm.summary().monthlyPayment()),
                reducePayment.summary().firstPayment().subtract(shortenTerm.summary().firstPayment()),
                reducePayment.summary().totalInterest().subtract(shortenTerm.summary().totalInterest()),
                reducePayment.summary().totalCost().subtract(shortenTerm.summary().totalCost()));

        return new ComparisonResult(baseline, shortenTerm, reducePayment, diff);
    }

    /**
     * 生成完整还款计划（等额本息或等额本金），按利率时间表逐期计息。
     */
    public List<ScheduleRow> schedule(RepaymentMethod method, List<RateSegmentInput> rateSchedule,
                                      LocalDate scheduleStart, BigDecimal principal, int periods) {
        RateTimeline timeline = RateTimeline.of(rateSchedule, scheduleStart);
        return schedule(method, timeline, scheduleStart, principal, periods);
    }

    private List<ScheduleRow> schedule(RepaymentMethod method, RateTimeline timeline,
                                       LocalDate start, BigDecimal principal, int periods) {
        return switch (method) {
            case EQUAL_INSTALLMENT -> equalInstallment(timeline, start, principal, periods);
            case EQUAL_PRINCIPAL -> equalPrincipal(timeline, start, principal, periods);
        };
    }

    /** 月利率 = 年利率 / 12。 */
    public BigDecimal monthlyRate(BigDecimal annualRate) {
        return annualRate.divide(BigDecimal.valueOf(12), RATE_SCALE, ROUND);
    }

    /** 等额本息月供：M = P·r·(1+r)^n / ((1+r)^n − 1)。 */
    public BigDecimal installmentPayment(BigDecimal principal, BigDecimal monthlyRate, int periods) {
        if (monthlyRate.signum() == 0) {
            return money(principal.divide(BigDecimal.valueOf(periods), MONEY_SCALE, ROUND));
        }
        BigDecimal factor = BigDecimal.ONE.add(monthlyRate).pow(periods);
        return principal.multiply(monthlyRate).multiply(factor)
                .divide(factor.subtract(BigDecimal.ONE), MONEY_SCALE, ROUND);
    }

    /** 等额本金每月偿还本金：P / n。 */
    public BigDecimal monthlyPrincipal(BigDecimal principal, int periods) {
        return principal.divide(BigDecimal.valueOf(periods), MONEY_SCALE, ROUND);
    }

    /**
     * 当期利息：按利率来源分段加权，利息 = 期初余额 × Σ(年利率ᵢ × 天数ᵢ) / (12 × 当期天数)。
     * 单段时退化为 期初余额 × 年利率 / 12。整期一次性四舍五入到分。
     */
    private BigDecimal interest(BigDecimal balance, List<RatePart> parts) {
        BigDecimal weightedRateDays = BigDecimal.ZERO;
        long totalDays = 0;
        for (RatePart part : parts) {
            weightedRateDays = weightedRateDays.add(
                    part.annualRate().multiply(BigDecimal.valueOf(part.days())));
            totalDays += part.days();
        }
        BigDecimal effectiveMonthlyRate = weightedRateDays.divide(
                BigDecimal.valueOf(totalDays * 12L), RATE_SCALE, ROUND);
        return money(balance.multiply(effectiveMonthlyRate));
    }

    /**
     * 等额本息：月供固定；利率调整后自首个完整期起按新利率重算月供；末期按剩余本金结清。
     */
    private List<ScheduleRow> equalInstallment(RateTimeline timeline, LocalDate start,
                                               BigDecimal principal, int periods) {
        BigDecimal paymentRate = timeline.rateAt(start);
        BigDecimal payment = installmentPayment(principal, monthlyRate(paymentRate), periods);
        List<ScheduleRow> rows = new ArrayList<>(periods);
        BigDecimal balance = principal;
        for (int i = 1; i <= periods; i++) {
            LocalDate from = start.plusMonths(i - 1);
            LocalDate to = start.plusMonths(i);
            BigDecimal rateAtStart = timeline.rateAt(from);
            if (rateAtStart.compareTo(paymentRate) != 0) {
                // 利率已调整：按新利率对剩余本金与剩余期数重算月供
                payment = installmentPayment(balance, monthlyRate(rateAtStart), periods - i + 1);
                paymentRate = rateAtStart;
            }
            List<RatePart> parts = timeline.parts(from, to);
            BigDecimal interest = interest(balance, parts);
            BigDecimal principalPart = (i == periods) ? balance : payment.subtract(interest);
            if (principalPart.compareTo(balance) > 0) {
                principalPart = balance;
            }
            if (principalPart.signum() < 0) {
                throw new IllegalArgumentException("月供不足以覆盖当期利息，参数不合理");
            }
            balance = balance.subtract(principalPart);
            rows.add(new ScheduleRow(i, from, to, principalPart.add(interest), principalPart, interest, balance, parts));
            if (balance.signum() == 0) {
                break; // 防御：余额已结清则不再产生后续零元期
            }
        }
        return rows;
    }

    /** 等额本金：每月偿还固定本金，末期按剩余本金结清。 */
    private List<ScheduleRow> equalPrincipal(RateTimeline timeline, LocalDate start,
                                             BigDecimal principal, int periods) {
        BigDecimal monthlyPrincipal = monthlyPrincipal(principal, periods);
        List<ScheduleRow> rows = new ArrayList<>(periods);
        BigDecimal balance = principal;
        for (int i = 1; i <= periods; i++) {
            LocalDate from = start.plusMonths(i - 1);
            LocalDate to = start.plusMonths(i);
            List<RatePart> parts = timeline.parts(from, to);
            BigDecimal interest = interest(balance, parts);
            BigDecimal principalPart = (i == periods) ? balance : monthlyPrincipal.min(balance);
            balance = balance.subtract(principalPart);
            rows.add(new ScheduleRow(i, from, to, principalPart.add(interest), principalPart, interest, balance, parts));
            if (balance.signum() == 0) {
                break;
            }
        }
        return rows;
    }

    /** 缩短期限（等额本息）：保持月供不变，逐月模拟直至结清，末期自动调整为剩余本息。 */
    private List<ScheduleRow> shortenByFixedPayment(RateTimeline timeline, LocalDate start,
                                                    BigDecimal principal, BigDecimal payment) {
        List<ScheduleRow> rows = new ArrayList<>();
        BigDecimal balance = principal;
        while (balance.signum() > 0) {
            if (rows.size() >= MAX_PERIODS) {
                throw new IllegalArgumentException("按期数上限仍无法结清，参数不合理");
            }
            LocalDate from = start.plusMonths(rows.size());
            LocalDate to = start.plusMonths(rows.size() + 1);
            List<RatePart> parts = timeline.parts(from, to);
            BigDecimal interest = interest(balance, parts);
            BigDecimal principalPart = payment.subtract(interest);
            if (principalPart.signum() <= 0) {
                throw new IllegalArgumentException("原月供不足以覆盖提前还款后的当期利息，无法采用缩短期限方案");
            }
            if (principalPart.compareTo(balance) >= 0) {
                principalPart = balance; // 末期结清
            }
            balance = balance.subtract(principalPart);
            rows.add(new ScheduleRow(rows.size() + 1, from, to, principalPart.add(interest),
                    principalPart, interest, balance, parts));
        }
        return rows;
    }

    /** 缩短期限（等额本金）：保持每月偿还本金不变，逐月模拟直至结清。 */
    private List<ScheduleRow> shortenByFixedPrincipal(RateTimeline timeline, LocalDate start,
                                                      BigDecimal principal, BigDecimal monthlyPrincipal) {
        List<ScheduleRow> rows = new ArrayList<>();
        BigDecimal balance = principal;
        while (balance.signum() > 0) {
            if (rows.size() >= MAX_PERIODS) {
                throw new IllegalArgumentException("按期数上限仍无法结清，参数不合理");
            }
            LocalDate from = start.plusMonths(rows.size());
            LocalDate to = start.plusMonths(rows.size() + 1);
            List<RatePart> parts = timeline.parts(from, to);
            BigDecimal interest = interest(balance, parts);
            BigDecimal principalPart = monthlyPrincipal.min(balance); // 末期结清
            balance = balance.subtract(principalPart);
            rows.add(new ScheduleRow(rows.size() + 1, from, to, principalPart.add(interest),
                    principalPart, interest, balance, parts));
        }
        return rows;
    }

    /** 汇总一个还款计划的关键指标。 */
    public PlanSummary summarize(RepaymentMethod method, List<ScheduleRow> rows, BigDecimal fee) {
        BigDecimal totalPayment = BigDecimal.ZERO;
        BigDecimal totalPrincipal = BigDecimal.ZERO;
        BigDecimal totalInterest = BigDecimal.ZERO;
        for (ScheduleRow row : rows) {
            totalPayment = totalPayment.add(row.payment());
            totalPrincipal = totalPrincipal.add(row.principal());
            totalInterest = totalInterest.add(row.interest());
        }
        BigDecimal first = rows.get(0).payment();
        BigDecimal last = rows.get(rows.size() - 1).payment();
        // 等额本金月供逐月递减，不存在“固定月供”，置 null 由前端展示首/末月；
        // 等额本息遇利率调整会重算月供，monthlyPayment 为首期月供
        BigDecimal monthly = (method == RepaymentMethod.EQUAL_INSTALLMENT) ? first : null;
        return new PlanSummary(rows.size(), first, last, monthly,
                totalPrincipal, totalInterest, totalPayment, fee, totalPayment.add(fee));
    }

    private void validate(RepaymentMethod method, BigDecimal remainingPrincipal,
                          int remainingPeriods, BigDecimal prepaymentAmount, BigDecimal fee) {
        if (method == null) {
            throw new IllegalArgumentException("还款方式不能为空");
        }
        if (remainingPrincipal == null || remainingPrincipal.signum() <= 0) {
            throw new IllegalArgumentException("剩余本金必须大于 0");
        }
        if (remainingPeriods < 1 || remainingPeriods > 600) {
            throw new IllegalArgumentException("剩余期数需在 1 ~ 600 之间");
        }
        if (prepaymentAmount == null || prepaymentAmount.signum() <= 0) {
            throw new IllegalArgumentException("提前还款金额必须大于 0");
        }
        if (prepaymentAmount.compareTo(remainingPrincipal) >= 0) {
            throw new IllegalArgumentException("提前还款金额必须小于剩余本金（大于等于剩余本金即为全额结清）");
        }
        if (fee == null || fee.signum() < 0) {
            throw new IllegalArgumentException("手续费不能为负");
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, ROUND);
    }

    private static BigDecimal subtractNullable(BigDecimal a, BigDecimal b) {
        return (a == null || b == null) ? null : a.subtract(b);
    }
}

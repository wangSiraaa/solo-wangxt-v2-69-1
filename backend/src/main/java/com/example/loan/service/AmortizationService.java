package com.example.loan.service;

import com.example.loan.api.ComparisonResult;
import com.example.loan.api.DiffSummary;
import com.example.loan.api.PlanResult;
import com.example.loan.api.PlanSummary;
import com.example.loan.api.ScheduleRow;
import com.example.loan.domain.RepaymentMethod;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 还款计划与提前还款对比计算。全部金额使用 BigDecimal：
 * <ul>
 *   <li>金额保留 2 位小数，HALF_UP 四舍五入；</li>
 *   <li>月利率 = 年利率 / 12，保留 12 位小数；</li>
 *   <li>每期利息 = 期初剩余本金 × 月利率（四舍五入到分）；</li>
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
     * @param annualRate         年利率，小数形式（0.049 表示 4.9%）
     * @param remainingPrincipal 当前剩余本金
     * @param remainingPeriods   当前剩余期数（月）
     * @param prepaymentAmount   提前还款金额（直接冲减本金）
     * @param fee                提前还款手续费（一次性成本，不冲减本金）
     */
    public ComparisonResult compare(RepaymentMethod method,
                                    BigDecimal annualRate,
                                    BigDecimal remainingPrincipal,
                                    int remainingPeriods,
                                    BigDecimal prepaymentAmount,
                                    BigDecimal fee) {
        validate(method, annualRate, remainingPrincipal, remainingPeriods, prepaymentAmount, fee);

        BigDecimal monthlyRate = monthlyRate(annualRate);
        BigDecimal newPrincipal = money(remainingPrincipal.subtract(prepaymentAmount));

        // 基准：不提前还款，原合同继续执行的汇总
        List<ScheduleRow> baselineRows = schedule(method, remainingPrincipal, monthlyRate, remainingPeriods);
        PlanSummary baseline = summarize(method, baselineRows, BigDecimal.ZERO);

        // 方案二：降低月供 —— 期限不变，按提前还款后的本金重新计算月供
        List<ScheduleRow> reduceRows = schedule(method, newPrincipal, monthlyRate, remainingPeriods);
        PlanResult reducePayment = new PlanResult(
                "REDUCE_PAYMENT", "降低月供（期限不变）",
                summarize(method, reduceRows, fee), reduceRows);

        // 方案一：缩短期限 —— 月供水平保持不变，期数相应减少
        List<ScheduleRow> shortenRows = switch (method) {
            // 等额本息：保持原月供金额不变
            case EQUAL_INSTALLMENT -> shortenByFixedPayment(newPrincipal, monthlyRate, baselineRows.get(0).payment());
            // 等额本金：保持每月偿还本金不变
            case EQUAL_PRINCIPAL -> shortenByFixedPrincipal(newPrincipal, monthlyRate,
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
     * 生成完整还款计划（等额本息或等额本金）。
     */
    public List<ScheduleRow> schedule(RepaymentMethod method, BigDecimal principal,
                                      BigDecimal monthlyRate, int periods) {
        return switch (method) {
            case EQUAL_INSTALLMENT -> equalInstallment(principal, monthlyRate, periods);
            case EQUAL_PRINCIPAL -> equalPrincipal(principal, monthlyRate, periods);
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

    /** 等额本息：月供固定，末期按剩余本金结清。 */
    private List<ScheduleRow> equalInstallment(BigDecimal principal, BigDecimal rate, int periods) {
        BigDecimal payment = installmentPayment(principal, rate, periods);
        List<ScheduleRow> rows = new ArrayList<>(periods);
        BigDecimal balance = principal;
        for (int i = 1; i <= periods; i++) {
            BigDecimal interest = money(balance.multiply(rate));
            BigDecimal principalPart = (i == periods) ? balance : payment.subtract(interest);
            if (principalPart.compareTo(balance) > 0) {
                principalPart = balance;
            }
            if (principalPart.signum() < 0) {
                throw new IllegalArgumentException("月供不足以覆盖当期利息，参数不合理");
            }
            balance = balance.subtract(principalPart);
            rows.add(new ScheduleRow(i, principalPart.add(interest), principalPart, interest, balance));
        }
        return rows;
    }

    /** 等额本金：每月偿还固定本金，末期按剩余本金结清。 */
    private List<ScheduleRow> equalPrincipal(BigDecimal principal, BigDecimal rate, int periods) {
        BigDecimal monthlyPrincipal = monthlyPrincipal(principal, periods);
        List<ScheduleRow> rows = new ArrayList<>(periods);
        BigDecimal balance = principal;
        for (int i = 1; i <= periods; i++) {
            BigDecimal interest = money(balance.multiply(rate));
            BigDecimal principalPart = (i == periods) ? balance : monthlyPrincipal.min(balance);
            balance = balance.subtract(principalPart);
            rows.add(new ScheduleRow(i, principalPart.add(interest), principalPart, interest, balance));
        }
        return rows;
    }

    /** 缩短期限（等额本息）：保持月供不变，逐月模拟直至结清，末期自动调整为剩余本息。 */
    private List<ScheduleRow> shortenByFixedPayment(BigDecimal principal, BigDecimal rate, BigDecimal payment) {
        List<ScheduleRow> rows = new ArrayList<>();
        BigDecimal balance = principal;
        while (balance.signum() > 0) {
            if (rows.size() >= MAX_PERIODS) {
                throw new IllegalArgumentException("按期数上限仍无法结清，参数不合理");
            }
            BigDecimal interest = money(balance.multiply(rate));
            BigDecimal principalPart = payment.subtract(interest);
            if (principalPart.signum() <= 0) {
                throw new IllegalArgumentException("原月供不足以覆盖提前还款后的当期利息，无法采用缩短期限方案");
            }
            if (principalPart.compareTo(balance) >= 0) {
                principalPart = balance; // 末期结清
            }
            balance = balance.subtract(principalPart);
            rows.add(new ScheduleRow(rows.size() + 1, principalPart.add(interest), principalPart, interest, balance));
        }
        return rows;
    }

    /** 缩短期限（等额本金）：保持每月偿还本金不变，逐月模拟直至结清。 */
    private List<ScheduleRow> shortenByFixedPrincipal(BigDecimal principal, BigDecimal rate, BigDecimal monthlyPrincipal) {
        List<ScheduleRow> rows = new ArrayList<>();
        BigDecimal balance = principal;
        while (balance.signum() > 0) {
            if (rows.size() >= MAX_PERIODS) {
                throw new IllegalArgumentException("按期数上限仍无法结清，参数不合理");
            }
            BigDecimal interest = money(balance.multiply(rate));
            BigDecimal principalPart = monthlyPrincipal.min(balance); // 末期结清
            balance = balance.subtract(principalPart);
            rows.add(new ScheduleRow(rows.size() + 1, principalPart.add(interest), principalPart, interest, balance));
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
        // 等额本金月供逐月递减，不存在“固定月供”，置 null 由前端展示首/末月
        BigDecimal monthly = (method == RepaymentMethod.EQUAL_INSTALLMENT) ? first : null;
        return new PlanSummary(rows.size(), first, last, monthly,
                totalPrincipal, totalInterest, totalPayment, fee, totalPayment.add(fee));
    }

    private void validate(RepaymentMethod method, BigDecimal annualRate, BigDecimal remainingPrincipal,
                          int remainingPeriods, BigDecimal prepaymentAmount, BigDecimal fee) {
        if (method == null) {
            throw new IllegalArgumentException("还款方式不能为空");
        }
        if (annualRate == null || annualRate.signum() < 0 || annualRate.compareTo(new BigDecimal("0.36")) > 0) {
            throw new IllegalArgumentException("年利率需在 0 ~ 36% 之间");
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

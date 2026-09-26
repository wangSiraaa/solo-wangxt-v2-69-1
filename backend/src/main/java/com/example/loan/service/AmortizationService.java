package com.example.loan.service;

import com.example.loan.api.ComparisonResult;
import com.example.loan.api.DiffSummary;
import com.example.loan.api.InterestBreakdown;
import com.example.loan.api.PlanResult;
import com.example.loan.api.PlanSummary;
import com.example.loan.api.RateSegmentView;
import com.example.loan.api.ScheduleRow;
import com.example.loan.domain.LoanContract;
import com.example.loan.domain.RepaymentMethod;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 还款计划与提前还款对比计算。全部金额使用 BigDecimal：
 * <ul>
 *   <li>金额保留 2 位小数，HALF_UP 四舍五入；</li>
 *   <li>月利率（旧的单利率接口）= 年利率 / 12，保留 12 位小数；</li>
 *   <li>分段利率按实际占用天数计息，日利率 = 年利率 / 360（ACT/360）；</li>
 *   <li>跨调息日的一期拆分为多个利率段，分段利息分摊舍入差额，合计仍等于当期利息；</li>
 *   <li>末期自动结清，保证尾期后余额恰好为 0，不出现负余额或跨期累计误差。</li>
 * </ul>
 */
@Service
public class AmortizationService {

    private static final int MONEY_SCALE = 2;
    private static final int RATE_SCALE = 12;
    private static final int CALC_SCALE = 18;
    private static final RoundingMode ROUND = RoundingMode.HALF_UP;
    private static final BigDecimal DAYS_PER_YEAR = BigDecimal.valueOf(360);
    /** 模拟计算的安全上限，防止异常参数导致死循环。 */
    private static final int MAX_PERIODS = 1200;

    private enum PlanMode {
        BASELINE, REDUCE_PAYMENT, SHORTEN_TERM
    }

    /**
     * 旧版单利率试算入口，保持既有行为：月利率 = 年利率 / 12。
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

        List<ScheduleRow> baselineRows = schedule(method, remainingPrincipal, monthlyRate, remainingPeriods);
        PlanSummary baseline = summarize(method, baselineRows, BigDecimal.ZERO);

        List<ScheduleRow> reduceRows = schedule(method, newPrincipal, monthlyRate, remainingPeriods);
        PlanResult reducePayment = new PlanResult(
                "REDUCE_PAYMENT", "降低月供（期限不变）",
                summarize(method, reduceRows, fee), reduceRows);

        List<ScheduleRow> shortenRows = switch (method) {
            case EQUAL_INSTALLMENT -> shortenByFixedPayment(newPrincipal, monthlyRate, baselineRows.get(0).payment());
            case EQUAL_PRINCIPAL -> shortenByFixedPrincipal(newPrincipal, monthlyRate,
                    monthlyPrincipal(remainingPrincipal, remainingPeriods));
        };
        PlanResult shortenTerm = new PlanResult(
                "SHORTEN_TERM", "缩短期限（月供不变）",
                summarize(method, shortenRows, fee), shortenRows);

        return new ComparisonResult(baseline, shortenTerm, reducePayment, diff(reducePayment, shortenTerm));
    }

    /**
     * 基于利率调整时间表的试算入口。
     *
     * <p>提前还款可发生在计划起始日或之后某个一期起始日。若提前还款日与调息日相同，事件顺序固定为：
     * 期初先按提前还款金额冲减本金，再按同日新生效利率对冲减后的本金起息。</p>
     */
    public ComparisonResult compareScheduled(RepaymentMethod method,
                                             BigDecimal remainingPrincipal,
                                             int remainingPeriods,
                                             LocalDate scheduleStartDate,
                                             List<LoanContract.StoredRateSegment> rateSchedule,
                                             Integer rateScheduleVersion,
                                             BigDecimal prepaymentAmount,
                                             LocalDate prepaymentDate,
                                             BigDecimal fee) {
        validateScheduled(method, remainingPrincipal, remainingPeriods, scheduleStartDate,
                rateSchedule, prepaymentAmount, fee);
        LocalDate eventDate = prepaymentDate == null ? scheduleStartDate : prepaymentDate;
        long prepaymentPeriod = ChronoUnit.MONTHS.between(
                scheduleStartDate.withDayOfMonth(1), eventDate.withDayOfMonth(1)) + 1;
        if (eventDate.isBefore(scheduleStartDate)) {
            throw new IllegalArgumentException("提前还款日不能早于模拟开始日期");
        }
        if (eventDate.getDayOfMonth() != scheduleStartDate.getDayOfMonth()) {
            throw new IllegalArgumentException("提前还款日必须是账期起始日（日号需与模拟开始日期一致）");
        }
        if (!eventDate.isEqual(scheduleStartDate.plusMonths(prepaymentPeriod - 1))) {
            throw new IllegalArgumentException("提前还款日必须是一期起始日: " + eventDate);
        }
        if (prepaymentPeriod < 1 || prepaymentPeriod >= remainingPeriods) {
            throw new IllegalArgumentException("提前还款期次需在剩余期限内");
        }
        if (prepaymentAmount == null || prepaymentAmount.signum() <= 0) {
            throw new IllegalArgumentException("提前还款金额必须大于 0");
        }
        if (prepaymentAmount.compareTo(remainingPrincipal) >= 0) {
            throw new IllegalArgumentException("提前还款金额必须小于剩余本金（大于等于剩余本金即为全额结清）");
        }

        BigDecimal newPrincipal = money(remainingPrincipal.subtract(prepaymentAmount));
        List<ScheduleRow> baselineRows = simulateScheduled(
                method, remainingPrincipal, remainingPeriods, scheduleStartDate,
                rateSchedule, remainingPrincipal, 1, PlanMode.BASELINE, null);
        PlanSummary baseline = summarize(method, baselineRows, BigDecimal.ZERO);

        List<ScheduleRow> reduceRows = simulateScheduled(
                method, newPrincipal, remainingPeriods, scheduleStartDate,
                rateSchedule, newPrincipal, (int) prepaymentPeriod, PlanMode.REDUCE_PAYMENT, null);
        PlanResult reducePayment = planResult(method, "REDUCE_PAYMENT", "降低月供（期限不变）", reduceRows, fee,
                scheduleStartDate, rateScheduleVersion, rateSchedule, eventDate);

        List<ScheduleRow> shortenRows = switch (method) {
            case EQUAL_INSTALLMENT -> simulateScheduled(
                    method, newPrincipal, remainingPeriods, scheduleStartDate,
                    rateSchedule, remainingPrincipal, (int) prepaymentPeriod,
                    PlanMode.SHORTEN_TERM, baselineRows);
            case EQUAL_PRINCIPAL -> simulateScheduled(
                    method, newPrincipal, remainingPeriods, scheduleStartDate,
                    rateSchedule, remainingPrincipal, (int) prepaymentPeriod,
                    PlanMode.SHORTEN_TERM, baselineRows);
        };
        PlanResult shortenTerm = planResult(method, "SHORTEN_TERM", "缩短期限（月供不变）", shortenRows, fee,
                scheduleStartDate, rateScheduleVersion, rateSchedule, eventDate);

        return new ComparisonResult(baseline, shortenTerm, reducePayment, diff(reducePayment, shortenTerm));
    }

    /**
     * 生成完整还款计划（等额本息或等额本金，旧版单利率）。
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

    private List<ScheduleRow> simulateScheduled(
            RepaymentMethod method,
            BigDecimal principal,
            int contractualPeriods,
            LocalDate startDate,
            List<LoanContract.StoredRateSegment> segments,
            BigDecimal baselinePrincipal,
            int startPeriod,
            PlanMode mode,
            List<ScheduleRow> baselineRows) {
        List<ScheduleRow> rows = new ArrayList<>();
        BigDecimal balance = principal;
        int period = startPeriod;
        int scheduledRemainingPeriods = contractualPeriods - period + 1;
        BigDecimal fixedPrincipal = method == RepaymentMethod.EQUAL_PRINCIPAL
                ? (mode == PlanMode.SHORTEN_TERM
                    ? monthlyPrincipal(baselinePrincipal, contractualPeriods)
                    : monthlyPrincipal(principal, scheduledRemainingPeriods))
                : null;
        BigDecimal payment = null;
        BigDecimal lastEndRate = null;
        int simulatedRows = 0;

        while (balance.signum() > 0) {
            if (simulatedRows >= MAX_PERIODS) {
                throw new IllegalArgumentException("按期数上限仍无法结清，参数不合理");
            }
            LocalDate periodStart = startDate.plusMonths(period - 1L);
            LocalDate dueDate = startDate.plusMonths(period);
            AccruedInterest accrued = accrue(balance, periodStart, dueDate, segments);
            BigDecimal startRate = activeRate(segments, periodStart);
            BigDecimal endRate = activeRate(segments, dueDate.minusDays(1));
            boolean rateChangedInside = accrued.breakdown().size() > 1;
            boolean previousRateChanged = lastEndRate != null && startRate.compareTo(lastEndRate) != 0;
            boolean resetPayment = simulatedRows == 0 || previousRateChanged || rateChangedInside;

            if (method == RepaymentMethod.EQUAL_INSTALLMENT) {
                if (mode == PlanMode.SHORTEN_TERM) {
                    payment = baselineRows.get(period - 1).payment();
                } else if (resetPayment) {
                    int remaining = contractualPeriods - period + 1;
                    payment = variableInstallmentPayment(balance, periodStart, dueDate, segments, remaining);
                }
            }

            BigDecimal principalPart;
            if (method == RepaymentMethod.EQUAL_PRINCIPAL) {
                boolean fixedFinal = mode != PlanMode.SHORTEN_TERM && period == contractualPeriods;
                principalPart = fixedFinal ? balance : fixedPrincipal.min(balance);
            } else {
                principalPart = payment.subtract(accrued.interest());
                if (principalPart.signum() <= 0) {
                    throw new IllegalArgumentException("月供不足以覆盖当期利息，参数不合理");
                }
                boolean fixedFinal = mode != PlanMode.SHORTEN_TERM && period == contractualPeriods;
                if (fixedFinal || principalPart.compareTo(balance) >= 0) {
                    principalPart = balance;
                }
            }

            BigDecimal actualPayment = principalPart.add(accrued.interest());
            balance = money(balance.subtract(principalPart));
            rows.add(new ScheduleRow(period, periodStart, dueDate, actualPayment, principalPart,
                    accrued.interest(), balance, accrued.breakdown()));
            lastEndRate = endRate;

            if (mode != PlanMode.SHORTEN_TERM && period == contractualPeriods && balance.signum() > 0) {
                throw new IllegalStateException("固定期限计划末期未能结清");
            }
            period++;
            simulatedRows++;
        }
        return rows;
    }

    /** 按期拆分利息，并把总分的舍入差额分摊到金额最大的利率段。 */
    private AccruedInterest accrue(BigDecimal balance,
                                   LocalDate periodStart,
                                   LocalDate dueDate,
                                   List<LoanContract.StoredRateSegment> segments) {
        List<LocalDate> boundaries = new ArrayList<>();
        boundaries.add(periodStart);
        for (LoanContract.StoredRateSegment segment : segments) {
            LocalDate date = segment.effectiveDate();
            if (date.isAfter(periodStart) && date.isBefore(dueDate)) {
                boundaries.add(date);
            }
        }
        boundaries.sort(Comparator.naturalOrder());

        List<RawSegment> rawSegments = new ArrayList<>();
        BigDecimal rawTotal = BigDecimal.ZERO;
        for (int i = 0; i < boundaries.size(); i++) {
            LocalDate segmentStart = boundaries.get(i);
            LocalDate next = (i + 1 < boundaries.size()) ? boundaries.get(i + 1) : dueDate;
            LocalDate segmentEnd = next.minusDays(1);
            long days = ChronoUnit.DAYS.between(segmentStart, next);
            LoanContract.StoredRateSegment source = activeSegment(segments, segmentStart);
            BigDecimal rawInterest = balance.multiply(source.annualRate())
                    .multiply(BigDecimal.valueOf(days))
                    .divide(DAYS_PER_YEAR, CALC_SCALE, ROUND);
            rawTotal = rawTotal.add(rawInterest);
            rawSegments.add(new RawSegment(segmentStart, segmentEnd, source.effectiveDate(),
                    source.annualRate(), Math.toIntExact(days), rawInterest));
        }

        BigDecimal totalInterest = money(rawTotal);
        List<BigDecimal> rounded = rawSegments.stream()
                .map(s -> money(s.rawInterest()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        BigDecimal roundedTotal = rounded.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal difference = totalInterest.subtract(roundedTotal);
        if (difference.signum() != 0) {
            int largest = 0;
            for (int i = 1; i < rawSegments.size(); i++) {
                if (rawSegments.get(i).rawInterest().compareTo(rawSegments.get(largest).rawInterest()) > 0) {
                    largest = i;
                }
            }
            rounded.set(largest, rounded.get(largest).add(difference));
        }

        List<InterestBreakdown> breakdown = new ArrayList<>();
        for (int i = 0; i < rawSegments.size(); i++) {
            RawSegment raw = rawSegments.get(i);
            breakdown.add(new InterestBreakdown(raw.segmentStart(), raw.segmentEnd(),
                    raw.effectiveDate(), raw.annualRate(), raw.days(), rounded.get(i)));
        }
        return new AccruedInterest(totalInterest, breakdown);
    }

    /** 已知未来各期利率时，求使未来现金流现值等于当前本金的固定月供。 */
    private BigDecimal variableInstallmentPayment(BigDecimal balance,
                                                   LocalDate currentStart,
                                                   LocalDate currentDue,
                                                   List<LoanContract.StoredRateSegment> segments,
                                                   int remainingPeriods) {
        BigDecimal discountFactor = BigDecimal.ONE;
        BigDecimal presentValueFactor = BigDecimal.ZERO;
        LocalDate start = currentStart;
        LocalDate due = currentDue;
        for (int i = 0; i < remainingPeriods; i++) {
            BigDecimal periodicRate = periodicRate(start, due, segments);
            discountFactor = discountFactor.divide(BigDecimal.ONE.add(periodicRate), CALC_SCALE, ROUND);
            presentValueFactor = presentValueFactor.add(discountFactor);
            start = due;
            due = currentStart.plusMonths(i + 2L);
        }
        BigDecimal payment = balance.divide(presentValueFactor, CALC_SCALE, ROUND);
        return money(payment);
    }

    private BigDecimal periodicRate(LocalDate start, LocalDate due,
                                    List<LoanContract.StoredRateSegment> segments) {
        long totalDays = ChronoUnit.DAYS.between(start, due);
        BigDecimal weighted = BigDecimal.ZERO;
        List<LocalDate> boundaries = new ArrayList<>();
        boundaries.add(start);
        segments.stream()
                .map(LoanContract.StoredRateSegment::effectiveDate)
                .filter(d -> d.isAfter(start) && d.isBefore(due))
                .forEach(boundaries::add);
        boundaries.sort(Comparator.naturalOrder());
        for (int i = 0; i < boundaries.size(); i++) {
            LocalDate segmentStart = boundaries.get(i);
            LocalDate segmentEnd = i + 1 < boundaries.size() ? boundaries.get(i + 1) : due;
            long days = ChronoUnit.DAYS.between(segmentStart, segmentEnd);
            weighted = weighted.add(activeRate(segments, segmentStart).multiply(BigDecimal.valueOf(days)));
        }
        return weighted.divide(DAYS_PER_YEAR.multiply(BigDecimal.valueOf(totalDays)), CALC_SCALE, ROUND);
    }

    private LoanContract.StoredRateSegment activeSegment(List<LoanContract.StoredRateSegment> segments,
                                                         LocalDate date) {
        return segments.stream()
                .filter(s -> !date.isBefore(s.effectiveDate()))
                .max(Comparator.comparing(LoanContract.StoredRateSegment::effectiveDate))
                .orElseThrow(() -> new IllegalArgumentException("利率时间表未覆盖日期: " + date));
    }

    private BigDecimal activeRate(List<LoanContract.StoredRateSegment> segments, LocalDate date) {
        return activeSegment(segments, date).annualRate();
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
                principalPart = balance;
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
            BigDecimal principalPart = monthlyPrincipal.min(balance);
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
        BigDecimal monthly = (method == RepaymentMethod.EQUAL_INSTALLMENT) ? first : null;
        return new PlanSummary(rows.size(), first, last, monthly,
                totalPrincipal, totalInterest, totalPayment, fee, totalPayment.add(fee));
    }

    private PlanResult planResult(RepaymentMethod method, String code, String label, List<ScheduleRow> rows,
                                  BigDecimal fee, LocalDate startDate, Integer version,
                                  List<LoanContract.StoredRateSegment> segments,
                                  LocalDate prepaymentDate) {
        List<RateSegmentView> views = segments.stream()
                .map(s -> new RateSegmentView(s.effectiveDate(), s.annualRate()))
                .toList();
        return new PlanResult(code, label, summarize(method, rows, fee), rows,
                startDate, version, views, prepaymentDate);
    }

    private DiffSummary diff(PlanResult reducePayment, PlanResult shortenTerm) {
        return new DiffSummary(
                reducePayment.summary().periods() - shortenTerm.summary().periods(),
                subtractNullable(reducePayment.summary().monthlyPayment(), shortenTerm.summary().monthlyPayment()),
                reducePayment.summary().firstPayment().subtract(shortenTerm.summary().firstPayment()),
                reducePayment.summary().totalInterest().subtract(shortenTerm.summary().totalInterest()),
                reducePayment.summary().totalCost().subtract(shortenTerm.summary().totalCost()));
    }

    private void validate(RepaymentMethod method, BigDecimal annualRate, BigDecimal remainingPrincipal,
                          int remainingPeriods, BigDecimal prepaymentAmount, BigDecimal fee) {
        if (method == null) {
            throw new IllegalArgumentException("还款方式不能为空");
        }
        validateRate(annualRate);
        validatePrincipal(remainingPrincipal);
        validatePeriods(remainingPeriods);
        validatePrepayment(remainingPrincipal, prepaymentAmount);
        validateFee(fee);
    }

    private void validateScheduled(RepaymentMethod method, BigDecimal remainingPrincipal, int remainingPeriods,
                                   LocalDate startDate, List<LoanContract.StoredRateSegment> segments,
                                   BigDecimal prepaymentAmount, BigDecimal fee) {
        if (method == null) {
            throw new IllegalArgumentException("还款方式不能为空");
        }
        validatePrincipal(remainingPrincipal);
        validatePeriods(remainingPeriods);
        validatePrepayment(remainingPrincipal, prepaymentAmount);
        validateFee(fee);
        if (startDate == null) {
            throw new IllegalArgumentException("模拟开始日期不能为空");
        }
        if (segments == null || segments.isEmpty()) {
            throw new IllegalArgumentException("利率时间表至少需要一个利率段");
        }
        if (segments.get(0).effectiveDate().isAfter(startDate)) {
            throw new IllegalArgumentException("首个利率段生效日必须不晚于模拟开始日期，利率时间表不能有空档");
        }
        for (int i = 1; i < segments.size(); i++) {
            if (!segments.get(i).effectiveDate().isAfter(segments.get(i - 1).effectiveDate())) {
                throw new IllegalArgumentException("利率段必须按生效日严格升序，且同一日期不能多次调整");
            }
        }
        for (LoanContract.StoredRateSegment segment : segments) {
            if (segment.annualRate() == null
                    || segment.annualRate().signum() < 0
                    || segment.annualRate().compareTo(new BigDecimal("0.36")) > 0) {
                throw new IllegalArgumentException("分段年利率需在 0 ~ 36% 之间");
            }
            if (segment.annualRate().stripTrailingZeros().scale() > 6) {
                throw new IllegalArgumentException("分段年利率最多保留 6 位小数");
            }
        }
    }

    private void validateRate(BigDecimal annualRate) {
        if (annualRate == null || annualRate.signum() < 0 || annualRate.compareTo(new BigDecimal("0.36")) > 0) {
            throw new IllegalArgumentException("年利率需在 0 ~ 36% 之间");
        }
    }

    private void validatePrincipal(BigDecimal remainingPrincipal) {
        if (remainingPrincipal == null || remainingPrincipal.signum() <= 0) {
            throw new IllegalArgumentException("剩余本金必须大于 0");
        }
    }

    private void validatePeriods(int remainingPeriods) {
        if (remainingPeriods < 1 || remainingPeriods > 600) {
            throw new IllegalArgumentException("剩余期数需在 1 ~ 600 之间");
        }
    }

    private void validatePrepayment(BigDecimal remainingPrincipal, BigDecimal prepaymentAmount) {
        if (prepaymentAmount == null || prepaymentAmount.signum() <= 0) {
            throw new IllegalArgumentException("提前还款金额必须大于 0");
        }
        if (prepaymentAmount.compareTo(remainingPrincipal) >= 0) {
            throw new IllegalArgumentException("提前还款金额必须小于剩余本金（大于等于剩余本金即为全额结清）");
        }
    }

    private void validateFee(BigDecimal fee) {
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

    private record AccruedInterest(BigDecimal interest, List<InterestBreakdown> breakdown) {
    }

    private record RawSegment(LocalDate segmentStart,
                              LocalDate segmentEnd,
                              LocalDate effectiveDate,
                              BigDecimal annualRate,
                              int days,
                              BigDecimal rawInterest) {
    }
}

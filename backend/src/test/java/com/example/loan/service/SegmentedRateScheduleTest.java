package com.example.loan.service;

import com.example.loan.api.ComparisonResult;
import com.example.loan.api.PlanResult;
import com.example.loan.api.RatePart;
import com.example.loan.api.RateSegmentInput;
import com.example.loan.api.ScheduleRow;
import com.example.loan.domain.RepaymentMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.example.loan.service.AmortizationServiceTest.assertScheduleConsistent;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 分段利率时间表的验收测试：
 * <ul>
 *   <li>期中调息按实际占用天数拆分利息，汇总准确；</li>
 *   <li>同日多次调整、起始空档、利率超精度均被拒绝；</li>
 *   <li>提前还款恰逢调息日时事件顺序明确：新利率先生效 → 冲减本金 → 计息；</li>
 *   <li>等额本息在利率调整后首个完整期重算月供；</li>
 *   <li>尾期结清为零、金额按规则舍入、无负余额或跨期累计误差。</li>
 * </ul>
 */
class SegmentedRateScheduleTest {

    private final AmortizationService service = new AmortizationService();

    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    // ---------- 期中调息：拆分利息且汇总准确 ----------

    @Test
    void midPeriodRateChange_splitsInterestByActualDays() {
        // 2026-02-15 降息 6% → 3%，第 2 期 [02-01, 03-01) 恰好跨越调息日
        List<RateSegmentInput> segments = List.of(
                new RateSegmentInput(START, new BigDecimal("0.06")),
                new RateSegmentInput(LocalDate.of(2026, 2, 15), new BigDecimal("0.03")));
        List<ScheduleRow> rows = service.schedule(
                RepaymentMethod.EQUAL_PRINCIPAL, segments, START, new BigDecimal("120000.00"), 12);

        assertEquals(12, rows.size());
        // 第 1 期（整期 6%）：120,000 × 0.06 / 12 = 600.00
        assertEquals(new BigDecimal("600.00"), rows.get(0).interest());
        assertEquals(1, rows.get(0).rateParts().size());

        // 第 2 期跨越调息日：02-01~02-15 共 14 天按 6%，02-15~03-01 共 14 天按 3%
        ScheduleRow crossing = rows.get(1);
        assertEquals(LocalDate.of(2026, 2, 1), crossing.periodStart());
        assertEquals(LocalDate.of(2026, 3, 1), crossing.periodEnd());
        assertEquals(2, crossing.rateParts().size());
        RatePart first = crossing.rateParts().get(0);
        RatePart second = crossing.rateParts().get(1);
        assertEquals(14, first.days());
        assertEquals(0, new BigDecimal("0.06").compareTo(first.annualRate()));
        assertEquals(LocalDate.of(2026, 2, 1), first.from());
        assertEquals(LocalDate.of(2026, 2, 15), first.to());
        assertEquals(14, second.days());
        assertEquals(0, new BigDecimal("0.03").compareTo(second.annualRate()));
        // 利息 = 110,000 × (0.06×14 + 0.03×14) / (12×28) = 110,000 × 0.00375 = 412.50
        assertEquals(new BigDecimal("412.50"), crossing.interest());

        // 第 3 期起整期适用新利率：100,000 × 0.03 / 12 = 250.00
        assertEquals(new BigDecimal("250.00"), rows.get(2).interest());
        assertEquals(1, rows.get(2).rateParts().size());

        // 汇总准确：总利息 = 各期利息之和；本金合计 = 本金；尾期结清为零
        assertScheduleConsistent(rows, new BigDecimal("120000.00"));
        BigDecimal sumInterest = rows.stream().map(ScheduleRow::interest).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(sumInterest, service.summarize(RepaymentMethod.EQUAL_PRINCIPAL, rows, BigDecimal.ZERO).totalInterest());
    }

    @Test
    void multipleMidPeriodChanges_splitIntoThreeParts() {
        // 一期内跨两次调息：01-01 起 6%，01-11 起 4%，01-21 起 2%
        List<RateSegmentInput> segments = List.of(
                new RateSegmentInput(START, new BigDecimal("0.06")),
                new RateSegmentInput(LocalDate.of(2026, 1, 11), new BigDecimal("0.04")),
                new RateSegmentInput(LocalDate.of(2026, 1, 21), new BigDecimal("0.02")));
        List<ScheduleRow> rows = service.schedule(
                RepaymentMethod.EQUAL_PRINCIPAL, segments, START, new BigDecimal("31000.00"), 2);

        ScheduleRow first = rows.get(0);
        assertEquals(3, first.rateParts().size());
        assertEquals(10, first.rateParts().get(0).days());
        assertEquals(10, first.rateParts().get(1).days());
        assertEquals(11, first.rateParts().get(2).days());
        // 利息 = 31,000 × (0.06×10 + 0.04×10 + 0.02×11) / (12×31) = 31,000 × 1.22/372 ≈ 101.67
        assertEquals(new BigDecimal("101.67"), first.interest());
        assertScheduleConsistent(rows, new BigDecimal("31000.00"));
    }

    // ---------- 校验：同日多次调整 / 空档 / 精度 ----------

    @Test
    void sameDayMultipleAdjustments_rejected() {
        List<RateSegmentInput> segments = List.of(
                new RateSegmentInput(START, new BigDecimal("0.05")),
                new RateSegmentInput(LocalDate.of(2026, 6, 1), new BigDecimal("0.04")),
                new RateSegmentInput(LocalDate.of(2026, 6, 1), new BigDecimal("0.045")));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                service.schedule(RepaymentMethod.EQUAL_PRINCIPAL, segments, START,
                        new BigDecimal("100000.00"), 12));
        assertTrue(e.getMessage().contains("同一生效日"), e.getMessage());
    }

    @Test
    void gapBeforeFirstSegment_rejected() {
        // 首个利率段生效日晚于计划起始日 → 起始空档
        List<RateSegmentInput> segments = List.of(
                new RateSegmentInput(LocalDate.of(2026, 2, 1), new BigDecimal("0.05")));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                service.schedule(RepaymentMethod.EQUAL_PRINCIPAL, segments, START,
                        new BigDecimal("100000.00"), 12));
        assertTrue(e.getMessage().contains("空档"), e.getMessage());
    }

    @Test
    void excessiveRatePrecision_rejected() {
        List<RateSegmentInput> segments = List.of(
                new RateSegmentInput(START, new BigDecimal("0.0491234"))); // 7 位小数
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                service.schedule(RepaymentMethod.EQUAL_PRINCIPAL, segments, START,
                        new BigDecimal("100000.00"), 12));
        assertTrue(e.getMessage().contains("6 位小数"), e.getMessage());
    }

    @Test
    void rateOutOfRange_rejected() {
        List<RateSegmentInput> segments = List.of(
                new RateSegmentInput(START, new BigDecimal("0.37")));
        assertThrows(IllegalArgumentException.class, () ->
                service.schedule(RepaymentMethod.EQUAL_PRINCIPAL, segments, START,
                        new BigDecimal("100000.00"), 12));
    }

    @Test
    void emptySchedule_rejected() {
        assertThrows(IllegalArgumentException.class, () ->
                service.schedule(RepaymentMethod.EQUAL_PRINCIPAL, List.of(), START,
                        new BigDecimal("100000.00"), 12));
    }

    // ---------- 提前还款恰逢调息日：事件顺序明确 ----------

    @Test
    void prepaymentOnRateChangeDate_newRateAppliesBeforeInterest() {
        // 调息日 == 计划起始日（提前还款日）：新利率段当日生效，先冲减本金再计息
        List<RateSegmentInput> segments = List.of(
                new RateSegmentInput(LocalDate.of(2025, 12, 1), new BigDecimal("0.048")),
                new RateSegmentInput(START, new BigDecimal("0.036")));
        ComparisonResult result = service.compare(
                RepaymentMethod.EQUAL_INSTALLMENT, segments, START,
                new BigDecimal("100000.00"), 12, new BigDecimal("20000.00"), BigDecimal.ZERO);

        // 缩短期限方案第 1 期：80,000 × 0.036 / 12 = 240.00（冲减后本金 × 当日生效的新利率）
        ScheduleRow shortenFirst = result.shortenTerm().schedule().get(0);
        assertEquals(new BigDecimal("240.00"), shortenFirst.interest());
        assertEquals(0, new BigDecimal("0.036").compareTo(shortenFirst.rateParts().get(0).annualRate()));

        // 降低月供方案第 1 期同样按 80,000 × 3.6% / 12 计息
        assertEquals(new BigDecimal("240.00"), result.reducePayment().schedule().get(0).interest());
        // 若误用旧利率则为 320.00，误用旧本金则为 300.00 —— 两者均被排除
        assertNotEquals(new BigDecimal("320.00"), result.reducePayment().schedule().get(0).interest());
        assertNotEquals(new BigDecimal("300.00"), result.reducePayment().schedule().get(0).interest());
    }

    // ---------- 等额本息：利率调整后重算月供 ----------

    @Test
    void equalInstallment_recalculatesPaymentAfterRateChange() {
        // 2026-04-01（第 4 期期初，与期边界对齐）降息 4.8% → 2.4%
        List<RateSegmentInput> segments = List.of(
                new RateSegmentInput(START, new BigDecimal("0.048")),
                new RateSegmentInput(LocalDate.of(2026, 4, 1), new BigDecimal("0.024")));
        List<ScheduleRow> rows = service.schedule(
                RepaymentMethod.EQUAL_INSTALLMENT, segments, START, new BigDecimal("100000.00"), 12);

        assertEquals(12, rows.size());
        BigDecimal initialPayment = rows.get(0).payment();
        // 前 3 期月供不变
        for (int i = 0; i < 3; i++) {
            assertEquals(initialPayment, rows.get(i).payment(), "第 " + (i + 1) + " 期月供");
        }
        // 第 4 期起按新利率重算月供，且之后保持稳定（除末期结清）
        BigDecimal recalculated = rows.get(3).payment();
        assertNotEquals(initialPayment, recalculated);
        for (int i = 3; i < 11; i++) {
            assertEquals(recalculated, rows.get(i).payment(), "第 " + (i + 1) + " 期月供");
        }
        // 重算值 = 按第 4 期期初余额、新利率、剩余 9 期的等额本息月供
        BigDecimal expected = service.installmentPayment(
                rows.get(2).balance(), service.monthlyRate(new BigDecimal("0.024")), 9);
        assertEquals(expected, recalculated);
        assertScheduleConsistent(rows, new BigDecimal("100000.00"));
    }

    @Test
    void equalInstallment_midPeriodChange_keepsPaymentForCrossingPeriod() {
        // 2026-02-15 降息：第 2 期跨调息日，仍按原月供还款、仅利息分段；第 3 期起重算月供
        List<RateSegmentInput> segments = List.of(
                new RateSegmentInput(START, new BigDecimal("0.048")),
                new RateSegmentInput(LocalDate.of(2026, 2, 15), new BigDecimal("0.024")));
        List<ScheduleRow> rows = service.schedule(
                RepaymentMethod.EQUAL_INSTALLMENT, segments, START, new BigDecimal("100000.00"), 12);

        BigDecimal initialPayment = rows.get(0).payment();
        ScheduleRow crossing = rows.get(1);
        assertEquals(2, crossing.rateParts().size());
        assertEquals(initialPayment, crossing.payment(), "跨调息日的当期月供不变");
        // 第 3 期起重算月供
        assertNotEquals(initialPayment, rows.get(2).payment());
        assertScheduleConsistent(rows, new BigDecimal("100000.00"));
    }

    // ---------- 提前还款两种策略 × 分段利率：尾期结清与汇总 ----------

    @Test
    void prepaymentWithSegmentedRates_bothStrategiesSettleToZero() {
        // 三段利率，其中一次调息落在期中间
        List<RateSegmentInput> segments = List.of(
                new RateSegmentInput(START, new BigDecimal("0.055")),
                new RateSegmentInput(LocalDate.of(2026, 7, 20), new BigDecimal("0.042")),
                new RateSegmentInput(LocalDate.of(2027, 1, 1), new BigDecimal("0.038")));
        BigDecimal principal = new BigDecimal("500000.00");
        BigDecimal prepay = new BigDecimal("80000.00");
        BigDecimal newPrincipal = new BigDecimal("420000.00");

        for (RepaymentMethod method : RepaymentMethod.values()) {
            ComparisonResult result = service.compare(method, segments, START, principal, 36, prepay,
                    new BigDecimal("300.00"));

            assertScheduleConsistent(result.shortenTerm().schedule(), newPrincipal);
            assertScheduleConsistent(result.reducePayment().schedule(), newPrincipal);

            // 降低月供方案期数不变；缩短期限方案期数更短且总利息更低
            assertEquals(36, result.reducePayment().summary().periods());
            PlanResult shorten = result.shortenTerm();
            assertTrue(shorten.summary().periods() < 36);
            assertTrue(shorten.summary().totalInterest()
                            .compareTo(result.reducePayment().summary().totalInterest()) < 0,
                    method + "：缩短期限应更省利息");

            // 汇总 = 逐期之和（无跨期累计误差）；手续费计入总成本
            for (PlanResult plan : List.of(shorten, result.reducePayment())) {
                BigDecimal sumInterest = plan.schedule().stream()
                        .map(ScheduleRow::interest).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal sumPayment = plan.schedule().stream()
                        .map(ScheduleRow::payment).reduce(BigDecimal.ZERO, BigDecimal::add);
                assertEquals(sumInterest, plan.summary().totalInterest());
                assertEquals(sumPayment, plan.summary().totalPayment());
                assertEquals(newPrincipal, plan.summary().totalPrincipal());
                assertEquals(plan.summary().totalPayment().add(new BigDecimal("300.00")),
                        plan.summary().totalCost());
            }
        }
    }

    @Test
    void rateIncrease_shortenTermStillSettles() {
        // 利率上调场景：月供不变时利息上升，缩短期限方案期数相应变化，尾期仍结清为零
        List<RateSegmentInput> segments = List.of(
                new RateSegmentInput(START, new BigDecimal("0.03")),
                new RateSegmentInput(LocalDate.of(2026, 6, 1), new BigDecimal("0.06")));
        ComparisonResult result = service.compare(
                RepaymentMethod.EQUAL_INSTALLMENT, segments, START,
                new BigDecimal("200000.00"), 24, new BigDecimal("50000.00"), BigDecimal.ZERO);
        assertScheduleConsistent(result.shortenTerm().schedule(), new BigDecimal("150000.00"));
        assertScheduleConsistent(result.reducePayment().schedule(), new BigDecimal("150000.00"));
    }

    // ---------- 利率时间轴本身 ----------

    @Test
    void timeline_rateAtAndParts() {
        RateTimeline timeline = RateTimeline.of(List.of(
                new RateSegmentInput(LocalDate.of(2026, 3, 1), new BigDecimal("0.04")),
                new RateSegmentInput(START, new BigDecimal("0.05"))), START);

        // 生效日左闭右开
        assertEquals(0, new BigDecimal("0.05").compareTo(timeline.rateAt(LocalDate.of(2026, 2, 28))));
        assertEquals(0, new BigDecimal("0.04").compareTo(timeline.rateAt(LocalDate.of(2026, 3, 1))));

        // 未跨调息日：单段覆盖整期
        List<RatePart> whole = timeline.parts(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1));
        assertEquals(1, whole.size());
        assertEquals(31, whole.get(0).days());

        // 月末日期加月：1 月 31 日起的下一期到 2 月 28 日（2026 非闰年）
        List<RatePart> monthEnd = timeline.parts(LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 1, 31).plusMonths(1));
        assertEquals(28, monthEnd.stream().mapToInt(RatePart::days).sum());
    }
}

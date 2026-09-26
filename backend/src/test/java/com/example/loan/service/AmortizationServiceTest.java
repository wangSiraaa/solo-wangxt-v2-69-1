package com.example.loan.service;

import com.example.loan.api.ComparisonResult;
import com.example.loan.api.PlanResult;
import com.example.loan.api.RateSegmentInput;
import com.example.loan.api.ScheduleRow;
import com.example.loan.domain.RepaymentMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用两种还款方式各一组示例核对逐期金额与汇总结果。
 *
 * 示例（两种还款方式共用）：
 *   剩余本金 1,000,000.00，年利率 4.9%（月利率 0.004083333333），剩余 240 期，
 *   提前还款 200,000.00，手续费 500.00。
 * 单一利率段（无调息）时结果与引入分段利率前完全一致。
 */
class AmortizationServiceTest {

    private final AmortizationService service = new AmortizationService();

    private static final BigDecimal PRINCIPAL = new BigDecimal("1000000.00");
    private static final BigDecimal RATE = new BigDecimal("0.049");
    private static final int PERIODS = 240;
    private static final BigDecimal PREPAY = new BigDecimal("200000.00");
    private static final BigDecimal FEE = new BigDecimal("500.00");
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final List<RateSegmentInput> SEGMENTS = List.of(new RateSegmentInput(START, RATE));

    // ---------- 等额本息：基准计划 ----------

    @Test
    void equalInstallment_baselineSchedule() {
        List<ScheduleRow> rows = service.schedule(
                RepaymentMethod.EQUAL_INSTALLMENT, SEGMENTS, START, PRINCIPAL, PERIODS);

        assertEquals(240, rows.size());
        // 月供 = P·r·(1+r)^n / ((1+r)^n − 1) = 6544.44
        assertEquals(new BigDecimal("6544.44"), rows.get(0).payment());
        // 首期利息 = 1,000,000 × 0.049 / 12 = 4083.33，首期本金 = 6544.44 − 4083.33
        assertEquals(new BigDecimal("4083.33"), rows.get(0).interest());
        assertEquals(new BigDecimal("2461.11"), rows.get(0).principal());
        assertEquals(new BigDecimal("997538.89"), rows.get(0).balance());
        // 未跨调息日：利率来源为单段，覆盖整期
        assertEquals(1, rows.get(0).rateParts().size());
        assertEquals(START, rows.get(0).periodStart());
        assertEquals(START.plusMonths(1), rows.get(0).periodEnd());

        assertScheduleConsistent(rows, PRINCIPAL);
        // 除末期外月供固定
        for (int i = 0; i < rows.size() - 1; i++) {
            assertEquals(new BigDecimal("6544.44"), rows.get(i).payment(), "第 " + (i + 1) + " 期月供");
        }
    }

    // ---------- 等额本金：基准计划 ----------

    @Test
    void equalPrincipal_baselineSchedule() {
        List<ScheduleRow> rows = service.schedule(
                RepaymentMethod.EQUAL_PRINCIPAL, SEGMENTS, START, PRINCIPAL, PERIODS);

        assertEquals(240, rows.size());
        // 每月本金 = 1,000,000 / 240 = 4166.67（末期兜底）
        assertEquals(new BigDecimal("4166.67"), rows.get(0).principal());
        // 首期利息 4083.33，首期月供 8250.00
        assertEquals(new BigDecimal("4083.33"), rows.get(0).interest());
        assertEquals(new BigDecimal("8250.00"), rows.get(0).payment());
        // 末期本金兜底 = 1,000,000 − 239 × 4166.67 = 4165.87
        ScheduleRow last = rows.get(rows.size() - 1);
        assertEquals(new BigDecimal("4165.87"), last.principal());
        assertEquals(new BigDecimal("17.01"), last.interest());
        assertEquals(new BigDecimal("4182.88"), last.payment());

        assertScheduleConsistent(rows, PRINCIPAL);
        // 月供逐月递减
        for (int i = 1; i < rows.size(); i++) {
            assertTrue(rows.get(i).payment().compareTo(rows.get(i - 1).payment()) < 0);
        }
    }

    // ---------- 提前还款对比：等额本息 ----------

    @Test
    void prepayment_equalInstallment() {
        ComparisonResult result = service.compare(
                RepaymentMethod.EQUAL_INSTALLMENT, SEGMENTS, START, PRINCIPAL, PERIODS, PREPAY, FEE);
        BigDecimal newPrincipal = new BigDecimal("800000.00");

        // 基准
        assertEquals(240, result.baseline().periods());
        assertEquals(new BigDecimal("6544.44"), result.baseline().monthlyPayment());
        assertEquals(new BigDecimal("570665.67"), result.baseline().totalInterest());

        PlanResult shorten = result.shortenTerm();
        PlanResult reduce = result.reducePayment();

        // 降低月供：期限不变，月供降为 5235.55，总利息 456,532.99
        assertEquals(240, reduce.summary().periods());
        assertEquals(new BigDecimal("5235.55"), reduce.summary().monthlyPayment());
        assertEquals(new BigDecimal("456532.99"), reduce.summary().totalInterest());
        assertScheduleConsistent(reduce.schedule(), newPrincipal);

        // 缩短期限：月供不变，期数 240 → 170，总利息 310,467.06
        assertEquals(new BigDecimal("6544.44"), shorten.summary().monthlyPayment());
        assertEquals(170, shorten.summary().periods());
        assertEquals(new BigDecimal("310467.06"), shorten.summary().totalInterest());
        assertScheduleConsistent(shorten.schedule(), newPrincipal);

        // 缩短期限总利息更少；差异字段 = 降低月供 − 缩短期限
        assertEquals(new BigDecimal("146065.93"), result.diff().interestDiff());
        assertEquals(70, result.diff().periodDiff());
        assertEquals(new BigDecimal("-1308.89"), result.diff().monthlyPaymentDiff());

        // 手续费计入两方案总成本
        assertEquals(reduce.summary().totalPayment().add(FEE), reduce.summary().totalCost());
        assertEquals(shorten.summary().totalPayment().add(FEE), shorten.summary().totalCost());
    }

    // ---------- 提前还款对比：等额本金 ----------

    @Test
    void prepayment_equalPrincipal() {
        ComparisonResult result = service.compare(
                RepaymentMethod.EQUAL_PRINCIPAL, SEGMENTS, START, PRINCIPAL, PERIODS, PREPAY, FEE);
        BigDecimal newPrincipal = new BigDecimal("800000.00");

        PlanResult shorten = result.shortenTerm();
        PlanResult reduce = result.reducePayment();

        // 基准总利息 492,041.29
        assertEquals(new BigDecimal("492041.29"), result.baseline().totalInterest());

        // 降低月供：期限不变，每月本金降为 800,000 / 240 = 3333.33，总利息 393,633.72
        assertEquals(240, reduce.summary().periods());
        assertEquals(new BigDecimal("3333.33"), reduce.schedule().get(0).principal());
        assertEquals(new BigDecimal("393633.72"), reduce.summary().totalInterest());
        assertScheduleConsistent(reduce.schedule(), newPrincipal);

        // 缩短期限：每月本金保持 4166.67，期数 = ceil(800,000 / 4166.67) = 192，总利息 315,233.07
        assertEquals(192, shorten.summary().periods());
        assertEquals(new BigDecimal("4166.67"), shorten.schedule().get(0).principal());
        assertEquals(new BigDecimal("315233.07"), shorten.summary().totalInterest());
        assertScheduleConsistent(shorten.schedule(), newPrincipal);

        // 等额本金无固定月供，monthlyPayment 为 null
        assertNull(shorten.summary().monthlyPayment());
        assertNull(reduce.summary().monthlyPayment());
        assertNull(result.diff().monthlyPaymentDiff());

        assertEquals(new BigDecimal("78400.65"), result.diff().interestDiff());
        assertEquals(48, result.diff().periodDiff());
    }

    // ---------- 参数校验 ----------

    @Test
    void prepaymentMustBeLessThanPrincipal() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                service.compare(RepaymentMethod.EQUAL_INSTALLMENT, SEGMENTS, START, PRINCIPAL, PERIODS,
                        PRINCIPAL, BigDecimal.ZERO));
        assertTrue(e.getMessage().contains("全额结清"));
    }

    @Test
    void negativeFeeRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                service.compare(RepaymentMethod.EQUAL_PRINCIPAL, SEGMENTS, START, PRINCIPAL, PERIODS,
                        PREPAY, new BigDecimal("-1")));
    }

    // ---------- 工具 ----------

    /** 核对逐期计划：本金合计 = 贷款本金、末期结清、无负余额、每期金额自洽。 */
    static void assertScheduleConsistent(List<ScheduleRow> rows, BigDecimal principal) {
        BigDecimal totalPrincipal = BigDecimal.ZERO;
        BigDecimal previousBalance = principal;
        for (ScheduleRow row : rows) {
            assertEquals(row.principal().add(row.interest()), row.payment(),
                    "第 " + row.period() + " 期：月供应等于本金+利息");
            assertEquals(previousBalance.subtract(row.principal()), row.balance(),
                    "第 " + row.period() + " 期：余额结转错误");
            assertTrue(row.balance().signum() >= 0, "第 " + row.period() + " 期出现负余额");
            totalPrincipal = totalPrincipal.add(row.principal());
            previousBalance = row.balance();
        }
        assertEquals(0, principal.compareTo(totalPrincipal), "各期本金合计应等于贷款本金");
        assertEquals(0, BigDecimal.ZERO.compareTo(rows.get(rows.size() - 1).balance()),
                "末期后余额应恰好为 0");
    }
}

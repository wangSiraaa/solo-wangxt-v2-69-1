package com.example.loan.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 基于历史计算派生新试算。
 * 默认沿用历史快照复现；若原合同仍存在，也可改用当前合同参数/利率版本。
 */
public record DeriveCalculationRequest(
        @NotNull(message = "提前还款金额不能为空")
        @DecimalMin(value = "0.01", message = "提前还款金额必须大于 0")
        BigDecimal prepaymentAmount,

        @NotNull(message = "手续费不能为空（可为 0）")
        @DecimalMin(value = "0", message = "手续费不能为负")
        BigDecimal fee,

        LocalDate prepaymentDate,

        /** true=使用合同当前利率时间表；false/null=沿用历史快照。 */
        Boolean useCurrentContractSchedule) {
}

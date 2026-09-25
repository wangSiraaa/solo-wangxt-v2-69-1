package com.example.loan.api;

import com.example.loan.domain.RepaymentMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 提前还款对比计算请求。
 * 若指定 contractId，则合同参数（还款方式、利率、剩余本金、剩余期数）取自模拟合同，
 * 否则使用请求中手工录入的参数。年利率为小数形式（0.049 表示 4.9%）。
 */
public record CompareRequest(
        Long contractId,

        RepaymentMethod method,

        @DecimalMin(value = "0", message = "年利率不能为负")
        @DecimalMax(value = "0.36", message = "年利率不能超过 36%")
        BigDecimal annualRate,

        @DecimalMin(value = "0.01", message = "剩余本金必须大于 0")
        BigDecimal remainingPrincipal,

        @Min(value = 1, message = "剩余期数至少为 1")
        @Max(value = 600, message = "剩余期数不能超过 600")
        Integer remainingPeriods,

        @NotNull(message = "提前还款金额不能为空")
        @DecimalMin(value = "0.01", message = "提前还款金额必须大于 0")
        BigDecimal prepaymentAmount,

        @NotNull(message = "手续费不能为空（可为 0）")
        @DecimalMin(value = "0", message = "手续费不能为负")
        BigDecimal fee) {
}

package com.example.loan.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 利率调整时间表中的一个利率段：从 effectiveDate 起按 annualRate 计息，直到下一段生效日前一日。
 */
public record RateSegmentRequest(
        @NotNull(message = "利率生效日不能为空")
        LocalDate effectiveDate,

        @NotNull(message = "分段年利率不能为空")
        @DecimalMin(value = "0", message = "分段年利率不能为负")
        @DecimalMax(value = "0.36", message = "分段年利率不能超过 36%")
        @Digits(integer = 1, fraction = 6, message = "分段年利率最多保留 6 位小数")
        BigDecimal annualRate) {
}

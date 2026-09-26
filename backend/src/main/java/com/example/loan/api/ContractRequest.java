package com.example.loan.api;

import com.example.loan.domain.RepaymentMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 新建模拟合同请求。年利率为小数形式（0.049 表示 4.9%）。
 * rateSchedule 缺省时以 annualRate 构成自计划起始日起的单一利率段（版本 1）。
 */
public record ContractRequest(
        @NotBlank(message = "合同编号不能为空")
        String contractNo,

        @NotBlank(message = "借款人姓名不能为空")
        String borrowerName,

        @NotNull(message = "还款方式不能为空")
        RepaymentMethod method,

        @NotNull(message = "年利率不能为空")
        @DecimalMin(value = "0", message = "年利率不能为负")
        @DecimalMax(value = "0.36", message = "年利率不能超过 36%")
        BigDecimal annualRate,

        @NotNull(message = "剩余本金不能为空")
        @DecimalMin(value = "0.01", message = "剩余本金必须大于 0")
        BigDecimal remainingPrincipal,

        @Min(value = 1, message = "剩余期数至少为 1")
        @Max(value = 600, message = "剩余期数不能超过 600")
        int remainingPeriods,

        LocalDate scheduleStartDate,

        List<RateSegmentInput> rateSchedule) {
}

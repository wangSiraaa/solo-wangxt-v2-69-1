package com.example.loan.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 利率时间表中的一个利率段：自 effectiveDate（含）起生效，直至下一段生效日（不含）。
 * 年利率为小数形式（0.049 表示 4.9%），最多 6 位小数。
 *
 * @param effectiveDate 生效日（含当天）
 * @param annualRate    年利率（小数）
 */
public record RateSegmentInput(LocalDate effectiveDate, BigDecimal annualRate) {
}

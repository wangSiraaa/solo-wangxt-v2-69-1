package com.example.loan.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 某一期内按实际占用天数拆分出的一段计息来源。
 * 当期未跨调息日时只有一段（覆盖整期）；跨调息日时有多段。
 *
 * @param from       分段起始日（含）
 * @param to         分段截止日（不含）
 * @param days       实际占用天数
 * @param annualRate 该分段适用的年利率（小数）
 */
public record RatePart(LocalDate from, LocalDate to, int days, BigDecimal annualRate) {
}

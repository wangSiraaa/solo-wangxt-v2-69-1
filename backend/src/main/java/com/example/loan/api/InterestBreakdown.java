package com.example.loan.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 一期内某个利率段的实际占用利息。
 *
 * @param segmentStart 该利率在本期内的开始适用日
 * @param segmentEnd   最后适用日（含）
 * @param effectiveDate 对应利率时间表的生效日
 * @param annualRate   年利率（小数）
 * @param days         实际占用天数
 * @param interest     分摊到该段的利息（已按分舍入，各段合计等于当期利息）
 */
public record InterestBreakdown(LocalDate segmentStart,
                                LocalDate segmentEnd,
                                LocalDate effectiveDate,
                                BigDecimal annualRate,
                                int days,
                                BigDecimal interest) {
}

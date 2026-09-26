package com.example.loan.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 还款计划中的一期。
 *
 * @param period    期次（从 1 开始）
 * @param periodStart 当期计息起始日（含）
 * @param periodEnd   当期计息截止日（不含）
 * @param payment   当期还款额（本金 + 利息）
 * @param principal 当期偿还本金
 * @param interest  当期利息
 * @param balance   当期还款后的剩余本金
 * @param rateParts 当期利率来源（按实际占用天数拆分；未跨调息日时为单段）
 */
public record ScheduleRow(int period,
                          LocalDate periodStart,
                          LocalDate periodEnd,
                          BigDecimal payment,
                          BigDecimal principal,
                          BigDecimal interest,
                          BigDecimal balance,
                          List<RatePart> rateParts) {
}

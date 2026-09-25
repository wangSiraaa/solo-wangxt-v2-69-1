package com.example.loan.api;

import java.math.BigDecimal;

/**
 * 还款计划中的一期。
 *
 * @param period    期次（从 1 开始）
 * @param payment   当期还款额（本金 + 利息）
 * @param principal 当期偿还本金
 * @param interest  当期利息
 * @param balance   当期还款后的剩余本金
 */
public record ScheduleRow(int period,
                          BigDecimal payment,
                          BigDecimal principal,
                          BigDecimal interest,
                          BigDecimal balance) {
}

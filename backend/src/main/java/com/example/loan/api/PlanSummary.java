package com.example.loan.api;

import java.math.BigDecimal;

/**
 * 一个还款方案的汇总指标。
 *
 * @param periods        总期数
 * @param firstPayment   首期还款额
 * @param lastPayment    末期还款额
 * @param monthlyPayment 每月固定月供（仅等额本息；等额本金为 null）
 * @param totalPrincipal 偿还本金总额（提前还款后的剩余本金）
 * @param totalInterest  利息总额
 * @param totalPayment   还款总额（本金 + 利息）
 * @param fee            提前还款手续费（一次性）
 * @param totalCost      总成本（还款总额 + 手续费）
 */
public record PlanSummary(int periods,
                          BigDecimal firstPayment,
                          BigDecimal lastPayment,
                          BigDecimal monthlyPayment,
                          BigDecimal totalPrincipal,
                          BigDecimal totalInterest,
                          BigDecimal totalPayment,
                          BigDecimal fee,
                          BigDecimal totalCost) {
}

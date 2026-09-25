package com.example.loan.api;

import java.math.BigDecimal;

/**
 * 两种提前还款方案的关键差异（均为「降低月供 − 缩短期限」）。
 *
 * @param periodDiff          期数差（正数表示降低月供方案的期数更多）
 * @param monthlyPaymentDiff  月供差（仅等额本息；等额本金为 null）
 * @param firstPaymentDiff    首期还款额差
 * @param interestDiff        总利息差（正数表示缩短期限方案更省利息）
 * @param totalCostDiff       总成本差（含手续费）
 */
public record DiffSummary(int periodDiff,
                          BigDecimal monthlyPaymentDiff,
                          BigDecimal firstPaymentDiff,
                          BigDecimal interestDiff,
                          BigDecimal totalCostDiff) {
}

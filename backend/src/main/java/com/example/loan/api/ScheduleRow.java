package com.example.loan.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 还款计划中的一期。
 *
 * @param period           期次（从 1 开始）
 * @param startDate        本期起息日
 * @param dueDate          本期到期/还款日
 * @param payment          当期还款额（本金 + 利息）
 * @param principal        当期偿还本金
 * @param interest         当期利息
 * @param balance          当期还款后的剩余本金
 * @param interestBreakdown 跨调息日时的分段利息来源；普通单利率场景仅一段
 */
public record ScheduleRow(int period,
                          LocalDate startDate,
                          LocalDate dueDate,
                          BigDecimal payment,
                          BigDecimal principal,
                          BigDecimal interest,
                          BigDecimal balance,
                          List<InterestBreakdown> interestBreakdown) {

    public ScheduleRow {
        interestBreakdown = interestBreakdown == null ? List.of() : List.copyOf(interestBreakdown);
    }

    /** 兼容旧的无日期、无利率来源明细的调用。 */
    public ScheduleRow(int period, BigDecimal payment, BigDecimal principal,
                       BigDecimal interest, BigDecimal balance) {
        this(period, null, null, payment, principal, interest, balance, List.of());
    }
}

package com.example.loan.api;

import com.example.loan.domain.RepaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 历史计算记录的列表视图（不暴露实体与懒加载关联）。
 */
public record RecordSummaryView(
        Long id,
        Instant createdAt,
        String contractNo,
        RepaymentMethod method,
        BigDecimal annualRate,
        BigDecimal remainingPrincipal,
        int remainingPeriods,
        LocalDate scheduleStartDate,
        int rateScheduleVersion,
        LocalDate prepaymentDate,
        BigDecimal prepaymentAmount,
        BigDecimal fee,
        int shortenPeriods,
        int reducePeriods,
        BigDecimal shortenTotalInterest,
        BigDecimal reduceTotalInterest,
        BigDecimal interestDiff) {
}

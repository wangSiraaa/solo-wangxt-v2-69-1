package com.example.loan.api;

import com.example.loan.domain.RepaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 合同及其当前利率时间表版本。
 */
public record ContractResponse(Long id,
                               String contractNo,
                               String borrowerName,
                               RepaymentMethod method,
                               BigDecimal annualRate,
                               BigDecimal remainingPrincipal,
                               int remainingPeriods,
                               LocalDate scheduleStartDate,
                               int rateScheduleVersion,
                               List<RateSegmentView> rateSegments,
                               Instant createdAt) {
}

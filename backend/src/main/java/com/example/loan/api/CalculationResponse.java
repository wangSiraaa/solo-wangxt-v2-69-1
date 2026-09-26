package com.example.loan.api;

import com.example.loan.domain.RepaymentMethod;

import java.time.LocalDate;
import java.util.List;

/**
 * 对比计算响应：recordId 为本次计算记录的持久化 ID；
 * rateVersionNo 与 rateSchedule 为计算时实际采用的利率版本与时间表快照，
 * 历史记录不随后续利率表编辑漂移。
 */
public record CalculationResponse(Long recordId,
                                  RepaymentMethod method,
                                  LocalDate scheduleStartDate,
                                  Integer rateVersionNo,
                                  List<RateSegmentInput> rateSchedule,
                                  ComparisonResult comparison) {
}

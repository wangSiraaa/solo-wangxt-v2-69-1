package com.example.loan.api;

import java.time.LocalDate;
import java.util.List;

/**
 * 由历史计算记录派生新计算的请求（可空）。
 * 提供 rateSchedule 时按给定利率时间表重算；否则合同类记录采用合同当前版本，
 * 手工记录沿用原快照。scheduleStartDate 缺省沿用原记录。
 */
public record DeriveRequest(List<RateSegmentInput> rateSchedule,
                            LocalDate scheduleStartDate) {
}

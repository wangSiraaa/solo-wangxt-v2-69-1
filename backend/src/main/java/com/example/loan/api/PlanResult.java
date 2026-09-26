package com.example.loan.api;

import java.time.LocalDate;
import java.util.List;

/**
 * 一个提前还款方案：汇总指标 + 逐期还款计划。
 */
public record PlanResult(String code,
                         String label,
                         PlanSummary summary,
                         List<ScheduleRow> schedule,
                         LocalDate scheduleStartDate,
                         Integer rateScheduleVersion,
                         List<RateSegmentView> rateScheduleSnapshot,
                         LocalDate prepaymentDate) {

    public PlanResult(String code, String label, PlanSummary summary, List<ScheduleRow> schedule) {
        this(code, label, summary, schedule, null, null, null, null);
    }
}

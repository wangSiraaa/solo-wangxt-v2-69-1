package com.example.loan.api;

import java.util.List;

/**
 * 一个提前还款方案：汇总指标 + 逐期还款计划。
 */
public record PlanResult(String code,
                         String label,
                         PlanSummary summary,
                         List<ScheduleRow> schedule) {
}

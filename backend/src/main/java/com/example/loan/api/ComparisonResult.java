package com.example.loan.api;

/**
 * 提前还款两种方案的完整对比结果。
 *
 * @param baseline     不提前还款时原合同的汇总（参考基准）
 * @param shortenTerm  方案一：缩短期限（月供基本不变）
 * @param reducePayment 方案二：降低月供（期限不变）
 * @param diff         两方案关键差异
 */
public record ComparisonResult(PlanSummary baseline,
                               PlanResult shortenTerm,
                               PlanResult reducePayment,
                               DiffSummary diff) {
}

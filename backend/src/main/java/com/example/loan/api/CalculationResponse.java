package com.example.loan.api;

import com.example.loan.domain.RepaymentMethod;

/**
 * 对比计算响应：recordId 为本次计算记录的持久化 ID。
 */
public record CalculationResponse(Long recordId, RepaymentMethod method, ComparisonResult comparison) {
}

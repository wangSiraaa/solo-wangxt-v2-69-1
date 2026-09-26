package com.example.loan.api;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 整体替换合同利率时间表的请求：校验通过后保存为新版本。
 */
public record RateScheduleUpdateRequest(
        @NotEmpty(message = "利率时间表不能为空")
        List<RateSegmentInput> segments) {
}

package com.example.loan.api;

import java.time.Instant;
import java.util.List;

/**
 * 利率时间表视图。versionId / versionNo 为 null 表示非版本化的临时快照（手工录入参数）。
 */
public record RateScheduleView(Long versionId,
                               Integer versionNo,
                               Instant createdAt,
                               List<RateSegmentInput> segments) {
}

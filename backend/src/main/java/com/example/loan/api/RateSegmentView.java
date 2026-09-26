package com.example.loan.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 利率段展示/快照对象。计算记录会保存完整快照，不依赖合同后续编辑。
 */
public record RateSegmentView(LocalDate effectiveDate, BigDecimal annualRate) {
}

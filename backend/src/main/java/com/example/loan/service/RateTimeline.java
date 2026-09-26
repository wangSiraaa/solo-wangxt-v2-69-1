package com.example.loan.service;

import com.example.loan.api.RatePart;
import com.example.loan.api.RateSegmentInput;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 分段利率时间轴：由若干按生效日排列的利率段构成，
 * 每段覆盖 [生效日, 下一段生效日)，末段覆盖至无穷远，段与段之间天然连续。
 *
 * <p>校验规则（构造时执行）：</p>
 * <ul>
 *   <li>利率段不能为空，生效日不能为空；</li>
 *   <li>同一生效日只允许一个利率段（拒绝同日多次调整）；</li>
 *   <li>首个利率段生效日不得晚于计划起始日（拒绝空档）；</li>
 *   <li>年利率 0 ~ 36%，且最多 6 位小数（精度校验）。</li>
 * </ul>
 */
public final class RateTimeline {

    /** 年利率上限（36%）。 */
    public static final BigDecimal MAX_RATE = new BigDecimal("0.36");
    /** 年利率允许的最大小数位数。 */
    public static final int RATE_MAX_SCALE = 6;
    /** 利率段数量上限，防止异常参数。 */
    public static final int MAX_SEGMENTS = 1000;

    private final List<RateSegmentInput> segments; // 按生效日升序

    private RateTimeline(List<RateSegmentInput> segments) {
        this.segments = segments;
    }

    /**
     * 校验并构建利率时间轴。
     *
     * @param segments      利率段（顺序不限，构建时按生效日排序）
     * @param scheduleStart 还款计划起始日（第 1 期计息起始日），用于空档校验
     */
    public static RateTimeline of(List<RateSegmentInput> segments, LocalDate scheduleStart) {
        validate(segments, scheduleStart);
        List<RateSegmentInput> sorted = segments.stream()
                .sorted(Comparator.comparing(RateSegmentInput::effectiveDate))
                .toList();
        return new RateTimeline(sorted);
    }

    /** 校验利率时间表（供计算与保存版本时共用）。 */
    public static void validate(List<RateSegmentInput> segments, LocalDate scheduleStart) {
        if (scheduleStart == null) {
            throw new IllegalArgumentException("计划起始日不能为空");
        }
        if (segments == null || segments.isEmpty()) {
            throw new IllegalArgumentException("利率时间表不能为空");
        }
        if (segments.size() > MAX_SEGMENTS) {
            throw new IllegalArgumentException("利率段数量不能超过 " + MAX_SEGMENTS);
        }
        Set<LocalDate> seen = new HashSet<>();
        LocalDate firstEffective = null;
        for (RateSegmentInput seg : segments) {
            if (seg == null || seg.effectiveDate() == null) {
                throw new IllegalArgumentException("利率段生效日不能为空");
            }
            if (!seen.add(seg.effectiveDate())) {
                throw new IllegalArgumentException("同一生效日存在多次利率调整: " + seg.effectiveDate());
            }
            BigDecimal rate = seg.annualRate();
            if (rate == null || rate.signum() < 0 || rate.compareTo(MAX_RATE) > 0) {
                throw new IllegalArgumentException("年利率需在 0 ~ 36% 之间: " + seg.effectiveDate());
            }
            if (rate.stripTrailingZeros().scale() > RATE_MAX_SCALE) {
                throw new IllegalArgumentException(
                        "年利率最多支持 " + RATE_MAX_SCALE + " 位小数: " + seg.effectiveDate() + " " + rate.toPlainString());
            }
            if (firstEffective == null || seg.effectiveDate().isBefore(firstEffective)) {
                firstEffective = seg.effectiveDate();
            }
        }
        if (firstEffective.isAfter(scheduleStart)) {
            throw new IllegalArgumentException(
                    "利率时间表存在空档：首个利率段生效日 " + firstEffective + " 晚于计划起始日 " + scheduleStart);
        }
    }

    /** 排序后的利率段（生效日升序）。 */
    public List<RateSegmentInput> segments() {
        return segments;
    }

    /** 指定日期适用的年利率（生效日左闭右开）。 */
    public BigDecimal rateAt(LocalDate date) {
        BigDecimal rate = null;
        for (RateSegmentInput seg : segments) {
            if (seg.effectiveDate().isAfter(date)) {
                break;
            }
            rate = seg.annualRate();
        }
        if (rate == null) {
            // 构造时已校验首段生效日 ≤ 计划起始日，正常计息不会走到这里
            throw new IllegalStateException("日期 " + date + " 不在利率时间表覆盖范围内");
        }
        return rate;
    }

    /**
     * 把 [from, to) 按利率段边界拆分为若干计息分段（按实际占用天数）。
     * 未跨调息日时返回单段。
     */
    public List<RatePart> parts(LocalDate from, LocalDate to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("计息区间不合法: " + from + " ~ " + to);
        }
        List<RatePart> parts = new ArrayList<>();
        LocalDate cursor = from;
        while (cursor.isBefore(to)) {
            BigDecimal rate = rateAt(cursor);
            LocalDate segEnd = to;
            for (RateSegmentInput seg : segments) {
                if (seg.effectiveDate().isAfter(cursor)) {
                    segEnd = seg.effectiveDate().isBefore(to) ? seg.effectiveDate() : to;
                    break;
                }
            }
            int days = (int) (segEnd.toEpochDay() - cursor.toEpochDay());
            parts.add(new RatePart(cursor, segEnd, days, rate));
            cursor = segEnd;
        }
        return parts;
    }
}

package com.example.loan.service;

import com.example.loan.api.RateSegmentRequest;
import com.example.loan.api.RateSegmentView;
import com.example.loan.domain.LoanContract;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * 分段利率调整时间表的规范化与校验。
 *
 * <p>时间表采用“生效日 + 下一生效日前一日”的半开区间，因此严格升序且首段不晚于
 * 计划起息日时，整个计息周期不存在空档；同一生效日出现两段则拒绝。</p>
 */
@Service
public class RateScheduleService {

    private static final BigDecimal MAX_RATE = new BigDecimal("0.36");
    private static final int RATE_SCALE = 6;

    public List<LoanContract.StoredRateSegment> normalize(List<RateSegmentRequest> segments,
                                                          LocalDate scheduleStartDate) {
        List<Segment> normalized = segments == null ? List.of() : segments.stream()
                .map(s -> new Segment(s.effectiveDate(), s.annualRate()))
                .toList();
        return validateAndSort(normalized, scheduleStartDate).stream()
                .map(s -> new LoanContract.StoredRateSegment(s.effectiveDate(), s.annualRate()))
                .toList();
    }

    public List<LoanContract.StoredRateSegment> normalizeStored(
            List<LoanContract.StoredRateSegment> segments, LocalDate scheduleStartDate) {
        List<Segment> normalized = segments == null ? List.of() : segments.stream()
                .map(s -> new Segment(s.effectiveDate(), s.annualRate()))
                .toList();
        return validateAndSort(normalized, scheduleStartDate);
    }


    public List<RateSegmentView> toViews(List<LoanContract.StoredRateSegment> segments) {
        return segments.stream()
                .map(s -> new RateSegmentView(s.effectiveDate(), s.annualRate()))
                .toList();
    }

    public BigDecimal activeRate(List<LoanContract.StoredRateSegment> segments, LocalDate date) {
        return segments.stream()
                .filter(s -> !date.isBefore(s.effectiveDate()))
                .max(Comparator.comparing(LoanContract.StoredRateSegment::effectiveDate))
                .orElseThrow(() -> new IllegalArgumentException("利率时间表未覆盖日期: " + date))
                .annualRate();
    }

    private List<LoanContract.StoredRateSegment> validateAndSort(List<Segment> input, LocalDate startDate) {
        if (startDate == null) {
            throw new IllegalArgumentException("模拟开始日期不能为空");
        }
        if (input.isEmpty()) {
            throw new IllegalArgumentException("利率时间表至少需要一个利率段");
        }
        for (Segment segment : input) {
            if (segment.effectiveDate() == null) {
                throw new IllegalArgumentException("利率生效日不能为空");
            }
            if (segment.annualRate() == null) {
                throw new IllegalArgumentException("分段年利率不能为空");
            }
            if (segment.annualRate().signum() < 0 || segment.annualRate().compareTo(MAX_RATE) > 0) {
                throw new IllegalArgumentException("分段年利率需在 0 ~ 36% 之间");
            }
            if (segment.annualRate().stripTrailingZeros().scale() > RATE_SCALE) {
                throw new IllegalArgumentException("分段年利率最多保留 6 位小数");
            }
        }

        List<Segment> sorted = input.stream()
                .sorted(Comparator.comparing(Segment::effectiveDate))
                .toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).effectiveDate().isEqual(sorted.get(i - 1).effectiveDate())) {
                throw new IllegalArgumentException("同一日期不能维护多次利率调整: "
                        + sorted.get(i).effectiveDate());
            }
        }
        if (sorted.get(0).effectiveDate().isAfter(startDate)) {
            throw new IllegalArgumentException("首个利率段生效日必须不晚于模拟开始日期，利率时间表不能有空档");
        }
        return sorted.stream()
                .map(s -> new LoanContract.StoredRateSegment(s.effectiveDate(),
                        s.annualRate().setScale(RATE_SCALE, RoundingMode.HALF_UP)))
                .toList();
    }

    private record Segment(LocalDate effectiveDate, BigDecimal annualRate) {
    }
}

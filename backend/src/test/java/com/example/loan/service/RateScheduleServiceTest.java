package com.example.loan.service;

import com.example.loan.api.RateSegmentRequest;
import com.example.loan.domain.LoanContract;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RateScheduleServiceTest {

    private final RateScheduleService service = new RateScheduleService();
    private final LocalDate start = LocalDate.of(2025, 1, 1);

    @Test
    void normalizesUnsortedSegments() {
        List<LoanContract.StoredRateSegment> normalized = service.normalize(List.of(
                new RateSegmentRequest(LocalDate.of(2025, 3, 1), new BigDecimal("0.048")),
                new RateSegmentRequest(start, new BigDecimal("0.036"))), start);

        assertEquals(start, normalized.get(0).effectiveDate());
        assertEquals(LocalDate.of(2025, 3, 1), normalized.get(1).effectiveDate());
        assertEquals(new BigDecimal("0.036000"), normalized.get(0).annualRate());
    }

    @Test
    void rejectsSameDayAdjustment() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.normalize(List.of(
                new RateSegmentRequest(start, new BigDecimal("0.036")),
                new RateSegmentRequest(start, new BigDecimal("0.048"))), start));
        assertTrue(e.getMessage().contains("同一日期"));
    }

    @Test
    void rejectsGapBeforeScheduleStart() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.normalize(List.of(
                new RateSegmentRequest(start.plusDays(1), new BigDecimal("0.036"))), start));
        assertTrue(e.getMessage().contains("空档"));
    }

    @Test
    void rejectsExcessivePrecision() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.normalize(List.of(
                new RateSegmentRequest(start, new BigDecimal("0.0360001"))), start));
        assertTrue(e.getMessage().contains("6 位小数"));
    }

    @Test
    void activeRateUsesLatestEffectiveDate() {
        List<LoanContract.StoredRateSegment> segments = service.normalize(List.of(
                new RateSegmentRequest(start, new BigDecimal("0.036")),
                new RateSegmentRequest(LocalDate.of(2025, 3, 1), new BigDecimal("0.048"))), start);
        assertEquals(new BigDecimal("0.036000"), service.activeRate(segments, LocalDate.of(2025, 2, 28)));
        assertEquals(new BigDecimal("0.048000"), service.activeRate(segments, LocalDate.of(2025, 3, 1)));
    }
}

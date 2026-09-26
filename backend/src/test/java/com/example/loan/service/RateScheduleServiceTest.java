package com.example.loan.service;

import com.example.loan.api.RateSegmentInput;
import com.example.loan.domain.LoanContract;
import com.example.loan.domain.RateScheduleVersion;
import com.example.loan.domain.RepaymentMethod;
import com.example.loan.repo.LoanContractRepository;
import com.example.loan.repo.RateScheduleVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 利率时间表版本管理：校验、版本号递增、段排序。
 */
class RateScheduleServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    private RateScheduleVersionRepository versionRepository;
    private RateScheduleService service;
    private LoanContract contract;

    @BeforeEach
    void setUp() {
        versionRepository = mock(RateScheduleVersionRepository.class);
        LoanContractRepository contractRepository = mock(LoanContractRepository.class);
        service = new RateScheduleService(versionRepository, contractRepository);
        when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        contract = new LoanContract("MOCK-T2", "测试", RepaymentMethod.EQUAL_INSTALLMENT,
                new BigDecimal("0.05"), new BigDecimal("100000.00"), 60, START);
    }

    @Test
    void versionsIncrementAndSegmentsSorted() {
        when(versionRepository.findTopByContractIdOrderByVersionNoDesc(any()))
                .thenReturn(Optional.empty());
        RateScheduleVersion v1 = service.saveNewVersion(contract, List.of(
                new RateSegmentInput(LocalDate.of(2026, 6, 1), new BigDecimal("0.04")),
                new RateSegmentInput(START, new BigDecimal("0.05"))));
        assertEquals(1, v1.getVersionNo());
        // 段按生效日升序落库
        assertEquals(START, v1.getSegments().get(0).getEffectiveDate());
        assertEquals(LocalDate.of(2026, 6, 1), v1.getSegments().get(1).getEffectiveDate());
        assertEquals(0, v1.getSegments().get(0).getSegmentIndex());
        assertEquals(1, v1.getSegments().get(1).getSegmentIndex());

        when(versionRepository.findTopByContractIdOrderByVersionNoDesc(any()))
                .thenReturn(Optional.of(v1));
        RateScheduleVersion v2 = service.saveNewVersion(contract, List.of(
                new RateSegmentInput(START, new BigDecimal("0.045"))));
        assertEquals(2, v2.getVersionNo());
    }

    @Test
    void invalidScheduleRejectedBeforeSave() {
        // 同日多次调整
        assertThrows(IllegalArgumentException.class, () -> service.saveNewVersion(contract, List.of(
                new RateSegmentInput(START, new BigDecimal("0.05")),
                new RateSegmentInput(START, new BigDecimal("0.04")))));
        // 起始空档
        assertThrows(IllegalArgumentException.class, () -> service.saveNewVersion(contract, List.of(
                new RateSegmentInput(START.plusDays(1), new BigDecimal("0.05")))));
        // 精度超限
        assertThrows(IllegalArgumentException.class, () -> service.saveNewVersion(contract, List.of(
                new RateSegmentInput(START, new BigDecimal("0.0512345")))));
        verify(versionRepository, never()).save(any());
    }
}

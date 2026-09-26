package com.example.loan.service;

import com.example.loan.api.CalculationResponse;
import com.example.loan.api.CompareRequest;
import com.example.loan.api.DeriveRequest;
import com.example.loan.api.RateSegmentInput;
import com.example.loan.domain.CalculationRecord;
import com.example.loan.domain.LoanContract;
import com.example.loan.domain.RateScheduleVersion;
import com.example.loan.domain.RateSegment;
import com.example.loan.domain.RepaymentMethod;
import com.example.loan.repo.CalculationRecordRepository;
import com.example.loan.repo.LoanContractRepository;
import com.example.loan.repo.RateScheduleVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 计算编排与持久化：利率时间表快照、版本引用、历史不漂移与派生新计算。
 */
class CalculationServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    private CalculationRecordRepository recordRepository;
    private LoanContractRepository contractRepository;
    private RateScheduleVersionRepository versionRepository;
    private CalculationService service;

    private LoanContract contract;
    private RateScheduleVersion v1;
    private RateScheduleVersion v2;

    @BeforeEach
    void setUp() {
        recordRepository = mock(CalculationRecordRepository.class);
        contractRepository = mock(LoanContractRepository.class);
        versionRepository = mock(RateScheduleVersionRepository.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new CalculationService(new AmortizationService(),
                new RateScheduleService(versionRepository, contractRepository),
                recordRepository, contractRepository, objectMapper);

        when(recordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        contract = new LoanContract("MOCK-T1", "测试", RepaymentMethod.EQUAL_INSTALLMENT,
                new BigDecimal("0.05"), new BigDecimal("600000.00"), 120, START);
        ReflectionTestUtils.setField(contract, "id", 1L);
        v1 = version(contract, 1, new BigDecimal("0.05"));
        v2 = version(contract, 2, new BigDecimal("0.04"));
        when(contractRepository.findById(1L)).thenReturn(Optional.of(contract));
    }

    private static RateScheduleVersion version(LoanContract contract, int no, BigDecimal rate) {
        RateScheduleVersion version = new RateScheduleVersion(contract, no);
        version.addSegment(new RateSegment(0, START, rate));
        return version;
    }

    private static CompareRequest contractRequest() {
        return new CompareRequest(1L, null, null, null, null, null, null,
                new BigDecimal("100000.00"), new BigDecimal("200.00"));
    }

    @Test
    void compareWithContract_capturesVersionAndSnapshot() {
        when(versionRepository.findTopByContractIdOrderByVersionNoDesc(any()))
                .thenReturn(Optional.of(v1));

        CalculationResponse resp = service.compareAndSave(contractRequest());

        assertEquals(1, resp.rateVersionNo());
        assertEquals(START, resp.scheduleStartDate());
        assertEquals(1, resp.rateSchedule().size());
        assertEquals(0, new BigDecimal("0.05").compareTo(resp.rateSchedule().get(0).annualRate()));

        ArgumentCaptor<CalculationRecord> captor = ArgumentCaptor.forClass(CalculationRecord.class);
        verify(recordRepository).save(captor.capture());
        CalculationRecord saved = captor.getValue();
        assertSame(v1, saved.getRateVersion());
        assertEquals(START, saved.getScheduleStartDate());
        assertTrue(saved.getRateSnapshotJson().contains("0.05"), saved.getRateSnapshotJson());
        // 列表展示用利率 = 计划起始日适用利率
        assertEquals(0, new BigDecimal("0.05").compareTo(saved.getAnnualRate()));
    }

    @Test
    void manualCompareWithSchedule_savesSnapshotWithoutVersion() {
        List<RateSegmentInput> segments = List.of(
                new RateSegmentInput(START, new BigDecimal("0.05")),
                new RateSegmentInput(START.plusMonths(6), new BigDecimal("0.04")));
        CompareRequest req = new CompareRequest(null, RepaymentMethod.EQUAL_PRINCIPAL, null, START,
                segments, new BigDecimal("300000.00"), 60,
                new BigDecimal("50000.00"), BigDecimal.ZERO);

        CalculationResponse resp = service.compareAndSave(req);

        assertNull(resp.rateVersionNo());
        assertEquals(2, resp.rateSchedule().size());

        ArgumentCaptor<CalculationRecord> captor = ArgumentCaptor.forClass(CalculationRecord.class);
        verify(recordRepository).save(captor.capture());
        assertNull(captor.getValue().getRateVersion());
        assertTrue(captor.getValue().getRateSnapshotJson().contains("0.04"));
    }

    @Test
    void historyDoesNotDriftAfterScheduleEdit_andDeriveUsesNewVersion() {
        // 第一次计算：采用版本 1（5%）
        when(versionRepository.findTopByContractIdOrderByVersionNoDesc(any()))
                .thenReturn(Optional.of(v1));
        CalculationResponse original = service.compareAndSave(contractRequest());
        ArgumentCaptor<CalculationRecord> captor = ArgumentCaptor.forClass(CalculationRecord.class);
        verify(recordRepository).save(captor.capture());
        CalculationRecord saved = captor.getValue();
        when(recordRepository.findById(7L)).thenReturn(Optional.of(saved));

        // 利率表随后被编辑：当前版本变为 v2（4%）
        when(versionRepository.findTopByContractIdOrderByVersionNoDesc(any()))
                .thenReturn(Optional.of(v2));

        // 旧计算仍可复现：结果与快照均保持版本 1 的内容
        CalculationResponse reloaded = service.getRecord(7L);
        assertEquals(1, reloaded.rateVersionNo());
        assertEquals(0, new BigDecimal("0.05").compareTo(reloaded.rateSchedule().get(0).annualRate()));
        assertEquals(original.comparison().reducePayment().summary().totalInterest(),
                reloaded.comparison().reducePayment().summary().totalInterest());

        // 派生新计算：采用合同当前版本 v2，利率更低 → 总利息更低
        CalculationResponse derived = service.deriveAndSave(7L, null);
        assertEquals(2, derived.rateVersionNo());
        assertEquals(0, new BigDecimal("0.04").compareTo(derived.rateSchedule().get(0).annualRate()));
        assertTrue(derived.comparison().reducePayment().summary().totalInterest()
                .compareTo(original.comparison().reducePayment().summary().totalInterest()) < 0);

        // 派生记录同样固化版本与快照
        ArgumentCaptor<CalculationRecord> allSaved = ArgumentCaptor.forClass(CalculationRecord.class);
        verify(recordRepository, times(2)).save(allSaved.capture());
        CalculationRecord derivedRecord = allSaved.getAllValues().get(1);
        assertSame(v2, derivedRecord.getRateVersion());
        assertTrue(derivedRecord.getRateSnapshotJson().contains("0.04"));
    }

    @Test
    void deriveWithExplicitSchedule_overridesSnapshot() {
        // 手工录入的记录（无合同、无版本）
        List<RateSegmentInput> segments = List.of(new RateSegmentInput(START, new BigDecimal("0.05")));
        CompareRequest req = new CompareRequest(null, RepaymentMethod.EQUAL_PRINCIPAL, null, START,
                segments, new BigDecimal("300000.00"), 60,
                new BigDecimal("50000.00"), BigDecimal.ZERO);
        service.compareAndSave(req);
        ArgumentCaptor<CalculationRecord> captor = ArgumentCaptor.forClass(CalculationRecord.class);
        verify(recordRepository).save(captor.capture());
        when(recordRepository.findById(9L)).thenReturn(Optional.of(captor.getValue()));

        // 派生时显式给定新利率时间表
        List<RateSegmentInput> override = List.of(
                new RateSegmentInput(START, new BigDecimal("0.05")),
                new RateSegmentInput(START.plusMonths(3), new BigDecimal("0.03")));
        CalculationResponse derived = service.deriveAndSave(9L, new DeriveRequest(override, null));

        assertNull(derived.rateVersionNo());
        assertEquals(2, derived.rateSchedule().size());
        assertEquals(START, derived.scheduleStartDate());
    }
}

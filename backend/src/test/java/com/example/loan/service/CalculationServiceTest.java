package com.example.loan.service;

import com.example.loan.api.CalculationResponse;
import com.example.loan.api.ComparisonResult;
import com.example.loan.api.DeriveCalculationRequest;
import com.example.loan.domain.CalculationRecord;
import com.example.loan.domain.LoanContract;
import com.example.loan.domain.RepaymentMethod;
import com.example.loan.repo.CalculationRecordRepository;
import com.example.loan.repo.LoanContractRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

class CalculationServiceTest {

    private AmortizationService amortizationService;
    private CalculationRecordRepository recordRepository;
    private LoanContractRepository contractRepository;
    private ObjectMapper objectMapper;
    private CalculationService service;

    private final LocalDate start = LocalDate.of(2025, 1, 1);

    @BeforeEach
    void setUp() {
        amortizationService = new AmortizationService();
        recordRepository = mock(CalculationRecordRepository.class);
        contractRepository = mock(LoanContractRepository.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        when(recordRepository.save(any(CalculationRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service = new CalculationService(amortizationService, recordRepository, contractRepository,
                new RateScheduleService(), objectMapper);
    }

    @Test
    void deriveWithSnapshot_reproducesOldCalculationAfterContractScheduleChanges() throws Exception {
        List<LoanContract.StoredRateSegment> oldSchedule = List.of(
                segment(start, new BigDecimal("0.036")));
        List<LoanContract.StoredRateSegment> currentSchedule = List.of(
                segment(start, new BigDecimal("0.036")),
                segment(LocalDate.of(2025, 2, 10), new BigDecimal("0.048")));
        LoanContract contract = new LoanContract("C-1", "张三", RepaymentMethod.EQUAL_PRINCIPAL,
                new BigDecimal("0.036"), new BigDecimal("1000000.00"), 12, start, currentSchedule, 3);
        CalculationRecord source = savedRecord(contract, oldSchedule, 1, new BigDecimal("100000.00"));
        when(recordRepository.findById(9L)).thenReturn(Optional.of(source));

        CalculationResponse response = service.deriveAndSave(9L, new DeriveCalculationRequest(
                new BigDecimal("100000.00"), BigDecimal.ZERO, start, false));

        CalculationRecord saved = captureSaved();
        assertEquals(1, saved.getRateScheduleVersion());
        assertEquals(oldSchedule, saved.getRateScheduleSnapshot());
        ComparisonResult original = objectMapper.readValue(source.getResultJson(), ComparisonResult.class);
        assertEquals(original.reducePayment().summary().totalInterest(),
                response.comparison().reducePayment().summary().totalInterest());
    }

    @Test
    void deriveWithCurrentSchedule_usesNewVersion() {
        List<LoanContract.StoredRateSegment> oldSchedule = List.of(segment(start, new BigDecimal("0.036")));
        List<LoanContract.StoredRateSegment> currentSchedule = List.of(
                segment(start, new BigDecimal("0.036")),
                segment(LocalDate.of(2025, 2, 10), new BigDecimal("0.048")));
        LoanContract contract = new LoanContract("C-1", "张三", RepaymentMethod.EQUAL_PRINCIPAL,
                new BigDecimal("0.048"), new BigDecimal("1000000.00"), 12, start, currentSchedule, 3);
        CalculationRecord source = savedRecord(contract, oldSchedule, 1, new BigDecimal("100000.00"));
        when(recordRepository.findById(9L)).thenReturn(Optional.of(source));

        CalculationResponse response = service.deriveAndSave(9L, new DeriveCalculationRequest(
                new BigDecimal("100000.00"), BigDecimal.ZERO, start, true));

        CalculationRecord saved = captureSaved();
        assertEquals(3, saved.getRateScheduleVersion());
        assertEquals(currentSchedule, saved.getRateScheduleSnapshot());
        assertTrue(response.comparison().reducePayment().schedule().get(1)
                .interestBreakdown().size() > 1);
    }

    private CalculationRecord savedRecord(LoanContract contract,
                                          List<LoanContract.StoredRateSegment> schedule,
                                          int version,
                                          BigDecimal prepayment) {
        ComparisonResult result = amortizationService.compareScheduled(
                RepaymentMethod.EQUAL_PRINCIPAL, new BigDecimal("1000000.00"), 12,
                start, schedule, version, prepayment, start, BigDecimal.ZERO);
        CalculationRecord record = new CalculationRecord();
        ReflectionTestUtils.setField(record, "id", 9L);
        record.setContract(contract);
        record.setMethod(RepaymentMethod.EQUAL_PRINCIPAL);
        record.setAnnualRate(new BigDecimal("0.036"));
        record.setRemainingPrincipal(new BigDecimal("1000000.00"));
        record.setRemainingPeriods(12);
        record.setScheduleStartDate(start);
        record.setRateScheduleVersion(version);
        record.setRateScheduleSnapshot(schedule);
        record.setPrepaymentDate(start);
        record.setPrepaymentAmount(prepayment);
        record.setFee(BigDecimal.ZERO);
        record.setResultJson(toJson(result));
        return record;
    }

    private CalculationRecord captureSaved() {
        ArgumentCaptor<CalculationRecord> captor = ArgumentCaptor.forClass(CalculationRecord.class);
        verify(recordRepository).save(captor.capture());
        return captor.getValue();
    }

    private LoanContract.StoredRateSegment segment(LocalDate date, BigDecimal rate) {
        return new LoanContract.StoredRateSegment(date, rate);
    }

    private String toJson(ComparisonResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

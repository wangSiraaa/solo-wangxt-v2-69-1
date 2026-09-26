package com.example.loan.service;

import com.example.loan.api.CalculationResponse;
import com.example.loan.api.CompareRequest;
import com.example.loan.api.ComparisonResult;
import com.example.loan.api.DeriveCalculationRequest;
import com.example.loan.api.RecordSummaryView;
import com.example.loan.domain.CalculationRecord;
import com.example.loan.domain.LoanContract;
import com.example.loan.domain.RepaymentMethod;
import com.example.loan.repo.CalculationRecordRepository;
import com.example.loan.repo.LoanContractRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 编排提前还款对比计算并持久化计算记录。
 */
@Service
public class CalculationService {

    private final AmortizationService amortizationService;
    private final CalculationRecordRepository recordRepository;
    private final LoanContractRepository contractRepository;
    private final RateScheduleService rateScheduleService;
    private final ObjectMapper objectMapper;

    public CalculationService(AmortizationService amortizationService,
                              CalculationRecordRepository recordRepository,
                              LoanContractRepository contractRepository,
                              RateScheduleService rateScheduleService,
                              ObjectMapper objectMapper) {
        this.amortizationService = amortizationService;
        this.recordRepository = recordRepository;
        this.contractRepository = contractRepository;
        this.rateScheduleService = rateScheduleService;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行对比计算并保存计算记录。
     */
    @Transactional
    public CalculationResponse compareAndSave(CompareRequest req) {
        LoanContract contract = null;
        RepaymentMethod method;
        BigDecimal annualRate;
        BigDecimal remainingPrincipal;
        int remainingPeriods;
        LocalDate scheduleStartDate = req.scheduleStartDate();
        List<LoanContract.StoredRateSegment> schedule;
        int scheduleVersion;
        LocalDate prepaymentDate = req.prepaymentDate();

        if (req.contractId() != null) {
            contract = findContract(req.contractId());
            contract.backfillLegacySchedule(LocalDate.of(2025, 1, 1));
            method = contract.getMethod();
            annualRate = contract.getAnnualRate();
            remainingPrincipal = contract.getRemainingPrincipal();
            remainingPeriods = contract.getRemainingPeriods();
            scheduleStartDate = contract.getScheduleStartDate();
            schedule = List.copyOf(contract.getRateSchedule());
            scheduleVersion = contract.getRateScheduleVersion();
            if (prepaymentDate == null) {
                prepaymentDate = scheduleStartDate;
            }
        } else {
            method = req.method();
            annualRate = req.annualRate();
            remainingPrincipal = req.remainingPrincipal();
            if (req.remainingPeriods() == null) {
                throw new IllegalArgumentException("剩余期数不能为空");
            }
            remainingPeriods = req.remainingPeriods();
            if (scheduleStartDate == null && annualRate == null
                    && (req.rateSegments() == null || req.rateSegments().isEmpty())) {
                throw new IllegalArgumentException("年利率不能为空，或提供利率时间表和模拟开始日期");
            }
            if (req.rateSegments() != null && !req.rateSegments().isEmpty()) {
                schedule = rateScheduleService.normalize(req.rateSegments(), scheduleStartDate);
            } else if (scheduleStartDate != null) {
                schedule = List.of(new LoanContract.StoredRateSegment(scheduleStartDate, annualRate));
            } else {
                schedule = List.of();
            }
            scheduleVersion = 1;
            if (prepaymentDate == null) {
                prepaymentDate = scheduleStartDate;
            }
        }

        ComparisonResult result;
        if (scheduleStartDate != null && !schedule.isEmpty()) {
            result = amortizationService.compareScheduled(
                    method, remainingPrincipal, remainingPeriods, scheduleStartDate, schedule,
                    scheduleVersion, req.prepaymentAmount(), prepaymentDate, req.fee());
            annualRate = rateScheduleService.activeRate(schedule, prepaymentDate);
        } else {
            result = amortizationService.compare(
                    method, annualRate, remainingPrincipal, remainingPeriods,
                    req.prepaymentAmount(), req.fee());
        }

        CalculationRecord record = new CalculationRecord();
        record.setContract(contract);
        record.setMethod(method);
        record.setAnnualRate(annualRate);
        record.setRemainingPrincipal(remainingPrincipal);
        record.setRemainingPeriods(remainingPeriods);
        record.setScheduleStartDate(scheduleStartDate);
        record.setRateScheduleVersion(scheduleStartDate == null || schedule.isEmpty() ? 0 : scheduleVersion);
        record.setRateScheduleSnapshot(schedule);
        record.setPrepaymentDate(prepaymentDate);
        record.setPrepaymentAmount(req.prepaymentAmount());
        record.setFee(req.fee());
        record.setResultJson(toJson(result));
        recordRepository.save(record);

        return new CalculationResponse(record.getId(), method, result);
    }

    @Transactional
    public CalculationResponse deriveAndSave(long sourceId, DeriveCalculationRequest req) {
        CalculationRecord source = findRecord(sourceId);
        LoanContract contract = source.getContract();

        RepaymentMethod method;
        BigDecimal annualRate;
        BigDecimal remainingPrincipal;
        int remainingPeriods;
        LocalDate scheduleStartDate;
        List<LoanContract.StoredRateSegment> schedule;
        int version;
        LocalDate prepaymentDate = req.prepaymentDate() != null ? req.prepaymentDate() : source.getPrepaymentDate();

        if (Boolean.TRUE.equals(req.useCurrentContractSchedule())) {
            if (contract == null) {
                throw new IllegalArgumentException("历史记录不是由模拟合同生成，无法改用当前合同利率表");
            }
            method = contract.getMethod();
            annualRate = contract.getAnnualRate();
            remainingPrincipal = contract.getRemainingPrincipal();
            remainingPeriods = contract.getRemainingPeriods();
            scheduleStartDate = contract.getScheduleStartDate();
            schedule = List.copyOf(contract.getRateSchedule());
            version = contract.getRateScheduleVersion();
            if (prepaymentDate == null) {
                prepaymentDate = scheduleStartDate;
            }
        } else {
            method = source.getMethod();
            annualRate = source.getAnnualRate();
            remainingPrincipal = source.getRemainingPrincipal();
            remainingPeriods = source.getRemainingPeriods();
            scheduleStartDate = source.getScheduleStartDate();
            schedule = List.copyOf(source.getRateScheduleSnapshot());
            version = source.getRateScheduleVersion();
        }

        ComparisonResult result;
        if (scheduleStartDate != null && !schedule.isEmpty()) {
            result = amortizationService.compareScheduled(
                    method, remainingPrincipal, remainingPeriods, scheduleStartDate, schedule,
                    version, req.prepaymentAmount(), prepaymentDate, req.fee());
            annualRate = rateScheduleService.activeRate(schedule, prepaymentDate);
        } else {
            result = amortizationService.compare(
                    method, annualRate, remainingPrincipal, remainingPeriods,
                    req.prepaymentAmount(), req.fee());
        }

        CalculationRecord record = new CalculationRecord();
        record.setContract(contract);
        record.setMethod(method);
        record.setAnnualRate(annualRate);
        record.setRemainingPrincipal(remainingPrincipal);
        record.setRemainingPeriods(remainingPeriods);
        record.setScheduleStartDate(scheduleStartDate);
        record.setRateScheduleVersion(scheduleStartDate == null || schedule.isEmpty() ? 0 : version);
        record.setRateScheduleSnapshot(schedule);
        record.setPrepaymentDate(prepaymentDate);
        record.setPrepaymentAmount(req.prepaymentAmount());
        record.setFee(req.fee());
        record.setResultJson(toJson(result));
        recordRepository.save(record);

        return new CalculationResponse(record.getId(), method, result);
    }

    @Transactional(readOnly = true)
    public List<RecordSummaryView> listRecords() {
        return recordRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toSummaryView)
                .toList();
    }

    private RecordSummaryView toSummaryView(CalculationRecord record) {
        ComparisonResult result = fromJson(record.getResultJson());
        LoanContract contract = record.getContract();
        return new RecordSummaryView(
                record.getId(),
                record.getCreatedAt(),
                contract == null ? null : contract.getContractNo(),
                record.getMethod(),
                record.getAnnualRate(),
                record.getRemainingPrincipal(),
                record.getRemainingPeriods(),
                record.getScheduleStartDate(),
                record.getRateScheduleVersion(),
                record.getPrepaymentDate(),
                record.getPrepaymentAmount(),
                record.getFee(),
                result.shortenTerm().summary().periods(),
                result.reducePayment().summary().periods(),
                result.shortenTerm().summary().totalInterest(),
                result.reducePayment().summary().totalInterest(),
                result.diff().interestDiff());
    }

    @Transactional(readOnly = true)
    public CalculationResponse getRecord(long id) {
        CalculationRecord record = findRecord(id);
        return new CalculationResponse(record.getId(), record.getMethod(), fromJson(record.getResultJson()));
    }

    private LoanContract findContract(long id) {
        return contractRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模拟合同不存在: id=" + id));
    }

    private CalculationRecord findRecord(long id) {
        return recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("计算记录不存在: id=" + id));
    }

    private String toJson(ComparisonResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("计算结果序列化失败", e);
        }
    }

    private ComparisonResult fromJson(String json) {
        try {
            return objectMapper.readValue(json, ComparisonResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("计算记录反序列化失败", e);
        }
    }
}

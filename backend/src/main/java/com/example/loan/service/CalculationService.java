package com.example.loan.service;

import com.example.loan.api.CalculationResponse;
import com.example.loan.api.CompareRequest;
import com.example.loan.api.ComparisonResult;
import com.example.loan.api.DeriveRequest;
import com.example.loan.api.RateSegmentInput;
import com.example.loan.api.RecordSummaryView;
import com.example.loan.domain.CalculationRecord;
import com.example.loan.domain.LoanContract;
import com.example.loan.domain.RateScheduleVersion;
import com.example.loan.domain.RepaymentMethod;
import com.example.loan.repo.CalculationRecordRepository;
import com.example.loan.repo.LoanContractRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 编排提前还款对比计算并持久化计算记录。
 * 每次计算保存利率时间表快照与采用的版本，历史结果不随后续编辑漂移。
 */
@Service
public class CalculationService {

    private final AmortizationService amortizationService;
    private final RateScheduleService rateScheduleService;
    private final CalculationRecordRepository recordRepository;
    private final LoanContractRepository contractRepository;
    private final ObjectMapper objectMapper;

    public CalculationService(AmortizationService amortizationService,
                              RateScheduleService rateScheduleService,
                              CalculationRecordRepository recordRepository,
                              LoanContractRepository contractRepository,
                              ObjectMapper objectMapper) {
        this.amortizationService = amortizationService;
        this.rateScheduleService = rateScheduleService;
        this.recordRepository = recordRepository;
        this.contractRepository = contractRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行对比计算并保存计算记录。
     */
    @Transactional
    public CalculationResponse compareAndSave(CompareRequest req) {
        LoanContract contract = null;
        RepaymentMethod method;
        BigDecimal remainingPrincipal;
        int remainingPeriods;
        LocalDate scheduleStart;
        List<RateSegmentInput> segments;
        RateScheduleVersion version = null;

        if (req.contractId() != null) {
            contract = contractRepository.findById(req.contractId())
                    .orElseThrow(() -> new IllegalArgumentException("模拟合同不存在: id=" + req.contractId()));
            method = contract.getMethod();
            remainingPrincipal = contract.getRemainingPrincipal();
            remainingPeriods = contract.getRemainingPeriods();
            scheduleStart = contract.getScheduleStartDate();
            version = rateScheduleService.currentVersion(contract.getId());
            // 兼容无版本的历史合同：以合同初始年利率构成单一利率段
            segments = version != null
                    ? RateScheduleService.toInputs(version)
                    : List.of(new RateSegmentInput(scheduleStart, contract.getAnnualRate()));
        } else {
            method = req.method();
            remainingPrincipal = req.remainingPrincipal();
            if (req.remainingPeriods() == null) {
                throw new IllegalArgumentException("剩余期数不能为空");
            }
            remainingPeriods = req.remainingPeriods();
            scheduleStart = req.scheduleStartDate() != null ? req.scheduleStartDate() : LocalDate.now();
            if (req.rateSchedule() != null && !req.rateSchedule().isEmpty()) {
                segments = req.rateSchedule();
            } else {
                if (req.annualRate() == null) {
                    throw new IllegalArgumentException("年利率与利率时间表不能同时为空");
                }
                segments = List.of(new RateSegmentInput(scheduleStart, req.annualRate()));
            }
        }

        ComparisonResult result = amortizationService.compare(
                method, segments, scheduleStart, remainingPrincipal, remainingPeriods,
                req.prepaymentAmount(), req.fee());

        CalculationRecord record = saveRecord(contract, method, scheduleStart, version, segments,
                remainingPrincipal, remainingPeriods, req.prepaymentAmount(), req.fee(), result);
        return toResponse(record, result);
    }

    /**
     * 由历史记录派生新计算：
     * <ul>
     *   <li>请求提供利率时间表时，按给定时间表重算（其余参数沿用原记录）；</li>
     *   <li>否则合同类记录采用合同当前利率版本（体现利率表编辑后的影响）；</li>
     *   <li>手工记录沿用原快照（复现原计算）。</li>
     * </ul>
     */
    @Transactional
    public CalculationResponse deriveAndSave(long recordId, DeriveRequest req) {
        CalculationRecord old = recordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("计算记录不存在: id=" + recordId));

        LoanContract contract = old.getContract();
        LocalDate scheduleStart = old.getScheduleStartDate();
        List<RateSegmentInput> segments;
        RateScheduleVersion version = null;

        if (req != null && req.rateSchedule() != null && !req.rateSchedule().isEmpty()) {
            segments = req.rateSchedule();
            if (req.scheduleStartDate() != null) {
                scheduleStart = req.scheduleStartDate();
            }
        } else if (contract != null) {
            version = rateScheduleService.currentVersion(contract.getId());
            segments = version != null
                    ? RateScheduleService.toInputs(version)
                    : List.of(new RateSegmentInput(scheduleStart, contract.getAnnualRate()));
            scheduleStart = contract.getScheduleStartDate();
        } else {
            segments = snapshotOf(old);
        }

        ComparisonResult result = amortizationService.compare(
                old.getMethod(), segments, scheduleStart,
                old.getRemainingPrincipal(), old.getRemainingPeriods(),
                old.getPrepaymentAmount(), old.getFee());

        CalculationRecord record = saveRecord(contract, old.getMethod(), scheduleStart, version, segments,
                old.getRemainingPrincipal(), old.getRemainingPeriods(),
                old.getPrepaymentAmount(), old.getFee(), result);
        return toResponse(record, result);
    }

    @Transactional(readOnly = true)
    public List<RecordSummaryView> listRecords() {
        return recordRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toSummaryView)
                .toList();
    }

    @Transactional(readOnly = true)
    public CalculationResponse getRecord(long id) {
        CalculationRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("计算记录不存在: id=" + id));
        return toResponse(record, fromJson(record.getResultJson()));
    }

    private CalculationRecord saveRecord(LoanContract contract, RepaymentMethod method,
                                         LocalDate scheduleStart, RateScheduleVersion version,
                                         List<RateSegmentInput> segments, BigDecimal remainingPrincipal,
                                         int remainingPeriods, BigDecimal prepaymentAmount,
                                         BigDecimal fee, ComparisonResult result) {
        CalculationRecord record = new CalculationRecord();
        record.setContract(contract);
        record.setMethod(method);
        // 列表展示用：计划起始日当期适用的年利率
        record.setAnnualRate(RateTimeline.of(segments, scheduleStart).rateAt(scheduleStart));
        record.setScheduleStartDate(scheduleStart);
        record.setRateVersion(version);
        record.setRateSnapshotJson(snapshotJson(segments));
        record.setRemainingPrincipal(remainingPrincipal);
        record.setRemainingPeriods(remainingPeriods);
        record.setPrepaymentAmount(prepaymentAmount);
        record.setFee(fee);
        record.setResultJson(toJson(result));
        return recordRepository.save(record);
    }

    private CalculationResponse toResponse(CalculationRecord record, ComparisonResult result) {
        RateScheduleVersion version = record.getRateVersion();
        return new CalculationResponse(record.getId(), record.getMethod(), record.getScheduleStartDate(),
                version == null ? null : version.getVersionNo(), snapshotOf(record), result);
    }

    private RecordSummaryView toSummaryView(CalculationRecord record) {
        ComparisonResult result = fromJson(record.getResultJson());
        LoanContract contract = record.getContract();
        RateScheduleVersion version = record.getRateVersion();
        return new RecordSummaryView(
                record.getId(),
                record.getCreatedAt(),
                contract == null ? null : contract.getContractNo(),
                record.getMethod(),
                record.getAnnualRate(),
                record.getScheduleStartDate(),
                version == null ? null : version.getVersionNo(),
                record.getRemainingPrincipal(),
                record.getRemainingPeriods(),
                record.getPrepaymentAmount(),
                record.getFee(),
                result.shortenTerm().summary().periods(),
                result.reducePayment().summary().periods(),
                result.shortenTerm().summary().totalInterest(),
                result.reducePayment().summary().totalInterest(),
                result.diff().interestDiff());
    }

    private List<RateSegmentInput> snapshotOf(CalculationRecord record) {
        try {
            return objectMapper.readValue(record.getRateSnapshotJson(), new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("利率快照反序列化失败", e);
        }
    }

    private String snapshotJson(List<RateSegmentInput> segments) {
        try {
            return objectMapper.writeValueAsString(segments);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("利率快照序列化失败", e);
        }
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

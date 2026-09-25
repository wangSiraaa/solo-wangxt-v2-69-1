package com.example.loan.service;

import com.example.loan.api.CalculationResponse;
import com.example.loan.api.CompareRequest;
import com.example.loan.api.ComparisonResult;
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
import java.util.List;

/**
 * 编排提前还款对比计算并持久化计算记录。
 */
@Service
public class CalculationService {

    private final AmortizationService amortizationService;
    private final CalculationRecordRepository recordRepository;
    private final LoanContractRepository contractRepository;
    private final ObjectMapper objectMapper;

    public CalculationService(AmortizationService amortizationService,
                              CalculationRecordRepository recordRepository,
                              LoanContractRepository contractRepository,
                              ObjectMapper objectMapper) {
        this.amortizationService = amortizationService;
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
        BigDecimal annualRate;
        BigDecimal remainingPrincipal;
        int remainingPeriods;

        if (req.contractId() != null) {
            contract = contractRepository.findById(req.contractId())
                    .orElseThrow(() -> new IllegalArgumentException("模拟合同不存在: id=" + req.contractId()));
            method = contract.getMethod();
            annualRate = contract.getAnnualRate();
            remainingPrincipal = contract.getRemainingPrincipal();
            remainingPeriods = contract.getRemainingPeriods();
        } else {
            method = req.method();
            annualRate = req.annualRate();
            remainingPrincipal = req.remainingPrincipal();
            if (req.remainingPeriods() == null) {
                throw new IllegalArgumentException("剩余期数不能为空");
            }
            remainingPeriods = req.remainingPeriods();
        }

        ComparisonResult result = amortizationService.compare(
                method, annualRate, remainingPrincipal, remainingPeriods,
                req.prepaymentAmount(), req.fee());

        CalculationRecord record = new CalculationRecord();
        record.setContract(contract);
        record.setMethod(method);
        record.setAnnualRate(annualRate);
        record.setRemainingPrincipal(remainingPrincipal);
        record.setRemainingPeriods(remainingPeriods);
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
        CalculationRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("计算记录不存在: id=" + id));
        return new CalculationResponse(record.getId(), record.getMethod(), fromJson(record.getResultJson()));
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

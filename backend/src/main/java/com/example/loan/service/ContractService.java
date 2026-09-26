package com.example.loan.service;

import com.example.loan.api.ContractRequest;
import com.example.loan.api.ContractResponse;
import com.example.loan.api.RateSegmentView;
import com.example.loan.api.UpdateContractRequest;
import com.example.loan.domain.LoanContract;
import com.example.loan.repo.LoanContractRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 模拟合同与当前利率时间表的查询、编辑。
 */
@Service
public class ContractService {

    private final LoanContractRepository repository;
    private final RateScheduleService rateScheduleService;

    public ContractService(LoanContractRepository repository, RateScheduleService rateScheduleService) {
        this.repository = repository;
        this.rateScheduleService = rateScheduleService;
    }

    @Transactional(readOnly = true)
    public List<ContractResponse> list() {
        return repository.findAll().stream()
                .peek(contract -> contract.backfillLegacySchedule(LocalDate.of(2025, 1, 1)))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ContractResponse get(long id) {
        LoanContract contract = find(id);
        contract.backfillLegacySchedule(LocalDate.of(2025, 1, 1));
        return toResponse(contract);
    }

    @Transactional
    public ContractResponse create(ContractRequest req) {
        var schedule = rateScheduleService.normalize(req.rateSegments(), req.scheduleStartDate());
        LoanContract contract = new LoanContract(
                req.contractNo(),
                req.borrowerName(),
                req.method(),
                schedule.get(0).annualRate(),
                req.remainingPrincipal(),
                req.remainingPeriods(),
                req.scheduleStartDate(),
                schedule,
                1);
        return toResponse(repository.save(contract));
    }

    @Transactional
    public ContractResponse update(long id, UpdateContractRequest req) {
        LoanContract contract = find(id);
        var schedule = rateScheduleService.normalize(req.rateSegments(), req.scheduleStartDate());
        contract.update(
                req.contractNo(),
                req.borrowerName(),
                req.method(),
                schedule.get(0).annualRate(),
                req.remainingPrincipal(),
                req.remainingPeriods(),
                req.scheduleStartDate(),
                schedule);
        return toResponse(contract);
    }

    private LoanContract find(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模拟合同不存在: id=" + id));
    }

    private ContractResponse toResponse(LoanContract contract) {
        List<RateSegmentView> views = rateScheduleService.toViews(contract.getRateSchedule());
        return new ContractResponse(
                contract.getId(),
                contract.getContractNo(),
                contract.getBorrowerName(),
                contract.getMethod(),
                contract.getAnnualRate(),
                contract.getRemainingPrincipal(),
                contract.getRemainingPeriods(),
                contract.getScheduleStartDate(),
                contract.getRateScheduleVersion(),
                views,
                contract.getCreatedAt());
    }
}

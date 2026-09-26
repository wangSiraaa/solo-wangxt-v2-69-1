package com.example.loan.service;

import com.example.loan.api.ContractRequest;
import com.example.loan.api.RateSegmentInput;
import com.example.loan.domain.LoanContract;
import com.example.loan.repo.LoanContractRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 模拟合同管理。建合同与初始利率时间表（版本 1）在同一事务内完成。
 */
@Service
public class ContractService {

    private final LoanContractRepository contractRepository;
    private final RateScheduleService rateScheduleService;

    public ContractService(LoanContractRepository contractRepository,
                           RateScheduleService rateScheduleService) {
        this.contractRepository = contractRepository;
        this.rateScheduleService = rateScheduleService;
    }

    /**
     * 新建模拟合同并保存初始利率时间表（版本 1）。
     * 未提供 rateSchedule 时以初始年利率构成自计划起始日起的单一利率段。
     */
    @Transactional
    public LoanContract create(ContractRequest req) {
        LocalDate scheduleStart = req.scheduleStartDate() != null ? req.scheduleStartDate() : LocalDate.now();
        LoanContract contract = new LoanContract(
                req.contractNo(), req.borrowerName(), req.method(),
                req.annualRate(), req.remainingPrincipal(), req.remainingPeriods(), scheduleStart);
        contract = contractRepository.save(contract);
        List<RateSegmentInput> segments = (req.rateSchedule() != null && !req.rateSchedule().isEmpty())
                ? req.rateSchedule()
                : List.of(new RateSegmentInput(scheduleStart, req.annualRate()));
        rateScheduleService.saveNewVersion(contract, segments);
        return contract;
    }
}

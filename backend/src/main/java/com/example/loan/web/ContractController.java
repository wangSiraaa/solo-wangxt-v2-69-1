package com.example.loan.web;

import com.example.loan.api.ContractRequest;
import com.example.loan.domain.LoanContract;
import com.example.loan.repo.LoanContractRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模拟合同接口。全部为演示数据，不连接真实放贷系统。
 */
@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final LoanContractRepository contractRepository;

    public ContractController(LoanContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    @GetMapping
    public List<LoanContract> list() {
        return contractRepository.findAll();
    }

    @GetMapping("/{id}")
    public LoanContract get(@PathVariable long id) {
        return contractRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("模拟合同不存在: id=" + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoanContract create(@Valid @RequestBody ContractRequest req) {
        LoanContract contract = new LoanContract(
                req.contractNo(), req.borrowerName(), req.method(),
                req.annualRate(), req.remainingPrincipal(), req.remainingPeriods());
        return contractRepository.save(contract);
    }
}

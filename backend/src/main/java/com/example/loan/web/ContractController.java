package com.example.loan.web;

import com.example.loan.api.ContractRequest;
import com.example.loan.api.ContractResponse;
import com.example.loan.api.UpdateContractRequest;
import com.example.loan.service.ContractService;
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

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @GetMapping
    public List<ContractResponse> list() {
        return contractService.list();
    }

    @GetMapping("/{id}")
    public ContractResponse get(@PathVariable long id) {
        return contractService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContractResponse create(@Valid @RequestBody ContractRequest req) {
        return contractService.create(req);
    }

    @PutMapping("/{id}")
    public ContractResponse update(@PathVariable long id, @Valid @RequestBody UpdateContractRequest req) {
        return contractService.update(id, req);
    }
}

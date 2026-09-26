package com.example.loan.web;

import com.example.loan.api.ContractRequest;
import com.example.loan.api.RateScheduleUpdateRequest;
import com.example.loan.api.RateScheduleView;
import com.example.loan.domain.LoanContract;
import com.example.loan.repo.LoanContractRepository;
import com.example.loan.service.ContractService;
import com.example.loan.service.RateScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模拟合同与利率时间表接口。全部为演示数据，不连接真实放贷系统。
 */
@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final LoanContractRepository contractRepository;
    private final ContractService contractService;
    private final RateScheduleService rateScheduleService;

    public ContractController(LoanContractRepository contractRepository,
                              ContractService contractService,
                              RateScheduleService rateScheduleService) {
        this.contractRepository = contractRepository;
        this.contractService = contractService;
        this.rateScheduleService = rateScheduleService;
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
        return contractService.create(req);
    }

    /** 合同当前利率时间表（最新版本）。 */
    @GetMapping("/{id}/rate-schedule")
    public RateScheduleView currentSchedule(@PathVariable long id) {
        RateScheduleView view = rateScheduleService.currentView(id);
        if (view == null) {
            throw new IllegalArgumentException("模拟合同不存在或尚无利率时间表: id=" + id);
        }
        return view;
    }

    /** 合同利率时间表的全部版本（新到旧）。 */
    @GetMapping("/{id}/rate-schedule/versions")
    public List<RateScheduleView> scheduleVersions(@PathVariable long id) {
        return rateScheduleService.versions(id);
    }

    /** 整体替换利率时间表：校验（重叠 / 空档 / 精度）通过后保存为新版本。 */
    @PutMapping("/{id}/rate-schedule")
    public RateScheduleView replaceSchedule(@PathVariable long id,
                                            @Valid @RequestBody RateScheduleUpdateRequest req) {
        return rateScheduleService.replaceSchedule(id, req.segments());
    }
}

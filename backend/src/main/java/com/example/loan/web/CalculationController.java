package com.example.loan.web;

import com.example.loan.api.CalculationResponse;
import com.example.loan.api.CompareRequest;
import com.example.loan.api.DeriveCalculationRequest;
import com.example.loan.api.RecordSummaryView;
import com.example.loan.service.CalculationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 提前还款对比计算与历史记录接口。
 */
@RestController
@RequestMapping("/api/calculations")
public class CalculationController {

    private final CalculationService calculationService;

    public CalculationController(CalculationService calculationService) {
        this.calculationService = calculationService;
    }

    /** 对同一笔提前还款生成「缩短期限」与「降低月供」两种方案并保存记录。 */
    @PostMapping("/compare")
    public CalculationResponse compare(@Valid @RequestBody CompareRequest req) {
        return calculationService.compareAndSave(req);
    }

    /** 基于历史记录复现（沿用快照）或使用合同当前利率版本派生新计算。 */
    @PostMapping("/{id}/derive")
    public CalculationResponse derive(@PathVariable long id,
                                      @Valid @RequestBody DeriveCalculationRequest req) {
        return calculationService.deriveAndSave(id, req);
    }

    /** 历史计算记录（列表视图）。 */
    @GetMapping
    public List<RecordSummaryView> list() {
        return calculationService.listRecords();
    }

    @GetMapping("/{id}")
    public CalculationResponse get(@PathVariable long id) {
        return calculationService.getRecord(id);
    }
}

package com.example.loan.web;

import com.example.loan.api.CalculationResponse;
import com.example.loan.api.CompareRequest;
import com.example.loan.api.DeriveRequest;
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

    /** 由历史记录派生新计算（默认采用合同当前利率版本；可在请求体中指定利率时间表）。 */
    @PostMapping("/{id}/derive")
    public CalculationResponse derive(@PathVariable long id,
                                      @RequestBody(required = false) DeriveRequest req) {
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

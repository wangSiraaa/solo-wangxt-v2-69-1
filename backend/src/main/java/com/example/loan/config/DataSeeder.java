package com.example.loan.config;

import com.example.loan.api.RateSegmentInput;
import com.example.loan.domain.LoanContract;
import com.example.loan.domain.RepaymentMethod;
import com.example.loan.repo.LoanContractRepository;
import com.example.loan.service.RateScheduleService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 启动时写入一批模拟合同及其初始利率时间表（仅当表为空时），供工作台直接选用。
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final LoanContractRepository contractRepository;
    private final RateScheduleService rateScheduleService;

    public DataSeeder(LoanContractRepository contractRepository,
                      RateScheduleService rateScheduleService) {
        this.contractRepository = contractRepository;
        this.rateScheduleService = rateScheduleService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (contractRepository.count() > 0) {
            return;
        }
        LocalDate start = LocalDate.of(2026, 9, 1);
        seed(new LoanContract("MOCK-2026-0001", "张三", RepaymentMethod.EQUAL_INSTALLMENT,
                        new BigDecimal("0.049"), new BigDecimal("1000000.00"), 240, start),
                List.of(new RateSegmentInput(start, new BigDecimal("0.049"))));
        seed(new LoanContract("MOCK-2026-0002", "李四", RepaymentMethod.EQUAL_PRINCIPAL,
                        new BigDecimal("0.049"), new BigDecimal("1000000.00"), 240, start),
                List.of(new RateSegmentInput(start, new BigDecimal("0.049"))));
        // 分段利率示例：LPR 下调，2027-03-15（期中间）起 3.60% → 3.10%
        seed(new LoanContract("MOCK-2026-0003", "王五", RepaymentMethod.EQUAL_INSTALLMENT,
                        new BigDecimal("0.036"), new BigDecimal("650000.00"), 180, start),
                List.of(new RateSegmentInput(start, new BigDecimal("0.036")),
                        new RateSegmentInput(LocalDate.of(2027, 3, 15), new BigDecimal("0.031"))));
        // 分段利率示例：2027-06-15（期中间）起利率上调
        seed(new LoanContract("MOCK-2026-0004", "赵六", RepaymentMethod.EQUAL_PRINCIPAL,
                        new BigDecimal("0.042"), new BigDecimal("2300000.00"), 300, start),
                List.of(new RateSegmentInput(start, new BigDecimal("0.042")),
                        new RateSegmentInput(LocalDate.of(2027, 6, 15), new BigDecimal("0.047"))));
    }

    private void seed(LoanContract contract, List<RateSegmentInput> segments) {
        LoanContract saved = contractRepository.save(contract);
        rateScheduleService.saveNewVersion(saved, segments);
    }
}

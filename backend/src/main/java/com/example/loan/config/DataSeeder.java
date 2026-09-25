package com.example.loan.config;

import com.example.loan.domain.LoanContract;
import com.example.loan.domain.RepaymentMethod;
import com.example.loan.repo.LoanContractRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 启动时写入一批模拟合同（仅当表为空时），供工作台直接选用。
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final LoanContractRepository contractRepository;

    public DataSeeder(LoanContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    @Override
    public void run(String... args) {
        if (contractRepository.count() > 0) {
            return;
        }
        contractRepository.saveAll(List.of(
                new LoanContract("MOCK-2024-0001", "张三", RepaymentMethod.EQUAL_INSTALLMENT,
                        new BigDecimal("0.049"), new BigDecimal("1000000.00"), 240),
                new LoanContract("MOCK-2024-0002", "李四", RepaymentMethod.EQUAL_PRINCIPAL,
                        new BigDecimal("0.049"), new BigDecimal("1000000.00"), 240),
                new LoanContract("MOCK-2024-0003", "王五", RepaymentMethod.EQUAL_INSTALLMENT,
                        new BigDecimal("0.036"), new BigDecimal("650000.00"), 180),
                new LoanContract("MOCK-2024-0004", "赵六", RepaymentMethod.EQUAL_PRINCIPAL,
                        new BigDecimal("0.042"), new BigDecimal("2300000.00"), 300)
        ));
    }
}

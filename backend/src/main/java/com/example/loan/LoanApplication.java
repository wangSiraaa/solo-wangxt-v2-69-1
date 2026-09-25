package com.example.loan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 提前还款方案对比工作台。
 * 仅使用模拟合同数据，不连接任何真实放贷接口。
 */
@SpringBootApplication
public class LoanApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoanApplication.class, args);
    }
}

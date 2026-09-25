package com.example.loan.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 模拟贷款合同。仅用于演示，不对应任何真实放贷数据。
 */
@Entity
@Table(name = "loan_contract")
public class LoanContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 合同编号（模拟）。 */
    @Column(nullable = false, unique = true, length = 32)
    private String contractNo;

    /** 借款人姓名（模拟）。 */
    @Column(nullable = false, length = 64)
    private String borrowerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RepaymentMethod method;

    /** 年利率，小数形式，例如 0.049 表示 4.9%。 */
    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal annualRate;

    /** 当前剩余本金。 */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal remainingPrincipal;

    /** 当前剩余期数（月）。 */
    @Column(nullable = false)
    private int remainingPeriods;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected LoanContract() {
    }

    public LoanContract(String contractNo, String borrowerName, RepaymentMethod method,
                        BigDecimal annualRate, BigDecimal remainingPrincipal, int remainingPeriods) {
        this.contractNo = contractNo;
        this.borrowerName = borrowerName;
        this.method = method;
        this.annualRate = annualRate;
        this.remainingPrincipal = remainingPrincipal;
        this.remainingPeriods = remainingPeriods;
    }

    public Long getId() {
        return id;
    }

    public String getContractNo() {
        return contractNo;
    }

    public String getBorrowerName() {
        return borrowerName;
    }

    public RepaymentMethod getMethod() {
        return method;
    }

    public BigDecimal getAnnualRate() {
        return annualRate;
    }

    public BigDecimal getRemainingPrincipal() {
        return remainingPrincipal;
    }

    public int getRemainingPeriods() {
        return remainingPeriods;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

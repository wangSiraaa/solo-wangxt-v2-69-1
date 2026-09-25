package com.example.loan.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 一次提前还款对比计算的记录。完整计算结果以 JSON 形式保存在 resultJson 中。
 */
@Entity
@Table(name = "calculation_record")
public class CalculationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的模拟合同；手工录入参数时为空。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private LoanContract contract;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RepaymentMethod method;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal annualRate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal remainingPrincipal;

    @Column(nullable = false)
    private int remainingPeriods;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal prepaymentAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal fee;

    /** 完整对比结果（两种方案的逐期计划与汇总），JSON 文本。 */
    @Column(nullable = false, columnDefinition = "text")
    private String resultJson;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public LoanContract getContract() {
        return contract;
    }

    public void setContract(LoanContract contract) {
        this.contract = contract;
    }

    public RepaymentMethod getMethod() {
        return method;
    }

    public void setMethod(RepaymentMethod method) {
        this.method = method;
    }

    public BigDecimal getAnnualRate() {
        return annualRate;
    }

    public void setAnnualRate(BigDecimal annualRate) {
        this.annualRate = annualRate;
    }

    public BigDecimal getRemainingPrincipal() {
        return remainingPrincipal;
    }

    public void setRemainingPrincipal(BigDecimal remainingPrincipal) {
        this.remainingPrincipal = remainingPrincipal;
    }

    public int getRemainingPeriods() {
        return remainingPeriods;
    }

    public void setRemainingPeriods(int remainingPeriods) {
        this.remainingPeriods = remainingPeriods;
    }

    public BigDecimal getPrepaymentAmount() {
        return prepaymentAmount;
    }

    public void setPrepaymentAmount(BigDecimal prepaymentAmount) {
        this.prepaymentAmount = prepaymentAmount;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

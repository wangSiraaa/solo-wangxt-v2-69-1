package com.example.loan.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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

    /** 计算时计划起始日适用的年利率（冗余展示用；完整利率时间表见 rateSnapshotJson）。 */
    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal annualRate;

    /** 计算时的还款计划起始日。 */
    @Column(nullable = false)
    private LocalDate scheduleStartDate;

    /** 计算时采用的利率时间表版本；手工录入参数时为空。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rate_version_id")
    private RateScheduleVersion rateVersion;

    /** 计算时实际采用的利率时间表快照（JSON），历史结果不随后续编辑漂移。 */
    @Column(nullable = false, columnDefinition = "text")
    private String rateSnapshotJson;

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

    public LocalDate getScheduleStartDate() {
        return scheduleStartDate;
    }

    public void setScheduleStartDate(LocalDate scheduleStartDate) {
        this.scheduleStartDate = scheduleStartDate;
    }

    public RateScheduleVersion getRateVersion() {
        return rateVersion;
    }

    public void setRateVersion(RateScheduleVersion rateVersion) {
        this.rateVersion = rateVersion;
    }

    public String getRateSnapshotJson() {
        return rateSnapshotJson;
    }

    public void setRateSnapshotJson(String rateSnapshotJson) {
        this.rateSnapshotJson = rateSnapshotJson;
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

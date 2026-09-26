package com.example.loan.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    /** 当前年利率，小数形式，例如 0.049 表示 4.9%；等于利率时间表首段/当前段。 */
    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal annualRate;

    /** 当前剩余本金。 */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal remainingPrincipal;

    /** 当前剩余期数（月）。 */
    @Column(nullable = false)
    private int remainingPeriods;

    /** 本次模拟计划的起息/当前账期开始日期。 */
    @Column
    private LocalDate scheduleStartDate;

    @Column(nullable = false, columnDefinition = "integer not null default 1")
    private int rateScheduleVersion = 1;

    /** PostgreSQL jsonb：按生效日排列的利率段快照。 */
    @Column(columnDefinition = "jsonb default '[]'::jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<StoredRateSegment> rateSchedule = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected LoanContract() {
    }

    public LoanContract(String contractNo, String borrowerName, RepaymentMethod method,
                        BigDecimal annualRate, BigDecimal remainingPrincipal, int remainingPeriods) {
        this(contractNo, borrowerName, method, annualRate, remainingPrincipal, remainingPeriods,
                null, List.of(), 1);
    }

    public LoanContract(String contractNo, String borrowerName, RepaymentMethod method,
                        BigDecimal annualRate, BigDecimal remainingPrincipal, int remainingPeriods,
                        LocalDate scheduleStartDate, List<StoredRateSegment> rateSchedule,
                        int rateScheduleVersion) {
        this.contractNo = contractNo;
        this.borrowerName = borrowerName;
        this.method = method;
        this.annualRate = annualRate;
        this.remainingPrincipal = remainingPrincipal;
        this.remainingPeriods = remainingPeriods;
        this.scheduleStartDate = scheduleStartDate;
        this.rateSchedule = rateSchedule == null ? new ArrayList<>() : new ArrayList<>(rateSchedule);
        this.rateScheduleVersion = rateScheduleVersion;
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

    public LocalDate getScheduleStartDate() {
        return scheduleStartDate;
    }

    public int getRateScheduleVersion() {
        return rateScheduleVersion;
    }

    public List<StoredRateSegment> getRateSchedule() {
        return rateSchedule == null ? List.of() : List.copyOf(rateSchedule);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void update(String contractNo, String borrowerName, RepaymentMethod method,
                       BigDecimal annualRate, BigDecimal remainingPrincipal, int remainingPeriods,
                       LocalDate scheduleStartDate, List<StoredRateSegment> rateSchedule) {
        this.contractNo = contractNo;
        this.borrowerName = borrowerName;
        this.method = method;
        this.annualRate = annualRate;
        this.remainingPrincipal = remainingPrincipal;
        this.remainingPeriods = remainingPeriods;
        this.scheduleStartDate = scheduleStartDate;
        this.rateSchedule = new ArrayList<>(rateSchedule);
        this.rateScheduleVersion++;
    }

    /** 兼容旧版合同：为无日期字段的数据补齐初始利率段，不提升利率表版本。 */
    public void backfillLegacySchedule(LocalDate defaultStartDate) {
        if (this.scheduleStartDate == null) {
            this.scheduleStartDate = defaultStartDate;
        }
        if (this.rateSchedule == null || this.rateSchedule.isEmpty()) {
            this.rateSchedule = new ArrayList<>(List.of(
                    new StoredRateSegment(this.scheduleStartDate, this.annualRate)));
        }
    }

    /** JPA JSON 序列化用的简单利率段结构。 */
    public record StoredRateSegment(LocalDate effectiveDate, BigDecimal annualRate) {
    }
}

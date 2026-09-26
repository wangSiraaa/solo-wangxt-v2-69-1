package com.example.loan.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 利率时间表中的一个利率段：自 effectiveDate（含）起生效，直至同版本下一段生效日（不含）。
 */
@Entity
@Table(name = "rate_segment")
public class RateSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private RateScheduleVersion version;

    /** 段内序号（按生效日升序），保证读取顺序稳定。 */
    @Column(nullable = false)
    private int segmentIndex;

    @Column(nullable = false)
    private LocalDate effectiveDate;

    /** 年利率，小数形式，例如 0.049 表示 4.9%。 */
    @Column(nullable = false, precision = 11, scale = 6)
    private BigDecimal annualRate;

    protected RateSegment() {
    }

    public RateSegment(int segmentIndex, LocalDate effectiveDate, BigDecimal annualRate) {
        this.segmentIndex = segmentIndex;
        this.effectiveDate = effectiveDate;
        this.annualRate = annualRate;
    }

    public Long getId() {
        return id;
    }

    public RateScheduleVersion getVersion() {
        return version;
    }

    public void setVersion(RateScheduleVersion version) {
        this.version = version;
    }

    public int getSegmentIndex() {
        return segmentIndex;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public BigDecimal getAnnualRate() {
        return annualRate;
    }
}

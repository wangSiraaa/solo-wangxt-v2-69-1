package com.example.loan.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 合同利率时间表的一个版本。每次编辑利率时间表都会生成新版本，
 * 历史计算记录引用计算时采用的版本并保存快照，不随后续编辑漂移。
 */
@Entity
@Table(name = "rate_schedule_version")
public class RateScheduleVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private LoanContract contract;

    /** 版本号（同一合同内从 1 递增）。 */
    @Column(nullable = false)
    private int versionNo;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("segmentIndex ASC")
    private List<RateSegment> segments = new ArrayList<>();

    protected RateScheduleVersion() {
    }

    public RateScheduleVersion(LoanContract contract, int versionNo) {
        this.contract = contract;
        this.versionNo = versionNo;
    }

    public void addSegment(RateSegment segment) {
        segment.setVersion(this);
        segments.add(segment);
    }

    public Long getId() {
        return id;
    }

    public LoanContract getContract() {
        return contract;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<RateSegment> getSegments() {
        return segments;
    }
}

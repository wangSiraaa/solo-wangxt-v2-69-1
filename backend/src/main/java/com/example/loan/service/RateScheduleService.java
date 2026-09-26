package com.example.loan.service;

import com.example.loan.api.RateScheduleView;
import com.example.loan.api.RateSegmentInput;
import com.example.loan.domain.LoanContract;
import com.example.loan.domain.RateScheduleVersion;
import com.example.loan.domain.RateSegment;
import com.example.loan.repo.LoanContractRepository;
import com.example.loan.repo.RateScheduleVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 合同利率时间表的版本管理。每次整体替换时间表都会校验并保存为新版本；
 * 历史版本只读，供计算记录引用与复现。
 */
@Service
public class RateScheduleService {

    private final RateScheduleVersionRepository versionRepository;
    private final LoanContractRepository contractRepository;

    public RateScheduleService(RateScheduleVersionRepository versionRepository,
                               LoanContractRepository contractRepository) {
        this.versionRepository = versionRepository;
        this.contractRepository = contractRepository;
    }

    /** 当前版本视图；无任何版本时返回 null。 */
    @Transactional(readOnly = true)
    public RateScheduleView currentView(long contractId) {
        return versionRepository.findTopByContractIdOrderByVersionNoDesc(contractId)
                .map(RateScheduleService::toView)
                .orElse(null);
    }

    /** 全部版本（新到旧）。 */
    @Transactional(readOnly = true)
    public List<RateScheduleView> versions(long contractId) {
        return versionRepository.findByContractIdOrderByVersionNoDesc(contractId).stream()
                .map(RateScheduleService::toView)
                .toList();
    }

    /**
     * 整体替换合同利率时间表：校验（重叠 / 空档 / 精度）通过后保存为新版本并返回。
     */
    @Transactional
    public RateScheduleView replaceSchedule(long contractId, List<RateSegmentInput> segments) {
        LoanContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("模拟合同不存在: id=" + contractId));
        RateScheduleVersion version = saveNewVersion(contract, segments);
        return toView(version);
    }

    /**
     * 校验并保存新版本（供建合同与替换时间表共用）。
     */
    @Transactional
    public RateScheduleVersion saveNewVersion(LoanContract contract, List<RateSegmentInput> segments) {
        // 校验：同日重复、起始空档、利率范围与精度
        RateTimeline.validate(segments, contract.getScheduleStartDate());
        int nextVersionNo = versionRepository
                .findTopByContractIdOrderByVersionNoDesc(contract.getId())
                .map(v -> v.getVersionNo() + 1)
                .orElse(1);
        RateScheduleVersion version = new RateScheduleVersion(contract, nextVersionNo);
        List<RateSegmentInput> sorted = segments.stream()
                .sorted(Comparator.comparing(RateSegmentInput::effectiveDate))
                .toList();
        for (int i = 0; i < sorted.size(); i++) {
            RateSegmentInput seg = sorted.get(i);
            version.addSegment(new RateSegment(i, seg.effectiveDate(), seg.annualRate()));
        }
        return versionRepository.save(version);
    }

    /** 合同当前版本实体；无版本时为 null（由调用方回退处理）。 */
    @Transactional(readOnly = true)
    public RateScheduleVersion currentVersion(long contractId) {
        return versionRepository.findTopByContractIdOrderByVersionNoDesc(contractId).orElse(null);
    }

    /** 版本实体 → 计算用的利率段列表（生效日升序）。 */
    public static List<RateSegmentInput> toInputs(RateScheduleVersion version) {
        return version.getSegments().stream()
                .sorted(Comparator.comparing(RateSegment::getSegmentIndex))
                .map(s -> new RateSegmentInput(s.getEffectiveDate(), s.getAnnualRate()))
                .toList();
    }

    public static RateScheduleView toView(RateScheduleVersion version) {
        return new RateScheduleView(version.getId(), version.getVersionNo(),
                version.getCreatedAt(), toInputs(version));
    }
}

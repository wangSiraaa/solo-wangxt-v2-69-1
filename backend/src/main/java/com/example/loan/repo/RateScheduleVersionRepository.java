package com.example.loan.repo;

import com.example.loan.domain.RateScheduleVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RateScheduleVersionRepository extends JpaRepository<RateScheduleVersion, Long> {

    Optional<RateScheduleVersion> findTopByContractIdOrderByVersionNoDesc(Long contractId);

    List<RateScheduleVersion> findByContractIdOrderByVersionNoDesc(Long contractId);
}

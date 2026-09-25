package com.example.loan.repo;

import com.example.loan.domain.CalculationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CalculationRecordRepository extends JpaRepository<CalculationRecord, Long> {

    List<CalculationRecord> findAllByOrderByCreatedAtDesc();
}

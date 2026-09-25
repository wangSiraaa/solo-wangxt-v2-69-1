package com.example.loan.repo;

import com.example.loan.domain.LoanContract;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanContractRepository extends JpaRepository<LoanContract, Long> {
}

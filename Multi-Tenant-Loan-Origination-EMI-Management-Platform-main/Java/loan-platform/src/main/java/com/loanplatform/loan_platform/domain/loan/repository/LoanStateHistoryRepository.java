package com.loanplatform.loan_platform.domain.loan.repository;

import com.loanplatform.loan_platform.domain.loan.model.LoanStateHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LoanStateHistoryRepository extends JpaRepository<LoanStateHistory, UUID> {
}

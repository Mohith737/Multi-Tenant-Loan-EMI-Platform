package com.loanplatform.loan_platform.domain.loan.repository;

import com.loanplatform.loan_platform.domain.loan.model.ReconciliationReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationReportRepository extends JpaRepository<ReconciliationReport, UUID> {

    Optional<ReconciliationReport> findByTenantIdAndReportDate(UUID tenantId, LocalDate reportDate);
}

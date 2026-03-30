package com.loanplatform.loan_platform.domain.borrower.repository;

import com.loanplatform.loan_platform.domain.borrower.model.CreditProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CreditProfileRepository extends JpaRepository<CreditProfile, UUID> {

    Optional<CreditProfile> findByBorrowerIdAndTenantId(UUID borrowerId, UUID tenantId);
}

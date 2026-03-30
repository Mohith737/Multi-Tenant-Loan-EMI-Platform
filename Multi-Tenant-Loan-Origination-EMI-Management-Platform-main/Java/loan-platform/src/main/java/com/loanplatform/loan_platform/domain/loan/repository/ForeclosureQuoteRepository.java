package com.loanplatform.loan_platform.domain.loan.repository;

import com.loanplatform.loan_platform.domain.loan.model.ForeclosureQuote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ForeclosureQuoteRepository extends JpaRepository<ForeclosureQuote, UUID> {

    Optional<ForeclosureQuote> findByIdAndTenantId(UUID id, UUID tenantId);
}

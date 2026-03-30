package com.loanplatform.loan_platform.domain.loan.repository;

import com.loanplatform.loan_platform.domain.loan.model.LoanOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanOfferRepository extends JpaRepository<LoanOffer, UUID> {

    List<LoanOffer> findByApplicationIdAndTenantIdOrderByOfferRankAsc(UUID applicationId, UUID tenantId);

    Optional<LoanOffer> findByIdAndApplicationIdAndTenantId(UUID id, UUID applicationId, UUID tenantId);

    List<LoanOffer> findByApplicationIdAndTenantId(UUID applicationId, UUID tenantId);
}

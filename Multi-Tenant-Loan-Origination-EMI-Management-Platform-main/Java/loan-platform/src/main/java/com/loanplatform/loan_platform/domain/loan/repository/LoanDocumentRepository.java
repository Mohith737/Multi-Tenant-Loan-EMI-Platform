package com.loanplatform.loan_platform.domain.loan.repository;

import com.loanplatform.loan_platform.domain.loan.model.LoanDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanDocumentRepository extends JpaRepository<LoanDocument, UUID> {

    List<LoanDocument> findByTenantIdAndApplicationIdOrderByDocumentTypeAsc(UUID tenantId, UUID applicationId);

    Optional<LoanDocument> findByIdAndTenantIdAndApplicationId(UUID id, UUID tenantId, UUID applicationId);

    long countByTenantIdAndApplicationId(UUID tenantId, UUID applicationId);
}

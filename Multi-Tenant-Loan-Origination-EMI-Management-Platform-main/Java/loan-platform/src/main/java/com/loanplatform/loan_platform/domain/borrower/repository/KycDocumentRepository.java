package com.loanplatform.loan_platform.domain.borrower.repository;

import com.loanplatform.loan_platform.domain.borrower.model.KycDocument;
import com.loanplatform.loan_platform.domain.borrower.model.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KycDocumentRepository extends JpaRepository<KycDocument, UUID> {

    List<KycDocument> findByBorrowerIdAndTenantIdAndArchivedFalse(UUID borrowerId, UUID tenantId);

    List<KycDocument> findByTenantIdAndStatusAndArchivedFalseOrderByCreatedAtAsc(UUID tenantId, KycStatus status);

    Optional<KycDocument> findByIdAndTenantId(UUID id, UUID tenantId);
}

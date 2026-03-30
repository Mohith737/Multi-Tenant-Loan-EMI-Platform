package com.loanplatform.loan_platform.domain.borrower.repository;

import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.model.BorrowerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BorrowerRepository extends JpaRepository<Borrower, UUID> {

    boolean existsByTenantIdAndPanEncrypted(UUID tenantId, String panEncrypted);

    boolean existsByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<Borrower> findByEmailIgnoreCase(String email);

    List<Borrower> findAllByEmailIgnoreCase(String email);

    Optional<Borrower> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);

    Optional<Borrower> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Borrower> findByIdAndTenantIdAndStripeCustomerIdIsNotNullAndStripePaymentMethodIdIsNotNull(UUID id, UUID tenantId);

    List<Borrower> findByStatusAndMambuClientIdIsNull(BorrowerStatus status);
}

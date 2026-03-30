package com.loanplatform.loan_platform.domain.tenant.repository;

import com.loanplatform.loan_platform.domain.tenant.model.LoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanProductRepository extends JpaRepository<LoanProduct, UUID> {
    List<LoanProduct> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<LoanProduct> findByTenantIdIn(List<UUID> tenantIds);

    Optional<LoanProduct> findByTenantIdAndMambuProductKey(UUID tenantId, String mambuProductKey);
}

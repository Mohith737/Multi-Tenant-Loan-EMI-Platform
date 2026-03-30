package com.loanplatform.loan_platform.domain.tenant.repository;

import com.loanplatform.loan_platform.domain.tenant.model.Tenant;
import com.loanplatform.loan_platform.domain.tenant.model.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    boolean existsByDomain(String domain);

    Optional<Tenant> findByDomain(String domain);

    List<Tenant> findByStatusOrderByCreatedAtDesc(TenantStatus status);

    List<Tenant> findByStatusAndStripeAccountIdIsNotNullOrderByCreatedAtDesc(TenantStatus status);
}

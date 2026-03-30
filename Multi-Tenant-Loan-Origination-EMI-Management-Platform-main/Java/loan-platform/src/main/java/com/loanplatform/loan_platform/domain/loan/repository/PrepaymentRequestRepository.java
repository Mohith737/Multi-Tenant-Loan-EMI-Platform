package com.loanplatform.loan_platform.domain.loan.repository;

import com.loanplatform.loan_platform.domain.loan.model.PrepaymentRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrepaymentRequestRepository extends JpaRepository<PrepaymentRequest, UUID> {

    Optional<PrepaymentRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<PrepaymentRequest> findByIdAndTenantIdAndBorrowerId(UUID id, UUID tenantId, UUID borrowerId);

    Optional<PrepaymentRequest> findByTenantIdAndStripePaymentIntentId(UUID tenantId, String stripePaymentIntentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select request
            from PrepaymentRequest request
            where request.id = :id
              and request.tenantId = :tenantId
            """)
    Optional<PrepaymentRequest> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select request
            from PrepaymentRequest request
            where request.tenantId = :tenantId
              and request.stripePaymentIntentId = :stripePaymentIntentId
            """)
    Optional<PrepaymentRequest> findByTenantIdAndStripePaymentIntentIdForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("stripePaymentIntentId") String stripePaymentIntentId
    );

    boolean existsByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    List<PrepaymentRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}

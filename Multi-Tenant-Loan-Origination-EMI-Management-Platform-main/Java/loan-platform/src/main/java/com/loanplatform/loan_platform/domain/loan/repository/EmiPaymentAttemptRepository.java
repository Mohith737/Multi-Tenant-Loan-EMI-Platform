package com.loanplatform.loan_platform.domain.loan.repository;

import com.loanplatform.loan_platform.domain.loan.model.EmiPaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmiPaymentAttemptRepository extends JpaRepository<EmiPaymentAttempt, UUID> {

    Optional<EmiPaymentAttempt> findTopByInstallmentIdOrderByAttemptedAtDesc(UUID installmentId);

    Optional<EmiPaymentAttempt> findByStripePaymentIntentId(String stripePaymentIntentId);

    boolean existsByStripePaymentIntentIdAndMambuPostedTrue(String stripePaymentIntentId);

    List<EmiPaymentAttempt> findByTenantIdAndApplicationIdOrderByAttemptedAtDesc(UUID tenantId, UUID applicationId);

    @Query("""
            select coalesce(sum(a.attemptNumber), 0)
            from EmiPaymentAttempt a
            where a.tenantId = :tenantId
              and a.attemptedAt >= :start
              and a.attemptedAt < :end
            """)
    long sumAttemptsForTenantBetween(@Param("tenantId") UUID tenantId,
                                     @Param("start") Instant start,
                                     @Param("end") Instant end);

    @Query("""
            select coalesce(sum(case when a.mambuPosted = true then 1 else 0 end), 0)
            from EmiPaymentAttempt a
            where a.tenantId = :tenantId
              and a.attemptedAt >= :start
              and a.attemptedAt < :end
            """)
    long countMambuPostedForTenantBetween(@Param("tenantId") UUID tenantId,
                                          @Param("start") Instant start,
                                          @Param("end") Instant end);

    @Query("""
            select coalesce(sum(i.totalDue), 0)
            from EmiPaymentAttempt a
            join LoanInstallment i on i.id = a.installmentId
            where a.tenantId = :tenantId
              and a.mambuPosted = true
              and a.attemptedAt >= :start
              and a.attemptedAt < :end
            """)
    BigDecimal sumMambuPostedAmountForTenantBetween(@Param("tenantId") UUID tenantId,
                                                    @Param("start") Instant start,
                                                    @Param("end") Instant end);
}

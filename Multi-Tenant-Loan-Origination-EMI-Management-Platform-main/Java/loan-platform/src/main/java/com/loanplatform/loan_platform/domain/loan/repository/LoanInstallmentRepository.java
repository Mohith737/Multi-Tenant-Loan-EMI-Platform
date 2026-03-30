package com.loanplatform.loan_platform.domain.loan.repository;

import com.loanplatform.loan_platform.domain.loan.model.LoanInstallment;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanInstallmentRepository extends JpaRepository<LoanInstallment, UUID> {

    List<LoanInstallment> findByTenantIdAndApplicationIdOrderByInstallmentNumberAsc(UUID tenantId, UUID applicationId);

    List<LoanInstallment> findByTenantIdAndBorrowerIdAndApplicationIdOrderByInstallmentNumberAsc(
            UUID tenantId,
            UUID borrowerId,
            UUID applicationId
    );

    void deleteByTenantIdAndApplicationId(UUID tenantId, UUID applicationId);

    long countByTenantIdAndApplicationId(UUID tenantId, UUID applicationId);

    List<LoanInstallment> findByTenantIdAndDueDateLessThanEqualAndStatus(
            UUID tenantId,
            LocalDate dueDate,
            LoanInstallmentStatus status
    );

    List<LoanInstallment> findByDueDateAndStatusIn(LocalDate dueDate, Collection<LoanInstallmentStatus> statuses);

    List<LoanInstallment> findByStatusInAndDueDateLessThanEqual(Collection<LoanInstallmentStatus> statuses, LocalDate dueDate);

    List<LoanInstallment> findByTenantIdAndStatusAndDueDateLessThanEqual(UUID tenantId,
                                                                          LoanInstallmentStatus status,
                                                                          LocalDate dueDate);

    List<LoanInstallment> findByTenantIdAndStatusOrderByDueDateAsc(UUID tenantId, LoanInstallmentStatus status);

    List<LoanInstallment> findByTenantIdAndDueDateAndStatusOrderByInstallmentNumberAsc(
            UUID tenantId,
            LocalDate dueDate,
            LoanInstallmentStatus status
    );

    Optional<LoanInstallment> findByTenantIdAndStripePaymentIntentId(UUID tenantId, String stripePaymentIntentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select installment
            from LoanInstallment installment
            where installment.id = :id
              and installment.tenantId = :tenantId
            """)
    Optional<LoanInstallment> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select installment
            from LoanInstallment installment
            where installment.tenantId = :tenantId
              and installment.stripePaymentIntentId = :stripePaymentIntentId
            """)
    Optional<LoanInstallment> findByTenantIdAndStripePaymentIntentIdForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("stripePaymentIntentId") String stripePaymentIntentId
    );

    @Query("""
            select count(installment)
            from LoanInstallment installment
            where installment.tenantId = :tenantId
              and installment.dueDate = :date
            """)
    long countDueByTenantAndDate(@Param("tenantId") UUID tenantId, @Param("date") LocalDate date);

    @Query("""
            select count(installment)
            from LoanInstallment installment
            where installment.tenantId = :tenantId
              and installment.paidDate = :date
              and installment.status = com.loanplatform.loan_platform.domain.loan.model.LoanInstallmentStatus.PAID
            """)
    long countPaidByTenantAndDate(@Param("tenantId") UUID tenantId, @Param("date") LocalDate date);

    @Query("""
            select count(installment)
            from LoanInstallment installment
            where installment.tenantId = :tenantId
              and installment.dueDate = :date
              and installment.status in (
                com.loanplatform.loan_platform.domain.loan.model.LoanInstallmentStatus.FAILED,
                com.loanplatform.loan_platform.domain.loan.model.LoanInstallmentStatus.RETRY_SCHEDULED,
                com.loanplatform.loan_platform.domain.loan.model.LoanInstallmentStatus.OVERDUE
              )
            """)
    long countFailedByTenantAndDate(@Param("tenantId") UUID tenantId, @Param("date") LocalDate date);

    @Query("""
            select coalesce(sum(installment.totalDue), 0)
            from LoanInstallment installment
            where installment.tenantId = :tenantId
              and installment.dueDate = :date
            """)
    BigDecimal sumTotalDueByTenantAndDate(@Param("tenantId") UUID tenantId, @Param("date") LocalDate date);

    @Query("""
            select coalesce(sum(installment.totalDue), 0)
            from LoanInstallment installment
            where installment.tenantId = :tenantId
              and installment.paidDate = :date
              and installment.status = com.loanplatform.loan_platform.domain.loan.model.LoanInstallmentStatus.PAID
            """)
    BigDecimal sumPaidAmountByTenantAndDate(@Param("tenantId") UUID tenantId, @Param("date") LocalDate date);

    @Query("""
            select installment
            from LoanInstallment installment
            where installment.mambuSyncPending = true
              and installment.updatedAt <= :cutoff
            """)
    List<LoanInstallment> findMambuSyncPendingOlderThan(@Param("cutoff") Instant cutoff);
}

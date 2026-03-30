package com.loanplatform.loan_platform.domain.loan.repository;

import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplicationStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {

    Optional<LoanApplication> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select application
            from LoanApplication application
            where application.id = :id
              and application.tenantId = :tenantId
            """)
    Optional<LoanApplication> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    Optional<LoanApplication> findByIdAndTenantIdAndBorrowerId(UUID id, UUID tenantId, UUID borrowerId);

    Optional<LoanApplication> findByTenantIdAndBorrowerIdAndMambuLoanId(UUID tenantId, UUID borrowerId, String mambuLoanId);

    Optional<LoanApplication> findByTenantIdAndMambuLoanId(UUID tenantId, String mambuLoanId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select application
            from LoanApplication application
            where application.stripeTransferId = :stripeTransferId
            """)
    Optional<LoanApplication> findByStripeTransferIdForUpdate(@Param("stripeTransferId") String stripeTransferId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select application
            from LoanApplication application
            where application.id = :id
              and application.tenantId = :tenantId
              and application.borrowerId = :borrowerId
            """)
    Optional<LoanApplication> findForUpdate(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("borrowerId") UUID borrowerId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select application
            from LoanApplication application
            where application.tenantId = :tenantId
              and application.borrowerId = :borrowerId
              and application.mambuLoanId = :mambuLoanId
            """)
    Optional<LoanApplication> findByTenantIdAndBorrowerIdAndMambuLoanIdForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("borrowerId") UUID borrowerId,
            @Param("mambuLoanId") String mambuLoanId
    );

    Optional<LoanApplication> findByIdAndTenantIdAndStatus(UUID id, UUID tenantId, LoanApplicationStatus status);

    boolean existsByTenantIdAndBorrowerIdAndStatusIn(UUID tenantId, UUID borrowerId, Collection<LoanApplicationStatus> statuses);

    List<LoanApplication> findByStatusAndOffersExpiresAtBefore(LoanApplicationStatus status, Instant now);

    List<LoanApplication> findByTenantIdAndStatusOrderBySubmittedAtAsc(UUID tenantId, LoanApplicationStatus status);

    List<LoanApplication> findByMambuSyncFailedTrueAndStatus(LoanApplicationStatus status);

    List<LoanApplication> findByMambuDisburseSyncFailedTrueAndStatus(LoanApplicationStatus status);

    List<LoanApplication> findByScheduleFetchFailedTrueAndStatus(LoanApplicationStatus status);

    Optional<LoanApplication> findByIdAndTenantIdAndLoanState(UUID id, UUID tenantId, LoanLifecycleState loanState);
}

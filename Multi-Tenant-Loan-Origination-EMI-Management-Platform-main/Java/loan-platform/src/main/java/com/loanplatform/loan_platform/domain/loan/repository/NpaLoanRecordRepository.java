package com.loanplatform.loan_platform.domain.loan.repository;

import com.loanplatform.loan_platform.domain.loan.model.NpaLoanRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NpaLoanRecordRepository extends JpaRepository<NpaLoanRecord, UUID> {

    List<NpaLoanRecord> findByTenantIdAndActiveTrueOrderByNpaFlaggedAtDesc(UUID tenantId);

    Optional<NpaLoanRecord> findByTenantIdAndLoanApplicationIdAndActiveTrue(UUID tenantId, UUID loanApplicationId);

    Optional<NpaLoanRecord> findByTenantIdAndMambuLoanIdAndActiveTrue(UUID tenantId, String mambuLoanId);

    List<NpaLoanRecord> findByActiveTrueOrderByNpaFlaggedAtAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select record
            from NpaLoanRecord record
            where record.tenantId = :tenantId
              and record.mambuLoanId = :mambuLoanId
              and record.active = true
            """)
    Optional<NpaLoanRecord> findByTenantIdAndMambuLoanIdForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("mambuLoanId") String mambuLoanId
    );
}

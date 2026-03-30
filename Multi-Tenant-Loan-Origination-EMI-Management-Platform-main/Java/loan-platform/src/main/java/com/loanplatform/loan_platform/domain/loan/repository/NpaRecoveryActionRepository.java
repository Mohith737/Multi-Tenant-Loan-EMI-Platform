package com.loanplatform.loan_platform.domain.loan.repository;

import com.loanplatform.loan_platform.domain.loan.model.NpaRecoveryAction;
import com.loanplatform.loan_platform.domain.loan.model.NpaRecoveryActionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NpaRecoveryActionRepository extends JpaRepository<NpaRecoveryAction, UUID> {

    boolean existsByTenantIdAndNpaLoanRecordIdAndActionType(
            UUID tenantId,
            UUID npaLoanRecordId,
            NpaRecoveryActionType actionType
    );
}

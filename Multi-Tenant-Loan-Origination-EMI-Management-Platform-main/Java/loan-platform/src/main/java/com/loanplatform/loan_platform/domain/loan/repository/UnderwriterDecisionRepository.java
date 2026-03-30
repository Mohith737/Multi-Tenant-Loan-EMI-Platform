package com.loanplatform.loan_platform.domain.loan.repository;

import com.loanplatform.loan_platform.domain.loan.model.UnderwriterDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UnderwriterDecisionRepository extends JpaRepository<UnderwriterDecision, UUID> {

    Optional<UnderwriterDecision> findByApplicationId(UUID applicationId);

    boolean existsByApplicationId(UUID applicationId);
}

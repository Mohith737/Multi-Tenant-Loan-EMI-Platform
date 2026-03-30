package com.loanplatform.loan_platform.domain.loan.repository;

import com.loanplatform.loan_platform.domain.loan.model.RevisedScheduleSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RevisedScheduleSnapshotRepository extends JpaRepository<RevisedScheduleSnapshot, UUID> {

    Optional<RevisedScheduleSnapshot> findTopByPrepaymentRequestIdOrderBySnapshotVersionDesc(UUID prepaymentRequestId);
}

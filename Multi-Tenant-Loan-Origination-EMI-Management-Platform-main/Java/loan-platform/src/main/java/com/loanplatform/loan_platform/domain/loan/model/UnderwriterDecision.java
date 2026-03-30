package com.loanplatform.loan_platform.domain.loan.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "underwriter_decisions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnderwriterDecision {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "underwriter_id", nullable = false, updatable = false)
    private UUID underwriterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UnderwriterDecisionType decision;

    @Column(length = 1000)
    private String remarks;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;
}

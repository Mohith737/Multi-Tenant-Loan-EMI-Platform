package com.loanplatform.loan_platform.domain.loan.service;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanAccountResponse;
import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplicationStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallment;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallmentStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleEvent;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleState;
import com.loanplatform.loan_platform.domain.loan.model.NpaLoanRecord;
import com.loanplatform.loan_platform.domain.loan.model.NpaRecoveryAction;
import com.loanplatform.loan_platform.domain.loan.model.NpaRecoveryActionType;
import com.loanplatform.loan_platform.domain.loan.model.NpaRecoveryStage;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanInstallmentRepository;
import com.loanplatform.loan_platform.domain.loan.repository.NpaLoanRecordRepository;
import com.loanplatform.loan_platform.domain.loan.repository.NpaRecoveryActionRepository;
import com.loanplatform.loan_platform.dto.request.NpaOverrideRequest;
import com.loanplatform.loan_platform.dto.response.NpaOverrideResponse;
import com.loanplatform.loan_platform.dto.response.NpaPortfolioResponse;
import com.loanplatform.loan_platform.exception.NpaOverrideNotAllowedException;
import com.loanplatform.loan_platform.mapper.NpaMapper;
import com.loanplatform.loan_platform.multitenancy.TenantAware;
import com.loanplatform.loan_platform.port.inbound.NpaUseCase;
import com.loanplatform.loan_platform.port.outbound.KafkaEventPort;
import com.loanplatform.loan_platform.port.outbound.LoanStateMachinePort;
import com.loanplatform.loan_platform.port.outbound.MambuPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NpaService implements NpaUseCase {

    private static final EnumSet<LoanInstallmentStatus> UNPAID_STATUSES = EnumSet.of(
            LoanInstallmentStatus.PENDING,
            LoanInstallmentStatus.PROCESSING,
            LoanInstallmentStatus.RETRY_SCHEDULED,
            LoanInstallmentStatus.REQUIRES_ACTION,
            LoanInstallmentStatus.OVERDUE,
            LoanInstallmentStatus.FAILED
    );

    private final LoanInstallmentRepository loanInstallmentRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final BorrowerRepository borrowerRepository;
    private final NpaLoanRecordRepository npaLoanRecordRepository;
    private final NpaRecoveryActionRepository npaRecoveryActionRepository;
    private final MambuPort mambuPort;
    private final LoanStateMachinePort loanStateMachinePort;
    private final KafkaEventPort kafkaEventPort;
    private final StringRedisTemplate stringRedisTemplate;
    private final NpaMapper npaMapper;

    @Value("${app.flow8.npa-dpd-threshold:90}")
    private int npaDpdThreshold;

    @Value("${app.flow8.redis.flag-ttl-seconds:86400}")
    private long flagRedisTtlSeconds;

    @Value("${app.flow8.redis.override-lock-ttl-seconds:300}")
    private long overrideLockTtlSeconds;

    @Value("${app.flow8.redis.escalation-ttl-seconds:86400}")
    private long escalationRedisTtlSeconds;

    @Value("${app.flow8.scheduler.zone:Asia/Kolkata}")
    private String schedulerZone;

    @Override
    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public NpaPortfolioResponse getNpaPortfolio(UUID tenantId, int page, int size) {
        log.debug("Flow8 getNpaPortfolio entry tenantId={} page={} size={}", tenantId, page, size);

        List<NpaLoanRecord> records = npaLoanRecordRepository.findByTenantIdAndActiveTrueOrderByNpaFlaggedAtDesc(tenantId);
        List<NpaPortfolioResponse.NpaLoanItem> items = paginate(records, page, size).stream()
                .map(record -> {
                    Borrower borrower = borrowerRepository.findByIdAndTenantId(record.getBorrowerId(), tenantId).orElse(null);
                    return npaMapper.toNpaLoanItem(record, borrower);
                })
                .toList();

        log.debug("Flow8 getNpaPortfolio exit tenantId={} total={}", tenantId, records.size());
        return NpaPortfolioResponse.builder()
                .npaloans(items)
                .total((long) records.size())
                .build();
    }

    @Override
    @TenantAware
    @Transactional(propagation = Propagation.REQUIRED)
    public NpaOverrideResponse overrideNpaLoan(UUID tenantId, UUID actorId, String loanAccountId, NpaOverrideRequest request) {
        String lockKey = buildOverrideLockKey(tenantId, loanAccountId);
        Boolean lockAcquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, actorId.toString(), Duration.ofSeconds(overrideLockTtlSeconds));
        if (!Boolean.TRUE.equals(lockAcquired)) {
            throw new NpaOverrideNotAllowedException("NPA override already in progress for loan account " + loanAccountId);
        }

        try {
            NpaLoanRecord record = npaLoanRecordRepository.findByTenantIdAndMambuLoanIdForUpdate(tenantId, loanAccountId)
                    .orElseThrow(() -> new IllegalArgumentException("Active NPA record not found for loan account: " + loanAccountId));

            if (!record.isActive() || record.getRecoveryStage() == NpaRecoveryStage.OVERRIDDEN) {
                throw new NpaOverrideNotAllowedException("NPA lock is already cleared for loan account " + loanAccountId);
            }

            LoanApplication application = loanApplicationRepository
                    .findByIdAndTenantIdForUpdate(record.getLoanApplicationId(), tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Loan application not found: " + record.getLoanApplicationId()));

            MambuLoanAccountResponse patched = mambuPort.patchLoanState(
                    loanAccountId,
                    "ACTIVE",
                    "NPA override cleared by admin. Reason: " + request.getOverrideReason()
            );

            if (application.getLoanState() == LoanLifecycleState.NON_PERFORMING
                    || application.getLoanState() == LoanLifecycleState.LEGAL_ESCALATION) {
                LoanLifecycleState transitioned = loanStateMachinePort.transition(
                        application.getLoanState(),
                        LoanLifecycleEvent.CLEAR_NPA_OVERRIDE
                );
                application.setLoanState(transitioned);
            } else if (application.getLoanState() != LoanLifecycleState.ACTIVE_REPAYMENT) {
                throw new NpaOverrideNotAllowedException(
                        "Cannot clear NPA for loan in state " + application.getLoanState()
                );
            }

            application.setStatus(LoanApplicationStatus.ACTIVE_REPAYMENT);
            loanApplicationRepository.save(application);

            Instant now = Instant.now();
            record.setRecoveryStage(NpaRecoveryStage.OVERRIDDEN);
            record.setOverrideReason(request.getOverrideReason());
            record.setAdminNote(request.getAdminNote());
            record.setOverriddenBy(actorId);
            record.setOverriddenAt(now);
            record.setActive(false);
            record.setMambuState(patched != null && patched.getAccountState() != null ? patched.getAccountState() : "ACTIVE");
            npaLoanRecordRepository.save(record);

            recordActionIfMissing(
                    record,
                    NpaRecoveryActionType.OVERRIDE_CLEARED,
                    request.getAdminNote(),
                    "ADMIN"
            );

            kafkaEventPort.publish(
                    "loan.npa.override.cleared",
                    tenantId,
                    record.getLoanApplicationId(),
                    "loanAccountId=" + loanAccountId + ",actorId=" + actorId
            );

            return NpaOverrideResponse.builder()
                    .loanAccountId(loanAccountId)
                    .npaLockCleared(true)
                    .overriddenAt(now)
                    .build();
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    @Override
    @TenantAware
    @Transactional(propagation = Propagation.REQUIRED)
    public void flagNpaLoans(LocalDate processingDate) {
        LocalDate thresholdDate = processingDate.minusDays(Math.max(1, npaDpdThreshold));
        List<LoanInstallment> candidates = loanInstallmentRepository.findByStatusInAndDueDateLessThanEqual(
                UNPAID_STATUSES,
                thresholdDate
        );
        log.debug(
                "Flow8 flagNpaLoans entry processingDate={} thresholdDate={} candidateInstallments={}",
                processingDate,
                thresholdDate,
                candidates.size()
        );

        Map<NpaCandidateKey, List<LoanInstallment>> groupedByLoan = new HashMap<>();
        for (LoanInstallment installment : candidates) {
            NpaCandidateKey key = new NpaCandidateKey(installment.getTenantId(), installment.getApplicationId());
            groupedByLoan.computeIfAbsent(key, ignored -> new ArrayList<>()).add(installment);
        }

        for (Map.Entry<NpaCandidateKey, List<LoanInstallment>> entry : groupedByLoan.entrySet()) {
            NpaCandidateKey key = entry.getKey();
            List<LoanInstallment> installments = entry.getValue();
            try {
                processFlagCandidate(key.tenantId(), key.applicationId(), installments, processingDate);
            } catch (Exception ex) {
                log.error(
                        "Flow8 flag candidate failed tenantId={} applicationId={} processingDate={}",
                        key.tenantId(),
                        key.applicationId(),
                        processingDate,
                        ex
                );
            }
        }

        log.debug("Flow8 flagNpaLoans exit processingDate={} candidatesProcessed={}", processingDate, groupedByLoan.size());
    }

    @Override
    @TenantAware
    @Transactional(propagation = Propagation.REQUIRED)
    public void runRecoveryEscalation(LocalDate processingDate) {
        List<NpaLoanRecord> activeRecords = npaLoanRecordRepository.findByActiveTrueOrderByNpaFlaggedAtAsc();
        log.debug("Flow8 runRecoveryEscalation entry processingDate={} activeRecords={}", processingDate, activeRecords.size());

        for (NpaLoanRecord record : activeRecords) {
            try {
                long elapsedDays = ChronoUnit.DAYS.between(
                        record.getNpaFlaggedAt().atZone(ZoneId.of(schedulerZone)).toLocalDate(),
                        processingDate
                );
                NpaRecoveryStage targetStage = resolveStageForElapsedDays(elapsedDays);
                if (targetStage == null) {
                    continue;
                }
                progressRecoveryStages(record, targetStage, processingDate);
            } catch (Exception ex) {
                log.error(
                        "Flow8 escalation failed tenantId={} npaRecordId={} loanApplicationId={}",
                        record.getTenantId(),
                        record.getId(),
                        record.getLoanApplicationId(),
                        ex
                );
            }
        }

        log.debug("Flow8 runRecoveryEscalation exit processingDate={}", processingDate);
    }

    public NpaRecoveryStage resolveStageForElapsedDays(long elapsedDays) {
        if (elapsedDays >= 30) {
            return NpaRecoveryStage.DAY_30_LEGAL_ESCALATION;
        }
        if (elapsedDays >= 7) {
            return NpaRecoveryStage.DAY_7_ESCALATION;
        }
        if (elapsedDays >= 1) {
            return NpaRecoveryStage.DAY_1_REMINDER;
        }
        return null;
    }

    private void processFlagCandidate(
            UUID tenantId,
            UUID applicationId,
            List<LoanInstallment> overdueInstallments,
            LocalDate processingDate
    ) {
        int maxDpd = overdueInstallments.stream()
                .mapToInt(installment -> (int) ChronoUnit.DAYS.between(installment.getDueDate(), processingDate))
                .max()
                .orElse(0);

        if (maxDpd < npaDpdThreshold) {
            return;
        }

        BigDecimal outstandingAmount = calculateOutstandingForLoan(tenantId, applicationId);
        if (outstandingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        Optional<NpaLoanRecord> existing = npaLoanRecordRepository.findByTenantIdAndLoanApplicationIdAndActiveTrue(tenantId, applicationId);
        if (existing.isPresent()) {
            NpaLoanRecord record = existing.get();
            record.setMaxDpd(Math.max(record.getMaxDpd(), maxDpd));
            record.setOutstandingAmount(outstandingAmount);
            npaLoanRecordRepository.save(record);
            return;
        }

        LoanApplication application = loanApplicationRepository.findByIdAndTenantIdForUpdate(applicationId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Loan application not found: " + applicationId));

        if (application.getMambuLoanId() == null || application.getMambuLoanId().isBlank()) {
            return;
        }

        String redisKey = buildFlagRedisKey(application.getMambuLoanId(), tenantId);
        Boolean lockAcquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(redisKey, processingDate.toString(), Duration.ofSeconds(flagRedisTtlSeconds));
        if (!Boolean.TRUE.equals(lockAcquired)) {
            return;
        }

        MambuLoanAccountResponse patched = mambuPort.patchLoanState(
                application.getMambuLoanId(),
                "NON_PERFORMING",
                "90+ DPD - Flagged as NPA by system on " + processingDate
        );

        if (application.getLoanState() == LoanLifecycleState.ACTIVE_REPAYMENT) {
            LoanLifecycleState transitioned = loanStateMachinePort.transition(
                    application.getLoanState(),
                    LoanLifecycleEvent.FLAG_NON_PERFORMING
            );
            application.setLoanState(transitioned);
            loanApplicationRepository.save(application);
        }

        NpaLoanRecord record = NpaLoanRecord.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .loanApplicationId(application.getId())
                .borrowerId(application.getBorrowerId())
                .mambuLoanId(application.getMambuLoanId())
                .maxDpd(maxDpd)
                .outstandingAmount(outstandingAmount)
                .npaFlaggedAt(Instant.now())
                .recoveryStage(NpaRecoveryStage.DAY_1_REMINDER)
                .mambuState(patched != null && patched.getAccountState() != null ? patched.getAccountState() : "NON_PERFORMING")
                .active(true)
                .build();
        NpaLoanRecord saved = npaLoanRecordRepository.save(record);

        recordActionIfMissing(
                saved,
                NpaRecoveryActionType.FLAGGED_NON_PERFORMING,
                "Loan flagged at " + maxDpd + " DPD",
                "SYSTEM"
        );

        kafkaEventPort.publish(
                "loan.npa.flagged",
                tenantId,
                applicationId,
                "loanAccountId=" + application.getMambuLoanId() + ",maxDpd=" + maxDpd
        );
    }

    private BigDecimal calculateOutstandingForLoan(UUID tenantId, UUID applicationId) {
        List<LoanInstallment> allInstallments = loanInstallmentRepository
                .findByTenantIdAndApplicationIdOrderByInstallmentNumberAsc(tenantId, applicationId);
        return allInstallments.stream()
                .filter(installment -> UNPAID_STATUSES.contains(installment.getStatus()))
                .map(LoanInstallment::getTotalDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void progressRecoveryStages(NpaLoanRecord record, NpaRecoveryStage targetStage, LocalDate processingDate) {
        if (record.getRecoveryStage() == NpaRecoveryStage.OVERRIDDEN) {
            return;
        }

        List<NpaRecoveryStage> orderedStages = List.of(
                NpaRecoveryStage.DAY_1_REMINDER,
                NpaRecoveryStage.DAY_7_ESCALATION,
                NpaRecoveryStage.DAY_30_LEGAL_ESCALATION
        );

        int currentIndex = orderedStages.indexOf(record.getRecoveryStage());
        int targetIndex = orderedStages.indexOf(targetStage);

        if (targetIndex < 0 || currentIndex > targetIndex) {
            return;
        }

        for (int index = Math.max(0, currentIndex + 1); index <= targetIndex; index++) {
            NpaRecoveryStage stage = orderedStages.get(index);
            applyRecoveryStage(record, stage, processingDate);
        }

        if (record.getRecoveryStage() == NpaRecoveryStage.DAY_1_REMINDER && targetStage == NpaRecoveryStage.DAY_1_REMINDER) {
            applyRecoveryStage(record, NpaRecoveryStage.DAY_1_REMINDER, processingDate);
        }
    }

    private void applyRecoveryStage(NpaLoanRecord record, NpaRecoveryStage stage, LocalDate processingDate) {
        NpaRecoveryActionType actionType = toActionType(stage);
        if (actionType == null) {
            return;
        }

        String escalationKey = buildEscalationRedisKey(record.getId(), stage);
        Boolean lockAcquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(escalationKey, processingDate.toString(), Duration.ofSeconds(escalationRedisTtlSeconds));
        if (!Boolean.TRUE.equals(lockAcquired)
                && npaRecoveryActionRepository.existsByTenantIdAndNpaLoanRecordIdAndActionType(
                record.getTenantId(),
                record.getId(),
                actionType
        )) {
            return;
        }

        if (stage == NpaRecoveryStage.DAY_30_LEGAL_ESCALATION) {
            escalateToLegal(record, processingDate);
            kafkaEventPort.publish(
                    "loan.npa.recovery.day30.legal",
                    record.getTenantId(),
                    record.getLoanApplicationId(),
                    "loanAccountId=" + record.getMambuLoanId()
            );
        } else if (stage == NpaRecoveryStage.DAY_7_ESCALATION) {
            kafkaEventPort.publish(
                    "loan.npa.recovery.day7",
                    record.getTenantId(),
                    record.getLoanApplicationId(),
                    "loanAccountId=" + record.getMambuLoanId()
            );
        } else if (stage == NpaRecoveryStage.DAY_1_REMINDER) {
            kafkaEventPort.publish(
                    "loan.npa.recovery.day1",
                    record.getTenantId(),
                    record.getLoanApplicationId(),
                    "loanAccountId=" + record.getMambuLoanId()
            );
        }

        record.setRecoveryStage(stage);
        npaLoanRecordRepository.save(record);
        recordActionIfMissing(record, actionType, "Recovery stage advanced to " + stage, "SYSTEM");
    }

    private void escalateToLegal(NpaLoanRecord record, LocalDate processingDate) {
        LoanApplication application = loanApplicationRepository
                .findByIdAndTenantIdForUpdate(record.getLoanApplicationId(), record.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Loan application not found: " + record.getLoanApplicationId()));

        if (application.getLoanState() == LoanLifecycleState.NON_PERFORMING) {
            LoanLifecycleState legalEscalated = loanStateMachinePort.transition(
                    application.getLoanState(),
                    LoanLifecycleEvent.ESCALATE_TO_LEGAL
            );
            application.setLoanState(legalEscalated);
            loanApplicationRepository.save(application);
        }

        mambuPort.patchLoanState(
                record.getMambuLoanId(),
                "NON_PERFORMING",
                "Day 30 legal escalation initiated on " + processingDate
        );

        record.setLegalEscalatedAt(Instant.now());
    }

    private void recordActionIfMissing(
            NpaLoanRecord record,
            NpaRecoveryActionType actionType,
            String actionNote,
            String triggeredBy
    ) {
        if (npaRecoveryActionRepository.existsByTenantIdAndNpaLoanRecordIdAndActionType(
                record.getTenantId(),
                record.getId(),
                actionType
        )) {
            return;
        }

        NpaRecoveryAction action = NpaRecoveryAction.builder()
                .id(UUID.randomUUID())
                .tenantId(record.getTenantId())
                .npaLoanRecordId(record.getId())
                .loanApplicationId(record.getLoanApplicationId())
                .actionType(actionType)
                .actionNote(actionNote)
                .triggeredBy(triggeredBy)
                .build();
        npaRecoveryActionRepository.save(action);
    }

    private NpaRecoveryActionType toActionType(NpaRecoveryStage stage) {
        if (stage == NpaRecoveryStage.DAY_1_REMINDER) {
            return NpaRecoveryActionType.DAY_1_REMINDER;
        }
        if (stage == NpaRecoveryStage.DAY_7_ESCALATION) {
            return NpaRecoveryActionType.DAY_7_ESCALATION;
        }
        if (stage == NpaRecoveryStage.DAY_30_LEGAL_ESCALATION) {
            return NpaRecoveryActionType.DAY_30_LEGAL_ESCALATION;
        }
        return null;
    }

    private String buildFlagRedisKey(String loanAccountId, UUID tenantId) {
        return "NPA:FLAG:" + loanAccountId + ":" + tenantId;
    }

    private String buildOverrideLockKey(UUID tenantId, String loanAccountId) {
        return "NPA:OVERRIDE:" + loanAccountId + ":" + tenantId;
    }

    private String buildEscalationRedisKey(UUID npaRecordId, NpaRecoveryStage stage) {
        return "NPA:ESCALATION:" + npaRecordId + ":" + stage.name();
    }

    private List<NpaLoanRecord> paginate(List<NpaLoanRecord> list, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        int fromIndex = safePage * safeSize;
        if (fromIndex >= list.size()) {
            return List.of();
        }
        int toIndex = Math.min(fromIndex + safeSize, list.size());
        List<NpaLoanRecord> sorted = new ArrayList<>(list);
        sorted.sort(Comparator.comparing(NpaLoanRecord::getNpaFlaggedAt).reversed());
        return sorted.subList(fromIndex, toIndex);
    }

    private record NpaCandidateKey(UUID tenantId, UUID applicationId) {
    }
}

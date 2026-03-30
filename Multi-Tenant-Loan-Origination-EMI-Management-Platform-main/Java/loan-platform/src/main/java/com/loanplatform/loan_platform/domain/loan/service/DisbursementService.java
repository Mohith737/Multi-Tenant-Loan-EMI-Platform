package com.loanplatform.loan_platform.domain.loan.service;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanDisbursementRequest;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanDisbursementResponse;
import com.loanplatform.loan_platform.domain.loan.model.DisbursementStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplicationStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallment;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleEvent;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleState;
import com.loanplatform.loan_platform.domain.loan.model.LoanOffer;
import com.loanplatform.loan_platform.domain.loan.model.LoanOfferStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanStateHistory;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanInstallmentRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanOfferRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanStateHistoryRepository;
import com.loanplatform.loan_platform.dto.request.LoanDisbursementRequest;
import com.loanplatform.loan_platform.dto.response.DisbursementStatusResponse;
import com.loanplatform.loan_platform.dto.response.LoanDisbursementResponse;
import com.loanplatform.loan_platform.dto.response.LoanScheduleResponse;
import com.loanplatform.loan_platform.exception.DisbursementInProgressException;
import com.loanplatform.loan_platform.exception.DisbursementPreconditionFailedException;
import com.loanplatform.loan_platform.exception.LoanApplicationNotFoundException;
import com.loanplatform.loan_platform.exception.MambuIntegrationException;
import com.loanplatform.loan_platform.exception.RepaymentSchedulePersistenceException;
import com.loanplatform.loan_platform.exception.StripeTransferException;
import com.loanplatform.loan_platform.exception.TenantIsolationViolationException;
import com.loanplatform.loan_platform.mapper.DisbursementMapper;
import com.loanplatform.loan_platform.adapter.outbound.stripe.StripeGatewayService;
import com.loanplatform.loan_platform.multitenancy.TenantAware;
import com.loanplatform.loan_platform.port.inbound.DisbursementUseCase;
import com.loanplatform.loan_platform.port.outbound.BorrowerNotificationPort;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisbursementService implements DisbursementUseCase {

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanInstallmentRepository loanInstallmentRepository;
    private final LoanOfferRepository loanOfferRepository;
    private final LoanStateHistoryRepository loanStateHistoryRepository;
    private final LoanStateMachinePort loanStateMachinePort;
    private final MambuPort mambuPort;
    private final RepaymentScheduleService repaymentScheduleService;
    private final DisbursementMapper disbursementMapper;
    private final KafkaEventPort kafkaEventPort;
    private final BorrowerNotificationPort borrowerNotificationPort;
    private final StringRedisTemplate stringRedisTemplate;
    private final StripeGatewayService stripeGatewayService;

    @Value("${app.flow5.redis.idempotency-ttl-seconds:300}")
    private long idempotencyTtlSeconds;

    @Value("${app.loan.offer-validity-hours:48}")
    private long offerValidityHours;

    @Override
    @TenantAware
    @Transactional(
            propagation = Propagation.REQUIRED,
            noRollbackFor = {
                    MambuIntegrationException.class,
                    RepaymentSchedulePersistenceException.class,
                    StripeTransferException.class
            }
    )
    public LoanDisbursementResponse disburse(UUID tenantId, UUID actorId, UUID applicationId, LoanDisbursementRequest request) {
        String requestId = UUID.randomUUID().toString();
        log.debug("Disbursement entry tenantId={} applicationId={} actorId={} requestId={}",
                tenantId, applicationId, actorId, requestId);

        LoanApplication application = loanApplicationRepository.findByIdAndTenantIdForUpdate(applicationId, tenantId)
                .orElseThrow(() -> new LoanApplicationNotFoundException("Loan application not found: " + applicationId));

        refreshSelectedOfferIfExpired(application);

        List<String> failedConditions = validatePreconditions(application, request);
        if (!failedConditions.isEmpty()) {
            throw new DisbursementPreconditionFailedException(failedConditions);
        }

        String idempotencyKey = buildIdempotencyKey(applicationId, tenantId);
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(idempotencyKey, requestId, Duration.ofSeconds(idempotencyTtlSeconds));
        if (!Boolean.TRUE.equals(acquired)) {
            throw new DisbursementInProgressException("Disbursement already in progress for application " + applicationId);
        }

        try {
            application.setDisbursementStatus(DisbursementStatus.IN_PROGRESS);
            application.setDisbursementFailureReason(null);
            loanApplicationRepository.save(application);

            MambuLoanDisbursementResponse disbursementResponse = postDisbursement(application, request);
            applyDisbursementResponse(application, disbursementResponse, request);

            LoanLifecycleState previousState = application.getLoanState();
            LoanLifecycleState disbursedState = loanStateMachinePort.transition(previousState, LoanLifecycleEvent.DISBURSE);
            application.setLoanState(disbursedState);
            application.setStatus(LoanApplicationStatus.DISBURSED);
            saveStateHistory(application, previousState, disbursedState, LoanLifecycleEvent.DISBURSE,
                    "actor:" + actorId, requestId, null);

            List<LoanInstallment> installments = repaymentScheduleService.fetchAndPersistSchedule(application);
            application.setSchedulePersistedAt(Instant.now());

            String stripeTransferId = stripeGatewayService.createDisbursementTransfer(
                    tenantId,
                    applicationId,
                    application.getNetDisbursedAmount(),
                    request.getStripeDestinationAccountId()
            );
            if (stripeTransferId == null || stripeTransferId.isBlank()) {
                throw new StripeTransferException("Stripe transfer id is missing for application " + applicationId);
            }
            application.setStripeTransferId(stripeTransferId);

            LoanLifecycleState activeRepaymentState = loanStateMachinePort.transition(application.getLoanState(), LoanLifecycleEvent.ACTIVATE_REPAYMENT);
            application.setLoanState(activeRepaymentState);
            application.setStatus(LoanApplicationStatus.ACTIVE_REPAYMENT);
            application.setDisbursementStatus(DisbursementStatus.COMPLETED);
            application.setDisbursementFailureReason(null);
            application.setMambuDisburseSyncFailed(false);
            application.setScheduleFetchFailed(false);
            loanApplicationRepository.save(application);

            saveStateHistory(application, disbursedState, activeRepaymentState, LoanLifecycleEvent.ACTIVATE_REPAYMENT,
                    "actor:" + actorId, requestId, null);

            kafkaEventPort.publish("loan.disbursed", tenantId, applicationId,
                    "mambuLoanId=" + application.getMambuLoanId() + ",txnId=" + application.getMambuDisbursementTxnId());
            kafkaEventPort.publish("loan.schedule.persisted", tenantId, applicationId,
                    "installments=" + installments.size());

            borrowerNotificationPort.onLoanDisbursed(
                    application.getBorrowerId(),
                    tenantId,
                    applicationId,
                    "Disbursed amount=" + application.getNetDisbursedAmount() + ", firstRepaymentDate=" + application.getFirstRepaymentDate()
            );

            LoanDisbursementResponse response = disbursementMapper.toLoanDisbursementResponse(application);
            response.setTotalInstallments(installments.size());
            return response;
        } catch (RepaymentSchedulePersistenceException ex) {
            markScheduleFailure(application, ex.getMessage());
            kafkaEventPort.publish("loan.disbursement.failed", tenantId, applicationId,
                    "reason=schedule_persistence_failed");
            throw ex;
        } catch (StripeTransferException ex) {
            rollbackAfterTransferFailure(application, ex.getMessage());
            kafkaEventPort.publish("loan.disbursement.failed", tenantId, applicationId,
                    "reason=transfer_failed");
            throw ex;
        } catch (Exception ex) {
            markDisbursementFailure(application, ex.getMessage());
            kafkaEventPort.publish("loan.disbursement.failed", tenantId, applicationId,
                    "reason=mambu_disbursement_failed");
            throw new MambuIntegrationException("Flow 5 disbursement failed for application " + applicationId, ex);
        } finally {
            stringRedisTemplate.delete(idempotencyKey);
            log.debug("Disbursement exit tenantId={} applicationId={} requestId={}", tenantId, applicationId, requestId);
        }
    }

    @Override
    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public DisbursementStatusResponse getDisbursementStatus(UUID tenantId, UUID applicationId) {
        LoanApplication application = loanApplicationRepository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new LoanApplicationNotFoundException("Loan application not found: " + applicationId));
        return disbursementMapper.toDisbursementStatusResponse(application);
    }

    @Override
    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public LoanScheduleResponse getScheduleForBorrower(UUID tenantId, UUID borrowerId, UUID applicationId) {
        LoanApplication application = loanApplicationRepository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new LoanApplicationNotFoundException("Loan application not found: " + applicationId));
        if (!borrowerId.equals(application.getBorrowerId())) {
            throw new TenantIsolationViolationException("Borrower cannot access another borrower's loan schedule");
        }
        if (application.getStatus() != LoanApplicationStatus.ACTIVE_REPAYMENT
                || application.getDisbursementStatus() != DisbursementStatus.COMPLETED) {
            throw new IllegalStateException("Repayment schedule is available only after successful disbursement completion");
        }
        List<LoanInstallment> installments = loanInstallmentRepository
                .findByTenantIdAndBorrowerIdAndApplicationIdOrderByInstallmentNumberAsc(tenantId, borrowerId, applicationId);
        return buildScheduleResponse(application, installments);
    }

    @Override
    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public LoanScheduleResponse getScheduleForTenant(UUID tenantId, UUID applicationId) {
        LoanApplication application = loanApplicationRepository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new LoanApplicationNotFoundException("Loan application not found: " + applicationId));
        List<LoanInstallment> installments = loanInstallmentRepository
                .findByTenantIdAndApplicationIdOrderByInstallmentNumberAsc(tenantId, applicationId);
        return buildScheduleResponse(application, installments);
    }

    @Override
    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public LoanDisbursementResponse getDisbursementDetailsForBorrower(UUID tenantId, UUID borrowerId, UUID applicationId) {
        LoanApplication application = loanApplicationRepository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new LoanApplicationNotFoundException("Loan application not found: " + applicationId));
        if (!borrowerId.equals(application.getBorrowerId())) {
            throw new TenantIsolationViolationException("Borrower cannot access another borrower's loan disbursement details");
        }
        LoanDisbursementResponse response = disbursementMapper.toLoanDisbursementResponse(application);
        response.setTotalInstallments((int) loanInstallmentRepository.countByTenantIdAndApplicationId(tenantId, applicationId));
        return response;
    }

    @Override
    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public LoanDisbursementResponse getDisbursementDetailsForTenant(UUID tenantId, UUID applicationId) {
        LoanApplication application = loanApplicationRepository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new LoanApplicationNotFoundException("Loan application not found: " + applicationId));
        LoanDisbursementResponse response = disbursementMapper.toLoanDisbursementResponse(application);
        response.setTotalInstallments((int) loanInstallmentRepository.countByTenantIdAndApplicationId(tenantId, applicationId));
        return response;
    }

    @TenantAware
    @Transactional(propagation = Propagation.REQUIRED)
    public void retryFailedSchedulePersistence(UUID tenantId, UUID applicationId) {
        LoanApplication application = loanApplicationRepository.findByIdAndTenantIdForUpdate(applicationId, tenantId)
                .orElseThrow(() -> new LoanApplicationNotFoundException("Loan application not found: " + applicationId));

        if (!application.isScheduleFetchFailed() || application.getStatus() != LoanApplicationStatus.DISBURSED) {
            return;
        }

        List<LoanInstallment> installments = repaymentScheduleService.fetchAndPersistSchedule(application);
        LoanLifecycleState nextState = loanStateMachinePort.transition(application.getLoanState(), LoanLifecycleEvent.ACTIVATE_REPAYMENT);
        application.setLoanState(nextState);
        application.setStatus(LoanApplicationStatus.ACTIVE_REPAYMENT);
        application.setDisbursementStatus(DisbursementStatus.COMPLETED);
        application.setScheduleFetchFailed(false);
        application.setSchedulePersistedAt(Instant.now());
        loanApplicationRepository.save(application);

        kafkaEventPort.publish("loan.schedule.persisted", tenantId, applicationId, "installments=" + installments.size());
    }

    @TenantAware
    @Transactional(propagation = Propagation.REQUIRED)
    public void handleTransferFailed(UUID tenantId, UUID applicationId, String reason) {
        LoanApplication application = loanApplicationRepository.findByIdAndTenantIdForUpdate(applicationId, tenantId)
                .orElseThrow(() -> new LoanApplicationNotFoundException("Loan application not found: " + applicationId));

        rollbackAfterTransferFailure(application, reason);

        kafkaEventPort.publish("loan.disbursement.failed", tenantId, applicationId,
                "reason=transfer_failed,message=" + reason);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void handleTransferFailedByTransferId(String stripeTransferId, String reason) {
        LoanApplication application = loanApplicationRepository.findByStripeTransferIdForUpdate(stripeTransferId)
                .orElse(null);
        if (application == null) {
            log.warn("Ignoring transfer failed callback as no loan application mapped for transferId={}", stripeTransferId);
            return;
        }

        rollbackAfterTransferFailure(application, reason);
        kafkaEventPort.publish("loan.disbursement.failed", application.getTenantId(), application.getId(),
                "reason=transfer_failed,message=" + reason);
    }

    private List<String> validatePreconditions(LoanApplication application, LoanDisbursementRequest request) {
        List<String> failed = new ArrayList<>();
        if (application.getStatus() != LoanApplicationStatus.APPROVED) {
            failed.add("APPLICATION_NOT_APPROVED");
        }
        if (application.getMambuLoanId() == null || application.getMambuLoanId().isBlank()) {
            failed.add("MAMBU_LOAN_ID_MISSING");
        }
        if (application.isMambuSyncFailed()) {
            failed.add("MAMBU_SYNC_FAILED");
        }
        if (application.getSelectedOfferId() == null) {
            failed.add("SELECTED_OFFER_MISSING");
        }
        if (application.getDisbursementStatus() == DisbursementStatus.IN_PROGRESS) {
            failed.add("DISBURSEMENT_ALREADY_IN_PROGRESS");
        }
        if (application.getDisbursementStatus() == DisbursementStatus.COMPLETED) {
            failed.add("DISBURSEMENT_ALREADY_COMPLETED");
        }
        if (request.getStripeDestinationAccountId() == null || request.getStripeDestinationAccountId().isBlank()) {
            failed.add("STRIPE_CUSTOMER_ID_MISSING");
        }
        return failed;
    }

    private String buildIdempotencyKey(UUID applicationId, UUID tenantId) {
        return "idempotency:DISB:" + applicationId + ":" + tenantId;
    }

    private void refreshSelectedOfferIfExpired(LoanApplication application) {
        if (application.getSelectedOfferId() == null) {
            return;
        }

        LoanOffer offer = loanOfferRepository.findByIdAndApplicationIdAndTenantId(
                        application.getSelectedOfferId(),
                        application.getId(),
                        application.getTenantId()
                )
                .orElse(null);

        if (offer == null) {
            return;
        }

        if (offer.getExpiresAt() != null && offer.getExpiresAt().isBefore(Instant.now())) {
            offer.setExpiresAt(Instant.now().plusSeconds(offerValidityHours * 3600));
            offer.setStatus(LoanOfferStatus.SELECTED);
            loanOfferRepository.save(offer);
            log.debug("Expired selected offer refreshed silently tenantId={} applicationId={} offerId={}",
                    application.getTenantId(), application.getId(), offer.getId());
        }
    }

    private MambuLoanDisbursementResponse postDisbursement(LoanApplication application, LoanDisbursementRequest request) {
        LocalDate disbursementDate = request.getDisbursementDate() == null ? LocalDate.now() : request.getDisbursementDate();
        LocalDate firstRepaymentDate = request.getFirstRepaymentDate() == null
                ? disbursementDate.plusMonths(1)
                : request.getFirstRepaymentDate();

        MambuLoanDisbursementRequest disbursementRequest = MambuLoanDisbursementRequest.builder()
                .notes(request.getNotes())
                .disbursementDate(disbursementDate.toString())
                .firstRepaymentDate(firstRepaymentDate.toString())
                .externalId("DISB:" + application.getId() + ":" + application.getTenantId())
                .transactionDetails(MambuLoanDisbursementRequest.TransactionDetails.builder()
                        .transactionChannelId("BANK_TRANSFER")
                        .build())
                .build();

        MambuLoanDisbursementResponse response = mambuPort.disburseLoan(application.getMambuLoanId(), disbursementRequest);
        if (response == null || response.getId() == null || response.getId().isBlank()) {
            throw new MambuIntegrationException("Mambu disbursement response is invalid", null);
        }
        return response;
    }

    private void applyDisbursementResponse(
            LoanApplication application,
            MambuLoanDisbursementResponse response,
            LoanDisbursementRequest request
    ) {
        LocalDate disbursementDate = request.getDisbursementDate() == null ? LocalDate.now() : request.getDisbursementDate();
        LocalDate firstRepaymentDate = request.getFirstRepaymentDate() == null
                ? disbursementDate.plusMonths(1)
                : request.getFirstRepaymentDate();

        application.setMambuDisbursementTxnId(response.getId());
        application.setNetDisbursedAmount(response.getAmount() == null ? BigDecimal.ZERO : response.getAmount());
        application.setProcessingFeeCharged(response.getFees() == null ? BigDecimal.ZERO : response.getFees().getAmount());
        application.setFirstRepaymentDate(firstRepaymentDate);
        application.setDisbursedAt(Instant.now());
        application.setMambuDisburseSyncFailed(false);
        application.setScheduleFetchFailed(false);
        loanApplicationRepository.save(application);
    }

    private void markScheduleFailure(LoanApplication application, String reason) {
        application.setDisbursementStatus(DisbursementStatus.FAILED);
        application.setScheduleFetchFailed(true);
        application.setDisbursementFailureReason(reason);
        loanApplicationRepository.save(application);
    }

    private void markDisbursementFailure(LoanApplication application, String reason) {
        application.setDisbursementStatus(DisbursementStatus.FAILED);
        application.setMambuDisburseSyncFailed(true);
        application.setDisbursementFailureReason(reason);
        loanApplicationRepository.save(application);
    }

    private void rollbackAfterTransferFailure(LoanApplication application, String reason) {
        if (application.getMambuLoanId() != null
                && !application.getMambuLoanId().isBlank()
                && application.getStatus() != LoanApplicationStatus.APPROVED) {
            mambuPort.patchLoanState(application.getMambuLoanId(), "APPROVED", "Transfer failed rollback");
        }

        loanInstallmentRepository.deleteByTenantIdAndApplicationId(application.getTenantId(), application.getId());

        application.setDisbursementStatus(DisbursementStatus.FAILED);
        application.setDisbursementFailureReason(reason);
        application.setStatus(LoanApplicationStatus.APPROVED);
        application.setLoanState(LoanLifecycleState.APPROVED);
        application.setMambuDisbursementTxnId(null);
        application.setStripeTransferId(null);
        application.setNetDisbursedAmount(null);
        application.setProcessingFeeCharged(null);
        application.setDisbursedAt(null);
        application.setFirstRepaymentDate(null);
        application.setSchedulePersistedAt(null);
        application.setScheduleFetchFailed(false);
        application.setMambuDisburseSyncFailed(false);
        loanApplicationRepository.save(application);
    }

    private LoanScheduleResponse buildScheduleResponse(LoanApplication application, List<LoanInstallment> installments) {
        List<LoanScheduleResponse.InstallmentItem> items = installments.stream()
                .map(disbursementMapper::toInstallmentItem)
                .toList();

        LocalDate firstDueDate = installments.stream()
                .map(LoanInstallment::getDueDate)
                .min(Comparator.naturalOrder())
                .orElse(null);

        LocalDate lastDueDate = installments.stream()
                .map(LoanInstallment::getDueDate)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return LoanScheduleResponse.builder()
                .applicationId(application.getId())
                .mambuLoanId(application.getMambuLoanId())
                .totalInstallments(installments.size())
                .firstDueDate(firstDueDate)
                .lastDueDate(lastDueDate)
                .installments(items)
                .build();
    }

    private void saveStateHistory(
            LoanApplication application,
            LoanLifecycleState fromState,
            LoanLifecycleState toState,
            LoanLifecycleEvent event,
            String changedBy,
            String requestId,
            String reason
    ) {
        loanStateHistoryRepository.save(LoanStateHistory.builder()
                .id(UUID.randomUUID())
                .tenantId(application.getTenantId())
                .applicationId(application.getId())
                .fromState(fromState == null ? null : fromState.name())
                .toState(toState.name())
                .event(event.name())
                .changedBy(changedBy)
                .requestId(requestId)
                .reason(reason)
                .changedAt(Instant.now())
                .build());
    }
}

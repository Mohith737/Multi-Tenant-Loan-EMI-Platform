package com.loanplatform.loan_platform.domain.loan.service;

import com.loanplatform.loan_platform.adapter.outbound.stripe.StripeGatewayService;
import com.loanplatform.loan_platform.adapter.outbound.stripe.dto.StripePaymentIntentCommand;
import com.loanplatform.loan_platform.adapter.outbound.stripe.dto.StripePaymentIntentResult;
import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.domain.loan.model.EmiPaymentAttempt;
import com.loanplatform.loan_platform.domain.loan.model.EmiPaymentAttemptStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallment;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallmentStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleEvent;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleState;
import com.loanplatform.loan_platform.domain.loan.model.ReconciliationReport;
import com.loanplatform.loan_platform.domain.loan.repository.EmiPaymentAttemptRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanInstallmentRepository;
import com.loanplatform.loan_platform.domain.loan.repository.ReconciliationReportRepository;
import com.loanplatform.loan_platform.domain.tenant.model.Tenant;
import com.loanplatform.loan_platform.domain.tenant.model.TenantStatus;
import com.loanplatform.loan_platform.domain.tenant.repository.TenantRepository;
import com.loanplatform.loan_platform.dto.request.EmiWaiveRequest;
import com.loanplatform.loan_platform.dto.response.AdminDueInstallmentsResponse;
import com.loanplatform.loan_platform.dto.response.AdminFailedInstallmentsResponse;
import com.loanplatform.loan_platform.dto.response.AdminOverdueInstallmentsResponse;
import com.loanplatform.loan_platform.dto.response.BorrowerInstallmentScheduleResponse;
import com.loanplatform.loan_platform.dto.response.PaymentHistoryResponse;
import com.loanplatform.loan_platform.dto.response.ReconciliationReportResponse;
import com.loanplatform.loan_platform.exception.DuplicateEmiProcessingException;
import com.loanplatform.loan_platform.exception.EmiCollectionPreconditionFailedException;
import com.loanplatform.loan_platform.exception.LedgerSyncException;
import com.loanplatform.loan_platform.exception.TenantIsolationViolationException;
import com.loanplatform.loan_platform.mapper.EmiCollectionMapper;
import com.loanplatform.loan_platform.multitenancy.TenantAware;
import com.loanplatform.loan_platform.port.inbound.EmiCollectionUseCase;
import com.loanplatform.loan_platform.port.outbound.KafkaEventPort;
import com.loanplatform.loan_platform.port.outbound.LoanStateMachinePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmiCollectionService implements EmiCollectionUseCase {

    private final LoanInstallmentRepository loanInstallmentRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final BorrowerRepository borrowerRepository;
    private final TenantRepository tenantRepository;
    private final EmiPaymentAttemptRepository emiPaymentAttemptRepository;
    private final ReconciliationReportRepository reconciliationReportRepository;
    private final StripeGatewayService stripeGatewayService;
    private final KafkaEventPort kafkaEventPort;
    private final LoanStateMachinePort loanStateMachinePort;
    private final StringRedisTemplate stringRedisTemplate;
    private final TransactionTemplate transactionTemplate;
    private final EmiCollectionMapper emiCollectionMapper;

    @Value("${app.flow6.redis.idempotency-ttl-seconds:300}")
    private long redisTtlSeconds;

    @Value("${app.flow6.max-retry-count:3}")
    private int maxRetryCount;

    @TenantAware
    public void dispatchDueInstallments(LocalDate processingDate, boolean retryMode) {
        List<LoanInstallmentStatus> statuses = retryMode
                ? List.of(LoanInstallmentStatus.RETRY_SCHEDULED)
                : List.of(LoanInstallmentStatus.PENDING, LoanInstallmentStatus.RETRY_SCHEDULED);

        List<LoanInstallment> dueInstallments = loanInstallmentRepository.findByDueDateAndStatusIn(processingDate, statuses);
        log.debug("Flow6 dispatch start processingDate={} retryMode={} count={}", processingDate, retryMode, dueInstallments.size());

        for (LoanInstallment installment : dueInstallments) {
            try {
                transactionTemplate.executeWithoutResult(status ->
                        processInstallmentAttempt(installment.getTenantId(), installment.getId(), retryMode)
                );
            } catch (Exception ex) {
                log.error("Flow6 installment dispatch failed tenantId={} installmentId={}",
                        installment.getTenantId(), installment.getId(), ex);
            }
        }
    }

    @Override
    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public BorrowerInstallmentScheduleResponse getInstallmentsForBorrower(UUID tenantId, UUID borrowerId, UUID applicationId) {
        LoanApplication application = loanApplicationRepository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Loan application not found: " + applicationId));
        if (!borrowerId.equals(application.getBorrowerId())) {
            throw new TenantIsolationViolationException("Borrower cannot access another borrower's installments");
        }

        List<LoanInstallment> installments = loanInstallmentRepository
                .findByTenantIdAndBorrowerIdAndApplicationIdOrderByInstallmentNumberAsc(tenantId, borrowerId, applicationId);
        return BorrowerInstallmentScheduleResponse.builder()
                .applicationId(applicationId)
                .borrowerId(borrowerId)
                .mambuLoanId(application.getMambuLoanId())
                .totalInstallments(installments.size())
                .installments(installments.stream().map(emiCollectionMapper::toInstallmentItem).toList())
                .build();
    }

    @Override
    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public PaymentHistoryResponse getPaymentHistoryForBorrower(UUID tenantId, UUID borrowerId, UUID applicationId) {
        LoanApplication application = loanApplicationRepository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Loan application not found: " + applicationId));
        if (!borrowerId.equals(application.getBorrowerId())) {
            throw new TenantIsolationViolationException("Borrower cannot access another borrower's payment history");
        }

        List<LoanInstallment> paidInstallments = loanInstallmentRepository
                .findByTenantIdAndBorrowerIdAndApplicationIdOrderByInstallmentNumberAsc(tenantId, borrowerId, applicationId)
                .stream()
                .filter(installment -> installment.getStatus() == LoanInstallmentStatus.PAID)
                .toList();

        List<PaymentHistoryResponse.PaymentItem> items = paidInstallments.stream()
                .map(installment -> {
                    EmiPaymentAttempt attempt = emiPaymentAttemptRepository.findTopByInstallmentIdOrderByAttemptedAtDesc(installment.getId())
                            .orElse(EmiPaymentAttempt.builder().build());
                    return emiCollectionMapper.toPaymentHistoryItem(installment, attempt);
                })
                .collect(Collectors.toList());

        return PaymentHistoryResponse.builder()
                .applicationId(applicationId)
                .borrowerId(borrowerId)
                .payments(items)
                .build();
    }

    @Override
    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public AdminDueInstallmentsResponse getDueTodayForAdmin(UUID tenantId, String statusFilter, int page, int size) {
        LoanInstallmentStatus status = statusFilter == null || statusFilter.isBlank()
                ? LoanInstallmentStatus.PENDING
                : LoanInstallmentStatus.valueOf(statusFilter.trim().toUpperCase(Locale.ROOT));
        List<LoanInstallment> list = loanInstallmentRepository
                .findByTenantIdAndDueDateAndStatusOrderByInstallmentNumberAsc(tenantId, LocalDate.now(), status);
        return AdminDueInstallmentsResponse.builder()
                .page(page)
                .size(size)
                .total((long) list.size())
                .installments(paginate(list, page, size).stream().map(emiCollectionMapper::toInstallmentItem).toList())
                .build();
    }

    @Override
    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public AdminFailedInstallmentsResponse getFailedForAdmin(UUID tenantId, int page, int size) {
        List<LoanInstallment> failed = findFailedForTenant(tenantId);
        return AdminFailedInstallmentsResponse.builder()
                .page(page)
                .size(size)
                .total((long) failed.size())
                .installments(paginate(failed, page, size).stream().map(emiCollectionMapper::toInstallmentItem).toList())
                .build();
    }

    @Override
    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public AdminOverdueInstallmentsResponse getOverdueForAdmin(UUID tenantId, int page, int size) {
        List<LoanInstallment> overdue = findOverdueForTenant(tenantId);
        return AdminOverdueInstallmentsResponse.builder()
                .page(page)
                .size(size)
                .total((long) overdue.size())
                .installments(paginate(overdue, page, size).stream().map(emiCollectionMapper::toInstallmentItem).toList())
                .build();
    }

    @Override
    @TenantAware
    @Transactional(propagation = Propagation.REQUIRED)
    public void waiveInstallment(UUID tenantId, UUID actorId, UUID installmentId, EmiWaiveRequest request) {
        LoanInstallment installment = loanInstallmentRepository.findByIdAndTenantIdForUpdate(installmentId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Installment not found: " + installmentId));
        if (installment.getStatus() == LoanInstallmentStatus.PAID) {
            throw new IllegalStateException("Cannot waive a paid installment");
        }

        installment.setStatus(LoanInstallmentStatus.WAIVED);
        installment.setWaived(true);
        installment.setWaivedBy(actorId);
        installment.setWaivedAt(Instant.now());
        installment.setWaivedReason(request.getReason());
        installment.setPaymentFailureReason("WAIVED_BY_TENANT_ADMIN");
        loanInstallmentRepository.save(installment);

        kafkaEventPort.publish("emi.waived", tenantId, installment.getApplicationId(),
                "installmentId=" + installmentId + ",actorId=" + actorId);
    }

    @Override
    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public ReconciliationReportResponse getReconciliationReport(UUID tenantId, LocalDate reportDate) {
        ReconciliationReport report = reconciliationReportRepository.findByTenantIdAndReportDate(tenantId, reportDate)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation report not found for date " + reportDate));
        return emiCollectionMapper.toReconciliationResponse(report);
    }

    @TenantAware
    @Transactional(propagation = Propagation.REQUIRED)
    public void processInstallmentAttempt(UUID tenantId, UUID installmentId, boolean retryMode) {
        String requestId = UUID.randomUUID().toString();
        LoanInstallment installment;
        try {
            installment = loanInstallmentRepository.findByIdAndTenantIdForUpdate(installmentId, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Installment not found: " + installmentId));
        } catch (PessimisticLockingFailureException ex) {
            throw new DuplicateEmiProcessingException("Installment is locked for processing: " + installmentId);
        }

        LoanApplication application = loanApplicationRepository
                .findByIdAndTenantIdAndLoanState(installment.getApplicationId(), tenantId, LoanLifecycleState.ACTIVE_REPAYMENT)
                .orElseThrow(() -> new EmiCollectionPreconditionFailedException(List.of("LOAN_NOT_ACTIVE_REPAYMENT")));

        Borrower borrower = borrowerRepository
                .findByIdAndTenantIdAndStripeCustomerIdIsNotNullAndStripePaymentMethodIdIsNotNull(installment.getBorrowerId(), tenantId)
                .orElseThrow(() -> new EmiCollectionPreconditionFailedException(List.of("BORROWER_PAYMENT_METHOD_MISSING")));

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EmiCollectionPreconditionFailedException(List.of("TENANT_NOT_FOUND")));

        List<String> preconditionFailures = validatePreconditions(installment, application, tenant, retryMode);
        if (!preconditionFailures.isEmpty()) {
            throw new EmiCollectionPreconditionFailedException(preconditionFailures);
        }

        String idempotencyKey = buildIdempotencyKey(installment);
        Boolean lockAcquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(idempotencyKey, requestId, Duration.ofSeconds(redisTtlSeconds));
        if (!Boolean.TRUE.equals(lockAcquired)) {
            throw new DuplicateEmiProcessingException("EMI collection already in progress for installment " + installmentId);
        }

        try {
            installment.setStatus(LoanInstallmentStatus.PROCESSING);
            installment.setPaymentFailureReason(null);
            installment.setLastRetryAt(Instant.now());
            loanInstallmentRepository.save(installment);

            Map<String, String> metadata = new HashMap<>();
            metadata.put("tenantId", tenantId.toString());
            metadata.put("applicationId", installment.getApplicationId().toString());
            metadata.put("installmentId", installment.getId().toString());
            metadata.put("borrowerId", installment.getBorrowerId().toString());
            metadata.put("mambuLoanId", installment.getMambuLoanId());
            metadata.put("emiNumber", String.valueOf(installment.getInstallmentNumber()));
            metadata.put("idempotencyKey", idempotencyKey);

            StripePaymentIntentResult paymentIntentResult = stripeGatewayService.createEmiPaymentIntent(
                    tenantId,
                    installment.getId(),
                    StripePaymentIntentCommand.builder()
                            .amount(installment.getTotalDue())
                            .customerId(borrower.getStripeCustomerId())
                            .paymentMethodId(borrower.getStripePaymentMethodId())
                            .description("EMI collection for installment " + installment.getInstallmentNumber())
                            .idempotencyKey(idempotencyKey)
                            .metadata(metadata)
                            .build()
            );

            installment.setStripePaymentIntentId(paymentIntentResult.getPaymentIntentId());
            installment.setStripePaymentStatus(paymentIntentResult.getStatus());
            loanInstallmentRepository.save(installment);

            EmiPaymentAttempt attempt = EmiPaymentAttempt.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .installmentId(installment.getId())
                    .applicationId(installment.getApplicationId())
                    .borrowerId(installment.getBorrowerId())
                    .attemptNumber(installment.getRetryCount() + 1)
                    .idempotencyKey(idempotencyKey)
                    .stripePaymentIntentId(paymentIntentResult.getPaymentIntentId())
                    .stripeStatus(paymentIntentResult.getStatus())
                    .mambuPosted(false)
                    .status(EmiPaymentAttemptStatus.INITIATED)
                    .build();
            emiPaymentAttemptRepository.save(attempt);
        } catch (Exception ex) {
            installment.setStatus(LoanInstallmentStatus.RETRY_SCHEDULED);
            installment.setRetryCount(Math.min(maxRetryCount, installment.getRetryCount() + 1));
            installment.setPaymentFailureReason(ex.getMessage());
            installment.setStripePaymentStatus("creation_failed");
            loanInstallmentRepository.save(installment);

            EmiPaymentAttempt failedAttempt = EmiPaymentAttempt.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .installmentId(installment.getId())
                    .applicationId(installment.getApplicationId())
                    .borrowerId(installment.getBorrowerId())
                    .attemptNumber(Math.max(1, installment.getRetryCount()))
                    .idempotencyKey(buildIdempotencyKey(installment))
                    .stripeStatus("creation_failed")
                    .mambuPosted(false)
                    .status(EmiPaymentAttemptStatus.STRIPE_FAILED)
                    .failureReason(ex.getMessage())
                    .build();
            emiPaymentAttemptRepository.save(failedAttempt);

            kafkaEventPort.publish("emi.failed", tenantId, installment.getApplicationId(),
                    "installmentId=" + installment.getId() + ",retryCount=" + installment.getRetryCount());

            if (installment.getRetryCount() >= maxRetryCount) {
                installment.setStatus(LoanInstallmentStatus.OVERDUE);
                loanInstallmentRepository.save(installment);
                loanStateMachinePort.transition(application.getLoanState(), LoanLifecycleEvent.EMI_RETRY_EXHAUSTED);
                kafkaEventPort.publish("emi.overdue", tenantId, installment.getApplicationId(),
                        "installmentId=" + installment.getId());
            }

            throw new LedgerSyncException("Failed to initiate EMI collection for installment " + installment.getId(), ex);
        }
    }

    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public List<LoanInstallment> findDueTodayForTenant(UUID tenantId) {
        return loanInstallmentRepository.findByTenantIdAndDueDateAndStatusOrderByInstallmentNumberAsc(
                tenantId,
                LocalDate.now(),
                LoanInstallmentStatus.PENDING
        );
    }

    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public List<LoanInstallment> findFailedForTenant(UUID tenantId) {
        return loanInstallmentRepository.findByTenantIdAndStatusAndDueDateLessThanEqual(
                tenantId,
                LoanInstallmentStatus.RETRY_SCHEDULED,
                LocalDate.now()
        );
    }

    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public List<LoanInstallment> findOverdueForTenant(UUID tenantId) {
        return loanInstallmentRepository.findByTenantIdAndStatusOrderByDueDateAsc(tenantId, LoanInstallmentStatus.OVERDUE);
    }

    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public Optional<LoanInstallment> resolveByPaymentIntent(UUID tenantId, String stripePaymentIntentId) {
        return loanInstallmentRepository.findByTenantIdAndStripePaymentIntentId(tenantId, stripePaymentIntentId);
    }

    public String buildIdempotencyKey(LoanInstallment installment) {
        return "idempotency:EMI:" + installment.getMambuLoanId() + ":" + installment.getInstallmentNumber() + ":" + installment.getTenantId();
    }

    private List<LoanInstallment> paginate(List<LoanInstallment> source, int page, int size) {
        if (size <= 0) {
            return List.of();
        }
        int safePage = Math.max(page, 0);
        int from = safePage * size;
        if (from >= source.size()) {
            return List.of();
        }
        int to = Math.min(source.size(), from + size);
        return source.stream()
                .sorted(Comparator.comparing(LoanInstallment::getDueDate).thenComparing(LoanInstallment::getInstallmentNumber))
                .toList()
                .subList(from, to);
    }

    private List<String> validatePreconditions(LoanInstallment installment,
                                               LoanApplication application,
                                               Tenant tenant,
                                               boolean retryMode) {
        List<String> failed = new ArrayList<>();

        if (installment.getDueDate() == null || installment.getDueDate().isAfter(LocalDate.now())) {
            failed.add("DUE_DATE_NOT_ELIGIBLE");
        }

        EnumSet<LoanInstallmentStatus> allowedStatuses = retryMode
                ? EnumSet.of(LoanInstallmentStatus.RETRY_SCHEDULED)
                : EnumSet.of(LoanInstallmentStatus.PENDING, LoanInstallmentStatus.RETRY_SCHEDULED);
        if (!allowedStatuses.contains(installment.getStatus())) {
            failed.add("INVALID_INSTALLMENT_STATUS");
        }

        if (application.getLoanState() != LoanLifecycleState.ACTIVE_REPAYMENT) {
            failed.add("LOAN_STATE_NOT_ACTIVE_REPAYMENT");
        }

        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            failed.add("TENANT_NOT_ACTIVE");
        }

        if (tenant.getStripeAccountId() == null || tenant.getStripeAccountId().isBlank()) {
            failed.add("TENANT_STRIPE_ACCOUNT_MISSING");
        }

        if (installment.getStatus() == LoanInstallmentStatus.REQUIRES_ACTION) {
            failed.add("REQUIRES_BORROWER_ACTION");
        }

        if (installment.getStatus() == LoanInstallmentStatus.PAID || installment.getStatus() == LoanInstallmentStatus.WAIVED) {
            failed.add("INSTALLMENT_ALREADY_SETTLED");
        }

        return failed;
    }
}

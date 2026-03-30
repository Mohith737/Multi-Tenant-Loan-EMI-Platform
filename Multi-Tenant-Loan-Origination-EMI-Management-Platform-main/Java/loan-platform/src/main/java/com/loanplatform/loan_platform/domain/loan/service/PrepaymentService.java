package com.loanplatform.loan_platform.domain.loan.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuEarlyRepaymentPreviewResponse;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuRepaymentRequest;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuRepaymentResponse;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuRepaymentScheduleResponse;
import com.loanplatform.loan_platform.adapter.outbound.stripe.StripeGatewayService;
import com.loanplatform.loan_platform.adapter.outbound.stripe.dto.StripePaymentIntentCommand;
import com.loanplatform.loan_platform.adapter.outbound.stripe.dto.StripePaymentIntentResult;
import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.domain.loan.model.ForeclosureQuote;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplicationStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallment;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleEvent;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleState;
import com.loanplatform.loan_platform.domain.loan.model.PrepaymentOption;
import com.loanplatform.loan_platform.domain.loan.model.PrepaymentRequest;
import com.loanplatform.loan_platform.domain.loan.model.PrepaymentStatus;
import com.loanplatform.loan_platform.domain.loan.model.PrepaymentType;
import com.loanplatform.loan_platform.domain.loan.model.RevisedScheduleSnapshot;
import com.loanplatform.loan_platform.domain.loan.repository.ForeclosureQuoteRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanInstallmentRepository;
import com.loanplatform.loan_platform.domain.loan.repository.PrepaymentRequestRepository;
import com.loanplatform.loan_platform.domain.loan.repository.RevisedScheduleSnapshotRepository;
import com.loanplatform.loan_platform.dto.request.PrepaymentExecutionRequest;
import com.loanplatform.loan_platform.dto.request.PrepaymentSimulationRequest;
import com.loanplatform.loan_platform.dto.response.NocResponse;
import com.loanplatform.loan_platform.dto.response.PrepaymentAdminViewResponse;
import com.loanplatform.loan_platform.dto.response.PrepaymentExecutionResponse;
import com.loanplatform.loan_platform.dto.response.PrepaymentSimulationResponse;
import com.loanplatform.loan_platform.exception.ForeclosureCalculationException;
import com.loanplatform.loan_platform.exception.InvalidPrepaymentOptionException;
import com.loanplatform.loan_platform.exception.PrepaymentQuoteExpiredException;
import com.loanplatform.loan_platform.exception.TenantIsolationViolationException;
import com.loanplatform.loan_platform.mapper.PrepaymentMapper;
import com.loanplatform.loan_platform.multitenancy.TenantAware;
import com.loanplatform.loan_platform.port.inbound.PrepaymentUseCase;
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
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrepaymentService implements PrepaymentUseCase {

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanInstallmentRepository loanInstallmentRepository;
    private final BorrowerRepository borrowerRepository;
    private final ForeclosureQuoteRepository foreclosureQuoteRepository;
    private final PrepaymentRequestRepository prepaymentRequestRepository;
    private final RevisedScheduleSnapshotRepository revisedScheduleSnapshotRepository;
    private final MambuPort mambuPort;
    private final StripeGatewayService stripeGatewayService;
    private final KafkaEventPort kafkaEventPort;
    private final LoanStateMachinePort loanStateMachinePort;
    private final StringRedisTemplate stringRedisTemplate;
    private final PrepaymentMapper prepaymentMapper;
    private final ObjectMapper objectMapper;

    @Value("${app.flow7.quote-ttl-seconds:900}")
    private long quoteTtlSeconds;

    @Value("${app.flow7.redis.idempotency-ttl-seconds:300}")
    private long idempotencyTtlSeconds;

    @Override
    @TenantAware
    @Transactional(propagation = Propagation.REQUIRED)
    public PrepaymentSimulationResponse simulatePrepayment(
            UUID tenantId,
            UUID borrowerId,
            String loanAccountId,
            PrepaymentSimulationRequest request
    ) {
        LoanApplication application = findBorrowerApplication(tenantId, borrowerId, loanAccountId);
        ensureActiveRepayment(application);

        LocalDate paymentDate = request.getRequestedDate() == null ? LocalDate.now() : request.getRequestedDate();
        MambuEarlyRepaymentPreviewResponse preview = mambuPort.previewEarlyRepayment(
                application.getMambuLoanId(),
                request.getAmount(),
                paymentDate
        );

        BigDecimal principalOutstanding = safeAmount(preview.getRemainingPrincipal());
        BigDecimal accruedInterest = safeAmount(preview.getAccruedInterest());
        BigDecimal penalty = safeAmount(preview.getPrepaymentPenalty());

        if (request.getPrepaymentType() == PrepaymentType.PARTIAL) {
            if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidPrepaymentOptionException("Partial prepayment amount must be greater than zero");
            }
            if (request.getAmount().compareTo(principalOutstanding) >= 0) {
                throw new InvalidPrepaymentOptionException("Partial prepayment amount must be lower than outstanding principal");
            }
        }

        Instant now = Instant.now();
        ForeclosureQuote quote = ForeclosureQuote.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .loanApplicationId(application.getId())
                .principalOutstanding(principalOutstanding)
                .accruedInterest(request.getPrepaymentType() == PrepaymentType.FULL ? accruedInterest : BigDecimal.ZERO)
                .penaltyAmount(request.getPrepaymentType() == PrepaymentType.FULL ? penalty : BigDecimal.ZERO)
                .totalPayable(request.getPrepaymentType() == PrepaymentType.FULL
                        ? safeAmount(preview.getTotalSettlementAmount())
                        : request.getAmount().setScale(2, RoundingMode.HALF_UP))
                .quoteGeneratedAt(now)
                .quoteExpiresAt(now.plusSeconds(quoteTtlSeconds))
                .mambuReference(preview.getLoanId())
                .build();
        foreclosureQuoteRepository.save(quote);

        List<PrepaymentSimulationResponse.OptionPreview> options = request.getPrepaymentType() == PrepaymentType.PARTIAL
                ? buildPartialOptions(application, borrowerId, request.getAmount(), principalOutstanding)
                : List.of();

        if (request.getPrepaymentType() == PrepaymentType.PARTIAL
                && request.getPrepaymentOption() != null
                && options.stream().noneMatch(option -> option.getOption() == request.getPrepaymentOption())) {
            throw new InvalidPrepaymentOptionException("Unsupported prepayment option: " + request.getPrepaymentOption());
        }

        return PrepaymentSimulationResponse.builder()
                .quoteId(quote.getId())
                .applicationId(application.getId())
                .loanAccountId(application.getMambuLoanId())
                .prepaymentType(request.getPrepaymentType())
                .principalOutstanding(principalOutstanding)
                .accruedInterest(quote.getAccruedInterest())
                .prepaymentPenalty(quote.getPenaltyAmount())
                .totalPayable(quote.getTotalPayable())
                .quoteGeneratedAt(quote.getQuoteGeneratedAt())
                .quoteExpiresAt(quote.getQuoteExpiresAt())
                .options(options)
                .build();
    }

    @Override
    @TenantAware
    @Transactional(propagation = Propagation.REQUIRED)
    public PrepaymentExecutionResponse executePrepayment(
            UUID tenantId,
            UUID borrowerId,
            String loanAccountId,
            PrepaymentExecutionRequest request
    ) {
        LoanApplication application = loanApplicationRepository
                .findByTenantIdAndBorrowerIdAndMambuLoanIdForUpdate(tenantId, borrowerId, loanAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Loan account not found: " + loanAccountId));
        ensureActiveRepayment(application);

        Borrower borrower = borrowerRepository.findByIdAndTenantIdAndStripeCustomerIdIsNotNullAndStripePaymentMethodIdIsNotNull(borrowerId, tenantId)
                .orElseThrow(() -> new IllegalStateException("Borrower payment profile not configured"));

        validateTypeAndOption(request.getPrepaymentType(), request.getPrepaymentOption());

        ForeclosureQuote quote = foreclosureQuoteRepository.findByIdAndTenantId(request.getQuoteId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Quote not found: " + request.getQuoteId()));

        if (!quote.getLoanApplicationId().equals(application.getId())) {
            throw new TenantIsolationViolationException("Quote does not belong to requested loan account");
        }
        if (quote.getQuoteExpiresAt().isBefore(Instant.now())) {
            throw new PrepaymentQuoteExpiredException("Prepayment quote expired; generate a new quote");
        }

        BigDecimal expectedAmount = quote.getTotalPayable().setScale(2, RoundingMode.HALF_UP);
        BigDecimal requestedAmount = request.getRequestedAmount().setScale(2, RoundingMode.HALF_UP);
        if (expectedAmount.compareTo(requestedAmount) != 0) {
            throw new ForeclosureCalculationException("Requested amount does not match quote total payable");
        }

        if (prepaymentRequestRepository.existsByTenantIdAndIdempotencyKey(tenantId, request.getIdempotencyKey())) {
            throw new IllegalStateException("Duplicate prepayment request idempotency key");
        }

        String redisKey = buildRedisExecutionKey(tenantId, loanAccountId, request.getPrepaymentType(), request.getQuoteId());
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(redisKey, request.getIdempotencyKey(), Duration.ofSeconds(idempotencyTtlSeconds));
        if (!Boolean.TRUE.equals(acquired)) {
            throw new IllegalStateException("Prepayment request is already in progress for this loan");
        }

        PrepaymentRequest prepaymentRequest = PrepaymentRequest.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .loanApplicationId(application.getId())
                .borrowerId(borrowerId)
                .prepaymentType(request.getPrepaymentType())
                .prepaymentOption(request.getPrepaymentOption())
                .requestedAmount(requestedAmount)
                .quoteId(quote.getId())
                .idempotencyKey(request.getIdempotencyKey())
                .stripeStatus("initiated")
                .status(PrepaymentStatus.PAYMENT_PENDING)
                .build();
        prepaymentRequestRepository.save(prepaymentRequest);

        try {
            Map<String, String> metadata = new HashMap<>();
            metadata.put("tenantId", tenantId.toString());
            metadata.put("borrowerId", borrowerId.toString());
            metadata.put("applicationId", application.getId().toString());
            metadata.put("loanAccountId", application.getMambuLoanId());
            metadata.put("prepaymentRequestId", prepaymentRequest.getId().toString());
            metadata.put("prepaymentType", request.getPrepaymentType().name());
            metadata.put("prepaymentOption", request.getPrepaymentOption() == null ? "" : request.getPrepaymentOption().name());
            metadata.put("idempotencyKey", request.getIdempotencyKey());

            StripePaymentIntentResult paymentIntentResult = stripeGatewayService.createPrepaymentPaymentIntent(
                    tenantId,
                    prepaymentRequest.getId(),
                    StripePaymentIntentCommand.builder()
                            .amount(requestedAmount)
                            .customerId(borrower.getStripeCustomerId())
                            .paymentMethodId(borrower.getStripePaymentMethodId())
                            .description("Prepayment " + request.getPrepaymentType() + " for loan " + application.getMambuLoanId())
                            .idempotencyKey(request.getIdempotencyKey())
                            .metadata(metadata)
                            .build()
            );

            prepaymentRequest.setStripePaymentIntentId(paymentIntentResult.getPaymentIntentId());
            prepaymentRequest.setStripeStatus(paymentIntentResult.getStatus());
            prepaymentRequestRepository.save(prepaymentRequest);

            kafkaEventPort.publish(
                    "loan.prepayment.requested",
                    tenantId,
                    application.getId(),
                    "requestId=" + prepaymentRequest.getId() + ",type=" + request.getPrepaymentType()
            );

            PrepaymentExecutionResponse response = prepaymentMapper.toExecutionResponse(prepaymentRequest, application);
            response.setMessage("Payment initiated; awaiting Stripe confirmation");
            return response;
        } catch (Exception ex) {
            prepaymentRequest.setStatus(PrepaymentStatus.FAILED);
            prepaymentRequest.setFailureReason(ex.getMessage());
            prepaymentRequest.setStripeStatus("creation_failed");
            prepaymentRequestRepository.save(prepaymentRequest);
            stringRedisTemplate.delete(redisKey);
            throw ex;
        }
    }

    @Override
    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public PrepaymentExecutionResponse getPrepaymentRequest(UUID tenantId, UUID borrowerId, String loanAccountId, UUID requestId) {
        PrepaymentRequest request = prepaymentRequestRepository.findByIdAndTenantIdAndBorrowerId(requestId, tenantId, borrowerId)
                .orElseThrow(() -> new IllegalArgumentException("Prepayment request not found: " + requestId));
        LoanApplication application = loanApplicationRepository.findByIdAndTenantId(request.getLoanApplicationId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Loan application not found: " + request.getLoanApplicationId()));

        if (!application.getMambuLoanId().equals(loanAccountId)) {
            throw new TenantIsolationViolationException("Prepayment request does not belong to loan account " + loanAccountId);
        }

        PrepaymentExecutionResponse response = prepaymentMapper.toExecutionResponse(request, application);
        if (request.getStatus() == PrepaymentStatus.COMPLETED) {
            response.setMessage("Prepayment completed");
        }
        return response;
    }

    @Override
    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public PrepaymentAdminViewResponse getAdminPrepayments(UUID tenantId, int page, int size) {
        List<PrepaymentRequest> all = prepaymentRequestRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        List<PrepaymentRequest> paged = paginate(all, page, size);

        List<PrepaymentAdminViewResponse.Item> items = new ArrayList<>();
        for (PrepaymentRequest request : paged) {
            LoanApplication application = loanApplicationRepository.findByIdAndTenantId(request.getLoanApplicationId(), tenantId)
                    .orElse(null);
            if (application != null) {
                items.add(prepaymentMapper.toAdminItem(request, application));
            }
        }

        return PrepaymentAdminViewResponse.builder()
                .page(page)
                .size(size)
                .total(all.size())
                .requests(items)
                .build();
    }

    @Override
    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public NocResponse getNocForBorrower(UUID tenantId, UUID borrowerId, String loanAccountId) {
        LoanApplication application = findBorrowerApplication(tenantId, borrowerId, loanAccountId);
        return buildNoc(application, borrowerId);
    }

    @Override
    @TenantAware
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public NocResponse getNocForTenant(UUID tenantId, String loanAccountId) {
        LoanApplication application = loanApplicationRepository.findByTenantIdAndMambuLoanId(tenantId, loanAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Loan account not found: " + loanAccountId));
        return buildNoc(application, application.getBorrowerId());
    }

    @TenantAware
    @Transactional(propagation = Propagation.REQUIRED)
    public void handlePaymentSucceeded(UUID tenantId, UUID prepaymentRequestId, String paymentIntentId) {
        PrepaymentRequest request = prepaymentRequestRepository.findByIdAndTenantIdForUpdate(prepaymentRequestId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Prepayment request not found: " + prepaymentRequestId));

        if (request.getStatus() == PrepaymentStatus.COMPLETED) {
            return;
        }

        LoanApplication application = loanApplicationRepository.findByIdAndTenantIdForUpdate(request.getLoanApplicationId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Loan application not found: " + request.getLoanApplicationId()));

        request.setStripePaymentIntentId(paymentIntentId);
        request.setStripeStatus("succeeded");
        request.setStatus(PrepaymentStatus.PAYMENT_CONFIRMED);

        MambuRepaymentResponse repaymentResponse = mambuPort.postRepayment(
                application.getMambuLoanId(),
                MambuRepaymentRequest.builder()
                        .amount(request.getRequestedAmount())
                        .date(LocalDate.now().toString())
                        .notes("Prepayment " + request.getPrepaymentType())
                        .externalId(request.getIdempotencyKey())
                        .transactionDetails(MambuRepaymentRequest.TransactionDetails.builder()
                                .transactionChannelId("ONLINE_PAYMENT")
                                .build())
                        .build()
        );

        request.setMambuTransactionId(repaymentResponse.getId());
        request.setStatus(PrepaymentStatus.MAMBU_UPDATED);

        Instant now = Instant.now();
        if (request.getPrepaymentType() == PrepaymentType.FULL) {
            mambuPort.patchLoanState(application.getMambuLoanId(), "CLOSED_OBLIGATIONS_MET", "Foreclosure completed");
            LoanLifecycleState foreclosed = loanStateMachinePort.transition(application.getLoanState(), LoanLifecycleEvent.FORECLOSURE_CONFIRMED);
            LoanLifecycleState withNoc = loanStateMachinePort.transition(foreclosed, LoanLifecycleEvent.GENERATE_NOC);

            application.setLoanState(withNoc);
            application.setStatus(LoanApplicationStatus.CLOSED);
            application.setForeclosedAt(now);
            application.setForeclosureAmount(request.getRequestedAmount());
            application.setClosureReason("FORECLOSURE");

            kafkaEventPort.publish("loan.foreclosed", tenantId, application.getId(), "requestId=" + request.getId());
            kafkaEventPort.publish("loan.noc.generated", tenantId, application.getId(), "requestId=" + request.getId());
        } else {
            LoanLifecycleState activeRepayment = loanStateMachinePort.transition(application.getLoanState(), LoanLifecycleEvent.PREPAY_PARTIAL_CONFIRMED);
            application.setLoanState(activeRepayment);
            application.setStatus(LoanApplicationStatus.ACTIVE_REPAYMENT);
            application.setLastPrepaymentAt(now);

            MambuRepaymentScheduleResponse scheduleResponse = mambuPort.getRepaymentSchedule(application.getMambuLoanId());
            String payload = toJson(scheduleResponse);
            int nextVersion = revisedScheduleSnapshotRepository.findTopByPrepaymentRequestIdOrderBySnapshotVersionDesc(request.getId())
                    .map(snapshot -> snapshot.getSnapshotVersion() + 1)
                    .orElse(1);

            RevisedScheduleSnapshot snapshot = RevisedScheduleSnapshot.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .prepaymentRequestId(request.getId())
                    .loanApplicationId(application.getId())
                    .snapshotVersion(nextVersion)
                    .schedulePayloadJson(payload)
                    .build();
            revisedScheduleSnapshotRepository.save(snapshot);

            kafkaEventPort.publish("loan.prepayment.completed", tenantId, application.getId(), "requestId=" + request.getId());
        }

        request.setStatus(PrepaymentStatus.COMPLETED);
        request.setFailureReason(null);
        prepaymentRequestRepository.save(request);
        loanApplicationRepository.save(application);

        clearRedisExecutionKey(tenantId, application.getMambuLoanId(), request.getPrepaymentType(), request.getQuoteId());
    }

    @TenantAware
    @Transactional(propagation = Propagation.REQUIRED)
    public void markPaymentFailed(UUID tenantId,
                                  UUID prepaymentRequestId,
                                  String paymentIntentId,
                                  String stripeStatus,
                                  String failureReason) {
        PrepaymentRequest request = prepaymentRequestRepository.findByIdAndTenantIdForUpdate(prepaymentRequestId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Prepayment request not found: " + prepaymentRequestId));

        if (request.getStatus() == PrepaymentStatus.COMPLETED) {
            return;
        }

        request.setStripePaymentIntentId(paymentIntentId);
        request.setStripeStatus(stripeStatus);
        request.setStatus(PrepaymentStatus.FAILED);
        request.setFailureReason(failureReason);
        prepaymentRequestRepository.save(request);

        kafkaEventPort.publish(
                "loan.prepayment.failed",
                tenantId,
                request.getLoanApplicationId(),
                "requestId=" + request.getId() + ",reason=" + failureReason
        );

        LoanApplication application = loanApplicationRepository.findByIdAndTenantId(request.getLoanApplicationId(), tenantId)
                .orElse(null);
        if (application != null) {
            clearRedisExecutionKey(tenantId, application.getMambuLoanId(), request.getPrepaymentType(), request.getQuoteId());
        }
    }

    private LoanApplication findBorrowerApplication(UUID tenantId, UUID borrowerId, String loanAccountId) {
        return loanApplicationRepository.findByTenantIdAndBorrowerIdAndMambuLoanId(tenantId, borrowerId, loanAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Loan account not found: " + loanAccountId));
    }

    private void ensureActiveRepayment(LoanApplication application) {
        if (application.getLoanState() != LoanLifecycleState.ACTIVE_REPAYMENT
                || application.getStatus() != LoanApplicationStatus.ACTIVE_REPAYMENT) {
            throw new IllegalStateException("Prepayment is allowed only for ACTIVE_REPAYMENT loans");
        }
    }

    private void validateTypeAndOption(PrepaymentType type, PrepaymentOption option) {
        if (type == PrepaymentType.PARTIAL && option == null) {
            throw new InvalidPrepaymentOptionException("prepaymentOption is required for PARTIAL prepayment");
        }
        if (type == PrepaymentType.FULL && option != null) {
            throw new InvalidPrepaymentOptionException("prepaymentOption must not be provided for FULL foreclosure");
        }
    }

    private List<PrepaymentSimulationResponse.OptionPreview> buildPartialOptions(
            LoanApplication application,
            UUID borrowerId,
            BigDecimal partialAmount,
            BigDecimal principalOutstanding
    ) {
        List<LoanInstallment> installments = loanInstallmentRepository
                .findByTenantIdAndBorrowerIdAndApplicationIdOrderByInstallmentNumberAsc(
                        application.getTenantId(), borrowerId, application.getId()
                );

        BigDecimal revisedOutstanding = principalOutstanding.subtract(partialAmount).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        int remainingMonths = installments.isEmpty()
                ? Math.max(1, application.getRequestedTenureMonths() == null ? 1 : application.getRequestedTenureMonths())
                : installments.size();

        BigDecimal currentEmi = installments.isEmpty()
                ? application.getRequestedAmount().divide(
                        BigDecimal.valueOf(Math.max(1, application.getRequestedTenureMonths() == null ? 1 : application.getRequestedTenureMonths())),
                        2,
                        RoundingMode.HALF_UP
                )
                : installments.getFirst().getTotalDue();

        int reducedTenure = currentEmi.compareTo(BigDecimal.ZERO) <= 0
                ? remainingMonths
                : revisedOutstanding.divide(currentEmi, 0, RoundingMode.CEILING).intValue();

        BigDecimal reducedEmi = revisedOutstanding.divide(BigDecimal.valueOf(Math.max(1, remainingMonths)), 2, RoundingMode.HALF_UP);

        List<PrepaymentSimulationResponse.OptionPreview> options = new ArrayList<>();
        options.add(PrepaymentSimulationResponse.OptionPreview.builder()
                .option(PrepaymentOption.REDUCE_TENURE_KEEP_EMI)
                .revisedOutstanding(revisedOutstanding)
                .estimatedEmi(currentEmi)
                .estimatedTenureMonths(Math.max(1, reducedTenure))
                .note("Keep current EMI and reduce remaining tenure")
                .build());
        options.add(PrepaymentSimulationResponse.OptionPreview.builder()
                .option(PrepaymentOption.REDUCE_EMI_KEEP_TENURE)
                .revisedOutstanding(revisedOutstanding)
                .estimatedEmi(reducedEmi)
                .estimatedTenureMonths(remainingMonths)
                .note("Keep current tenure and reduce monthly EMI")
                .build());
        return options;
    }

    private NocResponse buildNoc(LoanApplication application, UUID borrowerId) {
        if (application.getForeclosedAt() == null || application.getStatus() != LoanApplicationStatus.CLOSED) {
            throw new IllegalStateException("NOC is available only after foreclosure completion");
        }

        Optional<Borrower> borrower = borrowerRepository.findByIdAndTenantId(borrowerId, application.getTenantId());
        String borrowerName = borrower.map(b -> b.getFirstName() + " " + b.getLastName()).orElse("Borrower");

        String certificateNumber = "NOC-" + application.getTenantId().toString().substring(0, 8).toUpperCase(Locale.ROOT)
                + "-" + application.getId().toString().substring(0, 8).toUpperCase(Locale.ROOT);

        return NocResponse.builder()
                .applicationId(application.getId())
                .loanAccountId(application.getMambuLoanId())
                .borrowerName(borrowerName)
                .foreclosureAmount(application.getForeclosureAmount())
                .foreclosedAt(application.getForeclosedAt())
                .accountState("CLOSED_OBLIGATIONS_MET")
                .certificateNumber(certificateNumber)
                .generatedAt(Instant.now())
                .build();
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize revised schedule payload", ex);
        }
    }

    private BigDecimal safeAmount(MambuEarlyRepaymentPreviewResponse.AmountValue value) {
        if (value == null || value.getValue() == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.getValue().setScale(2, RoundingMode.HALF_UP);
    }

    private String buildRedisExecutionKey(UUID tenantId, String loanAccountId, PrepaymentType type, UUID quoteId) {
        String prefix = type == PrepaymentType.FULL ? "idempotency:FORECLOSE:" : "idempotency:PREPAY:";
        return prefix + loanAccountId + ":" + quoteId + ":" + tenantId;
    }

    private void clearRedisExecutionKey(UUID tenantId, String loanAccountId, PrepaymentType type, UUID quoteId) {
        stringRedisTemplate.delete(buildRedisExecutionKey(tenantId, loanAccountId, type, quoteId));
    }

    private <T> List<T> paginate(List<T> source, int page, int size) {
        if (source.isEmpty()) {
            return List.of();
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        int from = safePage * safeSize;
        if (from >= source.size()) {
            return List.of();
        }
        int to = Math.min(source.size(), from + safeSize);
        return source.subList(from, to);
    }
}

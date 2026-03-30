package com.loanplatform.loan_platform.unit.loan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuEarlyRepaymentPreviewResponse;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanAccountResponse;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuRepaymentResponse;
import com.loanplatform.loan_platform.adapter.outbound.stripe.StripeGatewayService;
import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.domain.loan.model.ForeclosureQuote;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplicationStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallment;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallmentStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleEvent;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleState;
import com.loanplatform.loan_platform.domain.loan.model.PrepaymentOption;
import com.loanplatform.loan_platform.domain.loan.model.PrepaymentRequest;
import com.loanplatform.loan_platform.domain.loan.model.PrepaymentStatus;
import com.loanplatform.loan_platform.domain.loan.model.PrepaymentType;
import com.loanplatform.loan_platform.domain.loan.repository.ForeclosureQuoteRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanInstallmentRepository;
import com.loanplatform.loan_platform.domain.loan.repository.PrepaymentRequestRepository;
import com.loanplatform.loan_platform.domain.loan.repository.RevisedScheduleSnapshotRepository;
import com.loanplatform.loan_platform.domain.loan.service.PrepaymentService;
import com.loanplatform.loan_platform.dto.request.PrepaymentExecutionRequest;
import com.loanplatform.loan_platform.dto.request.PrepaymentSimulationRequest;
import com.loanplatform.loan_platform.dto.response.PrepaymentExecutionResponse;
import com.loanplatform.loan_platform.exception.ForeclosureCalculationException;
import com.loanplatform.loan_platform.mapper.PrepaymentMapper;
import com.loanplatform.loan_platform.port.outbound.KafkaEventPort;
import com.loanplatform.loan_platform.port.outbound.LoanStateMachinePort;
import com.loanplatform.loan_platform.port.outbound.MambuPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrepaymentServiceTest {

    @Mock
    private LoanApplicationRepository loanApplicationRepository;
    @Mock
    private LoanInstallmentRepository loanInstallmentRepository;
    @Mock
    private BorrowerRepository borrowerRepository;
    @Mock
    private ForeclosureQuoteRepository foreclosureQuoteRepository;
    @Mock
    private PrepaymentRequestRepository prepaymentRequestRepository;
    @Mock
    private RevisedScheduleSnapshotRepository revisedScheduleSnapshotRepository;
    @Mock
    private MambuPort mambuPort;
    @Mock
    private StripeGatewayService stripeGatewayService;
    @Mock
    private KafkaEventPort kafkaEventPort;
    @Mock
    private LoanStateMachinePort loanStateMachinePort;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private PrepaymentMapper prepaymentMapper;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PrepaymentService prepaymentService;

    private UUID tenantId;
    private UUID borrowerId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        borrowerId = UUID.randomUUID();

        ReflectionTestUtils.setField(prepaymentService, "quoteTtlSeconds", 900L);
        ReflectionTestUtils.setField(prepaymentService, "idempotencyTtlSeconds", 300L);

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        lenient().when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(prepaymentRequestRepository.save(any(PrepaymentRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(foreclosureQuoteRepository.save(any(ForeclosureQuote.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void simulatePrepayment_partialScenario_returnsBothOptions() {
        LoanApplication application = activeRepaymentApplication();
        when(loanApplicationRepository.findByTenantIdAndBorrowerIdAndMambuLoanId(tenantId, borrowerId, "LN_FLOW7_001"))
                .thenReturn(Optional.of(application));
        when(mambuPort.previewEarlyRepayment(eq("LN_FLOW7_001"), any(), any()))
                .thenReturn(samplePreview());
        when(loanInstallmentRepository.findByTenantIdAndBorrowerIdAndApplicationIdOrderByInstallmentNumberAsc(
                tenantId, borrowerId, application.getId()))
                .thenReturn(List.of(
                        installment(1, "17000.00"),
                        installment(2, "17000.00")
                ));

        PrepaymentSimulationRequest request = PrepaymentSimulationRequest.builder()
                .prepaymentType(PrepaymentType.PARTIAL)
                .amount(new BigDecimal("100000.00"))
                .build();

        var response = prepaymentService.simulatePrepayment(tenantId, borrowerId, "LN_FLOW7_001", request);

        assertThat(response.getPrepaymentType()).isEqualTo(PrepaymentType.PARTIAL);
        assertThat(response.getTotalPayable()).isEqualByComparingTo("100000.00");
        assertThat(response.getOptions()).hasSize(2);
        verify(foreclosureQuoteRepository).save(any(ForeclosureQuote.class));
    }

    @Test
    void simulatePrepayment_fullScenario_returnsForeclosureAmount() {
        LoanApplication application = activeRepaymentApplication();
        when(loanApplicationRepository.findByTenantIdAndBorrowerIdAndMambuLoanId(tenantId, borrowerId, "LN_FLOW7_001"))
                .thenReturn(Optional.of(application));
        when(mambuPort.previewEarlyRepayment(eq("LN_FLOW7_001"), any(), any()))
                .thenReturn(samplePreview());

        PrepaymentSimulationRequest request = PrepaymentSimulationRequest.builder()
                .prepaymentType(PrepaymentType.FULL)
                .build();

        var response = prepaymentService.simulatePrepayment(tenantId, borrowerId, "LN_FLOW7_001", request);

        assertThat(response.getPrepaymentType()).isEqualTo(PrepaymentType.FULL);
        assertThat(response.getTotalPayable()).isEqualByComparingTo("412100.00");
        assertThat(response.getOptions()).isEmpty();
    }

    @Test
    void executePrepayment_amountMismatch_throwsForeclosureCalculationException() {
        LoanApplication application = activeRepaymentApplication();
        ForeclosureQuote quote = ForeclosureQuote.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .loanApplicationId(application.getId())
                .principalOutstanding(new BigDecimal("400000.00"))
                .accruedInterest(new BigDecimal("4100.00"))
                .penaltyAmount(new BigDecimal("8000.00"))
                .totalPayable(new BigDecimal("412100.00"))
                .quoteGeneratedAt(Instant.now())
                .quoteExpiresAt(Instant.now().plusSeconds(900))
                .build();

        when(loanApplicationRepository.findByTenantIdAndBorrowerIdAndMambuLoanIdForUpdate(tenantId, borrowerId, "LN_FLOW7_001"))
                .thenReturn(Optional.of(application));
        when(borrowerRepository.findByIdAndTenantIdAndStripeCustomerIdIsNotNullAndStripePaymentMethodIdIsNotNull(borrowerId, tenantId))
                .thenReturn(Optional.of(borrower()));
        when(foreclosureQuoteRepository.findByIdAndTenantId(quote.getId(), tenantId)).thenReturn(Optional.of(quote));

        PrepaymentExecutionRequest request = PrepaymentExecutionRequest.builder()
                .prepaymentType(PrepaymentType.FULL)
                .requestedAmount(new BigDecimal("410000.00"))
                .quoteId(quote.getId())
                .idempotencyKey("FORECLOSE:LN_FLOW7_001:tenant")
                .build();

        assertThatThrownBy(() -> prepaymentService.executePrepayment(tenantId, borrowerId, "LN_FLOW7_001", request))
                .isInstanceOf(ForeclosureCalculationException.class);
    }

    @Test
    void handlePaymentSucceeded_fullClosure_updatesLoanAndPublishesEvents() {
        UUID requestId = UUID.randomUUID();
        PrepaymentRequest request = PrepaymentRequest.builder()
                .id(requestId)
                .tenantId(tenantId)
                .loanApplicationId(UUID.randomUUID())
                .borrowerId(borrowerId)
                .prepaymentType(PrepaymentType.FULL)
                .requestedAmount(new BigDecimal("412100.00"))
                .quoteId(UUID.randomUUID())
                .idempotencyKey("FORECLOSE:LN_FLOW7_001:tenant")
                .status(PrepaymentStatus.PAYMENT_PENDING)
                .build();

        LoanApplication application = activeRepaymentApplication();
        application.setId(request.getLoanApplicationId());

        when(prepaymentRequestRepository.findByIdAndTenantIdForUpdate(requestId, tenantId)).thenReturn(Optional.of(request));
        when(loanApplicationRepository.findByIdAndTenantIdForUpdate(request.getLoanApplicationId(), tenantId))
                .thenReturn(Optional.of(application));
        when(mambuPort.postRepayment(eq("LN_FLOW7_001"), any()))
                .thenReturn(MambuRepaymentResponse.builder().id("txn_prepay_1").amount(new BigDecimal("412100.00")).build());
        when(mambuPort.patchLoanState("LN_FLOW7_001", "CLOSED_OBLIGATIONS_MET", "Foreclosure completed"))
                .thenReturn(MambuLoanAccountResponse.builder().id("LN_FLOW7_001").accountState("CLOSED_OBLIGATIONS_MET").build());
        when(loanStateMachinePort.transition(LoanLifecycleState.ACTIVE_REPAYMENT, LoanLifecycleEvent.FORECLOSURE_CONFIRMED))
                .thenReturn(LoanLifecycleState.FORECLOSED);
        when(loanStateMachinePort.transition(LoanLifecycleState.FORECLOSED, LoanLifecycleEvent.GENERATE_NOC))
                .thenReturn(LoanLifecycleState.FORECLOSED);

        prepaymentService.handlePaymentSucceeded(tenantId, requestId, "pi_flow7_success");

        assertThat(request.getStatus()).isEqualTo(PrepaymentStatus.COMPLETED);
        assertThat(request.getMambuTransactionId()).isEqualTo("txn_prepay_1");
        assertThat(application.getStatus()).isEqualTo(LoanApplicationStatus.CLOSED);
        assertThat(application.getLoanState()).isEqualTo(LoanLifecycleState.FORECLOSED);
        assertThat(application.getForeclosedAt()).isNotNull();
        verify(kafkaEventPort).publish(eq("loan.foreclosed"), eq(tenantId), eq(application.getId()), any());
        verify(kafkaEventPort).publish(eq("loan.noc.generated"), eq(tenantId), eq(application.getId()), any());
    }

    private LoanApplication activeRepaymentApplication() {
        return LoanApplication.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .loanProductId(UUID.randomUUID())
                .loanProductKey("LP_FLOW7")
                .requestedAmount(new BigDecimal("500000.00"))
                .requestedTenureMonths(24)
                .status(LoanApplicationStatus.ACTIVE_REPAYMENT)
                .loanState(LoanLifecycleState.ACTIVE_REPAYMENT)
                .eligibilityPassed(true)
                .eligibilityReasonCode("PASS")
                .riskCategory("PRIME")
                .creditScoreSnapshot(760)
                .dtiRatioSnapshot(new BigDecimal("24.5"))
                .monthlyIncomeSnapshot(new BigDecimal("120000.00"))
                .employmentTypeSnapshot("SALARIED")
                .maxApprovedAmountSnapshot(new BigDecimal("800000.00"))
                .offersExpiresAt(Instant.now().plusSeconds(3600))
                .mambuLoanId("LN_FLOW7_001")
                .build();
    }

    private LoanInstallment installment(int number, String totalDue) {
        return LoanInstallment.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .applicationId(UUID.randomUUID())
                .borrowerId(borrowerId)
                .mambuLoanId("LN_FLOW7_001")
                .installmentNumber(number)
                .dueDate(LocalDate.now().plusMonths(number))
                .principalAmount(new BigDecimal("12000.00"))
                .interestAmount(new BigDecimal("5000.00"))
                .totalDue(new BigDecimal(totalDue))
                .status(LoanInstallmentStatus.PENDING)
                .build();
    }

    private Borrower borrower() {
        return Borrower.builder()
                .id(borrowerId)
                .tenantId(tenantId)
                .firstName("Flow")
                .lastName("Seven")
                .email("borrower.flow7@test.com")
                .passwordHash("hash")
                .mobile("+919111111111")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender("MALE")
                .panEncrypted("enc_pan")
                .aadhaarLastFour("1234")
                .stripeCustomerId("cus_flow7")
                .stripePaymentMethodId("pm_flow7")
                .build();
    }

    private MambuEarlyRepaymentPreviewResponse samplePreview() {
        return MambuEarlyRepaymentPreviewResponse.builder()
                .loanId("LN_FLOW7_001")
                .remainingPrincipal(amount("400000.00"))
                .accruedInterest(amount("4100.00"))
                .prepaymentPenalty(amount("8000.00"))
                .totalSettlementAmount(amount("412100.00"))
                .build();
    }

    private MambuEarlyRepaymentPreviewResponse.AmountValue amount(String value) {
        return MambuEarlyRepaymentPreviewResponse.AmountValue.builder()
                .value(new BigDecimal(value))
                .build();
    }
}

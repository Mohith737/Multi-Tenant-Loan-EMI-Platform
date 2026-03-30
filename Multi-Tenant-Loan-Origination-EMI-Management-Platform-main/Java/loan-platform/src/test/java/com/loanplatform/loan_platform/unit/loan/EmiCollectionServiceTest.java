package com.loanplatform.loan_platform.unit.loan;

import com.loanplatform.loan_platform.adapter.outbound.stripe.StripeGatewayService;
import com.loanplatform.loan_platform.adapter.outbound.stripe.dto.StripePaymentIntentResult;
import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.domain.loan.model.EmiPaymentAttempt;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallment;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallmentStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleState;
import com.loanplatform.loan_platform.domain.loan.model.ReconciliationReport;
import com.loanplatform.loan_platform.domain.loan.repository.EmiPaymentAttemptRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanInstallmentRepository;
import com.loanplatform.loan_platform.domain.loan.repository.ReconciliationReportRepository;
import com.loanplatform.loan_platform.domain.loan.service.EmiCollectionService;
import com.loanplatform.loan_platform.domain.tenant.model.Tenant;
import com.loanplatform.loan_platform.domain.tenant.model.TenantStatus;
import com.loanplatform.loan_platform.domain.tenant.repository.TenantRepository;
import com.loanplatform.loan_platform.dto.request.EmiWaiveRequest;
import com.loanplatform.loan_platform.dto.response.BorrowerInstallmentScheduleResponse;
import com.loanplatform.loan_platform.exception.DuplicateEmiProcessingException;
import com.loanplatform.loan_platform.exception.EmiCollectionPreconditionFailedException;
import com.loanplatform.loan_platform.exception.LedgerSyncException;
import com.loanplatform.loan_platform.exception.TenantIsolationViolationException;
import com.loanplatform.loan_platform.mapper.EmiCollectionMapper;
import com.loanplatform.loan_platform.port.outbound.KafkaEventPort;
import com.loanplatform.loan_platform.port.outbound.LoanStateMachinePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmiCollectionServiceTest {

    @Mock
    private LoanInstallmentRepository loanInstallmentRepository;
    @Mock
    private LoanApplicationRepository loanApplicationRepository;
    @Mock
    private BorrowerRepository borrowerRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private EmiPaymentAttemptRepository emiPaymentAttemptRepository;
    @Mock
    private ReconciliationReportRepository reconciliationReportRepository;
    @Mock
    private StripeGatewayService stripeGatewayService;
    @Mock
    private KafkaEventPort kafkaEventPort;
    @Mock
    private LoanStateMachinePort loanStateMachinePort;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private EmiCollectionMapper emiCollectionMapper;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private EmiCollectionService emiCollectionService;

    private UUID tenantId;
    private UUID borrowerId;
    private UUID installmentId;
    private UUID applicationId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        borrowerId = UUID.randomUUID();
        installmentId = UUID.randomUUID();
        applicationId = UUID.randomUUID();

        ReflectionTestUtils.setField(emiCollectionService, "redisTtlSeconds", 300L);
        ReflectionTestUtils.setField(emiCollectionService, "maxRetryCount", 3);

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(loanInstallmentRepository.save(any(LoanInstallment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(emiPaymentAttemptRepository.save(any(EmiPaymentAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void processInstallmentAttempt_validInput_createsPaymentIntentAndAttemptRecord() {
        LoanInstallment installment = baseInstallment(LoanInstallmentStatus.PENDING, 0);
        LoanApplication application = activeRepaymentApplication();
        Borrower borrower = borrowerWithPaymentMethod();
        Tenant tenant = activeTenantWithStripe();

        when(loanInstallmentRepository.findByIdAndTenantIdForUpdate(installmentId, tenantId)).thenReturn(Optional.of(installment));
        when(loanApplicationRepository.findByIdAndTenantIdAndLoanState(applicationId, tenantId, LoanLifecycleState.ACTIVE_REPAYMENT))
                .thenReturn(Optional.of(application));
        when(borrowerRepository.findByIdAndTenantIdAndStripeCustomerIdIsNotNullAndStripePaymentMethodIdIsNotNull(borrowerId, tenantId))
                .thenReturn(Optional.of(borrower));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(stripeGatewayService.createEmiPaymentIntent(eq(tenantId), eq(installmentId), any()))
                .thenReturn(StripePaymentIntentResult.builder()
                        .paymentIntentId("pi_flow6_001")
                        .status("requires_confirmation")
                        .build());

        emiCollectionService.processInstallmentAttempt(tenantId, installmentId, false);

        assertThat(installment.getStatus()).isEqualTo(LoanInstallmentStatus.PROCESSING);
        assertThat(installment.getStripePaymentIntentId()).isEqualTo("pi_flow6_001");
        verify(emiPaymentAttemptRepository).save(any(EmiPaymentAttempt.class));
        verify(kafkaEventPort, never()).publish(eq("emi.failed"), any(), any(), any());
    }

    @Test
    void processInstallmentAttempt_tenantSuspended_throwsPreconditionFailure() {
        LoanInstallment installment = baseInstallment(LoanInstallmentStatus.PENDING, 0);

        when(loanInstallmentRepository.findByIdAndTenantIdForUpdate(installmentId, tenantId)).thenReturn(Optional.of(installment));
        when(loanApplicationRepository.findByIdAndTenantIdAndLoanState(applicationId, tenantId, LoanLifecycleState.ACTIVE_REPAYMENT))
                .thenReturn(Optional.of(activeRepaymentApplication()));
        when(borrowerRepository.findByIdAndTenantIdAndStripeCustomerIdIsNotNullAndStripePaymentMethodIdIsNotNull(borrowerId, tenantId))
                .thenReturn(Optional.of(borrowerWithPaymentMethod()));

        Tenant suspendedTenant = activeTenantWithStripe();
        suspendedTenant.setStatus(TenantStatus.SUSPENDED);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(suspendedTenant));

        assertThatThrownBy(() -> emiCollectionService.processInstallmentAttempt(tenantId, installmentId, false))
                .isInstanceOf(EmiCollectionPreconditionFailedException.class)
                .hasMessageContaining("preconditions failed");

        verify(stripeGatewayService, never()).createEmiPaymentIntent(any(), any(), any());
    }

    @Test
    void processInstallmentAttempt_redisLockAlreadyExists_throwsDuplicateProcessingException() {
        LoanInstallment installment = baseInstallment(LoanInstallmentStatus.PENDING, 0);

        when(loanInstallmentRepository.findByIdAndTenantIdForUpdate(installmentId, tenantId)).thenReturn(Optional.of(installment));
        when(loanApplicationRepository.findByIdAndTenantIdAndLoanState(applicationId, tenantId, LoanLifecycleState.ACTIVE_REPAYMENT))
                .thenReturn(Optional.of(activeRepaymentApplication()));
        when(borrowerRepository.findByIdAndTenantIdAndStripeCustomerIdIsNotNullAndStripePaymentMethodIdIsNotNull(borrowerId, tenantId))
                .thenReturn(Optional.of(borrowerWithPaymentMethod()));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(activeTenantWithStripe()));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> emiCollectionService.processInstallmentAttempt(tenantId, installmentId, false))
                .isInstanceOf(DuplicateEmiProcessingException.class)
                .hasMessageContaining("already in progress");

        verify(stripeGatewayService, never()).createEmiPaymentIntent(any(), any(), any());
    }

    @Test
    void processInstallmentAttempt_stripeCreationFailure_marksRetryAndPublishesFailureEvent() {
        LoanInstallment installment = baseInstallment(LoanInstallmentStatus.PENDING, 0);

        when(loanInstallmentRepository.findByIdAndTenantIdForUpdate(installmentId, tenantId)).thenReturn(Optional.of(installment));
        when(loanApplicationRepository.findByIdAndTenantIdAndLoanState(applicationId, tenantId, LoanLifecycleState.ACTIVE_REPAYMENT))
                .thenReturn(Optional.of(activeRepaymentApplication()));
        when(borrowerRepository.findByIdAndTenantIdAndStripeCustomerIdIsNotNullAndStripePaymentMethodIdIsNotNull(borrowerId, tenantId))
                .thenReturn(Optional.of(borrowerWithPaymentMethod()));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(activeTenantWithStripe()));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(stripeGatewayService.createEmiPaymentIntent(eq(tenantId), eq(installmentId), any()))
                .thenThrow(new RuntimeException("stripe unavailable"));

        assertThatThrownBy(() -> emiCollectionService.processInstallmentAttempt(tenantId, installmentId, false))
                .isInstanceOf(LedgerSyncException.class);

        assertThat(installment.getStatus()).isEqualTo(LoanInstallmentStatus.RETRY_SCHEDULED);
        assertThat(installment.getRetryCount()).isEqualTo(1);
        verify(kafkaEventPort).publish(eq("emi.failed"), eq(tenantId), eq(applicationId), any());
    }

    @Test
    void processInstallmentAttempt_retryExhausted_marksOverdueAndPublishesOverdueEvent() {
        LoanInstallment installment = baseInstallment(LoanInstallmentStatus.RETRY_SCHEDULED, 2);

        when(loanInstallmentRepository.findByIdAndTenantIdForUpdate(installmentId, tenantId)).thenReturn(Optional.of(installment));
        when(loanApplicationRepository.findByIdAndTenantIdAndLoanState(applicationId, tenantId, LoanLifecycleState.ACTIVE_REPAYMENT))
                .thenReturn(Optional.of(activeRepaymentApplication()));
        when(borrowerRepository.findByIdAndTenantIdAndStripeCustomerIdIsNotNullAndStripePaymentMethodIdIsNotNull(borrowerId, tenantId))
                .thenReturn(Optional.of(borrowerWithPaymentMethod()));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(activeTenantWithStripe()));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(stripeGatewayService.createEmiPaymentIntent(eq(tenantId), eq(installmentId), any()))
                .thenThrow(new RuntimeException("stripe unavailable"));

        assertThatThrownBy(() -> emiCollectionService.processInstallmentAttempt(tenantId, installmentId, true))
                .isInstanceOf(LedgerSyncException.class);

        assertThat(installment.getStatus()).isEqualTo(LoanInstallmentStatus.OVERDUE);
        assertThat(installment.getRetryCount()).isEqualTo(3);
        verify(kafkaEventPort).publish(eq("emi.overdue"), eq(tenantId), eq(applicationId), any());
    }

    @Test
    void getInstallmentsForBorrower_crossBorrowerAccess_throwsTenantIsolationViolationException() {
        LoanApplication application = activeRepaymentApplication();
        UUID anotherBorrowerId = UUID.randomUUID();

        when(loanApplicationRepository.findByIdAndTenantId(applicationId, tenantId)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> emiCollectionService.getInstallmentsForBorrower(tenantId, anotherBorrowerId, applicationId))
                .isInstanceOf(TenantIsolationViolationException.class)
                .hasMessageContaining("cannot access another borrower");
    }

    @Test
    void waiveInstallment_paidInstallment_throwsIllegalStateException() {
        LoanInstallment installment = baseInstallment(LoanInstallmentStatus.PAID, 0);
        when(loanInstallmentRepository.findByIdAndTenantIdForUpdate(installmentId, tenantId)).thenReturn(Optional.of(installment));

        assertThatThrownBy(() -> emiCollectionService.waiveInstallment(
                tenantId,
                UUID.randomUUID(),
                installmentId,
                EmiWaiveRequest.builder().reason("manual waive").build()
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void waiveInstallment_validRequest_marksInstallmentWaived() {
        LoanInstallment installment = baseInstallment(LoanInstallmentStatus.RETRY_SCHEDULED, 1);
        UUID actorId = UUID.randomUUID();
        when(loanInstallmentRepository.findByIdAndTenantIdForUpdate(installmentId, tenantId)).thenReturn(Optional.of(installment));

        emiCollectionService.waiveInstallment(
                tenantId,
                actorId,
                installmentId,
                EmiWaiveRequest.builder().reason("policy waive").build()
        );

        assertThat(installment.getStatus()).isEqualTo(LoanInstallmentStatus.WAIVED);
        assertThat(installment.isWaived()).isTrue();
        assertThat(installment.getWaivedBy()).isEqualTo(actorId);
        verify(kafkaEventPort).publish(eq("emi.waived"), eq(tenantId), eq(applicationId), any());
    }

    @Test
    void getDueTodayForAdmin_lowercaseFilter_convertsToEnumAndReturnsData() {
        LoanInstallment installment = baseInstallment(LoanInstallmentStatus.PENDING, 0);
        BorrowerInstallmentScheduleResponse.InstallmentItem item = BorrowerInstallmentScheduleResponse.InstallmentItem.builder()
                .installmentId(installment.getId())
                .installmentNumber(installment.getInstallmentNumber())
                .paymentStatus(installment.getStatus().name())
                .build();

        when(loanInstallmentRepository.findByTenantIdAndDueDateAndStatusOrderByInstallmentNumberAsc(
                tenantId,
                LocalDate.now(),
                LoanInstallmentStatus.PENDING
        )).thenReturn(List.of(installment));
        when(emiCollectionMapper.toInstallmentItem(installment)).thenReturn(item);

        var response = emiCollectionService.getDueTodayForAdmin(tenantId, "pending", 0, 20);

        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getInstallments()).hasSize(1);
    }

    @Test
    void getReconciliationReport_missingReport_throwsIllegalArgumentException() {
        when(reconciliationReportRepository.findByTenantIdAndReportDate(tenantId, LocalDate.now())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emiCollectionService.getReconciliationReport(tenantId, LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reconciliation report not found");
    }

    @Test
    void getReconciliationReport_existingReport_mapsResponse() {
        ReconciliationReport report = ReconciliationReport.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .reportDate(LocalDate.now())
                .totalDueCount(5)
                .totalCollectedCount(4)
                .totalFailedCount(1)
                .totalAmountDue(new BigDecimal("5000"))
                .totalAmountCollected(new BigDecimal("4000"))
                .mambuTotalPosted(new BigDecimal("4000"))
                .discrepancyAmount(BigDecimal.ZERO)
                .reconciled(true)
                .createdAt(Instant.now())
                .build();

        when(reconciliationReportRepository.findByTenantIdAndReportDate(tenantId, report.getReportDate()))
                .thenReturn(Optional.of(report));
        when(emiCollectionMapper.toReconciliationResponse(report)).thenReturn(
                com.loanplatform.loan_platform.dto.response.ReconciliationReportResponse.builder()
                        .reportDate(report.getReportDate())
                        .reconciled(true)
                        .build()
        );

        var response = emiCollectionService.getReconciliationReport(tenantId, report.getReportDate());

        assertThat(response.isReconciled()).isTrue();
        assertThat(response.getReportDate()).isEqualTo(report.getReportDate());
    }

    private LoanInstallment baseInstallment(LoanInstallmentStatus status, int retryCount) {
        return LoanInstallment.builder()
                .id(installmentId)
                .tenantId(tenantId)
                .applicationId(applicationId)
                .borrowerId(borrowerId)
                .mambuLoanId("LN_FLOW6_001")
                .installmentNumber(1)
                .dueDate(LocalDate.now())
                .principalAmount(new BigDecimal("900.00"))
                .interestAmount(new BigDecimal("100.00"))
                .totalDue(new BigDecimal("1000.00"))
                .status(status)
                .retryCount(retryCount)
                .build();
    }

    private LoanApplication activeRepaymentApplication() {
        return LoanApplication.builder()
                .id(applicationId)
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .loanState(LoanLifecycleState.ACTIVE_REPAYMENT)
                .status(com.loanplatform.loan_platform.domain.loan.model.LoanApplicationStatus.ACTIVE_REPAYMENT)
                .mambuLoanId("LN_FLOW6_001")
                .build();
    }

    private Borrower borrowerWithPaymentMethod() {
        return Borrower.builder()
                .id(borrowerId)
                .tenantId(tenantId)
                .stripeCustomerId("cus_123")
                .stripePaymentMethodId("pm_123")
                .build();
    }

    private Tenant activeTenantWithStripe() {
        return Tenant.builder()
                .id(tenantId)
                .status(TenantStatus.ACTIVE)
                .stripeAccountId("acct_123")
                .name("Tenant")
                .domain("tenant.flow6.test")
                .planTier("STANDARD")
                .build();
    }
}

package com.loanplatform.loan_platform.unit.loan;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanDisbursementResponse;
import com.loanplatform.loan_platform.adapter.outbound.stripe.StripeGatewayService;
import com.loanplatform.loan_platform.domain.loan.model.DisbursementStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplicationStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallment;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallmentStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleEvent;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleState;
import com.loanplatform.loan_platform.domain.loan.model.LoanOffer;
import com.loanplatform.loan_platform.domain.loan.model.LoanOfferStatus;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanInstallmentRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanOfferRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanStateHistoryRepository;
import com.loanplatform.loan_platform.domain.loan.service.DisbursementService;
import com.loanplatform.loan_platform.domain.loan.service.RepaymentScheduleService;
import com.loanplatform.loan_platform.dto.request.LoanDisbursementRequest;
import com.loanplatform.loan_platform.dto.response.LoanDisbursementResponse;
import com.loanplatform.loan_platform.exception.DisbursementInProgressException;
import com.loanplatform.loan_platform.exception.DisbursementPreconditionFailedException;
import com.loanplatform.loan_platform.exception.MambuIntegrationException;
import com.loanplatform.loan_platform.exception.RepaymentSchedulePersistenceException;
import com.loanplatform.loan_platform.mapper.DisbursementMapper;
import com.loanplatform.loan_platform.port.outbound.BorrowerNotificationPort;
import com.loanplatform.loan_platform.port.outbound.KafkaEventPort;
import com.loanplatform.loan_platform.port.outbound.LoanStateMachinePort;
import com.loanplatform.loan_platform.port.outbound.MambuPort;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisbursementServiceTest {

    @Mock
    private LoanApplicationRepository loanApplicationRepository;
    @Mock
    private LoanInstallmentRepository loanInstallmentRepository;
    @Mock
    private LoanOfferRepository loanOfferRepository;
    @Mock
    private LoanStateHistoryRepository loanStateHistoryRepository;
    @Mock
    private LoanStateMachinePort loanStateMachinePort;
    @Mock
    private MambuPort mambuPort;
    @Mock
    private RepaymentScheduleService repaymentScheduleService;
    @Mock
    private DisbursementMapper disbursementMapper;
    @Mock
    private KafkaEventPort kafkaEventPort;
    @Mock
    private BorrowerNotificationPort borrowerNotificationPort;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private StripeGatewayService stripeGatewayService;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private DisbursementService disbursementService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(disbursementService, "idempotencyTtlSeconds", 300L);
        ReflectionTestUtils.setField(disbursementService, "offerValidityHours", 48L);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void precondition_stripeCustomerIdNull_returns422() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        LoanApplication application = approvedApplication(tenantId, applicationId);
        LoanOffer selectedOffer = activeSelectedOffer(application);

        when(loanApplicationRepository.findByIdAndTenantIdForUpdate(applicationId, tenantId)).thenReturn(Optional.of(application));
        when(loanOfferRepository.findByIdAndApplicationIdAndTenantId(selectedOffer.getId(), applicationId, tenantId))
                .thenReturn(Optional.of(selectedOffer));

        LoanDisbursementRequest request = LoanDisbursementRequest.builder()
                .notes("Flow5")
                .build();

        assertThatThrownBy(() -> disbursementService.disburse(tenantId, actorId, applicationId, request))
                .isInstanceOf(DisbursementPreconditionFailedException.class)
                .satisfies(ex -> assertThat(((DisbursementPreconditionFailedException) ex).getFailedConditions())
                        .contains("STRIPE_CUSTOMER_ID_MISSING"));

        verify(valueOperations, never()).setIfAbsent(any(), any(), any(Duration.class));
    }

    @Test
    void redisKeyExists_returns409() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        LoanApplication application = approvedApplication(tenantId, applicationId);
        LoanOffer selectedOffer = activeSelectedOffer(application);

        when(loanApplicationRepository.findByIdAndTenantIdForUpdate(applicationId, tenantId)).thenReturn(Optional.of(application));
        when(loanOfferRepository.findByIdAndApplicationIdAndTenantId(selectedOffer.getId(), applicationId, tenantId))
                .thenReturn(Optional.of(selectedOffer));
        when(valueOperations.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(false);

        LoanDisbursementRequest request = LoanDisbursementRequest.builder()
                .stripeDestinationAccountId("acct_123")
                .build();

        assertThatThrownBy(() -> disbursementService.disburse(tenantId, actorId, applicationId, request))
                .isInstanceOf(DisbursementInProgressException.class);
    }

    @Test
    void mambuDisburseFails_setsSyncFailedFlag() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        LoanApplication application = approvedApplication(tenantId, applicationId);
        LoanOffer selectedOffer = activeSelectedOffer(application);

        when(loanApplicationRepository.findByIdAndTenantIdForUpdate(applicationId, tenantId)).thenReturn(Optional.of(application));
        when(loanOfferRepository.findByIdAndApplicationIdAndTenantId(selectedOffer.getId(), applicationId, tenantId))
                .thenReturn(Optional.of(selectedOffer));
        when(valueOperations.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(true);
        when(mambuPort.disburseLoan(any(), any())).thenThrow(new RuntimeException("mambu down"));

        LoanDisbursementRequest request = LoanDisbursementRequest.builder()
                .stripeDestinationAccountId("acct_123")
                .build();

        assertThatThrownBy(() -> disbursementService.disburse(tenantId, actorId, applicationId, request))
                .isInstanceOf(MambuIntegrationException.class);

        assertThat(application.getDisbursementStatus()).isEqualTo(DisbursementStatus.FAILED);
        assertThat(application.isMambuDisburseSyncFailed()).isTrue();
        verify(stringRedisTemplate).delete("idempotency:DISB:" + applicationId + ":" + tenantId);
    }

    @Test
    void netDisbursedAmount_usedForStripeNotGross() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        LoanApplication application = approvedApplication(tenantId, applicationId);
        application.setRequestedAmount(BigDecimal.valueOf(500000));
        LoanOffer selectedOffer = activeSelectedOffer(application);

        when(loanApplicationRepository.findByIdAndTenantIdForUpdate(applicationId, tenantId)).thenReturn(Optional.of(application));
        when(loanOfferRepository.findByIdAndApplicationIdAndTenantId(selectedOffer.getId(), applicationId, tenantId))
                .thenReturn(Optional.of(selectedOffer));
        when(valueOperations.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(true);

        MambuLoanDisbursementResponse disbursementResponse = MambuLoanDisbursementResponse.builder()
                .id("txn_1")
                .amount(BigDecimal.valueOf(492500))
                .fees(MambuLoanDisbursementResponse.Fee.builder().amount(BigDecimal.valueOf(7500)).build())
                .build();

        when(mambuPort.disburseLoan(any(), any())).thenReturn(disbursementResponse);
        when(loanStateMachinePort.transition(LoanLifecycleState.APPROVED, LoanLifecycleEvent.DISBURSE))
                .thenReturn(LoanLifecycleState.DISBURSED);
        when(loanStateMachinePort.transition(LoanLifecycleState.DISBURSED, LoanLifecycleEvent.ACTIVATE_REPAYMENT))
                .thenReturn(LoanLifecycleState.ACTIVE_REPAYMENT);
        when(repaymentScheduleService.fetchAndPersistSchedule(any())).thenReturn(List.of(
                LoanInstallment.builder()
                        .id(UUID.randomUUID())
                        .tenantId(tenantId)
                        .applicationId(applicationId)
                        .borrowerId(application.getBorrowerId())
                        .mambuLoanId(application.getMambuLoanId())
                        .installmentNumber(1)
                        .dueDate(java.time.LocalDate.now().plusMonths(1))
                        .principalAmount(BigDecimal.valueOf(10000))
                        .interestAmount(BigDecimal.valueOf(2000))
                        .totalDue(BigDecimal.valueOf(12000))
                        .status(LoanInstallmentStatus.PENDING)
                        .build()
        ));
        when(disbursementMapper.toLoanDisbursementResponse(any())).thenReturn(LoanDisbursementResponse.builder().build());
        when(stripeGatewayService.createDisbursementTransfer(any(), any(), any(), any())).thenReturn("tr_123");

        LoanDisbursementRequest request = LoanDisbursementRequest.builder()
                .stripeDestinationAccountId("acct_123")
                .build();

        disbursementService.disburse(tenantId, actorId, applicationId, request);

        ArgumentCaptor<BigDecimal> amountCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(stripeGatewayService).createDisbursementTransfer(eq(tenantId), eq(applicationId), amountCaptor.capture(), eq("acct_123"));
        assertThat(amountCaptor.getValue()).isEqualByComparingTo("492500");
        verify(borrowerNotificationPort).onLoanDisbursed(eq(application.getBorrowerId()), eq(tenantId), eq(applicationId), any());
    }

    @Test
    void scheduleFetchFails_setsRetryFlag_noBorrowerNotification() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        LoanApplication application = approvedApplication(tenantId, applicationId);
        LoanOffer selectedOffer = activeSelectedOffer(application);

        when(loanApplicationRepository.findByIdAndTenantIdForUpdate(applicationId, tenantId)).thenReturn(Optional.of(application));
        when(loanOfferRepository.findByIdAndApplicationIdAndTenantId(selectedOffer.getId(), applicationId, tenantId))
                .thenReturn(Optional.of(selectedOffer));
        when(valueOperations.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(true);

        MambuLoanDisbursementResponse disbursementResponse = MambuLoanDisbursementResponse.builder()
                .id("txn_1")
                .amount(BigDecimal.valueOf(492500))
                .fees(MambuLoanDisbursementResponse.Fee.builder().amount(BigDecimal.valueOf(7500)).build())
                .build();

        when(mambuPort.disburseLoan(any(), any())).thenReturn(disbursementResponse);
        when(loanStateMachinePort.transition(LoanLifecycleState.APPROVED, LoanLifecycleEvent.DISBURSE))
                .thenReturn(LoanLifecycleState.DISBURSED);
        when(repaymentScheduleService.fetchAndPersistSchedule(any()))
                .thenThrow(new RepaymentSchedulePersistenceException("schedule down", new RuntimeException("boom")));

        LoanDisbursementRequest request = LoanDisbursementRequest.builder()
                .stripeDestinationAccountId("acct_123")
                .build();

        assertThatThrownBy(() -> disbursementService.disburse(tenantId, actorId, applicationId, request))
                .isInstanceOf(RepaymentSchedulePersistenceException.class);

        assertThat(application.isScheduleFetchFailed()).isTrue();
        assertThat(application.getDisbursementStatus()).isEqualTo(DisbursementStatus.FAILED);
        verify(borrowerNotificationPort, never()).onLoanDisbursed(any(), any(), any(), any());
    }

    private LoanApplication approvedApplication(UUID tenantId, UUID applicationId) {
        return LoanApplication.builder()
                .id(applicationId)
                .tenantId(tenantId)
                .borrowerId(UUID.randomUUID())
                .selectedOfferId(UUID.randomUUID())
                .mambuLoanId("LN_001")
                .status(LoanApplicationStatus.APPROVED)
                .loanState(LoanLifecycleState.APPROVED)
                .disbursementStatus(DisbursementStatus.PENDING)
                .requestedAmount(BigDecimal.valueOf(500000))
                .build();
    }

    private LoanOffer activeSelectedOffer(LoanApplication application) {
        return LoanOffer.builder()
                .id(application.getSelectedOfferId())
                .tenantId(application.getTenantId())
                .applicationId(application.getId())
                .offerRank((short) 1)
                .tenureMonths(36)
                .principalAmount(BigDecimal.valueOf(500000))
                .emiAmount(BigDecimal.valueOf(17200))
                .totalInterest(BigDecimal.valueOf(119200))
                .effectiveAnnualRate(BigDecimal.valueOf(14.5))
                .processingFeeAmount(BigDecimal.valueOf(7500))
                .totalPayableAmount(BigDecimal.valueOf(619200))
                .expiresAt(Instant.now().plusSeconds(3600))
                .status(LoanOfferStatus.SELECTED)
                .build();
    }
}

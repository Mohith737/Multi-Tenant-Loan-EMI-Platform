package com.loanplatform.loan_platform.unit.loan;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanAccountResponse;
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
import com.loanplatform.loan_platform.domain.loan.service.NpaService;
import com.loanplatform.loan_platform.dto.request.NpaOverrideRequest;
import com.loanplatform.loan_platform.dto.response.NpaOverrideResponse;
import com.loanplatform.loan_platform.mapper.NpaMapper;
import com.loanplatform.loan_platform.port.outbound.KafkaEventPort;
import com.loanplatform.loan_platform.port.outbound.LoanStateMachinePort;
import com.loanplatform.loan_platform.port.outbound.MambuPort;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.extension.ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NpaServiceTest {

    @Mock
    private LoanInstallmentRepository loanInstallmentRepository;
    @Mock
    private LoanApplicationRepository loanApplicationRepository;
    @Mock
    private BorrowerRepository borrowerRepository;
    @Mock
    private NpaLoanRecordRepository npaLoanRecordRepository;
    @Mock
    private NpaRecoveryActionRepository npaRecoveryActionRepository;
    @Mock
    private MambuPort mambuPort;
    @Mock
    private LoanStateMachinePort loanStateMachinePort;
    @Mock
    private KafkaEventPort kafkaEventPort;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private NpaMapper npaMapper;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private NpaService npaService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(npaService, "npaDpdThreshold", 90);
        ReflectionTestUtils.setField(npaService, "flagRedisTtlSeconds", 86400L);
        ReflectionTestUtils.setField(npaService, "overrideLockTtlSeconds", 300L);
        ReflectionTestUtils.setField(npaService, "escalationRedisTtlSeconds", 86400L);
        ReflectionTestUtils.setField(npaService, "schedulerZone", "Asia/Kolkata");

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(npaLoanRecordRepository.save(any(NpaLoanRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @ParameterizedTest
    @MethodSource("stageResolutionCases")
    void resolveStageForElapsedDays_variousInputs_expectedStage(int elapsedDays, NpaRecoveryStage expectedStage) {
        assertThat(npaService.resolveStageForElapsedDays(elapsedDays)).isEqualTo(expectedStage);
    }

    @Test
    void flagNpaLoans_loanCrosses90Dpd_flagsAsNonPerformingAndPersistsRecord() {
        UUID tenantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        LocalDate processingDate = LocalDate.now();

        LoanInstallment installment = LoanInstallment.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .applicationId(applicationId)
                .borrowerId(borrowerId)
                .mambuLoanId("LN_NPA_001")
                .dueDate(processingDate.minusDays(95))
                .totalDue(new BigDecimal("1200.00"))
                .status(LoanInstallmentStatus.OVERDUE)
                .build();

        LoanApplication application = LoanApplication.builder()
                .id(applicationId)
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .loanProductId(UUID.randomUUID())
                .loanProductKey("LP_FLOW8")
                .requestedAmount(new BigDecimal("500000"))
                .requestedTenureMonths(36)
                .status(LoanApplicationStatus.ACTIVE_REPAYMENT)
                .loanState(LoanLifecycleState.ACTIVE_REPAYMENT)
                .eligibilityPassed(true)
                .eligibilityReasonCode("PASS")
                .riskCategory("PRIME")
                .creditScoreSnapshot(760)
                .dtiRatioSnapshot(new BigDecimal("24.2"))
                .monthlyIncomeSnapshot(new BigDecimal("100000"))
                .employmentTypeSnapshot("SALARIED")
                .maxApprovedAmountSnapshot(new BigDecimal("600000"))
                .offersExpiresAt(Instant.now().plusSeconds(3600))
                .submittedAt(Instant.now())
                .mambuLoanId("LN_NPA_001")
                .build();

        when(loanInstallmentRepository.findByStatusInAndDueDateLessThanEqual(any(), eq(processingDate.minusDays(90))))
                .thenReturn(List.of(installment));
        when(loanInstallmentRepository.findByTenantIdAndApplicationIdOrderByInstallmentNumberAsc(tenantId, applicationId))
                .thenReturn(List.of(installment));
        when(npaLoanRecordRepository.findByTenantIdAndLoanApplicationIdAndActiveTrue(tenantId, applicationId))
                .thenReturn(Optional.empty());
        when(loanApplicationRepository.findByIdAndTenantIdForUpdate(applicationId, tenantId))
                .thenReturn(Optional.of(application));
        when(mambuPort.patchLoanState("LN_NPA_001", "NON_PERFORMING", "90+ DPD - Flagged as NPA by system on " + processingDate))
                .thenReturn(MambuLoanAccountResponse.builder().id("LN_NPA_001").accountState("NON_PERFORMING").build());
        when(loanStateMachinePort.transition(LoanLifecycleState.ACTIVE_REPAYMENT, LoanLifecycleEvent.FLAG_NON_PERFORMING))
                .thenReturn(LoanLifecycleState.NON_PERFORMING);
        when(npaRecoveryActionRepository.existsByTenantIdAndNpaLoanRecordIdAndActionType(any(), any(), any())).thenReturn(false);

        npaService.flagNpaLoans(processingDate);

        ArgumentCaptor<NpaLoanRecord> npaRecordCaptor = ArgumentCaptor.forClass(NpaLoanRecord.class);
        verify(npaLoanRecordRepository).save(npaRecordCaptor.capture());
        NpaLoanRecord savedRecord = npaRecordCaptor.getValue();
        assertThat(savedRecord.getTenantId()).isEqualTo(tenantId);
        assertThat(savedRecord.getLoanApplicationId()).isEqualTo(applicationId);
        assertThat(savedRecord.getRecoveryStage()).isEqualTo(NpaRecoveryStage.DAY_1_REMINDER);
        assertThat(savedRecord.getMambuState()).isEqualTo("NON_PERFORMING");
        assertThat(savedRecord.getMaxDpd()).isGreaterThanOrEqualTo(95);
        assertThat(savedRecord.getOutstandingAmount()).isEqualByComparingTo("1200.00");

        verify(kafkaEventPort).publish(eq("loan.npa.flagged"), eq(tenantId), eq(applicationId), any());
        verify(npaRecoveryActionRepository).save(any(NpaRecoveryAction.class));
    }

    @Test
    void overrideNpaLoan_activeRecord_clearsLockAndTransitionsToActiveRepayment() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID npaRecordId = UUID.randomUUID();
        String loanAccountId = "LN_NPA_002";

        NpaLoanRecord record = NpaLoanRecord.builder()
                .id(npaRecordId)
                .tenantId(tenantId)
                .loanApplicationId(applicationId)
                .borrowerId(UUID.randomUUID())
                .mambuLoanId(loanAccountId)
                .maxDpd(110)
                .outstandingAmount(new BigDecimal("35000.00"))
                .npaFlaggedAt(Instant.now().minusSeconds(10_000))
                .recoveryStage(NpaRecoveryStage.DAY_30_LEGAL_ESCALATION)
                .mambuState("NON_PERFORMING")
                .active(true)
                .build();

        LoanApplication application = LoanApplication.builder()
                .id(applicationId)
                .tenantId(tenantId)
                .borrowerId(record.getBorrowerId())
                .loanProductId(UUID.randomUUID())
                .loanProductKey("LP_FLOW8")
                .requestedAmount(new BigDecimal("250000"))
                .requestedTenureMonths(24)
                .status(LoanApplicationStatus.ACTIVE_REPAYMENT)
                .loanState(LoanLifecycleState.LEGAL_ESCALATION)
                .eligibilityPassed(true)
                .eligibilityReasonCode("PASS")
                .riskCategory("PRIME")
                .creditScoreSnapshot(750)
                .dtiRatioSnapshot(new BigDecimal("21.0"))
                .monthlyIncomeSnapshot(new BigDecimal("90000"))
                .employmentTypeSnapshot("SALARIED")
                .maxApprovedAmountSnapshot(new BigDecimal("500000"))
                .offersExpiresAt(Instant.now().plusSeconds(3600))
                .submittedAt(Instant.now())
                .mambuLoanId(loanAccountId)
                .build();

        when(npaLoanRecordRepository.findByTenantIdAndMambuLoanIdForUpdate(tenantId, loanAccountId))
                .thenReturn(Optional.of(record));
        when(loanApplicationRepository.findByIdAndTenantIdForUpdate(applicationId, tenantId))
                .thenReturn(Optional.of(application));
        when(mambuPort.patchLoanState(loanAccountId, "ACTIVE", "NPA override cleared by admin. Reason: Settlement agreed"))
                .thenReturn(MambuLoanAccountResponse.builder().id(loanAccountId).accountState("ACTIVE").build());
        when(loanStateMachinePort.transition(LoanLifecycleState.LEGAL_ESCALATION, LoanLifecycleEvent.CLEAR_NPA_OVERRIDE))
                .thenReturn(LoanLifecycleState.ACTIVE_REPAYMENT);
        when(npaRecoveryActionRepository.existsByTenantIdAndNpaLoanRecordIdAndActionType(
                tenantId,
                npaRecordId,
                NpaRecoveryActionType.OVERRIDE_CLEARED
        )).thenReturn(false);

        NpaOverrideRequest request = NpaOverrideRequest.builder()
                .overrideReason("Settlement agreed")
                .adminNote("Borrower paid negotiated settlement")
                .build();

        NpaOverrideResponse response = npaService.overrideNpaLoan(tenantId, actorId, loanAccountId, request);

        assertThat(response.isNpaLockCleared()).isTrue();
        assertThat(response.getLoanAccountId()).isEqualTo(loanAccountId);
        assertThat(response.getOverriddenAt()).isNotNull();

        ArgumentCaptor<NpaLoanRecord> recordCaptor = ArgumentCaptor.forClass(NpaLoanRecord.class);
        verify(npaLoanRecordRepository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().isActive()).isFalse();
        assertThat(recordCaptor.getValue().getRecoveryStage()).isEqualTo(NpaRecoveryStage.OVERRIDDEN);

        verify(loanApplicationRepository).save(application);
        verify(kafkaEventPort).publish(eq("loan.npa.override.cleared"), eq(tenantId), eq(applicationId), any());
    }

    @Test
    void runRecoveryEscalation_day30Reached_movesToLegalEscalation() {
        UUID tenantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID npaRecordId = UUID.randomUUID();
        LocalDate processingDate = LocalDate.now();

        NpaLoanRecord record = NpaLoanRecord.builder()
                .id(npaRecordId)
                .tenantId(tenantId)
                .loanApplicationId(applicationId)
                .borrowerId(UUID.randomUUID())
                .mambuLoanId("LN_NPA_003")
                .maxDpd(130)
                .outstandingAmount(new BigDecimal("78000.00"))
                .npaFlaggedAt(Instant.now().minus(35, ChronoUnit.DAYS))
                .recoveryStage(NpaRecoveryStage.DAY_7_ESCALATION)
                .mambuState("NON_PERFORMING")
                .active(true)
                .build();

        LoanApplication application = LoanApplication.builder()
                .id(applicationId)
                .tenantId(tenantId)
                .borrowerId(record.getBorrowerId())
                .loanProductId(UUID.randomUUID())
                .loanProductKey("LP_FLOW8")
                .requestedAmount(new BigDecimal("300000"))
                .requestedTenureMonths(24)
                .status(LoanApplicationStatus.ACTIVE_REPAYMENT)
                .loanState(LoanLifecycleState.NON_PERFORMING)
                .eligibilityPassed(true)
                .eligibilityReasonCode("PASS")
                .riskCategory("PRIME")
                .creditScoreSnapshot(730)
                .dtiRatioSnapshot(new BigDecimal("25.0"))
                .monthlyIncomeSnapshot(new BigDecimal("85000"))
                .employmentTypeSnapshot("SALARIED")
                .maxApprovedAmountSnapshot(new BigDecimal("450000"))
                .offersExpiresAt(Instant.now().plusSeconds(3600))
                .submittedAt(Instant.now())
                .mambuLoanId("LN_NPA_003")
                .build();

        when(npaLoanRecordRepository.findByActiveTrueOrderByNpaFlaggedAtAsc()).thenReturn(List.of(record));
        when(npaRecoveryActionRepository.existsByTenantIdAndNpaLoanRecordIdAndActionType(any(), any(), any())).thenReturn(false);
        when(loanApplicationRepository.findByIdAndTenantIdForUpdate(applicationId, tenantId)).thenReturn(Optional.of(application));
        when(loanStateMachinePort.transition(LoanLifecycleState.NON_PERFORMING, LoanLifecycleEvent.ESCALATE_TO_LEGAL))
                .thenReturn(LoanLifecycleState.LEGAL_ESCALATION);

        npaService.runRecoveryEscalation(processingDate);

        verify(mambuPort).patchLoanState(eq("LN_NPA_003"), eq("NON_PERFORMING"), anyString());
        verify(kafkaEventPort).publish(eq("loan.npa.recovery.day30.legal"), eq(tenantId), eq(applicationId), any());

        ArgumentCaptor<NpaLoanRecord> recordCaptor = ArgumentCaptor.forClass(NpaLoanRecord.class);
        verify(npaLoanRecordRepository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getRecoveryStage()).isEqualTo(NpaRecoveryStage.DAY_30_LEGAL_ESCALATION);
        assertThat(recordCaptor.getValue().getLegalEscalatedAt()).isNotNull();
    }

    @Test
    void flagNpaLoans_belowThreshold_doesNotPatchMambu() {
        UUID tenantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        LocalDate processingDate = LocalDate.now();

        LoanInstallment installment = LoanInstallment.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .applicationId(applicationId)
                .borrowerId(borrowerId)
                .mambuLoanId("LN_NPA_004")
                .dueDate(processingDate.minusDays(20))
                .totalDue(new BigDecimal("1000.00"))
                .status(LoanInstallmentStatus.OVERDUE)
                .build();

        when(loanInstallmentRepository.findByStatusInAndDueDateLessThanEqual(any(), eq(processingDate.minusDays(90))))
                .thenReturn(List.of(installment));

        npaService.flagNpaLoans(processingDate);

        verify(mambuPort, never()).patchLoanState(anyString(), anyString(), anyString());
        verify(npaLoanRecordRepository, never()).save(any(NpaLoanRecord.class));
    }

    private static Stream<Arguments> stageResolutionCases() {
        return IntStream.range(0, 100)
                .mapToObj(days -> Arguments.of(days, expectedStage(days)));
    }

    private static NpaRecoveryStage expectedStage(int days) {
        if (days >= 30) {
            return NpaRecoveryStage.DAY_30_LEGAL_ESCALATION;
        }
        if (days >= 7) {
            return NpaRecoveryStage.DAY_7_ESCALATION;
        }
        if (days >= 1) {
            return NpaRecoveryStage.DAY_1_REMINDER;
        }
        return null;
    }
}

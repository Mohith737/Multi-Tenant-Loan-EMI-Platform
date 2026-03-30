package com.loanplatform.loan_platform.unit.loan;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanAccountResponse;
import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplicationStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleEvent;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleState;
import com.loanplatform.loan_platform.domain.loan.model.LoanOffer;
import com.loanplatform.loan_platform.domain.loan.model.UnderwriterDecision;
import com.loanplatform.loan_platform.domain.loan.model.UnderwriterDecisionType;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanOfferRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanStateHistoryRepository;
import com.loanplatform.loan_platform.domain.loan.repository.UnderwriterDecisionRepository;
import com.loanplatform.loan_platform.domain.loan.service.LoanDocumentService;
import com.loanplatform.loan_platform.domain.loan.service.UnderwriterService;
import com.loanplatform.loan_platform.dto.request.UnderwriterDecisionRequest;
import com.loanplatform.loan_platform.dto.response.UnderwriterDecisionResponse;
import com.loanplatform.loan_platform.dto.response.UnderwriterQueueItemResponse;
import com.loanplatform.loan_platform.exception.UnderwriterDecisionConflictException;
import com.loanplatform.loan_platform.exception.UnderwriterDecisionNotFoundException;
import com.loanplatform.loan_platform.mapper.LoanDocumentMapper;
import com.loanplatform.loan_platform.port.outbound.BorrowerNotificationPort;
import com.loanplatform.loan_platform.port.outbound.KafkaEventPort;
import com.loanplatform.loan_platform.port.outbound.LoanStateMachinePort;
import com.loanplatform.loan_platform.port.outbound.MambuPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnderwriterServiceTest {

    @Mock
    private LoanApplicationRepository loanApplicationRepository;
    @Mock
    private BorrowerRepository borrowerRepository;
    @Mock
    private LoanOfferRepository loanOfferRepository;
    @Mock
    private UnderwriterDecisionRepository underwriterDecisionRepository;
    @Mock
    private LoanDocumentService loanDocumentService;
    @Mock
    private LoanDocumentMapper loanDocumentMapper;
    @Mock
    private LoanStateMachinePort loanStateMachinePort;
    @Mock
    private LoanStateHistoryRepository loanStateHistoryRepository;
    @Mock
    private KafkaEventPort kafkaEventPort;
    @Mock
    private BorrowerNotificationPort borrowerNotificationPort;
    @Mock
    private MambuPort mambuPort;

    @InjectMocks
    private UnderwriterService underwriterService;

    @Test
    void decide_approveSuccess_marksApplicationApproved() {
        UUID tenantId = UUID.randomUUID();
        UUID underwriterId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();

        LoanApplication application = LoanApplication.builder()
                .id(applicationId)
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .selectedOfferId(offerId)
                .loanProductKey("LP_TEST")
                .status(LoanApplicationStatus.UNDER_VERIFICATION)
                .loanState(LoanLifecycleState.UNDER_VERIFICATION)
                .requestedDisbursementDate(java.time.LocalDate.now())
                .build();

        Borrower borrower = Borrower.builder()
                .id(borrowerId)
                .tenantId(tenantId)
                .mambuClientKey("cli_001")
                .build();

        LoanOffer offer = LoanOffer.builder()
                .id(offerId)
                .applicationId(applicationId)
                .tenantId(tenantId)
                .principalAmount(BigDecimal.valueOf(500000))
                .effectiveAnnualRate(BigDecimal.valueOf(14.5))
                .tenureMonths(36)
                .build();

        UnderwriterDecisionRequest request = UnderwriterDecisionRequest.builder()
                .decision(UnderwriterDecisionType.APPROVED)
                .remarks("Documents verified")
                .build();

        UnderwriterDecisionResponse mapped = UnderwriterDecisionResponse.builder()
                .applicationId(applicationId)
                .decision("APPROVED")
                .status("APPROVED")
                .loanState("APPROVED")
                .mambuLoanId("LN_1001")
                .decidedAt(Instant.now())
                .build();

        when(loanApplicationRepository.findByIdAndTenantId(applicationId, tenantId)).thenReturn(Optional.of(application));
        when(underwriterDecisionRepository.existsByApplicationId(applicationId)).thenReturn(false);
        when(underwriterDecisionRepository.save(any(UnderwriterDecision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(borrowerRepository.findByIdAndTenantId(borrowerId, tenantId)).thenReturn(Optional.of(borrower));
        when(loanOfferRepository.findByIdAndApplicationIdAndTenantId(offerId, applicationId, tenantId)).thenReturn(Optional.of(offer));
        when(mambuPort.createLoanAccount(any())).thenReturn(MambuLoanAccountResponse.builder().id("LN_1001").accountState("PENDING_APPROVAL").build());
        when(mambuPort.approveLoanAccount(eq("LN_1001"), any())).thenReturn(MambuLoanAccountResponse.builder().id("LN_1001").accountState("APPROVED").build());
        when(loanStateMachinePort.transition(LoanLifecycleState.UNDER_VERIFICATION, LoanLifecycleEvent.APPROVE))
                .thenReturn(LoanLifecycleState.APPROVED);
        when(loanDocumentMapper.toUnderwriterDecisionResponse(any(), any())).thenReturn(mapped);
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UnderwriterDecisionResponse response = underwriterService.decide(tenantId, underwriterId, applicationId, request);

        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(application.getStatus()).isEqualTo(LoanApplicationStatus.APPROVED);
        assertThat(application.getLoanState()).isEqualTo(LoanLifecycleState.APPROVED);
        assertThat(application.getMambuLoanId()).isEqualTo("LN_1001");
        assertThat(application.isMambuSyncFailed()).isFalse();

        verify(loanDocumentService, times(1)).validateRequiredDocumentsForDecision(tenantId, application);
        verify(mambuPort, times(1)).createLoanAccount(any());
        verify(mambuPort, times(1)).approveLoanAccount(eq("LN_1001"), any());
        verify(kafkaEventPort, times(1)).publish(eq("loan.application.approved"), eq(tenantId), eq(applicationId), any());
        verify(borrowerNotificationPort, times(1)).onLoanApproved(eq(borrowerId), eq(tenantId), eq(applicationId));
    }

    @Test
    void decide_rejectDecision_marksApplicationRejected() {
        UUID tenantId = UUID.randomUUID();
        UUID underwriterId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();

        LoanApplication application = LoanApplication.builder()
                .id(applicationId)
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .status(LoanApplicationStatus.UNDER_VERIFICATION)
                .loanState(LoanLifecycleState.UNDER_VERIFICATION)
                .build();

        UnderwriterDecisionRequest request = UnderwriterDecisionRequest.builder()
                .decision(UnderwriterDecisionType.REJECTED)
                .remarks("Income proof mismatch")
                .build();

        when(loanApplicationRepository.findByIdAndTenantId(applicationId, tenantId)).thenReturn(Optional.of(application));
        when(underwriterDecisionRepository.existsByApplicationId(applicationId)).thenReturn(false);
        when(underwriterDecisionRepository.save(any(UnderwriterDecision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(loanStateMachinePort.transition(LoanLifecycleState.UNDER_VERIFICATION, LoanLifecycleEvent.REJECT))
                .thenReturn(LoanLifecycleState.REJECTED);
        when(loanDocumentMapper.toUnderwriterDecisionResponse(any(), any())).thenReturn(
                UnderwriterDecisionResponse.builder().status("REJECTED").loanState("REJECTED").build()
        );

        underwriterService.decide(tenantId, underwriterId, applicationId, request);

        assertThat(application.getStatus()).isEqualTo(LoanApplicationStatus.REJECTED);
        assertThat(application.getLoanState()).isEqualTo(LoanLifecycleState.REJECTED);
        assertThat(application.getRejectionReason()).isEqualTo("Income proof mismatch");
        verify(loanDocumentService, times(1)).validateRequiredDocumentsForDecision(tenantId, application);
        verify(mambuPort, never()).createLoanAccount(any());
        verify(mambuPort, never()).approveLoanAccount(any(), any());
        verify(kafkaEventPort, times(1)).publish(eq("loan.application.rejected"), eq(tenantId), eq(applicationId), any());
    }

    @Test
    void decide_missingRequiredDocuments_throwsConflictException() {
        UUID tenantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID underwriterId = UUID.randomUUID();

        LoanApplication application = LoanApplication.builder()
                .id(applicationId)
                .tenantId(tenantId)
                .status(LoanApplicationStatus.UNDER_VERIFICATION)
                .loanState(LoanLifecycleState.UNDER_VERIFICATION)
                .build();

        when(loanApplicationRepository.findByIdAndTenantId(applicationId, tenantId)).thenReturn(Optional.of(application));
        doThrow(new UnderwriterDecisionConflictException("Missing required documents for decision: INCOME_SOURCE_PROOF"))
                .when(loanDocumentService).validateRequiredDocumentsForDecision(tenantId, application);

        assertThatThrownBy(() -> underwriterService.decide(
                tenantId,
                underwriterId,
                applicationId,
                UnderwriterDecisionRequest.builder().decision(UnderwriterDecisionType.APPROVED).build()
        )).isInstanceOf(UnderwriterDecisionConflictException.class)
                .hasMessageContaining("Missing required documents");

        verify(underwriterDecisionRepository, never()).save(any(UnderwriterDecision.class));
    }

    @Test
    void decide_duplicateDecision_throwsConflictException() {
        UUID tenantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID underwriterId = UUID.randomUUID();

        LoanApplication application = LoanApplication.builder()
                .id(applicationId)
                .tenantId(tenantId)
                .status(LoanApplicationStatus.UNDER_VERIFICATION)
                .loanState(LoanLifecycleState.UNDER_VERIFICATION)
                .build();

        when(loanApplicationRepository.findByIdAndTenantId(applicationId, tenantId)).thenReturn(Optional.of(application));
        when(underwriterDecisionRepository.existsByApplicationId(applicationId)).thenReturn(true);

        assertThatThrownBy(() -> underwriterService.decide(
                tenantId,
                underwriterId,
                applicationId,
                UnderwriterDecisionRequest.builder().decision(UnderwriterDecisionType.APPROVED).build()
        )).isInstanceOf(UnderwriterDecisionConflictException.class);
    }

    @Test
    void getDecision_missingDecision_throwsNotFoundException() {
        UUID tenantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();

        LoanApplication application = LoanApplication.builder()
                .id(applicationId)
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .build();

        when(loanApplicationRepository.findByIdAndTenantIdAndBorrowerId(applicationId, tenantId, borrowerId))
                .thenReturn(Optional.of(application));
        when(underwriterDecisionRepository.findByApplicationId(applicationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> underwriterService.getDecision(tenantId, borrowerId, applicationId, false))
                .isInstanceOf(UnderwriterDecisionNotFoundException.class);
    }

    @Test
    void retryMambuSync_failedApplication_makesApproved() {
        UUID tenantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();

        LoanApplication application = LoanApplication.builder()
                .id(applicationId)
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .selectedOfferId(offerId)
                .loanProductKey("LP_TEST")
                .status(LoanApplicationStatus.UNDER_VERIFICATION)
                .loanState(LoanLifecycleState.UNDER_VERIFICATION)
                .mambuSyncFailed(true)
                .build();

        Borrower borrower = Borrower.builder().id(borrowerId).tenantId(tenantId).mambuClientKey("cli_100").build();
        LoanOffer offer = LoanOffer.builder()
                .id(offerId)
                .applicationId(applicationId)
                .tenantId(tenantId)
                .principalAmount(BigDecimal.valueOf(300000))
                .effectiveAnnualRate(BigDecimal.valueOf(13.5))
                .tenureMonths(24)
                .build();

        when(borrowerRepository.findByIdAndTenantId(borrowerId, tenantId)).thenReturn(Optional.of(borrower));
        when(loanOfferRepository.findByIdAndApplicationIdAndTenantId(offerId, applicationId, tenantId)).thenReturn(Optional.of(offer));
        when(mambuPort.createLoanAccount(any())).thenReturn(MambuLoanAccountResponse.builder().id("LN_RETRY").accountState("PENDING_APPROVAL").build());
        when(mambuPort.approveLoanAccount(eq("LN_RETRY"), any())).thenReturn(MambuLoanAccountResponse.builder().id("LN_RETRY").accountState("APPROVED").build());
        when(loanStateMachinePort.transition(LoanLifecycleState.UNDER_VERIFICATION, LoanLifecycleEvent.APPROVE))
                .thenReturn(LoanLifecycleState.APPROVED);

        underwriterService.retryMambuSync(application);

        assertThat(application.getStatus()).isEqualTo(LoanApplicationStatus.APPROVED);
        assertThat(application.getLoanState()).isEqualTo(LoanLifecycleState.APPROVED);
        assertThat(application.isMambuSyncFailed()).isFalse();

        ArgumentCaptor<LoanApplication> appCaptor = ArgumentCaptor.forClass(LoanApplication.class);
        verify(loanApplicationRepository, times(1)).save(appCaptor.capture());
        assertThat(appCaptor.getValue().getMambuLoanId()).isEqualTo("LN_RETRY");
    }

    @Test
    void getQueue_underVerificationOnly_returnsQueueItems() {
        UUID tenantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        LoanApplication application = LoanApplication.builder()
                .id(applicationId)
                .tenantId(tenantId)
                .borrowerId(UUID.randomUUID())
                .status(LoanApplicationStatus.UNDER_VERIFICATION)
                .loanState(LoanLifecycleState.UNDER_VERIFICATION)
                .build();

        when(loanApplicationRepository.findByTenantIdAndStatusOrderBySubmittedAtAsc(tenantId, LoanApplicationStatus.UNDER_VERIFICATION))
                .thenReturn(List.of(application));
        when(loanDocumentService.countDocuments(tenantId, applicationId)).thenReturn(2L);
        when(loanDocumentMapper.toUnderwriterQueueItemResponse(application, 2)).thenReturn(
                UnderwriterQueueItemResponse.builder().applicationId(applicationId).documentsSubmitted(2).build()
        );

        var queueResponse = underwriterService.getQueue(tenantId);
        assertThat(queueResponse.getTotal()).isEqualTo(1);
        assertThat(queueResponse.getQueue()).hasSize(1);
        assertThat(queueResponse.getQueue().getFirst().getApplicationId()).isEqualTo(applicationId);
    }
}

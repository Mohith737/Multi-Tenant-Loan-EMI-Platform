package com.loanplatform.loan_platform.unit.loan;

import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplicationStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanDocument;
import com.loanplatform.loan_platform.domain.loan.model.LoanDocumentStatus;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleEvent;
import com.loanplatform.loan_platform.domain.loan.model.LoanLifecycleState;
import com.loanplatform.loan_platform.domain.loan.repository.LoanApplicationRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanDocumentRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanStateHistoryRepository;
import com.loanplatform.loan_platform.domain.loan.service.LoanDocumentService;
import com.loanplatform.loan_platform.dto.request.LoanDocumentUploadRequest;
import com.loanplatform.loan_platform.dto.response.LoanDocumentResponse;
import com.loanplatform.loan_platform.exception.LoanApplicationNotFoundException;
import com.loanplatform.loan_platform.exception.UnderwriterDecisionConflictException;
import com.loanplatform.loan_platform.mapper.LoanDocumentMapper;
import com.loanplatform.loan_platform.port.outbound.KafkaEventPort;
import com.loanplatform.loan_platform.port.outbound.LoanStateMachinePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanDocumentServiceTest {

    @Mock
    private LoanApplicationRepository loanApplicationRepository;

    @Mock
    private LoanDocumentRepository loanDocumentRepository;

    @Mock
    private LoanStateHistoryRepository loanStateHistoryRepository;

    @Mock
    private LoanStateMachinePort loanStateMachinePort;

    @Mock
    private KafkaEventPort kafkaEventPort;

    @Mock
    private LoanDocumentMapper loanDocumentMapper;

    @InjectMocks
    private LoanDocumentService loanDocumentService;

    @Test
    void uploadDocument_midAmountWithBankOnly_keepsApplicationInOfferSelected() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID selectedOfferId = UUID.randomUUID();

        LoanApplication application = LoanApplication.builder()
                .id(applicationId)
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .selectedOfferId(selectedOfferId)
                .requestedAmount(BigDecimal.valueOf(500000))
                .status(LoanApplicationStatus.OFFER_SELECTED)
                .loanState(LoanLifecycleState.OFFER_SELECTED)
                .build();

        LoanDocumentUploadRequest request = LoanDocumentUploadRequest.builder()
                .documentType("bank details")
                .documentReference("s3://docs/app/bank.pdf")
                .build();

        LoanDocument persisted = LoanDocument.builder()
                .id(UUID.randomUUID())
                .applicationId(applicationId)
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .documentType("BANK_ACCOUNT_DETAILS")
                .documentReference("s3://docs/app/bank.pdf")
                .status(LoanDocumentStatus.UPLOADED)
                .build();

        when(loanApplicationRepository.findForUpdate(applicationId, tenantId, borrowerId))
                .thenReturn(Optional.of(application));
        when(loanDocumentRepository.findByTenantIdAndApplicationIdOrderByDocumentTypeAsc(tenantId, applicationId))
                .thenReturn(List.of());
        when(loanDocumentRepository.save(any(LoanDocument.class))).thenReturn(persisted);
        when(loanDocumentMapper.toLoanDocumentResponse(persisted)).thenReturn(
                LoanDocumentResponse.builder()
                        .documentId(persisted.getId())
                        .status("UPLOADED")
                        .build()
        );

        LoanDocumentResponse response = loanDocumentService.uploadDocument(tenantId, borrowerId, applicationId, request);

        assertThat(response.getDocumentId()).isEqualTo(persisted.getId());
        assertThat(application.getStatus()).isEqualTo(LoanApplicationStatus.OFFER_SELECTED);
        assertThat(application.getLoanState()).isEqualTo(LoanLifecycleState.OFFER_SELECTED);
        verify(loanStateMachinePort, never()).transition(any(), any());
        verify(loanApplicationRepository, never()).save(application);
        verify(kafkaEventPort, never()).publish(eq("loan.underwriter.queue.entered"), eq(tenantId), eq(applicationId), any());
        verify(kafkaEventPort, times(1)).publish(eq("loan.documents.uploaded"), eq(tenantId), eq(applicationId), any());

        ArgumentCaptor<LoanDocument> documentCaptor = ArgumentCaptor.forClass(LoanDocument.class);
        verify(loanDocumentRepository).save(documentCaptor.capture());
        assertThat(documentCaptor.getValue().getDocumentType()).isEqualTo("BANK_ACCOUNT_DETAILS");
    }

    @BeforeEach
    void setThresholds() {
        ReflectionTestUtils.setField(loanDocumentService, "lowAmountMax", BigDecimal.valueOf(300000));
        ReflectionTestUtils.setField(loanDocumentService, "midAmountMax", BigDecimal.valueOf(700000));
    }

    @Test
    void uploadDocument_midAmountWithIncomeAfterBank_movesApplicationToUnderVerification() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID selectedOfferId = UUID.randomUUID();

        LoanApplication application = LoanApplication.builder()
                .id(applicationId)
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .selectedOfferId(selectedOfferId)
                .requestedAmount(BigDecimal.valueOf(500000))
                .status(LoanApplicationStatus.OFFER_SELECTED)
                .loanState(LoanLifecycleState.OFFER_SELECTED)
                .build();

        LoanDocumentUploadRequest request = LoanDocumentUploadRequest.builder()
                .documentType("income_proof")
                .documentReference("s3://docs/app/income.pdf")
                .build();

        LoanDocument bankDetails = LoanDocument.builder()
                .id(UUID.randomUUID())
                .applicationId(applicationId)
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .documentType("BANK_ACCOUNT_DETAILS")
                .documentReference("s3://docs/app/bank.pdf")
                .status(LoanDocumentStatus.UPLOADED)
                .build();
        LoanDocument persisted = LoanDocument.builder()
                .id(UUID.randomUUID())
                .applicationId(applicationId)
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .documentType("INCOME_SOURCE_PROOF")
                .documentReference("s3://docs/app/income.pdf")
                .status(LoanDocumentStatus.UPLOADED)
                .build();

        when(loanApplicationRepository.findForUpdate(applicationId, tenantId, borrowerId))
                .thenReturn(Optional.of(application));
        when(loanDocumentRepository.findByTenantIdAndApplicationIdOrderByDocumentTypeAsc(tenantId, applicationId))
                .thenReturn(List.of(bankDetails), List.of(bankDetails, persisted));
        when(loanDocumentRepository.save(any(LoanDocument.class))).thenReturn(persisted);
        when(loanStateMachinePort.transition(LoanLifecycleState.OFFER_SELECTED, LoanLifecycleEvent.START_UNDER_VERIFICATION))
                .thenReturn(LoanLifecycleState.UNDER_VERIFICATION);
        when(loanDocumentMapper.toLoanDocumentResponse(persisted)).thenReturn(
                LoanDocumentResponse.builder()
                        .documentId(persisted.getId())
                        .status("UPLOADED")
                        .build()
        );

        LoanDocumentResponse response = loanDocumentService.uploadDocument(tenantId, borrowerId, applicationId, request);

        assertThat(response.getDocumentId()).isEqualTo(persisted.getId());
        assertThat(application.getStatus()).isEqualTo(LoanApplicationStatus.UNDER_VERIFICATION);
        assertThat(application.getLoanState()).isEqualTo(LoanLifecycleState.UNDER_VERIFICATION);
        verify(loanApplicationRepository, times(1)).save(application);
        verify(kafkaEventPort, times(1)).publish(eq("loan.underwriter.queue.entered"), eq(tenantId), eq(applicationId), any());
    }

    @Test
    void uploadDocument_highAmountWithLandPaperAfterRequiredDocs_movesApplicationToUnderVerification() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID selectedOfferId = UUID.randomUUID();

        LoanApplication application = LoanApplication.builder()
                .id(applicationId)
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .selectedOfferId(selectedOfferId)
                .requestedAmount(BigDecimal.valueOf(900000))
                .status(LoanApplicationStatus.OFFER_SELECTED)
                .loanState(LoanLifecycleState.OFFER_SELECTED)
                .build();

        LoanDocumentUploadRequest request = LoanDocumentUploadRequest.builder()
                .documentType("property paper")
                .documentReference("s3://docs/app/land.pdf")
                .build();

        LoanDocument bankDetails = LoanDocument.builder()
                .documentType("BANK_ACCOUNT_DETAILS")
                .documentReference("s3://docs/app/bank.pdf")
                .status(LoanDocumentStatus.UPLOADED)
                .build();
        LoanDocument incomeProof = LoanDocument.builder()
                .documentType("INCOME_SOURCE_PROOF")
                .documentReference("s3://docs/app/income.pdf")
                .status(LoanDocumentStatus.UPLOADED)
                .build();
        LoanDocument persisted = LoanDocument.builder()
                .id(UUID.randomUUID())
                .applicationId(applicationId)
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .documentType("LAND_PAPER")
                .documentReference("s3://docs/app/land.pdf")
                .status(LoanDocumentStatus.UPLOADED)
                .build();

        when(loanApplicationRepository.findForUpdate(applicationId, tenantId, borrowerId))
                .thenReturn(Optional.of(application));
        when(loanDocumentRepository.findByTenantIdAndApplicationIdOrderByDocumentTypeAsc(tenantId, applicationId))
                .thenReturn(List.of(bankDetails, incomeProof), List.of(bankDetails, incomeProof, persisted));
        when(loanDocumentRepository.save(any(LoanDocument.class))).thenReturn(persisted);
        when(loanStateMachinePort.transition(LoanLifecycleState.OFFER_SELECTED, LoanLifecycleEvent.START_UNDER_VERIFICATION))
                .thenReturn(LoanLifecycleState.UNDER_VERIFICATION);
        when(loanDocumentMapper.toLoanDocumentResponse(persisted)).thenReturn(
                LoanDocumentResponse.builder()
                        .documentId(persisted.getId())
                        .status("UPLOADED")
                        .build()
        );

        loanDocumentService.uploadDocument(tenantId, borrowerId, applicationId, request);

        assertThat(application.getStatus()).isEqualTo(LoanApplicationStatus.UNDER_VERIFICATION);
        assertThat(application.getLoanState()).isEqualTo(LoanLifecycleState.UNDER_VERIFICATION);

        ArgumentCaptor<LoanDocument> documentCaptor = ArgumentCaptor.forClass(LoanDocument.class);
        verify(loanDocumentRepository).save(documentCaptor.capture());
        assertThat(documentCaptor.getValue().getDocumentType()).isEqualTo("LAND_PAPER");
    }

    @Test
    void uploadDocument_duplicateDocumentReference_throwsValidationError() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        LoanApplication application = LoanApplication.builder()
                .id(applicationId)
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .selectedOfferId(UUID.randomUUID())
                .requestedAmount(BigDecimal.valueOf(500000))
                .status(LoanApplicationStatus.OFFER_SELECTED)
                .loanState(LoanLifecycleState.OFFER_SELECTED)
                .build();

        LoanDocumentUploadRequest request = LoanDocumentUploadRequest.builder()
                .documentType("INCOME_PROOF")
                .documentReference("s3://docs/app/income.pdf")
                .build();

        LoanDocument existing = LoanDocument.builder()
                .id(UUID.randomUUID())
                .applicationId(applicationId)
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .documentType("INCOME_SOURCE_PROOF")
                .documentReference("s3://docs/app/income.pdf")
                .status(LoanDocumentStatus.UPLOADED)
                .build();

        when(loanApplicationRepository.findForUpdate(applicationId, tenantId, borrowerId))
                .thenReturn(Optional.of(application));
        when(loanDocumentRepository.findByTenantIdAndApplicationIdOrderByDocumentTypeAsc(tenantId, applicationId))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> loanDocumentService.uploadDocument(tenantId, borrowerId, applicationId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate document upload");

        verify(loanDocumentRepository, never()).save(any());
    }

    @Test
    void uploadDocument_selectedOfferMissing_throwsValidationError() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        LoanApplication application = LoanApplication.builder()
                .id(applicationId)
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .selectedOfferId(null)
                .requestedAmount(BigDecimal.valueOf(100000))
                .status(LoanApplicationStatus.APPLICATION_SUBMITTED)
                .loanState(LoanLifecycleState.APPLICATION_SUBMITTED)
                .build();

        LoanDocumentUploadRequest request = LoanDocumentUploadRequest.builder()
                .documentType("INCOME_PROOF")
                .documentReference("s3://docs/app/income.pdf")
                .build();

        when(loanApplicationRepository.findForUpdate(applicationId, tenantId, borrowerId))
                .thenReturn(Optional.of(application));

        assertThatThrownBy(() -> loanDocumentService.uploadDocument(tenantId, borrowerId, applicationId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Selected offer is required");

        verify(loanDocumentRepository, never()).save(any());
    }

    @Test
    void uploadDocument_invalidNormalizedType_throwsValidationError() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        LoanApplication application = LoanApplication.builder()
                .id(applicationId)
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .selectedOfferId(UUID.randomUUID())
                .requestedAmount(BigDecimal.valueOf(100000))
                .status(LoanApplicationStatus.OFFER_SELECTED)
                .loanState(LoanLifecycleState.OFFER_SELECTED)
                .build();

        LoanDocumentUploadRequest request = LoanDocumentUploadRequest.builder()
                .documentType("###")
                .documentReference("s3://docs/app/bank.pdf")
                .build();

        when(loanApplicationRepository.findForUpdate(applicationId, tenantId, borrowerId))
                .thenReturn(Optional.of(application));

        assertThatThrownBy(() -> loanDocumentService.uploadDocument(tenantId, borrowerId, applicationId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid documentType");

        verify(loanDocumentRepository, never()).save(any());
    }

    @Test
    void getDocumentsForBorrower_applicationNotFound_throwsNotFound() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        when(loanApplicationRepository.findByIdAndTenantIdAndBorrowerId(applicationId, tenantId, borrowerId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanDocumentService.getDocumentsForBorrower(tenantId, borrowerId, applicationId))
                .isInstanceOf(LoanApplicationNotFoundException.class);
    }

    @Test
    void validateRequiredDocumentsForDecision_midAmountMissingIncomeProof_throwsConflict() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        LoanApplication application = LoanApplication.builder()
                .id(applicationId)
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .requestedAmount(BigDecimal.valueOf(500000))
                .build();

        LoanDocument bankDetails = LoanDocument.builder()
                .documentType("bank_statement")
                .documentReference("s3://docs/app/bank.pdf")
                .build();
        when(loanDocumentRepository.findByTenantIdAndApplicationIdOrderByDocumentTypeAsc(tenantId, applicationId))
                .thenReturn(List.of(bankDetails));

        assertThatThrownBy(() -> loanDocumentService.validateRequiredDocumentsForDecision(tenantId, application))
                .isInstanceOf(UnderwriterDecisionConflictException.class)
                .hasMessageContaining("INCOME_SOURCE_PROOF");
    }

    @Test
    void validateRequiredDocumentsForDecision_highAmountWithAliases_passesValidation() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        LoanApplication application = LoanApplication.builder()
                .id(applicationId)
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .requestedAmount(BigDecimal.valueOf(900000))
                .build();

        LoanDocument bankDetails = LoanDocument.builder()
                .documentType("bank statement")
                .documentReference("s3://docs/app/bank.pdf")
                .build();
        LoanDocument incomeSource = LoanDocument.builder()
                .documentType("income_proof")
                .documentReference("s3://docs/app/income.pdf")
                .build();
        LoanDocument collateral = LoanDocument.builder()
                .documentType("property paper")
                .documentReference("s3://docs/app/land.pdf")
                .build();
        when(loanDocumentRepository.findByTenantIdAndApplicationIdOrderByDocumentTypeAsc(tenantId, applicationId))
                .thenReturn(List.of(bankDetails, incomeSource, collateral));

        assertThatCode(() -> loanDocumentService.validateRequiredDocumentsForDecision(tenantId, application))
                .doesNotThrowAnyException();
    }

    @Test
    void getDocumentsForTenant_mapsResponseList() {
        UUID tenantId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        LoanApplication application = LoanApplication.builder()
                .id(applicationId)
                .tenantId(tenantId)
                .borrowerId(UUID.randomUUID())
                .build();
        LoanDocument document = LoanDocument.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .applicationId(applicationId)
                .build();

        when(loanApplicationRepository.findByIdAndTenantId(applicationId, tenantId))
                .thenReturn(Optional.of(application));
        when(loanDocumentRepository.findByTenantIdAndApplicationIdOrderByDocumentTypeAsc(tenantId, applicationId))
                .thenReturn(List.of(document));
        when(loanDocumentMapper.toLoanDocumentResponse(document)).thenReturn(
                LoanDocumentResponse.builder().documentId(document.getId()).build()
        );

        List<LoanDocumentResponse> response = loanDocumentService.getDocumentsForTenant(tenantId, applicationId);
        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getDocumentId()).isEqualTo(document.getId());

        ArgumentCaptor<UUID> tenantCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> appCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(loanDocumentRepository).findByTenantIdAndApplicationIdOrderByDocumentTypeAsc(tenantCaptor.capture(), appCaptor.capture());
        assertThat(tenantCaptor.getValue()).isEqualTo(tenantId);
        assertThat(appCaptor.getValue()).isEqualTo(applicationId);
    }
}

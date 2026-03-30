package com.loanplatform.loan_platform.unit.borrower;

import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.model.BorrowerStatus;
import com.loanplatform.loan_platform.domain.borrower.model.KycDocument;
import com.loanplatform.loan_platform.domain.borrower.model.KycDocumentType;
import com.loanplatform.loan_platform.domain.borrower.model.KycStatus;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.domain.borrower.repository.KycDocumentRepository;
import com.loanplatform.loan_platform.domain.borrower.service.KycService;
import com.loanplatform.loan_platform.dto.request.KycDocumentInput;
import com.loanplatform.loan_platform.dto.request.KycSubmissionRequest;
import com.loanplatform.loan_platform.dto.response.KycDocumentResponse;
import com.loanplatform.loan_platform.dto.response.KycSubmissionResponse;
import com.loanplatform.loan_platform.mapper.BorrowerMapper;
import com.loanplatform.loan_platform.port.outbound.BorrowerNotificationPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycServiceTest {

    @Mock
    private BorrowerRepository borrowerRepository;

    @Mock
    private KycDocumentRepository kycDocumentRepository;

    @Mock
    private BorrowerMapper borrowerMapper;

    @Mock
    private BorrowerNotificationPort borrowerNotificationPort;

    @InjectMocks
    private KycService kycService;

    @Test
    void submitKyc_valid() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        Borrower borrower = Borrower.builder()
                .id(borrowerId)
                .tenantId(tenantId)
                .status(BorrowerStatus.UNVERIFIED)
                .build();

        KycSubmissionRequest request = KycSubmissionRequest.builder()
                .documents(List.of(
                        KycDocumentInput.builder().documentType(KycDocumentType.PAN_CARD).documentNumber("ABCDE1234F").documentReference("s3://pan").build(),
                        KycDocumentInput.builder().documentType(KycDocumentType.AADHAAR).documentNumber("123412341234").documentReference("s3://aadhaar").build()
                ))
                .build();

        when(borrowerRepository.findByIdAndTenantId(borrowerId, tenantId)).thenReturn(Optional.of(borrower));
        when(kycDocumentRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(borrowerRepository.save(any(Borrower.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(borrowerMapper.toKycDocumentResponse(any(KycDocument.class))).thenAnswer(invocation -> {
            KycDocument doc = invocation.getArgument(0);
            return KycDocumentResponse.builder()
                    .kycDocumentId(doc.getId())
                    .borrowerId(doc.getBorrowerId())
                    .documentType(doc.getDocumentType().name())
                    .status(doc.getStatus().name())
                    .submittedAt(Instant.now())
                    .build();
        });

        KycSubmissionResponse response = kycService.submit(tenantId, borrowerId, request);

        assertThat(response.getSubmittedDocs()).hasSize(2);
        assertThat(borrower.getStatus()).isEqualTo(BorrowerStatus.KYC_SUBMITTED);
        verify(borrowerNotificationPort).onKycSubmitted(borrowerId, tenantId, 2);
    }

    @Test
    void resubmitAfterRejection_archivesOldDocs() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        Borrower borrower = Borrower.builder()
                .id(borrowerId)
                .tenantId(tenantId)
                .status(BorrowerStatus.KYC_REJECTED)
                .build();

        List<KycDocument> existingDocs = List.of(
                KycDocument.builder().id(UUID.randomUUID()).tenantId(tenantId).borrowerId(borrowerId).status(KycStatus.REJECTED).archived(false).build(),
                KycDocument.builder().id(UUID.randomUUID()).tenantId(tenantId).borrowerId(borrowerId).status(KycStatus.VERIFIED).archived(false).build()
        );

        KycSubmissionRequest request = KycSubmissionRequest.builder()
                .documents(List.of(
                        KycDocumentInput.builder().documentType(KycDocumentType.PAN_CARD).documentNumber("ABCDE1234F").documentReference("s3://pan-new").build(),
                        KycDocumentInput.builder().documentType(KycDocumentType.ADDRESS_PROOF).documentNumber("ADDR-1").documentReference("s3://addr-new").build()
                ))
                .build();

        when(borrowerRepository.findByIdAndTenantId(borrowerId, tenantId)).thenReturn(Optional.of(borrower));
        when(kycDocumentRepository.findByBorrowerIdAndTenantIdAndArchivedFalse(borrowerId, tenantId)).thenReturn(existingDocs);
        when(kycDocumentRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(borrowerRepository.save(any(Borrower.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(borrowerMapper.toKycDocumentResponse(any(KycDocument.class))).thenReturn(KycDocumentResponse.builder().status("PENDING").build());

        kycService.submit(tenantId, borrowerId, request);

        ArgumentCaptor<List<KycDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(kycDocumentRepository, times(2)).saveAll(captor.capture());
        List<KycDocument> firstBatch = captor.getAllValues().get(0);
        assertThat(firstBatch).allMatch(KycDocument::isArchived);
    }

    @Test
    void submitKyc_duplicateDocumentNumberAcrossTypes_rejected() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        Borrower borrower = Borrower.builder()
                .id(borrowerId)
                .tenantId(tenantId)
                .status(BorrowerStatus.UNVERIFIED)
                .build();

        KycSubmissionRequest request = KycSubmissionRequest.builder()
                .documents(List.of(
                        KycDocumentInput.builder().documentType(KycDocumentType.PAN_CARD).documentNumber("ABCDE1234F").documentReference("s3://pan").build(),
                        KycDocumentInput.builder().documentType(KycDocumentType.AADHAAR).documentNumber("ABCDE1234F").documentReference("s3://aadhaar").build()
                ))
                .build();

        when(borrowerRepository.findByIdAndTenantId(borrowerId, tenantId)).thenReturn(Optional.of(borrower));

        assertThatThrownBy(() -> kycService.submit(tenantId, borrowerId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate documentNumber");

        verify(kycDocumentRepository, never()).saveAll(any());
    }

    @Test
    void submitKyc_whenAlreadyVerified_rejected() {
        UUID tenantId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        Borrower borrower = Borrower.builder()
                .id(borrowerId)
                .tenantId(tenantId)
                .status(BorrowerStatus.KYC_VERIFIED)
                .build();

        KycSubmissionRequest request = KycSubmissionRequest.builder()
                .documents(List.of(
                        KycDocumentInput.builder().documentType(KycDocumentType.PAN_CARD).documentNumber("ABCDE1234F").documentReference("s3://pan").build(),
                        KycDocumentInput.builder().documentType(KycDocumentType.AADHAAR).documentNumber("123412341234").documentReference("s3://aadhaar").build()
                ))
                .build();

        when(borrowerRepository.findByIdAndTenantId(borrowerId, tenantId)).thenReturn(Optional.of(borrower));

        assertThatThrownBy(() -> kycService.submit(tenantId, borrowerId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already completed");
    }
}

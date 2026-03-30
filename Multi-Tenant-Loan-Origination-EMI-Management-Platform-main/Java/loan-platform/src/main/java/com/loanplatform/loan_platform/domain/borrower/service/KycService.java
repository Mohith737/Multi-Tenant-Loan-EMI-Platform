package com.loanplatform.loan_platform.domain.borrower.service;

import com.loanplatform.loan_platform.domain.borrower.model.Borrower;
import com.loanplatform.loan_platform.domain.borrower.model.BorrowerStatus;
import com.loanplatform.loan_platform.domain.borrower.model.KycDocument;
import com.loanplatform.loan_platform.domain.borrower.model.KycDocumentType;
import com.loanplatform.loan_platform.domain.borrower.model.KycStatus;
import com.loanplatform.loan_platform.domain.borrower.repository.BorrowerRepository;
import com.loanplatform.loan_platform.domain.borrower.repository.KycDocumentRepository;
import com.loanplatform.loan_platform.dto.request.KycDocumentInput;
import com.loanplatform.loan_platform.dto.request.KycSubmissionRequest;
import com.loanplatform.loan_platform.dto.request.KycVerificationDecision;
import com.loanplatform.loan_platform.dto.request.KycVerificationRequest;
import com.loanplatform.loan_platform.dto.response.KycDocumentResponse;
import com.loanplatform.loan_platform.dto.response.KycSubmissionResponse;
import com.loanplatform.loan_platform.exception.BorrowerNotFoundException;
import com.loanplatform.loan_platform.exception.KycDocumentNotFoundException;
import com.loanplatform.loan_platform.exception.TenantIsolationViolationException;
import com.loanplatform.loan_platform.mapper.BorrowerMapper;
import com.loanplatform.loan_platform.multitenancy.TenantAware;
import com.loanplatform.loan_platform.port.outbound.BorrowerNotificationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class KycService {

    private static final Pattern PAN_PATTERN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");
    private static final Pattern AADHAAR_PATTERN = Pattern.compile("^[0-9]{12}$");

    private final BorrowerRepository borrowerRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final BorrowerMapper borrowerMapper;
    private final BorrowerNotificationPort borrowerNotificationPort;

    @TenantAware
    @Transactional
    public KycSubmissionResponse submit(UUID tenantId, UUID borrowerId, KycSubmissionRequest request) {
        Borrower borrower = borrowerRepository.findByIdAndTenantId(borrowerId, tenantId)
                .orElseThrow(() -> new BorrowerNotFoundException("Borrower not found: " + borrowerId));

        validateSubmissionState(borrower.getStatus());
        validateRequiredDocumentSet(request.getDocuments());

        if (borrower.getStatus() == BorrowerStatus.KYC_REJECTED) {
            List<KycDocument> existingDocs = kycDocumentRepository.findByBorrowerIdAndTenantIdAndArchivedFalse(borrowerId, tenantId);
            existingDocs.forEach(document -> document.setArchived(true));
            kycDocumentRepository.saveAll(existingDocs);
        }

        List<KycDocument> submittedDocs = request.getDocuments().stream()
                .map(input -> buildKycDocument(tenantId, borrowerId, input))
                .toList();

        List<KycDocument> savedDocs = kycDocumentRepository.saveAll(submittedDocs);
        borrower.setStatus(BorrowerStatus.KYC_SUBMITTED);
        borrowerRepository.save(borrower);

        borrowerNotificationPort.onKycSubmitted(borrowerId, tenantId, savedDocs.size());

        return KycSubmissionResponse.builder()
                .submittedDocs(savedDocs.stream().map(borrowerMapper::toKycDocumentResponse).toList())
                .build();
    }

    @TenantAware
    @Transactional
    public KycDocumentResponse verify(UUID adminTenantId, UUID kycDocumentId, KycVerificationRequest request, String verifiedBy) {
        KycDocument kycDocument = kycDocumentRepository.findById(kycDocumentId)
                .orElseThrow(() -> new KycDocumentNotFoundException("KYC document not found: " + kycDocumentId));

        Borrower borrower = borrowerRepository.findById(kycDocument.getBorrowerId())
                .orElseThrow(() -> new BorrowerNotFoundException("Borrower not found: " + kycDocument.getBorrowerId()));

        if (!adminTenantId.equals(borrower.getTenantId()) || !adminTenantId.equals(kycDocument.getTenantId())) {
            throw new TenantIsolationViolationException("Tenant isolation violation for KYC verification");
        }
        if (kycDocument.isArchived()) {
            throw new IllegalArgumentException("Archived KYC document cannot be verified");
        }
        if (kycDocument.getStatus() != KycStatus.PENDING) {
            throw new IllegalArgumentException("KYC document is not in PENDING state");
        }

        if (request.getDecision() == KycVerificationDecision.VERIFIED) {
            kycDocument.setStatus(KycStatus.VERIFIED);
            kycDocument.setRejectionReason(null);
        } else {
            kycDocument.setStatus(KycStatus.REJECTED);
            kycDocument.setRejectionReason(request.getRejectionReason());
        }
        kycDocument.setVerifiedBy(verifiedBy);
        kycDocument.setVerifiedAt(Instant.now());

        KycDocument updatedDoc = kycDocumentRepository.save(kycDocument);

        List<KycDocument> activeDocs = kycDocumentRepository.findByBorrowerIdAndTenantIdAndArchivedFalse(
                borrower.getId(),
                borrower.getTenantId()
        );

        boolean anyRejected = activeDocs.stream().anyMatch(document -> document.getStatus() == KycStatus.REJECTED);
        boolean allVerified = !activeDocs.isEmpty() && activeDocs.stream().allMatch(document -> document.getStatus() == KycStatus.VERIFIED);

        if (anyRejected) {
            borrower.setStatus(BorrowerStatus.KYC_REJECTED);
            borrowerRepository.save(borrower);
            String reason = activeDocs.stream()
                    .filter(document -> document.getStatus() == KycStatus.REJECTED)
                    .map(KycDocument::getRejectionReason)
                    .findFirst()
                    .orElse("KYC rejected");
            borrowerNotificationPort.onKycRejected(borrower.getId(), borrower.getTenantId(), reason);
        } else if (allVerified && borrower.getStatus() != BorrowerStatus.CREDIT_PROFILE_COMPLETE) {
            borrower.setStatus(BorrowerStatus.KYC_VERIFIED);
            borrowerRepository.save(borrower);
            borrowerNotificationPort.onKycVerified(borrower.getId(), borrower.getTenantId());
        }

        return borrowerMapper.toKycDocumentResponse(updatedDoc);
    }

    @TenantAware
    @Transactional(readOnly = true)
    public List<KycDocumentResponse> listPending(UUID tenantId) {
        return kycDocumentRepository.findByTenantIdAndStatusAndArchivedFalseOrderByCreatedAtAsc(tenantId, KycStatus.PENDING)
                .stream()
                .map(borrowerMapper::toKycDocumentResponse)
                .toList();
    }

    private KycDocument buildKycDocument(UUID tenantId, UUID borrowerId, KycDocumentInput input) {
        String normalizedNumber = normalizeDocumentNumber(input.getDocumentType(), input.getDocumentNumber());
        return KycDocument.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .borrowerId(borrowerId)
                .documentType(input.getDocumentType())
                .documentNumber(normalizedNumber)
                .documentReference(input.getDocumentReference())
                .status(KycStatus.PENDING)
                .archived(false)
                .build();
    }

    private void validateRequiredDocumentSet(List<KycDocumentInput> documents) {
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("At least one KYC document is required");
        }

        Set<KycDocumentType> seenTypes = new HashSet<>();
        Set<String> seenDocumentNumbers = new HashSet<>();

        boolean hasPan = documents.stream().anyMatch(document -> document.getDocumentType() == KycDocumentType.PAN_CARD);
        boolean hasAadhaar = documents.stream().anyMatch(document -> document.getDocumentType() == KycDocumentType.AADHAAR);
        boolean hasAddressProof = documents.stream().anyMatch(document -> document.getDocumentType() == KycDocumentType.ADDRESS_PROOF);

        for (KycDocumentInput document : documents) {
            if (!seenTypes.add(document.getDocumentType())) {
                throw new IllegalArgumentException("Duplicate documentType in KYC submission: " + document.getDocumentType());
            }

            String normalizedNumber = normalizeDocumentNumber(document.getDocumentType(), document.getDocumentNumber());
            if (!seenDocumentNumbers.add(normalizedNumber)) {
                throw new IllegalArgumentException("Duplicate documentNumber in KYC submission is not allowed");
            }

            validateDocumentNumberByType(document.getDocumentType(), normalizedNumber);
        }

        if (!hasPan) {
            throw new IllegalArgumentException("PAN_CARD document is mandatory");
        }
        if (!hasAadhaar && !hasAddressProof) {
            throw new IllegalArgumentException("At least one of AADHAAR or ADDRESS_PROOF is mandatory");
        }
    }

    private void validateSubmissionState(BorrowerStatus status) {
        if (status == BorrowerStatus.KYC_SUBMITTED) {
            throw new IllegalArgumentException("KYC is already submitted and pending verification");
        }
        if (status == BorrowerStatus.KYC_VERIFIED || status == BorrowerStatus.CREDIT_PROFILE_COMPLETE) {
            throw new IllegalArgumentException("KYC is already completed for borrower");
        }
    }

    private String normalizeDocumentNumber(KycDocumentType documentType, String documentNumber) {
        if (documentNumber == null) {
            return null;
        }
        String normalized = documentNumber.trim();
        if (documentType == KycDocumentType.PAN_CARD) {
            return normalized.toUpperCase(Locale.ROOT);
        }
        return normalized;
    }

    private void validateDocumentNumberByType(KycDocumentType documentType, String normalizedNumber) {
        if (documentType == KycDocumentType.PAN_CARD && !PAN_PATTERN.matcher(normalizedNumber).matches()) {
            throw new IllegalArgumentException("PAN_CARD documentNumber must match standard PAN format");
        }
        if (documentType == KycDocumentType.AADHAAR && !AADHAAR_PATTERN.matcher(normalizedNumber).matches()) {
            throw new IllegalArgumentException("AADHAAR documentNumber must be 12 digits");
        }
    }
}

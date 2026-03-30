package com.loanplatform.loan_platform.mapper;

import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanDocument;
import com.loanplatform.loan_platform.domain.loan.model.UnderwriterDecision;
import com.loanplatform.loan_platform.dto.response.LoanDocumentResponse;
import com.loanplatform.loan_platform.dto.response.UnderwriterDecisionResponse;
import com.loanplatform.loan_platform.dto.response.UnderwriterQueueItemResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LoanDocumentMapper {

    @Mapping(target = "documentId", source = "id")
    @Mapping(target = "status", expression = "java(document.getStatus().name())")
    LoanDocumentResponse toLoanDocumentResponse(LoanDocument document);

    @Mapping(target = "applicationId", source = "application.id")
    @Mapping(target = "borrowerId", source = "application.borrowerId")
    @Mapping(target = "documentsSubmitted", source = "documentsSubmitted")
    @Mapping(target = "status", expression = "java(application.getStatus().name())")
    @Mapping(target = "loanState", expression = "java(application.getLoanState().name())")
    UnderwriterQueueItemResponse toUnderwriterQueueItemResponse(LoanApplication application, int documentsSubmitted);

    @Mapping(target = "applicationId", source = "application.id")
    @Mapping(target = "decision", expression = "java(decision.getDecision().name())")
    @Mapping(target = "status", expression = "java(application.getStatus().name())")
    @Mapping(target = "loanState", expression = "java(application.getLoanState().name())")
    @Mapping(target = "mambuLoanId", source = "application.mambuLoanId")
    @Mapping(target = "rejectionReason", source = "application.rejectionReason")
    @Mapping(target = "decidedAt", source = "decision.decidedAt")
    UnderwriterDecisionResponse toUnderwriterDecisionResponse(LoanApplication application, UnderwriterDecision decision);
}

package com.loanplatform.loan_platform.mapper;

import com.loanplatform.loan_platform.domain.borrower.model.CreditProfile;
import com.loanplatform.loan_platform.domain.borrower.model.KycDocument;
import com.loanplatform.loan_platform.dto.response.CreditProfileResponse;
import com.loanplatform.loan_platform.dto.response.KycDocumentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BorrowerMapper {

    @Mapping(target = "kycDocumentId", source = "id")
    @Mapping(target = "documentType", expression = "java(kycDocument.getDocumentType().name())")
    @Mapping(target = "status", expression = "java(kycDocument.getStatus().name())")
    @Mapping(target = "submittedAt", source = "createdAt")
    KycDocumentResponse toKycDocumentResponse(KycDocument kycDocument);

    @Mapping(target = "creditProfileId", source = "id")
    @Mapping(target = "creditBureau", source = "creditBureau")
    CreditProfileResponse toCreditProfileResponse(CreditProfile creditProfile);
}

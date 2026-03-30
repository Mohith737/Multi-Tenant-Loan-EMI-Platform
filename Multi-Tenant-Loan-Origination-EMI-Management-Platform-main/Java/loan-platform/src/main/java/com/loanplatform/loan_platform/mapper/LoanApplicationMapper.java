package com.loanplatform.loan_platform.mapper;

import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanOffer;
import com.loanplatform.loan_platform.dto.response.LoanApplicationResponse;
import com.loanplatform.loan_platform.dto.response.LoanOfferResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface LoanApplicationMapper {

    @Mapping(target = "applicationId", source = "id")
    @Mapping(target = "status", expression = "java(application.getStatus().name())")
    @Mapping(target = "loanState", expression = "java(application.getLoanState().name())")
    @Mapping(target = "eligibilityResult", source = "application")
    @Mapping(target = "offers", ignore = true)
    LoanApplicationResponse toLoanApplicationResponse(LoanApplication application);

    @Mapping(target = "eligible", source = "eligibilityPassed")
    @Mapping(target = "riskCategory", source = "riskCategory")
    @Mapping(target = "maxApprovedAmount", source = "maxApprovedAmountSnapshot")
    LoanApplicationResponse.EligibilityResult toEligibilityResult(LoanApplication application);

    @Mapping(target = "offerId", source = "id")
    @Mapping(target = "requestedAmount", source = "principalAmount")
    @Mapping(target = "processingFee", source = "processingFeeAmount")
    @Mapping(target = "totalPayable", source = "totalPayableAmount")
    @Mapping(target = "status", expression = "java(offer.getStatus().name())")
    LoanOfferResponse toLoanOfferResponse(LoanOffer offer);

    List<LoanOfferResponse> toLoanOfferResponses(List<LoanOffer> offers);
}

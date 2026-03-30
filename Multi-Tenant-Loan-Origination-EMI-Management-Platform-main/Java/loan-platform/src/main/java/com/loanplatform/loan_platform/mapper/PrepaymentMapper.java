package com.loanplatform.loan_platform.mapper;

import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.PrepaymentRequest;
import com.loanplatform.loan_platform.dto.response.PrepaymentAdminViewResponse;
import com.loanplatform.loan_platform.dto.response.PrepaymentExecutionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PrepaymentMapper {

    @Mapping(target = "requestId", source = "request.id")
    @Mapping(target = "applicationId", source = "request.loanApplicationId")
    @Mapping(target = "loanAccountId", source = "application.mambuLoanId")
    @Mapping(target = "prepaymentType", source = "request.prepaymentType")
    @Mapping(target = "prepaymentOption", source = "request.prepaymentOption")
    @Mapping(target = "requestedAmount", source = "request.requestedAmount")
    @Mapping(target = "stripePaymentIntentId", source = "request.stripePaymentIntentId")
    @Mapping(target = "stripeStatus", source = "request.stripeStatus")
    @Mapping(target = "mambuTransactionId", source = "request.mambuTransactionId")
    @Mapping(target = "status", source = "request.status")
    @Mapping(target = "loanState", expression = "java(application.getLoanState().name())")
    @Mapping(target = "nocAvailable", expression = "java(application.getForeclosedAt() != null)")
    @Mapping(target = "message", ignore = true)
    @Mapping(target = "createdAt", source = "request.createdAt")
    @Mapping(target = "updatedAt", source = "request.updatedAt")
    PrepaymentExecutionResponse toExecutionResponse(PrepaymentRequest request, LoanApplication application);

    @Mapping(target = "requestId", source = "request.id")
    @Mapping(target = "applicationId", source = "request.loanApplicationId")
    @Mapping(target = "loanAccountId", source = "application.mambuLoanId")
    @Mapping(target = "borrowerId", source = "request.borrowerId")
    @Mapping(target = "prepaymentType", source = "request.prepaymentType")
    @Mapping(target = "prepaymentOption", source = "request.prepaymentOption")
    @Mapping(target = "requestedAmount", source = "request.requestedAmount")
    @Mapping(target = "status", source = "request.status")
    @Mapping(target = "stripeStatus", source = "request.stripeStatus")
    @Mapping(target = "mambuTransactionId", source = "request.mambuTransactionId")
    @Mapping(target = "createdAt", source = "request.createdAt")
    @Mapping(target = "updatedAt", source = "request.updatedAt")
    PrepaymentAdminViewResponse.Item toAdminItem(PrepaymentRequest request, LoanApplication application);
}

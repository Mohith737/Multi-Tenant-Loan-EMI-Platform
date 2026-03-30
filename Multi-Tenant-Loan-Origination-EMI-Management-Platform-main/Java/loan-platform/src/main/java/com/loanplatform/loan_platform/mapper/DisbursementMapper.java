package com.loanplatform.loan_platform.mapper;

import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallment;
import com.loanplatform.loan_platform.dto.response.DisbursementStatusResponse;
import com.loanplatform.loan_platform.dto.response.LoanDisbursementResponse;
import com.loanplatform.loan_platform.dto.response.LoanScheduleResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DisbursementMapper {

    @Mapping(target = "applicationId", source = "id")
    @Mapping(target = "status", expression = "java(application.getStatus().name())")
    @Mapping(target = "loanState", expression = "java(application.getLoanState().name())")
    @Mapping(target = "disbursementStatus", expression = "java(application.getDisbursementStatus() == null ? null : application.getDisbursementStatus().name())")
    @Mapping(target = "failureReason", source = "disbursementFailureReason")
    DisbursementStatusResponse toDisbursementStatusResponse(LoanApplication application);

    @Mapping(target = "applicationId", source = "id")
    @Mapping(target = "status", expression = "java(application.getStatus().name())")
    @Mapping(target = "loanState", expression = "java(application.getLoanState().name())")
    @Mapping(target = "disbursementStatus", expression = "java(application.getDisbursementStatus() == null ? null : application.getDisbursementStatus().name())")
    @Mapping(target = "totalInstallments", ignore = true)
    LoanDisbursementResponse toLoanDisbursementResponse(LoanApplication application);

    @Mapping(target = "installmentNumber", source = "installmentNumber")
    @Mapping(target = "status", expression = "java(installment.getStatus().name())")
    LoanScheduleResponse.InstallmentItem toInstallmentItem(LoanInstallment installment);
}

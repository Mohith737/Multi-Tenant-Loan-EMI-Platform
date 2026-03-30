package com.loanplatform.loan_platform.mapper;

import com.loanplatform.loan_platform.domain.tenant.model.LoanProduct;
import com.loanplatform.loan_platform.domain.tenant.model.LoanProductConfigSnapshot;
import com.loanplatform.loan_platform.domain.tenant.model.Tenant;
import com.loanplatform.loan_platform.dto.request.LoanProductConfigRequest;
import com.loanplatform.loan_platform.dto.response.LoanProductResponse;
import com.loanplatform.loan_platform.dto.response.PublicTenantBrowseResponse;
import com.loanplatform.loan_platform.dto.response.PublicTenantProductResponse;
import com.loanplatform.loan_platform.dto.response.TenantRegistrationResponse;
import com.loanplatform.loan_platform.dto.response.TenantResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TenantMapper {

    @Mapping(target = "tenantId", source = "id")
    @Mapping(target = "status", expression = "java(tenant.getStatus().name())")
    @Mapping(target = "mambuBranchKey", source = "mambuBranchEncodedKey")
    TenantResponse toTenantResponse(Tenant tenant);

    @Mapping(target = "tenantId", source = "id")
    @Mapping(target = "status", expression = "java(tenant.getStatus().name())")
    @Mapping(target = "mambuBranchKey", source = "mambuBranchEncodedKey")
    TenantRegistrationResponse toTenantRegistrationResponse(Tenant tenant);

    @Mapping(target = "loanProductId", source = "id")
    LoanProductResponse toLoanProductResponse(LoanProduct loanProduct);

    LoanProductConfigRequest toLoanProductConfigRequest(LoanProductConfigSnapshot snapshot);

    @Mapping(target = "tenantId", source = "id")
    @Mapping(target = "tenantName", source = "name")
    @Mapping(target = "loanProducts", ignore = true)
    PublicTenantBrowseResponse toPublicTenantBrowseResponse(Tenant tenant);

    @Mapping(target = "productName", source = "loanProductConfig.productName")
    @Mapping(target = "interestRate", source = "loanProductConfig.annualInterestRate")
    @Mapping(target = "minAmount", source = "loanProductConfig.minLoanAmount")
    @Mapping(target = "maxAmount", source = "loanProductConfig.maxLoanAmount")
    @Mapping(target = "minTenure", source = "loanProductConfig.minTenureMonths")
    @Mapping(target = "maxTenure", source = "loanProductConfig.maxTenureMonths")
    PublicTenantProductResponse toPublicTenantProductResponse(LoanProduct loanProduct);
}

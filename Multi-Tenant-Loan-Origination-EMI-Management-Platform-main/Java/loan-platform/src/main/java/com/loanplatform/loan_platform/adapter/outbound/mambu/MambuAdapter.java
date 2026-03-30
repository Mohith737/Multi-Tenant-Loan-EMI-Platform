package com.loanplatform.loan_platform.adapter.outbound.mambu;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuBranchCreateRequest;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuBranchResponse;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuClientCreateRequest;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuClientResponse;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuEarlyRepaymentPreviewResponse;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanAccountResponse;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanDisbursementRequest;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanDisbursementResponse;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanApproveRequest;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanCreateRequest;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuRepaymentRequest;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuRepaymentResponse;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuRepaymentScheduleResponse;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanSimulationRequest;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanSimulationResponse;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanProductCreateRequest;
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanProductResponse;
import com.loanplatform.loan_platform.dto.request.LoanProductConfigRequest;
import com.loanplatform.loan_platform.port.outbound.MambuPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MambuAdapter implements MambuPort {

    private final MambuClient mambuClient;

    @Override
    public MambuBranchResponse createBranch(String tenantId, String name, String adminEmail, String adminPhone) {
        String compactTenantId = tenantId.replace("-", "");
        String branchId = "branch_" + compactTenantId.substring(0, Math.min(compactTenantId.length(), 8)).toLowerCase(Locale.ROOT);

        MambuBranchCreateRequest request = MambuBranchCreateRequest.builder()
                .name(name + " - Main Branch")
                .id(branchId)
                .state("ACTIVE")
                .address(MambuBranchCreateRequest.Address.builder()
                        .country("IN")
                        .city("Bengaluru")
                        .build())
                .emailAddress(adminEmail)
                .phoneNumber(adminPhone)
                .notes("Tenant: " + name)
                .build();

        return mambuClient.createBranch(request);
    }

    @Override
    public MambuLoanProductResponse createLoanProduct(String branchEncodedKey, LoanProductConfigRequest config) {
        String productId = "LP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);

        MambuLoanProductCreateRequest request = MambuLoanProductCreateRequest.builder()
                .name(config.getProductName())
                .id(productId)
                .type("FIXED_TERM_LOAN")
                .loanAmountSettings(MambuLoanProductCreateRequest.LoanAmountSettings.builder()
                        .defaultAmount(new MambuLoanProductCreateRequest.AmountValue(config.getMinLoanAmount().add(config.getMaxLoanAmount()).divide(BigDecimal.valueOf(2))))
                        .minAmount(new MambuLoanProductCreateRequest.AmountValue(config.getMinLoanAmount()))
                        .maxAmount(new MambuLoanProductCreateRequest.AmountValue(config.getMaxLoanAmount()))
                        .build())
                .scheduleSettings(MambuLoanProductCreateRequest.ScheduleSettings.builder()
                        .defaultRepaymentPeriodCount(config.getMaxTenureMonths())
                        .defaultRepaymentPeriodUnit("MONTHS")
                        .repaymentScheduleMethod("STANDARD")
                        .build())
                .interestSettings(MambuLoanProductCreateRequest.InterestSettings.builder()
                        .defaultInterestRate(new MambuLoanProductCreateRequest.AmountValue(config.getAnnualInterestRate()))
                        .interestChargeFrequency("ANNUALIZED")
                        .interestCalculationMethod("DECLINING_BALANCE")
                        .build())
                .currency(new MambuLoanProductCreateRequest.Currency(config.getCurrency()))
                .forBranchKey(branchEncodedKey)
                .state("ACTIVE")
                .build();

        return mambuClient.createLoanProduct(request);
    }

    @Override
    public MambuClientResponse createClient(MambuClientCreateRequest request) {
        return mambuClient.createClient(request);
    }

    @Override
    public MambuLoanSimulationResponse simulateLoan(MambuLoanSimulationRequest request) {
        return mambuClient.simulateLoan(request);
    }

    @Override
    public MambuLoanAccountResponse createLoanAccount(MambuLoanCreateRequest request) {
        return mambuClient.createLoanAccount(request);
    }

    @Override
    public MambuLoanAccountResponse approveLoanAccount(String loanId, MambuLoanApproveRequest request) {
        return mambuClient.approveLoanAccount(loanId, request);
    }

    @Override
    public MambuLoanDisbursementResponse disburseLoan(String loanId, MambuLoanDisbursementRequest request) {
        return mambuClient.disburseLoan(loanId, request);
    }

    @Override
    public MambuRepaymentResponse postRepayment(String loanId, MambuRepaymentRequest request) {
        return mambuClient.postRepayment(loanId, request);
    }

    @Override
    public MambuRepaymentScheduleResponse getRepaymentSchedule(String loanId) {
        return mambuClient.getRepaymentSchedule(loanId);
    }

    @Override
    public MambuEarlyRepaymentPreviewResponse previewEarlyRepayment(String loanId, BigDecimal amount, LocalDate paymentDate) {
        return mambuClient.previewEarlyRepayment(loanId, amount, paymentDate == null ? null : paymentDate.toString());
    }

    @Override
    public MambuLoanAccountResponse getLoanAccount(String loanId) {
        return mambuClient.getLoanAccount(loanId);
    }

    @Override
    public MambuLoanAccountResponse patchLoanState(String loanId, String loanState, String notes) {
        String normalizedNotes = notes == null ? "" : notes;
        return mambuClient.patchLoanState(loanId, Map.of(
                "loanState", loanState,
                "notes", normalizedNotes,
                "notesArray", java.util.List.of(java.util.Map.of(
                        "notes", normalizedNotes,
                        "creationDate", LocalDate.now().toString()
                ))
        ));
    }
}

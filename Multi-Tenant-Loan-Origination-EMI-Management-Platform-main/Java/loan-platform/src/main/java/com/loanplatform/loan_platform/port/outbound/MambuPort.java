package com.loanplatform.loan_platform.port.outbound;

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
import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuLoanProductResponse;
import com.loanplatform.loan_platform.dto.request.LoanProductConfigRequest;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface MambuPort {
    MambuBranchResponse createBranch(String tenantId, String name, String adminEmail, String adminPhone);

    MambuLoanProductResponse createLoanProduct(String branchEncodedKey, LoanProductConfigRequest config);

    MambuClientResponse createClient(MambuClientCreateRequest request);

    MambuLoanSimulationResponse simulateLoan(MambuLoanSimulationRequest request);

    MambuLoanAccountResponse createLoanAccount(MambuLoanCreateRequest request);

    MambuLoanAccountResponse approveLoanAccount(String loanId, MambuLoanApproveRequest request);

    MambuLoanDisbursementResponse disburseLoan(String loanId, MambuLoanDisbursementRequest request);

    MambuRepaymentResponse postRepayment(String loanId, MambuRepaymentRequest request);

    MambuRepaymentScheduleResponse getRepaymentSchedule(String loanId);

    MambuEarlyRepaymentPreviewResponse previewEarlyRepayment(String loanId, BigDecimal amount, LocalDate paymentDate);

    MambuLoanAccountResponse getLoanAccount(String loanId);

    MambuLoanAccountResponse patchLoanState(String loanId, String loanState, String notes);
}

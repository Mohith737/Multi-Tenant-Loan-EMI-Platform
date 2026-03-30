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
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "mambuClient", url = "${mambu.base-url}")
public interface MambuClient {

    @PostMapping("/api/v2/branches")
    MambuBranchResponse createBranch(@RequestBody MambuBranchCreateRequest request);

    @PostMapping("/api/v2/loanproducts")
    MambuLoanProductResponse createLoanProduct(@RequestBody MambuLoanProductCreateRequest request);

    @PostMapping("/api/v2/clients")
    MambuClientResponse createClient(@RequestBody MambuClientCreateRequest request);

    @PostMapping("/api/v2/loans:simulate")
    MambuLoanSimulationResponse simulateLoan(@RequestBody MambuLoanSimulationRequest request);

    @PostMapping("/api/v2/loans")
    MambuLoanAccountResponse createLoanAccount(@RequestBody MambuLoanCreateRequest request);

    @PostMapping("/api/v2/loans/{loanId}/approve")
    MambuLoanAccountResponse approveLoanAccount(@PathVariable("loanId") String loanId,
                                                @RequestBody MambuLoanApproveRequest request);

    @PostMapping("/api/v2/loans/{loanId}/disbursement")
    MambuLoanDisbursementResponse disburseLoan(@PathVariable("loanId") String loanId,
                                               @RequestBody MambuLoanDisbursementRequest request);

    @PostMapping("/api/v2/loans/{loanId}/repayments")
    MambuRepaymentResponse postRepayment(@PathVariable("loanId") String loanId,
                                         @RequestBody MambuRepaymentRequest request);

    @GetMapping("/api/v2/loans/{loanId}/schedule")
    MambuRepaymentScheduleResponse getRepaymentSchedule(@PathVariable("loanId") String loanId);

    @GetMapping("/api/v2/loans/{loanId}/preview-early-repayment")
    MambuEarlyRepaymentPreviewResponse previewEarlyRepayment(
            @PathVariable("loanId") String loanId,
            @RequestParam(value = "amount", required = false) java.math.BigDecimal amount,
            @RequestParam(value = "paymentDate", required = false) String paymentDate
    );

    @GetMapping("/api/v2/loans/{loanId}")
    MambuLoanAccountResponse getLoanAccount(@PathVariable("loanId") String loanId);

    @PutMapping("/api/v2/loans/{loanId}")
    MambuLoanAccountResponse patchLoanState(@PathVariable("loanId") String loanId,
                                            @RequestBody java.util.Map<String, Object> request);
}

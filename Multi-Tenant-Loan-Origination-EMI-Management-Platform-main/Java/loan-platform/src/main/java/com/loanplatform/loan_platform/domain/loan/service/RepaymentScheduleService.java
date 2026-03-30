package com.loanplatform.loan_platform.domain.loan.service;

import com.loanplatform.loan_platform.adapter.outbound.mambu.dto.MambuRepaymentScheduleResponse;
import com.loanplatform.loan_platform.domain.loan.model.LoanApplication;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallment;
import com.loanplatform.loan_platform.domain.loan.model.LoanInstallmentStatus;
import com.loanplatform.loan_platform.domain.loan.repository.LoanInstallmentRepository;
import com.loanplatform.loan_platform.exception.RepaymentSchedulePersistenceException;
import com.loanplatform.loan_platform.multitenancy.TenantAware;
import com.loanplatform.loan_platform.port.outbound.MambuPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepaymentScheduleService {

    private final MambuPort mambuPort;
    private final LoanInstallmentRepository loanInstallmentRepository;

    @TenantAware
    @Transactional(
            propagation = Propagation.REQUIRED,
            noRollbackFor = RepaymentSchedulePersistenceException.class
    )
    public List<LoanInstallment> fetchAndPersistSchedule(LoanApplication application) {
        try {
            MambuRepaymentScheduleResponse response = mambuPort.getRepaymentSchedule(application.getMambuLoanId());
            if (response == null || response.getInstallments() == null || response.getInstallments().isEmpty()) {
                throw new IllegalStateException("Mambu repayment schedule response is empty");
            }

            loanInstallmentRepository.deleteByTenantIdAndApplicationId(application.getTenantId(), application.getId());

            List<LoanInstallment> installments = new ArrayList<>();
            for (MambuRepaymentScheduleResponse.Installment installment : response.getInstallments()) {
                installments.add(LoanInstallment.builder()
                        .id(UUID.randomUUID())
                        .tenantId(application.getTenantId())
                        .applicationId(application.getId())
                        .borrowerId(application.getBorrowerId())
                        .mambuLoanId(application.getMambuLoanId())
                        .installmentNumber(installment.getNumber())
                        .dueDate(LocalDate.parse(installment.getDueDate()))
                        .principalAmount(resolvePrincipal(installment))
                        .interestAmount(resolveInterest(installment))
                        .totalDue(resolveTotalDue(installment))
                        .status(LoanInstallmentStatus.PENDING)
                        .mambuInstallmentState(installment.getState())
                        .lastPaidDate(resolveLastPaidDate(installment.getLastPaidDate()))
                        .build());
            }

            return loanInstallmentRepository.saveAll(installments);
        } catch (Exception ex) {
            throw new RepaymentSchedulePersistenceException(
                    "Unable to fetch/persist repayment schedule for application " + application.getId(),
                    ex
            );
        }
    }

    private BigDecimal resolvePrincipal(MambuRepaymentScheduleResponse.Installment installment) {
        if (installment.getPrincipal() == null) {
            return BigDecimal.ZERO;
        }
        if (installment.getPrincipal().getAmount() != null && installment.getPrincipal().getAmount().getValue() != null) {
            return installment.getPrincipal().getAmount().getValue();
        }
        if (installment.getPrincipal().getDue() != null && installment.getPrincipal().getDue().getValue() != null) {
            return installment.getPrincipal().getDue().getValue();
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal resolveInterest(MambuRepaymentScheduleResponse.Installment installment) {
        if (installment.getInterest() == null) {
            return BigDecimal.ZERO;
        }
        if (installment.getInterest().getAmount() != null && installment.getInterest().getAmount().getValue() != null) {
            return installment.getInterest().getAmount().getValue();
        }
        if (installment.getInterest().getDue() != null && installment.getInterest().getDue().getValue() != null) {
            return installment.getInterest().getDue().getValue();
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal resolveTotalDue(MambuRepaymentScheduleResponse.Installment installment) {
        if (installment.getTotalDue() != null && installment.getTotalDue().getValue() != null) {
            return installment.getTotalDue().getValue();
        }
        return resolvePrincipal(installment).add(resolveInterest(installment));
    }

    private LocalDate resolveLastPaidDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }
}

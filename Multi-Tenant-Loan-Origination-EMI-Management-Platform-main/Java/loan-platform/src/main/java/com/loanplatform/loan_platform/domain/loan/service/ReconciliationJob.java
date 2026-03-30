package com.loanplatform.loan_platform.domain.loan.service;

import com.loanplatform.loan_platform.domain.loan.model.ReconciliationReport;
import com.loanplatform.loan_platform.domain.loan.repository.EmiPaymentAttemptRepository;
import com.loanplatform.loan_platform.domain.loan.repository.LoanInstallmentRepository;
import com.loanplatform.loan_platform.domain.loan.repository.ReconciliationReportRepository;
import com.loanplatform.loan_platform.domain.tenant.model.Tenant;
import com.loanplatform.loan_platform.domain.tenant.model.TenantStatus;
import com.loanplatform.loan_platform.domain.tenant.repository.TenantRepository;
import com.loanplatform.loan_platform.port.outbound.KafkaEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationJob {

    private final TenantRepository tenantRepository;
    private final LoanInstallmentRepository loanInstallmentRepository;
    private final EmiPaymentAttemptRepository emiPaymentAttemptRepository;
    private final ReconciliationReportRepository reconciliationReportRepository;
    private final KafkaEventPort kafkaEventPort;

    @Scheduled(cron = "${app.flow6.scheduler.reconciliation-cron:0 0 23 * * *}", zone = "${app.flow6.scheduler.zone:Asia/Kolkata}")
    public void runNightlyReconciliation() {
        LocalDate reportDate = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        List<Tenant> tenants = tenantRepository.findByStatusAndStripeAccountIdIsNotNullOrderByCreatedAtDesc(TenantStatus.ACTIVE);

        for (Tenant tenant : tenants) {
            try {
                generateForTenant(tenant.getId(), reportDate);
            } catch (Exception ex) {
                log.error("Flow6 reconciliation failed tenantId={} reportDate={}", tenant.getId(), reportDate, ex);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void generateForTenant(UUID tenantId, LocalDate reportDate) {
        Instant dayStart = reportDate.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant();
        Instant dayEnd = reportDate.plusDays(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant();

        long totalDueCount = loanInstallmentRepository.countDueByTenantAndDate(tenantId, reportDate);
        long totalCollectedCount = loanInstallmentRepository.countPaidByTenantAndDate(tenantId, reportDate);
        long totalFailedCount = loanInstallmentRepository.countFailedByTenantAndDate(tenantId, reportDate);
        BigDecimal totalDueAmount = loanInstallmentRepository.sumTotalDueByTenantAndDate(tenantId, reportDate);
        BigDecimal totalCollectedAmount = loanInstallmentRepository.sumPaidAmountByTenantAndDate(tenantId, reportDate);
        BigDecimal mambuPostedAmount = emiPaymentAttemptRepository.sumMambuPostedAmountForTenantBetween(tenantId, dayStart, dayEnd);

        BigDecimal discrepancy = totalCollectedAmount.subtract(mambuPostedAmount);

        ReconciliationReport report = reconciliationReportRepository.findByTenantIdAndReportDate(tenantId, reportDate)
                .orElseGet(() -> ReconciliationReport.builder()
                        .id(UUID.randomUUID())
                        .tenantId(tenantId)
                        .reportDate(reportDate)
                        .build());

        report.setTotalDueCount((int) totalDueCount);
        report.setTotalCollectedCount((int) totalCollectedCount);
        report.setTotalFailedCount((int) totalFailedCount);
        report.setTotalAmountDue(totalDueAmount);
        report.setTotalAmountCollected(totalCollectedAmount);
        report.setMambuTotalPosted(mambuPostedAmount);
        report.setDiscrepancyAmount(discrepancy);
        report.setReconciled(discrepancy.compareTo(BigDecimal.ZERO) == 0);

        reconciliationReportRepository.save(report);

        if (discrepancy.compareTo(BigDecimal.ZERO) != 0) {
            kafkaEventPort.publish("emi.reconciliation.discrepancy", tenantId, report.getId(),
                    "reportDate=" + reportDate + ",discrepancy=" + discrepancy);
            log.error("Flow6 discrepancy detected tenantId={} reportDate={} discrepancy={}", tenantId, reportDate, discrepancy);
        }
    }
}

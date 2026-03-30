package com.loanplatform.loan_platform.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

public final class DatabaseCleanupHelper {

    private static final List<String> TABLES_IN_DELETE_ORDER = List.of(
            "kyc_documents",
            "credit_profiles",
            "emi_payment_attempts",
            "revised_schedule_snapshots",
            "prepayment_requests",
            "foreclosure_quotes",
            "npa_recovery_actions",
            "npa_loan_records",
            "loan_installments",
            "loan_state_history",
            "loan_offers",
            "loan_documents",
            "underwriter_decisions",
            "loan_applications",
            "borrowers",
            "loan_products",
            "reconciliation_reports",
            "tenants"
    );

    private DatabaseCleanupHelper() {
    }

    public static void clearDomainTables(JdbcTemplate jdbcTemplate) {
        TABLES_IN_DELETE_ORDER.forEach(table -> jdbcTemplate.execute("DELETE FROM " + table));
    }
}

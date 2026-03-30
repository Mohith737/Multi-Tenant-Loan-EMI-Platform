ALTER TABLE borrowers
    ADD COLUMN IF NOT EXISTS stripe_customer_id VARCHAR(120);

ALTER TABLE borrowers
    ADD COLUMN IF NOT EXISTS stripe_payment_method_id VARCHAR(120);

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS stripe_account_id VARCHAR(120);

ALTER TABLE loan_installments
    ADD COLUMN IF NOT EXISTS stripe_payment_intent_id VARCHAR(120);

ALTER TABLE loan_installments
    ADD COLUMN IF NOT EXISTS stripe_payment_status VARCHAR(40);

ALTER TABLE loan_installments
    ADD COLUMN IF NOT EXISTS paid_date DATE;

ALTER TABLE loan_installments
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE loan_installments
    ADD COLUMN IF NOT EXISTS last_retry_at TIMESTAMP;

ALTER TABLE loan_installments
    ADD COLUMN IF NOT EXISTS payment_failure_reason VARCHAR(500);

ALTER TABLE loan_installments
    ADD COLUMN IF NOT EXISTS mambu_sync_pending BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE loan_installments
    ADD COLUMN IF NOT EXISTS waived BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE loan_installments
    ADD COLUMN IF NOT EXISTS waived_reason VARCHAR(500);

ALTER TABLE loan_installments
    ADD COLUMN IF NOT EXISTS waived_by UUID;

ALTER TABLE loan_installments
    ADD COLUMN IF NOT EXISTS waived_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_loan_installments_tenant_due_status_retry
    ON loan_installments (tenant_id, due_date, status, retry_count);

CREATE INDEX IF NOT EXISTS idx_loan_installments_tenant_status_due
    ON loan_installments (tenant_id, status, due_date);

CREATE INDEX IF NOT EXISTS idx_loan_installments_stripe_pi
    ON loan_installments (stripe_payment_intent_id);

CREATE INDEX IF NOT EXISTS idx_loan_installments_mambu_sync_pending
    ON loan_installments (mambu_sync_pending, status);

CREATE TABLE IF NOT EXISTS emi_payment_attempts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    installment_id UUID NOT NULL,
    application_id UUID NOT NULL,
    borrower_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    idempotency_key VARCHAR(180) NOT NULL,
    stripe_payment_intent_id VARCHAR(120),
    stripe_status VARCHAR(40),
    mambu_transaction_id VARCHAR(120),
    mambu_transaction_key VARCHAR(120),
    mambu_posted BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(40) NOT NULL,
    failure_reason VARCHAR(500),
    attempted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_emi_payment_attempts_installment FOREIGN KEY (installment_id) REFERENCES loan_installments(id),
    CONSTRAINT uk_emi_attempt_tenant_installment_attempt UNIQUE (tenant_id, installment_id, attempt_number),
    CONSTRAINT uk_emi_attempt_stripe_pi UNIQUE (stripe_payment_intent_id)
);

CREATE INDEX IF NOT EXISTS idx_emi_attempt_tenant_attempted_at
    ON emi_payment_attempts (tenant_id, attempted_at);

CREATE INDEX IF NOT EXISTS idx_emi_attempt_installment_mambu_posted
    ON emi_payment_attempts (installment_id, mambu_posted);

CREATE TABLE IF NOT EXISTS reconciliation_reports (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    report_date DATE NOT NULL,
    total_due_count INTEGER NOT NULL DEFAULT 0,
    total_collected_count INTEGER NOT NULL DEFAULT 0,
    total_failed_count INTEGER NOT NULL DEFAULT 0,
    total_amount_due NUMERIC(19,2) NOT NULL DEFAULT 0,
    total_amount_collected NUMERIC(19,2) NOT NULL DEFAULT 0,
    mambu_total_posted NUMERIC(19,2) NOT NULL DEFAULT 0,
    discrepancy_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    reconciled BOOLEAN NOT NULL DEFAULT FALSE,
    reviewed_by UUID,
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reconciliation_reports_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT uk_recon_tenant_report_date UNIQUE (tenant_id, report_date)
);

CREATE INDEX IF NOT EXISTS idx_recon_discrepancy
    ON reconciliation_reports (reconciled, discrepancy_amount);

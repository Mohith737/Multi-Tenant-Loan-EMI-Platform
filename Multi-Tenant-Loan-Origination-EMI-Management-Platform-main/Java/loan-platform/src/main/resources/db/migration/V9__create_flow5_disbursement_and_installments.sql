ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS disbursement_status VARCHAR(30) NOT NULL DEFAULT 'PENDING';

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS mambu_disbursement_txn_id VARCHAR(120);

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS stripe_transfer_id VARCHAR(120);

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS net_disbursed_amount NUMERIC(19,2);

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS processing_fee_charged NUMERIC(19,2);

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS disbursed_at TIMESTAMP;

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS first_repayment_date DATE;

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS schedule_fetch_failed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS mambu_disburse_sync_failed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS disbursement_failure_reason VARCHAR(500);

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS schedule_persisted_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_loan_applications_tenant_disbursement_status
    ON loan_applications (tenant_id, disbursement_status);

CREATE INDEX IF NOT EXISTS idx_loan_applications_tenant_disbursed_at
    ON loan_applications (tenant_id, disbursed_at);

CREATE INDEX IF NOT EXISTS idx_loan_applications_schedule_fetch_failed
    ON loan_applications (schedule_fetch_failed, status);

CREATE TABLE IF NOT EXISTS loan_installments (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    application_id UUID NOT NULL,
    borrower_id UUID NOT NULL,
    mambu_loan_id VARCHAR(120) NOT NULL,
    installment_number INTEGER NOT NULL,
    due_date DATE NOT NULL,
    principal_amount NUMERIC(19,2) NOT NULL,
    interest_amount NUMERIC(19,2) NOT NULL,
    total_due NUMERIC(19,2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    mambu_installment_state VARCHAR(30),
    last_paid_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_installments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_loan_installments_application FOREIGN KEY (application_id) REFERENCES loan_applications(id),
    CONSTRAINT fk_loan_installments_borrower FOREIGN KEY (borrower_id) REFERENCES borrowers(id),
    CONSTRAINT uk_loan_installments_app_number UNIQUE (application_id, installment_number)
);

CREATE INDEX IF NOT EXISTS idx_loan_installments_tenant_due_status
    ON loan_installments (tenant_id, due_date, status);

CREATE INDEX IF NOT EXISTS idx_loan_installments_tenant_borrower
    ON loan_installments (tenant_id, borrower_id);

CREATE INDEX IF NOT EXISTS idx_loan_installments_tenant_mambu_loan
    ON loan_installments (tenant_id, mambu_loan_id);

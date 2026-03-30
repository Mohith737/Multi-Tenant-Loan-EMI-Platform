CREATE TABLE IF NOT EXISTS foreclosure_quotes (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    loan_application_id UUID NOT NULL,
    principal_outstanding NUMERIC(19,2) NOT NULL,
    accrued_interest NUMERIC(19,2) NOT NULL,
    penalty_amount NUMERIC(19,2) NOT NULL,
    total_payable NUMERIC(19,2) NOT NULL,
    quote_generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    quote_expires_at TIMESTAMP NOT NULL,
    mambu_reference VARCHAR(120),
    CONSTRAINT fk_foreclosure_quotes_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_foreclosure_quotes_application FOREIGN KEY (loan_application_id) REFERENCES loan_applications(id)
);

CREATE INDEX IF NOT EXISTS idx_foreclosure_quote_tenant_loan_expiry
    ON foreclosure_quotes (tenant_id, loan_application_id, quote_expires_at);

CREATE TABLE IF NOT EXISTS prepayment_requests (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    loan_application_id UUID NOT NULL,
    borrower_id UUID NOT NULL,
    prepayment_type VARCHAR(20) NOT NULL,
    prepayment_option VARCHAR(40),
    requested_amount NUMERIC(19,2) NOT NULL,
    quote_id UUID NOT NULL,
    idempotency_key VARCHAR(180) NOT NULL,
    stripe_payment_intent_id VARCHAR(120),
    stripe_status VARCHAR(40),
    mambu_transaction_id VARCHAR(120),
    status VARCHAR(40) NOT NULL,
    failure_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_prepayment_requests_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_prepayment_requests_application FOREIGN KEY (loan_application_id) REFERENCES loan_applications(id),
    CONSTRAINT fk_prepayment_requests_borrower FOREIGN KEY (borrower_id) REFERENCES borrowers(id),
    CONSTRAINT fk_prepayment_requests_quote FOREIGN KEY (quote_id) REFERENCES foreclosure_quotes(id),
    CONSTRAINT uk_prepayment_idempotency UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_prepayment_tenant_loan_created
    ON prepayment_requests (tenant_id, loan_application_id, created_at);

CREATE INDEX IF NOT EXISTS idx_prepayment_tenant_status
    ON prepayment_requests (tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_prepayment_tenant_stripe_intent
    ON prepayment_requests (tenant_id, stripe_payment_intent_id);

CREATE TABLE IF NOT EXISTS revised_schedule_snapshots (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    prepayment_request_id UUID NOT NULL,
    loan_application_id UUID NOT NULL,
    snapshot_version INTEGER NOT NULL DEFAULT 1,
    schedule_payload_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_revised_schedule_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_revised_schedule_request FOREIGN KEY (prepayment_request_id) REFERENCES prepayment_requests(id),
    CONSTRAINT fk_revised_schedule_application FOREIGN KEY (loan_application_id) REFERENCES loan_applications(id),
    CONSTRAINT uk_revised_schedule_request_version UNIQUE (prepayment_request_id, snapshot_version)
);

CREATE INDEX IF NOT EXISTS idx_revised_schedule_tenant_request
    ON revised_schedule_snapshots (tenant_id, prepayment_request_id);

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS last_prepayment_at TIMESTAMP;

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS foreclosed_at TIMESTAMP;

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS foreclosure_amount NUMERIC(19,2);

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS closure_reason VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_loan_app_foreclosed_at
    ON loan_applications (tenant_id, foreclosed_at);

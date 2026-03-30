CREATE TABLE IF NOT EXISTS loan_applications (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    borrower_id UUID NOT NULL,
    loan_product_id UUID NOT NULL,
    loan_product_key VARCHAR(100) NOT NULL,
    requested_amount NUMERIC(19, 2) NOT NULL,
    requested_tenure_months INTEGER NOT NULL,
    loan_purpose VARCHAR(120),
    requested_disbursement_date DATE,
    status VARCHAR(40) NOT NULL,
    loan_state VARCHAR(40) NOT NULL,
    eligibility_passed BOOLEAN NOT NULL,
    eligibility_reason_code VARCHAR(80),
    risk_category VARCHAR(30),
    credit_score_snapshot INTEGER NOT NULL,
    dti_ratio_snapshot NUMERIC(10, 2) NOT NULL,
    monthly_income_snapshot NUMERIC(19, 2) NOT NULL,
    employment_type_snapshot VARCHAR(40) NOT NULL,
    max_approved_amount_snapshot NUMERIC(19, 2) NOT NULL,
    offers_expires_at TIMESTAMP NOT NULL,
    selected_offer_id UUID,
    submitted_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_applications_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_loan_applications_borrower FOREIGN KEY (borrower_id) REFERENCES borrowers (id),
    CONSTRAINT fk_loan_applications_loan_product FOREIGN KEY (loan_product_id) REFERENCES loan_products (id)
);

CREATE INDEX IF NOT EXISTS idx_loan_applications_tenant_borrower
    ON loan_applications (tenant_id, borrower_id, created_at);
CREATE INDEX IF NOT EXISTS idx_loan_applications_tenant_status
    ON loan_applications (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_loan_applications_offer_expiry
    ON loan_applications (tenant_id, offers_expires_at);

CREATE TABLE IF NOT EXISTS loan_offers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    application_id UUID NOT NULL,
    offer_rank SMALLINT NOT NULL,
    tenure_months INTEGER NOT NULL,
    principal_amount NUMERIC(19, 2) NOT NULL,
    emi_amount NUMERIC(19, 2) NOT NULL,
    total_interest NUMERIC(19, 2) NOT NULL,
    effective_annual_rate NUMERIC(10, 4) NOT NULL,
    processing_fee_amount NUMERIC(19, 2) NOT NULL,
    total_payable_amount NUMERIC(19, 2) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    status VARCHAR(30) NOT NULL,
    mambu_simulation_ref VARCHAR(120),
    selected_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_offers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_loan_offers_application FOREIGN KEY (application_id) REFERENCES loan_applications (id),
    CONSTRAINT uk_loan_offers_rank_per_application UNIQUE (application_id, offer_rank)
);

CREATE INDEX IF NOT EXISTS idx_loan_offers_tenant_application
    ON loan_offers (tenant_id, application_id);
CREATE INDEX IF NOT EXISTS idx_loan_offers_tenant_status
    ON loan_offers (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_loan_offers_expiry
    ON loan_offers (tenant_id, expires_at);

CREATE TABLE IF NOT EXISTS loan_state_history (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    application_id UUID NOT NULL,
    from_state VARCHAR(40),
    to_state VARCHAR(40) NOT NULL,
    event VARCHAR(40) NOT NULL,
    changed_by VARCHAR(120) NOT NULL,
    reason VARCHAR(500),
    request_id VARCHAR(100),
    metadata_json VARCHAR(4000),
    changed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_state_history_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_loan_state_history_application FOREIGN KEY (application_id) REFERENCES loan_applications (id)
);

CREATE INDEX IF NOT EXISTS idx_loan_state_history_tenant_app_time
    ON loan_state_history (tenant_id, application_id, changed_at);
CREATE INDEX IF NOT EXISTS idx_loan_state_history_tenant_to_state
    ON loan_state_history (tenant_id, to_state);

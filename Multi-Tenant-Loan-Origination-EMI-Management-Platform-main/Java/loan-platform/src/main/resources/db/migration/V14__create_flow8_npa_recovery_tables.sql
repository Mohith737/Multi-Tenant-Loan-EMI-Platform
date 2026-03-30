CREATE TABLE IF NOT EXISTS npa_loan_records (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    loan_application_id UUID NOT NULL,
    borrower_id UUID NOT NULL,
    mambu_loan_id VARCHAR(120) NOT NULL,
    max_dpd INTEGER NOT NULL,
    outstanding_amount NUMERIC(19,2) NOT NULL,
    npa_flagged_at TIMESTAMP NOT NULL,
    recovery_stage VARCHAR(50) NOT NULL,
    mambu_state VARCHAR(50) NOT NULL,
    legal_escalated_at TIMESTAMP,
    override_reason VARCHAR(500),
    admin_note VARCHAR(1000),
    overridden_by UUID,
    overridden_at TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_npa_records_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_npa_records_application FOREIGN KEY (loan_application_id) REFERENCES loan_applications(id),
    CONSTRAINT fk_npa_records_borrower FOREIGN KEY (borrower_id) REFERENCES borrowers(id)
);

CREATE INDEX IF NOT EXISTS idx_npa_records_tenant_active_flagged
    ON npa_loan_records (tenant_id, active, npa_flagged_at);

CREATE INDEX IF NOT EXISTS idx_npa_records_tenant_stage
    ON npa_loan_records (tenant_id, recovery_stage);

CREATE INDEX IF NOT EXISTS idx_npa_records_tenant_application
    ON npa_loan_records (tenant_id, loan_application_id);

CREATE INDEX IF NOT EXISTS idx_npa_records_tenant_mambu
    ON npa_loan_records (tenant_id, mambu_loan_id);

CREATE TABLE IF NOT EXISTS npa_recovery_actions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    npa_loan_record_id UUID NOT NULL,
    loan_application_id UUID NOT NULL,
    action_type VARCHAR(60) NOT NULL,
    action_note VARCHAR(1000),
    triggered_by VARCHAR(40) NOT NULL,
    triggered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_npa_actions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_npa_actions_record FOREIGN KEY (npa_loan_record_id) REFERENCES npa_loan_records(id),
    CONSTRAINT fk_npa_actions_application FOREIGN KEY (loan_application_id) REFERENCES loan_applications(id)
);

CREATE INDEX IF NOT EXISTS idx_npa_actions_tenant_record_time
    ON npa_recovery_actions (tenant_id, npa_loan_record_id, triggered_at);

CREATE UNIQUE INDEX IF NOT EXISTS uk_npa_action_once
    ON npa_recovery_actions (tenant_id, npa_loan_record_id, action_type);

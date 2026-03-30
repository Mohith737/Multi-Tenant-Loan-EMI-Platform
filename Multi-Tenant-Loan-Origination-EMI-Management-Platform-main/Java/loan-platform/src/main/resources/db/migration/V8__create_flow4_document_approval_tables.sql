CREATE TABLE IF NOT EXISTS loan_documents (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    borrower_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    document_type VARCHAR(60) NOT NULL,
    document_reference VARCHAR(500) NOT NULL,
    status VARCHAR(40) NOT NULL,
    rejection_reason VARCHAR(500),
    verified_by UUID,
    verified_at TIMESTAMP,
    CONSTRAINT fk_loan_documents_application FOREIGN KEY (application_id) REFERENCES loan_applications (id),
    CONSTRAINT fk_loan_documents_borrower FOREIGN KEY (borrower_id) REFERENCES borrowers (id),
    CONSTRAINT fk_loan_documents_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE INDEX IF NOT EXISTS idx_loan_documents_tenant_application
    ON loan_documents (tenant_id, application_id);

CREATE INDEX IF NOT EXISTS idx_loan_documents_tenant_status
    ON loan_documents (tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_loan_documents_app_borrower
    ON loan_documents (application_id, borrower_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_loan_documents_ref_per_app
    ON loan_documents (application_id, document_type, document_reference);

CREATE TABLE IF NOT EXISTS underwriter_decisions (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    underwriter_id UUID NOT NULL,
    decision VARCHAR(30) NOT NULL,
    remarks VARCHAR(1000),
    decided_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_underwriter_decisions_application FOREIGN KEY (application_id) REFERENCES loan_applications (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_underwriter_decision_application
    ON underwriter_decisions (application_id);

CREATE INDEX IF NOT EXISTS idx_underwriter_decisions_underwriter_time
    ON underwriter_decisions (underwriter_id, decided_at);

CREATE INDEX IF NOT EXISTS idx_underwriter_decisions_application_time
    ON underwriter_decisions (application_id, decided_at);

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS mambu_loan_id VARCHAR(120);

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(500);

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS decision_at TIMESTAMP;

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS underwriter_id UUID;

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS underwriter_remarks VARCHAR(1000);

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS mambu_sync_failed BOOLEAN NOT NULL DEFAULT FALSE;

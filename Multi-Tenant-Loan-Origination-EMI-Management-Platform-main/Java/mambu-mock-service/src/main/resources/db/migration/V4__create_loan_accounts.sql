-- ============================================================
-- V4: Mambu Mock — Loan Accounts
-- Mirrors Mambu LoanAccount object
-- ============================================================

CREATE TABLE IF NOT EXISTS mambu_loan_accounts (
    encoded_key                 VARCHAR(64)     NOT NULL PRIMARY KEY,
    loan_id                     VARCHAR(100)    NOT NULL UNIQUE,
    account_state               VARCHAR(100)    NOT NULL DEFAULT 'PENDING_APPROVAL',
    client_key                  VARCHAR(64)     NOT NULL,
    product_type_key            VARCHAR(64)     NOT NULL,
    assigned_branch_key         VARCHAR(64)     NOT NULL,
    -- Financials
    loan_amount                 DECIMAL(18, 2)  NOT NULL,
    interest_rate               DECIMAL(10, 4)  NOT NULL,
    repayment_installments      INT             NOT NULL,
    principal_balance           DECIMAL(18, 2)  DEFAULT 0,
    interest_balance            DECIMAL(18, 2)  DEFAULT 0,
    fees_balance                DECIMAL(18, 2)  DEFAULT 0,
    penalty_balance             DECIMAL(18, 2)  DEFAULT 0,
    -- Dates
    disbursement_date           DATE,
    first_repayment_date        DATE,
    last_repayment_date         DATE,
    approved_date               TIMESTAMP,
    closed_date                 TIMESTAMP,
    -- External
    external_id                 VARCHAR(255),
    notes                       TEXT,
    creation_date               TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_date          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_loan_client  FOREIGN KEY (client_key)         REFERENCES mambu_clients(encoded_key),
    CONSTRAINT fk_loan_product FOREIGN KEY (product_type_key)   REFERENCES mambu_loan_products(encoded_key),
    CONSTRAINT fk_loan_branch  FOREIGN KEY (assigned_branch_key) REFERENCES mambu_branches(encoded_key)
);

CREATE INDEX IF NOT EXISTS idx_loan_id            ON mambu_loan_accounts(loan_id);
CREATE INDEX IF NOT EXISTS idx_loan_state         ON mambu_loan_accounts(account_state);
CREATE INDEX IF NOT EXISTS idx_loan_client        ON mambu_loan_accounts(client_key);
CREATE INDEX IF NOT EXISTS idx_loan_branch        ON mambu_loan_accounts(assigned_branch_key);


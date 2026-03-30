-- ============================================================
-- V2: Mambu Mock — Loan Products
-- Mirrors Mambu LoanProduct object
-- ============================================================

CREATE TABLE IF NOT EXISTS mambu_loan_products (
    encoded_key                     VARCHAR(64)     NOT NULL PRIMARY KEY,
    product_id                      VARCHAR(100)    NOT NULL UNIQUE,
    name                            VARCHAR(255)    NOT NULL,
    state                           VARCHAR(50)     NOT NULL DEFAULT 'ACTIVE',
    type                            VARCHAR(100)    NOT NULL DEFAULT 'FIXED_TERM_LOAN',
    currency_code                   VARCHAR(10)     NOT NULL DEFAULT 'INR',
    for_branch_key                  VARCHAR(64)     NOT NULL,
    -- Interest Settings
    default_interest_rate           DECIMAL(10, 4)  NOT NULL,
    interest_calculation_method     VARCHAR(100)    NOT NULL DEFAULT 'DECLINING_BALANCE',
    interest_charge_frequency       VARCHAR(100)    NOT NULL DEFAULT 'ANNUALIZED',
    -- Amount Settings
    min_loan_amount                 DECIMAL(18, 2)  NOT NULL,
    max_loan_amount                 DECIMAL(18, 2)  NOT NULL,
    default_loan_amount             DECIMAL(18, 2)  NOT NULL,
    -- Schedule Settings
    default_repayment_period_count  INT             NOT NULL DEFAULT 12,
    default_repayment_period_unit   VARCHAR(50)     NOT NULL DEFAULT 'MONTHS',
    repayment_schedule_method       VARCHAR(100)    NOT NULL DEFAULT 'STANDARD',
    -- Fees
    processing_fee_percent          DECIMAL(10, 4)  DEFAULT 0,
    prepayment_penalty_percent      DECIMAL(10, 4)  DEFAULT 0,

    creation_date                   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_date              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_product_branch FOREIGN KEY (for_branch_key) REFERENCES mambu_branches(encoded_key)
);

CREATE INDEX IF NOT EXISTS idx_product_id ON mambu_loan_products(product_id);
CREATE INDEX IF NOT EXISTS idx_product_branch ON mambu_loan_products(for_branch_key);


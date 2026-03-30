-- ============================================================
-- V1: Mambu Mock — Branches
-- Mirrors Mambu Branch object
-- ============================================================

CREATE TABLE IF NOT EXISTS mambu_branches (
    encoded_key         VARCHAR(64)     NOT NULL PRIMARY KEY,
    branch_id           VARCHAR(100)    NOT NULL UNIQUE,
    name                VARCHAR(255)    NOT NULL,
    state               VARCHAR(50)     NOT NULL DEFAULT 'ACTIVE',
    email_address       VARCHAR(255),
    phone_number        VARCHAR(50),
    country             VARCHAR(100),
    city                VARCHAR(100),
    address_line1       VARCHAR(255),
    notes               TEXT,
    creation_date       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_date  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_branch_id ON mambu_branches(branch_id);
CREATE INDEX IF NOT EXISTS idx_branch_state ON mambu_branches(state);


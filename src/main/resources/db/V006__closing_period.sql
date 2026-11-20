ALTER TABLE budget_workspace ADD period_key VARCHAR2(7) DEFAULT '2026-11' NOT NULL;
ALTER TABLE budget_workspace ADD period_state VARCHAR2(8) DEFAULT 'OPEN' NOT NULL;
CREATE TABLE budget_statement_line (
    workspace_id VARCHAR2(36) NOT NULL REFERENCES budget_workspace,
    period_key VARCHAR2(7) NOT NULL,
    category VARCHAR2(20) NOT NULL,
    claim_count NUMBER(12) NOT NULL,
    spent NUMBER(15) NOT NULL,
    PRIMARY KEY (workspace_id, period_key, category)
);

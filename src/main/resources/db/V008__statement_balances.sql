CREATE TABLE budget_statement_balance (
    workspace_id VARCHAR2(36) NOT NULL REFERENCES budget_workspace,
    period_key VARCHAR2(7) NOT NULL,
    allocation NUMBER(15) NOT NULL,
    committed NUMBER(15) NOT NULL,
    spent NUMBER(15) NOT NULL,
    PRIMARY KEY (workspace_id, period_key)
);
INSERT INTO budget_statement_balance (workspace_id, period_key, allocation, committed, spent)
SELECT workspace_id, period_key, allocation, committed, spent
FROM budget_workspace WHERE period_state='CLOSED';

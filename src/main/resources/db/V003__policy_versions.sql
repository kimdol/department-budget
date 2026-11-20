ALTER TABLE expense_claim ADD policy_generation NUMBER(10) DEFAULT 0 NOT NULL;
CREATE TABLE policy_draft (
    workspace_id VARCHAR2(36) NOT NULL REFERENCES budget_workspace,
    draft_id VARCHAR2(36) NOT NULL,
    category VARCHAR2(20) NOT NULL,
    base_generation NUMBER(10) NOT NULL,
    formula VARCHAR2(2048 CHAR) NOT NULL,
    PRIMARY KEY (workspace_id, draft_id)
);
CREATE TABLE policy_publication (
    workspace_id VARCHAR2(36) NOT NULL REFERENCES budget_workspace,
    category VARCHAR2(20) NOT NULL,
    generation NUMBER(10) NOT NULL,
    formula VARCHAR2(2048 CHAR) NOT NULL,
    published_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    PRIMARY KEY (workspace_id, category, generation)
);

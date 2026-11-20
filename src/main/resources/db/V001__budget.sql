CREATE TABLE budget_workspace (
    workspace_id VARCHAR2(36) PRIMARY KEY,
    allocation NUMBER(15) DEFAULT 1000000 NOT NULL,
    committed NUMBER(15) DEFAULT 0 NOT NULL,
    spent NUMBER(15) DEFAULT 0 NOT NULL,
    CHECK (committed >= 0 AND spent >= 0 AND allocation >= committed + spent)
);
CREATE TABLE expense_claim (
    claim_id VARCHAR2(36) PRIMARY KEY,
    workspace_id VARCHAR2(36) NOT NULL REFERENCES budget_workspace,
    title VARCHAR2(180 CHAR) NOT NULL,
    category VARCHAR2(20) NOT NULL,
    status VARCHAR2(20) NOT NULL,
    estimated NUMBER(15) NOT NULL,
    days NUMBER(4) NOT NULL,
    formula VARCHAR2(2048 CHAR) NOT NULL,
    approved NUMBER(15) DEFAULT 0 NOT NULL,
    actual NUMBER(15),
    settled NUMBER(15) DEFAULT 0 NOT NULL,
    UNIQUE (workspace_id, claim_id)
);
CREATE TABLE budget_posting (
    claim_id VARCHAR2(36) NOT NULL,
    workspace_id VARCHAR2(36) NOT NULL,
    kind VARCHAR2(20) NOT NULL,
    committed_delta NUMBER(15) NOT NULL,
    spent_delta NUMBER(15) NOT NULL,
    posted_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    PRIMARY KEY (claim_id, kind),
    FOREIGN KEY (workspace_id, claim_id) REFERENCES expense_claim (workspace_id, claim_id)
);

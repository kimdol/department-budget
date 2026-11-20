CREATE TABLE branch_allocation (
    head_id VARCHAR2(36) NOT NULL REFERENCES budget_workspace(workspace_id),
    request_key VARCHAR2(36) NOT NULL,
    branch_id VARCHAR2(36) NOT NULL REFERENCES budget_workspace(workspace_id),
    period_key VARCHAR2(7) NOT NULL,
    amount NUMBER(15) NOT NULL CHECK (amount > 0),
    recorded_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    PRIMARY KEY (head_id,request_key),
    CHECK (head_id <> branch_id)
);

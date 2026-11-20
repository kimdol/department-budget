CREATE TABLE submission_receipt (
    workspace_id VARCHAR2(36) NOT NULL,
    request_key VARCHAR2(36) NOT NULL,
    claim_id VARCHAR2(36) NOT NULL,
    PRIMARY KEY (workspace_id, request_key),
    FOREIGN KEY (workspace_id, claim_id) REFERENCES expense_claim (workspace_id, claim_id)
);

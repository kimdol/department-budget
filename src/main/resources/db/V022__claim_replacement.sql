CREATE TABLE claim_replacement (
    workspace_id VARCHAR2(36) NOT NULL,
    original_id VARCHAR2(36) NOT NULL,
    replacement_id VARCHAR2(36) NOT NULL,
    PRIMARY KEY (workspace_id,original_id),
    UNIQUE (workspace_id,replacement_id),
    FOREIGN KEY (workspace_id,original_id) REFERENCES expense_claim(workspace_id,claim_id),
    FOREIGN KEY (workspace_id,replacement_id) REFERENCES expense_claim(workspace_id,claim_id),
    CHECK (original_id <> replacement_id)
);

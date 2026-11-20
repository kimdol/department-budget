CREATE TABLE claim_version (
    workspace_id VARCHAR2(36) NOT NULL,
    series_id VARCHAR2(36) NOT NULL,
    version_no NUMBER(10) NOT NULL CHECK (version_no >= 1),
    claim_id VARCHAR2(36) NOT NULL,
    PRIMARY KEY (workspace_id,series_id,version_no),
    UNIQUE (workspace_id,claim_id),
    FOREIGN KEY (workspace_id,series_id) REFERENCES expense_claim(workspace_id,claim_id),
    FOREIGN KEY (workspace_id,claim_id) REFERENCES expense_claim(workspace_id,claim_id)
);

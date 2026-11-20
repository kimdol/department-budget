ALTER TABLE policy_publication ADD reviewed_draft_id VARCHAR2(36);
ALTER TABLE policy_publication ADD review_hash VARCHAR2(64);
ALTER TABLE policy_publication ADD CONSTRAINT publication_review_pair CHECK (
    (reviewed_draft_id IS NULL AND review_hash IS NULL) OR
    (reviewed_draft_id IS NOT NULL AND review_hash IS NOT NULL)
);
ALTER TABLE policy_publication ADD CONSTRAINT publication_review_draft_fk
    FOREIGN KEY (workspace_id, reviewed_draft_id) REFERENCES policy_draft(workspace_id, draft_id);

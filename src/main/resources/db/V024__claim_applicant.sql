ALTER TABLE expense_claim ADD applicant_id VARCHAR2(24) DEFAULT 'STAFF_01' NOT NULL;
ALTER TABLE claim_review_event ADD actor_member_id VARCHAR2(24);

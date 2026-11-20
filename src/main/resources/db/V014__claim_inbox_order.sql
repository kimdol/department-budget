CREATE SEQUENCE claim_inbox_seq START WITH 1 INCREMENT BY 1;
ALTER TABLE expense_claim ADD inbox_order NUMBER(15);
UPDATE expense_claim SET inbox_order=claim_inbox_seq.NEXTVAL;
ALTER TABLE expense_claim MODIFY inbox_order NOT NULL;
CREATE UNIQUE INDEX claim_inbox_page ON expense_claim(workspace_id,inbox_order);

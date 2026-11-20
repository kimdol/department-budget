package kr.budget.claims;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:posting;MODE=Oracle",
    "spring.datasource.username=sa","spring.datasource.password=","spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:db/V001__budget.sql,classpath:db/V002__submission_receipt.sql,classpath:db/V003__policy_versions.sql,classpath:db/V004__claim_review.sql,classpath:db/V005__claim_evidence.sql,classpath:db/V006__closing_period.sql,classpath:db/V007__portal_outbox.sql,classpath:db/V008__statement_balances.sql,classpath:db/V009__posting_period.sql,classpath:db/V010__organization_type.sql","spring.batch.jdbc.initialize-schema=always"})
class LocalPostingTest extends PostingContract {}

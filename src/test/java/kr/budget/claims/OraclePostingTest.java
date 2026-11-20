package kr.budget.claims;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties={"spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:db/V001__budget.sql,classpath:db/V002__submission_receipt.sql,classpath:db/V003__policy_versions.sql,classpath:db/V004__claim_review.sql,classpath:db/V005__claim_evidence.sql"})
@EnabledIfEnvironmentVariable(named="BUDGET_TEST_EMPTY_SCHEMA", matches="yes")
class OraclePostingTest extends PostingContract {}

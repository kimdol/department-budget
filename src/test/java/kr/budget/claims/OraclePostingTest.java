package kr.budget.claims;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties={"spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:db/V001__budget.sql,classpath:db/V002__submission_receipt.sql"})
@EnabledIfEnvironmentVariable(named="BUDGET_TEST_EMPTY_SCHEMA", matches="yes")
class OraclePostingTest extends PostingContract {}

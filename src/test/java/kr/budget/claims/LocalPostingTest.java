package kr.budget.claims;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:posting;MODE=Oracle",
    "spring.datasource.username=sa","spring.datasource.password=","spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:db/V001__budget.sql,classpath:db/V002__submission_receipt.sql,classpath:db/V003__policy_versions.sql"})
class LocalPostingTest extends PostingContract {}

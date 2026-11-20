package kr.budget.claims;

import java.util.concurrent.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:db/V001__budget.sql"})
@EnabledIfEnvironmentVariable(named="BUDGET_TEST_EMPTY_SCHEMA", matches="yes")
class OraclePostingTest {
    @Autowired ClaimService service;
    @Autowired BudgetMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void retriesLeaveOneSettlementAndTheExactLedgerBalance() {
        String workspace = service.open();
        String id = service.submit(workspace, "교육 참가", "EDUCATION", 300000, 1);
        service.approve(workspace, id);
        assertEquals(200000, mapper.balance(workspace).committed());
        service.review(workspace, id, 225000);
        service.settle(workspace, id);
        service.settle(workspace, id);
        Balance balance = mapper.balance(workspace);
        assertEquals(0, balance.committed());
        assertEquals(180000, balance.spent());
        assertEquals(820000, balance.getAvailable());
        assertEquals(2, jdbc.queryForObject("select count(*) from budget_posting where claim_id=?", Integer.class, id));
        assertEquals(180000, jdbc.queryForObject("select sum(spent_delta) from budget_posting where claim_id=?", Long.class, id));
        assertThrows(ResponseStatusException.class, () -> service.settle(service.open(), id));
    }

    @Test
    void competingApprovalsCannotExceedAllocation() throws Exception {
        String workspace = service.open();
        jdbc.update("update budget_workspace set allocation=100000 where workspace_id=?", workspace);
        String a = service.submit(workspace, "교육 A", "EDUCATION", 100000, 1);
        String b = service.submit(workspace, "교육 B", "EDUCATION", 100000, 1);
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(a,b).stream().map(id -> executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                try {
                    service.approve(workspace, id);
                    return 200;
                } catch (ResponseStatusException rejected) {
                    return rejected.getStatusCode().value();
                }
            })).toList();
            assertEquals(List.of(200,422), List.of(futures.get(0).get(15, TimeUnit.SECONDS),
                    futures.get(1).get(15, TimeUnit.SECONDS)).stream().sorted().toList());
        }
        assertEquals(20000, mapper.balance(workspace).getAvailable());
    }
}

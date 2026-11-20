package kr.budget.claims;

import java.util.concurrent.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

abstract class PostingContract {
    @Autowired ClaimService service;
    @Autowired BudgetMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired kr.budget.policy.PolicyService policies;

    @Test
    void increasesOnlyTheDifferenceAndRequiresTheLatestReviewRound() {
        String workspace = service.open();
        String id = service.submit(workspace, UUID.randomUUID(), "세미나", "EDUCATION", 187500, 1);
        service.approve(workspace, id);
        service.review(workspace, id, 225000);
        assertEquals(150000, mapper.balance(workspace).committed());
        service.returnReview(workspace, id, 1, "참가 금액을 확인하세요.", true);
        service.review(workspace, id, 225000, 0);
        assertEquals("NEEDS_RECEIPT", mapper.lockClaim(workspace, id).status());
        service.review(workspace, id, 225000, 1);
        assertThrows(ResponseStatusException.class, () -> service.increase(workspace, id, 1));
        service.increase(workspace, id, 2);
        service.increase(workspace, id, 2);
        assertEquals(180000, mapper.balance(workspace).committed());
        assertThrows(ResponseStatusException.class, () -> service.settle(workspace, id, 1));
        service.settle(workspace, id, 2);
        assertEquals(820000, mapper.balance(workspace).getAvailable());
        assertEquals(30000, jdbc.queryForObject("select committed_delta from budget_posting where claim_id=? and kind='INCREASE_2'", Long.class, id));
    }

    @Test
    void conferenceRulesArePinnedAtSubmissionAndCanBeRestoredForNewClaims() {
        String workspace = service.open();
        String original = service.submit(workspace, UUID.randomUUID(), "학회", "CONFERENCE", 180000, 1);
        String expression = "min(eligible_cost * 0.90, event_cap)";
        var preview = policies.preview(workspace, "CONFERENCE", expression, 180000, 1);
        assertEquals(144000, preview.original());
        assertEquals(162000, preview.candidate());
        String draft = policies.draft(workspace, "CONFERENCE", 0, expression);
        assertEquals(1, policies.publish(workspace, draft));
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> policies.publish(workspace, draft)).getStatusCode().value());
        String preferred = service.submit(workspace, UUID.randomUUID(), "학회", "CONFERENCE", 180000, 1);
        String regular = service.submit(workspace, UUID.randomUUID(), "일반", "EDUCATION", 180000, 1);
        String restore = policies.draft(workspace, "CONFERENCE", 1, policies.history(workspace, "CONFERENCE").getLast().formula());
        policies.publish(workspace, restore);
        String restored = service.submit(workspace, UUID.randomUUID(), "학회", "CONFERENCE", 180000, 1);
        for (String id : List.of(original, regular, preferred, restored)) service.approve(workspace, id);
        assertEquals(144000, mapper.lockClaim(workspace, original).approved());
        assertEquals(144000, mapper.lockClaim(workspace, regular).approved());
        assertEquals(162000, mapper.lockClaim(workspace, preferred).approved());
        assertEquals(1, mapper.lockClaim(workspace, preferred).policyGeneration());
        assertEquals(144000, mapper.lockClaim(workspace, restored).approved());
        service.review(workspace, preferred, 100000);
        service.settle(workspace, preferred);
        assertEquals(90000, mapper.balance(workspace).spent());
    }

    @Test
    void lostSubmissionResponseReplaysOriginalClaimOnlyForSameInput() {
        String workspace = service.open();
        UUID key = UUID.randomUUID();
        String id = service.submit(workspace, key, "직무 교육", "EDUCATION", 300000, 1);
        assertEquals(id, service.submit(workspace, key, "직무 교육", "EDUCATION", 300000, 1));
        ResponseStatusException mismatch = assertThrows(ResponseStatusException.class,
                () -> service.submit(workspace, key, "직무 교육", "EDUCATION", 300001, 1));
        assertEquals(409, mismatch.getStatusCode().value());
        assertEquals(1, mapper.claims(workspace).size());
        assertNotEquals(id, service.submit(service.open(), key, "직무 교육", "EDUCATION", 300000, 1));
    }

    @Test
    void retriesLeaveOneSettlementAndTheExactLedgerBalance() {
        String workspace = service.open();
        String id = service.submit(workspace, UUID.randomUUID(), "교육 참가", "EDUCATION", 300000, 1);
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
        String a = service.submit(workspace, UUID.randomUUID(), "교육 A", "EDUCATION", 100000, 1);
        String b = service.submit(workspace, UUID.randomUUID(), "교육 B", "EDUCATION", 100000, 1);
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


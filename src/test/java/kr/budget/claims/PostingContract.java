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
    @Autowired EvidenceService evidence;
    @Autowired JdbcTemplate jdbc;
    @Autowired kr.budget.policy.PolicyService policies;
    @Autowired kr.budget.closing.PeriodClosing periods;
    @Autowired kr.budget.closing.ClosingService closing;
    @Autowired kr.budget.portal.PortalOutbox outbox;

    @Test
    void branchCanCommitWhileHeadOfficeBudgetIsLocked() throws Exception {
        var departments = service.openDepartments(OrganizationType.BANK);
        String headOffice = departments.primaryId();
        String branch = departments.secondaryId();
        String claim = service.submit(branch, UUID.randomUUID(), "지점 교육", "EDUCATION", 300000, 1);
        assertEquals(headOffice, jdbc.queryForObject(
                "select parent_workspace from budget_workspace where workspace_id=?", String.class, branch));
        try (var worker = Executors.newSingleThreadExecutor();
             var connection = jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (var lock = connection.prepareStatement("select allocation from budget_workspace where workspace_id=? for update")) {
                lock.setString(1, headOffice);
                try (var rows = lock.executeQuery()) {
                    assertTrue(rows.next());
                    worker.submit(() -> service.approve(branch, claim)).get(10, TimeUnit.SECONDS);
                }
            } finally {
                connection.rollback();
            }
        }
        assertEquals(200000, mapper.balance(branch).committed());
        assertEquals(0, mapper.balance(headOffice).committed());
    }

    @Test
    void reversesClosedSettlementOnlyInAnOpenPeriodWithoutChangingOriginalStatement() {
        String workspace = service.open();
        String id = service.submit(workspace,UUID.randomUUID(),"교육","EDUCATION",300000,1);
        service.approve(workspace,id);
        evidence.attach(workspace, id, EvidenceService.Type.RECEIPT, 225000);
        service.review(workspace,id,225000);
        service.settle(workspace,id);
        var closed = closing.close(workspace);
        String next = periods.advance(workspace,closed.period()).period();
        periods.reverse(workspace,id,1,30000,next);
        periods.reverse(workspace,id,1,30000,next);
        assertThrows(ResponseStatusException.class, () -> periods.reverse(workspace,id,1,20000,next));
        assertThrows(ResponseStatusException.class, () -> periods.reverse(workspace,id,2,150001,next));
        var corrected = closing.close(workspace);
        assertEquals(-30000,corrected.lines().getFirst().spent());
        assertEquals(850000,corrected.balance().getAvailable());
        assertEquals(closed,periods.archive(workspace,closed.period()));
    }

    @Test
    void oneThousandPolicyPreviewsLeaveCommittedBudgetAndPostingRowsUntouched() {
        String workspace = service.open();
        String claim = service.submit(workspace, UUID.randomUUID(), "교육", "EDUCATION", 300000, 1);
        service.approve(workspace, claim);
        var balance = mapper.balance(workspace);
        String postingRows = "select claim_id, kind, committed_delta, spent_delta from budget_posting where workspace_id=? order by claim_id, kind";
        var postings = jdbc.queryForList(postingRows, workspace);
        assertEquals(1, postings.size());
        assertEquals(200000, balance.committed());
        for (int index = 0; index < 1000; index++) {
            long cost = 200000L + index * 100;
            var preview = policies.preview(workspace, "EDUCATION",
                    "min(eligible_cost * support_rate, event_cap * 1.25)", cost, 1);
            assertEquals(Math.min(cost * 8 / 10, 200000), preview.original());
            assertEquals(cost * 8 / 10, preview.candidate());
        }
        assertEquals(balance, mapper.balance(workspace));
        assertEquals(postings, jdbc.queryForList(postingRows, workspace));
        assertEquals(1, policies.history(workspace, "EDUCATION").size());
    }

    @Test
    void displayedEstimateDetectsPublicationBeforeSubmissionAndPreservesCommittedRetry() {
        String workspace = service.open();
        var estimate = service.estimate(workspace,"EDUCATION",300000,1);
        assertEquals(200000,estimate.supported());
        assertEquals(100000,estimate.employeeShare());
        UUID request = UUID.randomUUID();
        String id = service.submit(workspace,request,"교육","EDUCATION",300000,1,estimate.generation());
        policies.publish(workspace,policies.draft(workspace,"EDUCATION",0,"floor(eligible_cost * 0.5)"));
        assertEquals(id,service.submit(workspace,request,"교육","EDUCATION",300000,1,estimate.generation()));
        assertEquals(409,assertThrows(ResponseStatusException.class, () ->
                service.submit(workspace,UUID.randomUUID(),"교육","EDUCATION",300000,1,estimate.generation())).getStatusCode().value());
        assertEquals(150000,service.estimate(workspace,"EDUCATION",300000,1).supported());
        service.approve(workspace,id);
        assertEquals(200000,mapper.balance(workspace).committed());
    }

    @Test
    void incompleteConferenceEvidenceBlocksPostingUntilTheEmployeeCorrectsIt() {
        String workspace = service.open(OrganizationType.HOSPITAL);
        String id = service.submit(workspace,UUID.randomUUID(),"학회","CONFERENCE",300000,1);
        service.approve(workspace,id);
        evidence.attach(workspace,id,EvidenceService.Type.RECEIPT,220000);
        service.review(workspace,id,225000);
        assertEquals(2,evidence.outstanding(workspace,id).size());
        assertEquals(422,assertThrows(ResponseStatusException.class,() -> service.settle(workspace,id)).getStatusCode().value());
        assertEquals(0,mapper.balance(workspace).spent());
        service.returnReview(workspace,id,1,"영수증 금액과 참석 확인서를 보완하세요.",false);
        evidence.attach(workspace,id,EvidenceService.Type.RECEIPT,225000);
        evidence.attach(workspace,id,EvidenceService.Type.RECEIPT,225000);
        evidence.attach(workspace,id,EvidenceService.Type.ATTENDANCE,225000);
        service.review(workspace,id,225000,1);
        service.settle(workspace,id,2);
        assertEquals(180000,mapper.balance(workspace).spent());
        assertEquals(List.of(220000L),jdbc.queryForList("select previous_amount from evidence_replacement where claim_id=?",Long.class,id));
    }

    @Test
    void attendancePolicyChangesAndRestorationPreserveEachSubmissionsEvidenceRequirements() {
        String workspace = service.open(OrganizationType.HOSPITAL);
        String category = "EDUCATION";
        var initial = policies.history(workspace, category).getFirst();
        String original = service.submit(workspace, UUID.randomUUID(), "기존 교육", category, 300000, 1);
        policies.publish(workspace, policies.draft(workspace, category, 0, initial.formula(),
                300000, 1, null, "ATTACHED"));
        String strict = service.submit(workspace, UUID.randomUUID(), "참석 필수 교육", category, 300000, 1);
        policies.publish(workspace, policies.draft(workspace, category, 1, initial.formula(),
                300000, 1, 0L, initial.attendanceRule()));
        String restored = service.submit(workspace, UUID.randomUUID(), "복구 이후 교육", category, 300000, 1);
        for (String id : List.of(original, strict, restored)) {
            service.approve(workspace, id);
            evidence.attach(workspace, id, EvidenceService.Type.RECEIPT, 225000);
            service.review(workspace, id, 225000);
        }
        service.settle(workspace, original);
        service.settle(workspace, restored);
        assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> service.settle(workspace, strict)).getStatusCode().value());
        assertEquals(360000, mapper.balance(workspace).spent());
        service.returnReview(workspace, strict, 1, "참석 확인서를 제출하세요.", false);
        evidence.attach(workspace, strict, EvidenceService.Type.ATTENDANCE, 225000);
        service.review(workspace, strict, 225000, 1);
        service.settle(workspace, strict, 2);
        assertEquals(540000, mapper.balance(workspace).spent());
        assertEquals(460000, mapper.balance(workspace).getAvailable());
    }

    @Test
    void cancellationReleasesOnlyAnExistingCommitmentOnce() {
        String workspace = service.open();
        for (boolean approved : List.of(false, true)) {
            String id = service.submit(workspace, UUID.randomUUID(), "교육", "EDUCATION", 300000, 1);
            if (approved) {
                service.approve(workspace, id);
                assertThrows(ResponseStatusException.class, () -> service.cancel(workspace, id, false));
            }
            service.cancel(workspace, id, approved);
            service.cancel(workspace, id, approved);
            assertThrows(ResponseStatusException.class, () -> service.approve(workspace, id));
            assertEquals(approved ? 2 : 0, jdbc.queryForObject("select count(*) from budget_posting where claim_id=?", Integer.class, id));
            assertEquals(1, mapper.history(workspace,id).stream()
                    .filter(event -> event.eventType().equals(approved ? "CANCEL_APPROVAL" : "WITHDRAW")).count());
            assertEquals(1000000, mapper.balance(workspace).getAvailable());
        }
        assertTrue(periods.statement(workspace).pending().isEmpty());
    }

    @Test
    void deliveryCanResumeAfterReceiverCommitsButItsAcknowledgementIsLost() {
        String workspace = service.open();
        String id = service.submit(workspace, UUID.randomUUID(), "교육", "EDUCATION", 300000, 1);
        service.approve(workspace, id);
        evidence.attach(workspace, id, EvidenceService.Type.RECEIPT, 225000);
        service.review(workspace, id, 225000);
        service.settle(workspace, id);
        var receipts = new java.util.LinkedHashMap<String,Long>();
        var attempts = new java.util.ArrayList<Integer>();
        var relay = new kr.budget.portal.PortalRelay(outbox,event -> {
            if (!event.claimId().equals(id)) return;
            receipts.putIfAbsent(event.eventId(),event.amount());
            attempts.add(event.sequenceNo());
            if (attempts.size() == 1) throw new IllegalStateException("응답 유실");
        });
        relay.deliverPending();
        assertEquals(180000, mapper.balance(workspace).spent());
        var failure = outbox.failures(workspace).getFirst();
        assertEquals(id, failure.claimId());
        assertEquals(1, failure.attempts());
        assertNull(outbox.retryable(service.open(), failure.eventId()));
        assertTrue(relay.deliver(outbox.retryable(workspace, failure.eventId())));
        assertTrue(outbox.failures(workspace).isEmpty());
        assertNull(outbox.retryable(workspace, failure.eventId()));
        relay.deliverPending();
        relay.deliverPending();
        assertEquals(List.of(1,1,2), attempts);
        assertEquals(List.of(200000L,180000L), List.copyOf(receipts.values()));
        assertEquals(2, jdbc.queryForObject("select count(*) from portal_outbox where claim_id=? and delivered=1", Integer.class, id));
    }

    @Test
    void resumesAfterPreparingCloseAndFreezesExactlyOneStatement() {
        String workspace = service.open();
        String id = service.submit(workspace, UUID.randomUUID(), "교육", "EDUCATION", 300000, 1);
        service.approve(workspace, id);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> closing.close(workspace)).getStatusCode().value());
        assertEquals(List.of(id), periods.statement(workspace).pending());
        evidence.attach(workspace, id, EvidenceService.Type.RECEIPT, 225000);
        service.review(workspace, id, 225000);
        service.settle(workspace, id);
        periods.prepare(workspace);
        assertEquals("CLOSING", periods.statement(workspace).state());
        assertThrows(ResponseStatusException.class, () -> service.submit(workspace, UUID.randomUUID(), "추가", "EDUCATION", 1000, 1));
        var statement = closing.close(workspace);
        assertEquals("CLOSED", statement.state());
        assertEquals(1, statement.lines().size());
        assertEquals(180000, statement.lines().getFirst().spent());
        assertEquals(statement, closing.close(workspace));
        jdbc.update("update budget_workspace set allocation=allocation+1000 where workspace_id=?", workspace);
        assertEquals(statement, periods.archive(workspace, statement.period()));
        assertEquals(statement, periods.statement(workspace));
        assertThrows(ResponseStatusException.class, () -> periods.archive(service.open(), statement.period()));
    }

    @Test
    void failedSnapshotRestartsTheSameBatchInstance() {
        String workspace = service.open();
        String id = service.submit(workspace, UUID.randomUUID(), "출장", "TRAVEL", 50000, 1);
        service.approve(workspace, id);
        evidence.attach(workspace, id, EvidenceService.Type.RECEIPT, 45000);
        service.review(workspace, id, 45000);
        service.settle(workspace, id);
        String period = periods.prepare(workspace).periodKey();
        jdbc.update("insert into budget_statement_line(workspace_id,period_key,category,claim_count,spent) values (?,?,?,0,0)",
                workspace, period, "TRAVEL");
        assertThrows(IllegalStateException.class, () -> closing.close(workspace));
        assertEquals("CLOSING", periods.statement(workspace).state());
        jdbc.update("delete from budget_statement_line where workspace_id=? and period_key=?", workspace, period);
        var statement = closing.close(workspace);
        assertEquals("CLOSED", statement.state());
        assertEquals(45000, statement.lines().getFirst().spent());
        var statuses = jdbc.queryForList("""
            select e.status from batch_job_execution e
            join batch_job_execution_params p on p.job_execution_id=e.job_execution_id
            where p.parameter_name='workspace' and p.parameter_value=? order by e.job_execution_id
            """, String.class, workspace);
        assertEquals(List.of("FAILED","COMPLETED"), statuses);
        assertEquals(1, jdbc.queryForObject("""
            select count(distinct e.job_instance_id) from batch_job_execution e
            join batch_job_execution_params p on p.job_execution_id=e.job_execution_id
            where p.parameter_name='workspace' and p.parameter_value=?
            """, Integer.class, workspace));
    }

    @Test
    void increasesOnlyTheDifferenceAndRequiresTheLatestReviewRound() {
        String workspace = service.open();
        String id = service.submit(workspace, UUID.randomUUID(), "세미나", "EDUCATION", 187500, 1);
        service.approve(workspace, id);
        evidence.attach(workspace, id, EvidenceService.Type.RECEIPT, 225000);
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
        String workspace = service.open(OrganizationType.HOSPITAL);
        String bank = service.open(OrganizationType.BANK);
        String company = service.open(OrganizationType.COMPANY);
        assertEquals(OrganizationType.HOSPITAL,mapper.organization(workspace));
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
        for (String other : List.of(bank,company)) {
            String application = service.submit(other, UUID.randomUUID(), "외부 학회", "CONFERENCE", 180000, 1);
            service.approve(other,application);
            assertEquals(144000,mapper.lockClaim(other,application).approved());
        }
        String regular = service.submit(workspace, UUID.randomUUID(), "일반", "EDUCATION", 180000, 1);
        String restore = policies.draft(workspace, "CONFERENCE", 1, policies.history(workspace, "CONFERENCE").getLast().formula(), 180000, 1, 0L);
        policies.publish(workspace, restore);
        String restored = service.submit(workspace, UUID.randomUUID(), "학회", "CONFERENCE", 180000, 1);
        for (String id : List.of(original, regular, preferred, restored)) service.approve(workspace, id);
        assertEquals(144000, mapper.lockClaim(workspace, original).approved());
        assertEquals(144000, mapper.lockClaim(workspace, regular).approved());
        assertEquals(162000, mapper.lockClaim(workspace, preferred).approved());
        assertEquals(1, mapper.lockClaim(workspace, preferred).policyGeneration());
        assertEquals(144000, mapper.lockClaim(workspace, restored).approved());
        evidence.attach(workspace, preferred, EvidenceService.Type.RECEIPT, 100000);
        evidence.attach(workspace,preferred,EvidenceService.Type.ATTENDANCE,100000);
        service.review(workspace, preferred, 100000);
        service.settle(workspace, preferred);
        assertEquals(90000, mapper.balance(workspace).spent());
        var decision = service.replay(workspace,preferred);
        assertEquals("decimal-v2",decision.evaluatorVersion());
        assertEquals("decimal-v1", service.replay(workspace, original).evaluatorVersion());
        assertEquals("decimal-v1", service.replay(workspace, restored).evaluatorVersion());
        assertEquals(162000,decision.initialSupport());
        assertEquals(90000L,decision.actualSupport());
        assertEquals(expression,decision.formula());
        jdbc.update("update expense_claim set evaluator_version='unavailable' where claim_id=?",preferred);
        assertThrows(IllegalStateException.class,() -> service.replay(workspace,preferred));
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
        evidence.attach(workspace, id, EvidenceService.Type.RECEIPT, 225000);
        service.review(workspace, id, 225000);
        service.settle(workspace, id);
        service.settle(workspace, id);
        Balance balance = mapper.balance(workspace);
        assertEquals(0, balance.committed());
        assertEquals(180000, balance.spent());
        assertEquals(820000, balance.getAvailable());
        var history = mapper.history(workspace,id);
        assertEquals(List.of("SUBMIT","FINAL_APPROVAL","SUBMIT_ACTUAL","SETTLE"),
                history.stream().map(BudgetMapper.ReviewEvent::eventType).toList());
        assertEquals(List.of(300000L,200000L,225000L,180000L),
                history.stream().map(BudgetMapper.ReviewEvent::amount).toList());
        assertEquals(2, jdbc.queryForObject("select count(*) from budget_posting where claim_id=?", Integer.class, id));
        assertEquals(180000, jdbc.queryForObject("select sum(spent_delta) from budget_posting where claim_id=?", Long.class, id));
        assertThrows(ResponseStatusException.class, () -> service.settle(service.open(), id));
    }

    @Test
    void financeApprovalReservesTravelCostsOnlyAfterBothSteps() {
        String workspace = service.open();
        assertEquals(ApprovalRoute.MANAGER,service.estimate(workspace,"TRAVEL",299999,6).route());
        assertEquals(ApprovalRoute.MANAGER_FINANCE,service.estimate(workspace,"TRAVEL",300000,6).route());
        String id = service.submit(workspace,UUID.randomUUID(),"학술 출장","TRAVEL",300000,6);
        assertThrows(ResponseStatusException.class,() -> service.financeApproval(workspace,id));
        service.approve(workspace,id);
        service.approve(workspace,id);
        assertEquals("FINANCE_PENDING",mapper.lockClaim(workspace,id).status());
        assertEquals(0,mapper.balance(workspace).committed());
        service.financeApproval(workspace,id);
        service.financeApproval(workspace,id);
        assertEquals(300000,mapper.balance(workspace).committed());
        assertEquals(1,jdbc.queryForObject("select count(*) from budget_posting where claim_id=?",Integer.class,id));
        assertEquals(List.of("EMPLOYEE","MANAGER","FINANCE"),
                mapper.history(workspace,id).stream().map(BudgetMapper.ReviewEvent::actorRole).toList());
    }

    @Test
    void laterPagesKeepTheirBoundaryWhenAnotherApplicationArrives() {
        String workspace = service.open();
        var ordered = new java.util.ArrayList<String>();
        for (int index=0;index<52;index++) ordered.add(service.submit(workspace,UUID.randomUUID(),"교육 "+index,"EDUCATION",1000,1));
        var first = mapper.inbox(workspace,Long.MAX_VALUE);
        assertEquals(51,first.size());
        assertEquals(ordered.getLast(),first.getFirst().claimId());
        long cursor = first.get(49).inboxOrder();
        service.submit(workspace,UUID.randomUUID(),"추가 신청","EDUCATION",1000,1);
        var other = service.open();
        service.submit(other,UUID.randomUUID(),"별도 공간","EDUCATION",1000,1);
        assertEquals(List.of(ordered.get(1),ordered.getFirst()),mapper.inbox(workspace,cursor).stream().map(BudgetMapper.InboxItem::claimId).toList());
        assertTrue(mapper.inbox(other,cursor).isEmpty());
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


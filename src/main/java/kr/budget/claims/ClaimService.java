package kr.budget.claims;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import kr.budget.policy.PolicyService;
import kr.budget.policy.PolicyVersion;

@Service
@Transactional
public class ClaimService {
    private final BudgetMapper mapper;

    private final PolicyService policies;

    public ClaimService(BudgetMapper mapper, PolicyService policies) {
        this.mapper = mapper;
        this.policies = policies;
    }

    public String open() {
        String workspace = UUID.randomUUID().toString();
        mapper.open(workspace, java.time.YearMonth.now(java.time.ZoneId.of("Asia/Seoul")).toString());
        return workspace;
    }

    public String submit(String workspace, UUID requestKey, String title, String category, long estimated, int days) {
        checkAmount(estimated);
        if (title == null || title.isBlank() || title.length() > 100 || days < 1 || days > 365)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        mapper.lockBalance(workspace);
        String key = requestKey.toString();
        Claim previous = mapper.previousSubmission(workspace, key);
        if (previous != null) {
            if (previous.title().equals(title.strip()) && previous.category().equals(category)
                    && previous.estimated() == estimated && previous.days() == days) return previous.claimId();
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        requireOpen(workspace);
        String id = UUID.randomUUID().toString();
        PolicyVersion policy = policies.active(workspace, category);
        policy.calculate(estimated, days);
        mapper.submit(id, workspace, title.strip(), category, estimated, days, policy.formula(), policy.generation());
        mapper.rememberSubmission(workspace, key, id);
        return id;
    }

    public void approve(String workspace, String id) {
        Balance balance = mapper.lockBalance(workspace);
        Claim claim = locked(workspace, id);
        if (!claim.status().equals("SUBMITTED")) return;
        requireOpen(workspace);
        long amount = amount(claim, claim.estimated());
        if (amount > balance.getAvailable()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        mapper.approve(workspace, id, amount);
        post(workspace, id, "APPROVAL", amount, 0);
    }

    public void review(String workspace, String id, long actual) {
        review(workspace, id, actual, 0);
    }

    public void review(String workspace, String id, long actual, int expectedRound) {
        checkAmount(actual);
        Claim claim = locked(workspace, id);
        if (claim.reviewRound() == expectedRound + 1 && claim.actual() != null && claim.actual() == actual) return;
        requireRound(claim, expectedRound);
        if (!claim.status().equals("NEEDS_RECEIPT")) require(claim, "APPROVED");
        String next = amount(claim, actual) > claim.approved() ? "INCREASE_REQUESTED" : "IN_REVIEW";
        mapper.review(workspace, id, actual, next);
        mapper.event(workspace, id, claim.reviewRound() + 1, "SUBMIT_ACTUAL", "EMPLOYEE", actual, null);
    }

    public void increase(String workspace, String id, int round) {
        Balance balance = mapper.lockBalance(workspace);
        Claim claim = locked(workspace, id);
        requireRound(claim, round);
        if (claim.status().equals("IN_REVIEW") && claim.approved() == amount(claim, claim.actual())) return;
        require(claim, "INCREASE_REQUESTED");
        long adjusted = amount(claim, claim.actual());
        long extra = adjusted - claim.approved();
        if (extra > balance.getAvailable()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        mapper.increase(workspace, id, adjusted);
        post(workspace, id, "INCREASE_" + round, extra, 0);
        mapper.event(workspace, id, round, "APPROVE_INCREASE", "MANAGER", extra, null);
    }

    public void returnReview(String workspace, String id, int round, String reason, boolean manager) {
        if (reason == null || reason.isBlank() || reason.length() > 240)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        Claim claim = locked(workspace, id);
        requireRound(claim, round);
        if (claim.status().equals("NEEDS_RECEIPT") && reason.equals(claim.reviewNote())) return;
        require(claim, manager ? "INCREASE_REQUESTED" : "IN_REVIEW");
        mapper.returnReview(workspace, id, reason);
        mapper.event(workspace, id, round, "RETURN", manager ? "MANAGER" : "FINANCE", claim.actual(), reason);
    }

    private void requireRound(Claim claim, int round) {
        if (claim.reviewRound() != round) throw new ResponseStatusException(HttpStatus.CONFLICT);
    }

    public void settle(String workspace, String id) {
        settle(workspace, id, 1);
    }

    public void settle(String workspace, String id, int round) {
        mapper.lockBalance(workspace);
        Claim claim = locked(workspace, id);
        requireRound(claim, round);
        if (claim.status().equals("SETTLED")) return;
        require(claim, "IN_REVIEW");
        long amount = amount(claim, claim.actual());
        mapper.settle(workspace, id, amount);
        post(workspace, id, "SETTLEMENT", -claim.approved(), amount);
    }

    private Claim locked(String workspace, String id) {
        Claim claim = mapper.lockClaim(workspace, id);
        if (claim == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return claim;
    }

    private void requireOpen(String workspace) {
        if (!"OPEN".equals(mapper.periodState(workspace))) throw new ResponseStatusException(HttpStatus.CONFLICT);
    }

    private void require(Claim claim, String status) {
        if (!claim.status().equals(status)) throw new ResponseStatusException(HttpStatus.CONFLICT);
    }

    private void post(String workspace, String id, String kind, long reserved, long spent) {
        mapper.changeBalance(workspace, reserved, spent);
        mapper.post(workspace, id, kind, reserved, spent);
    }

    private long amount(Claim claim, long cost) {
        return new PolicyVersion(claim.policyGeneration(), claim.formula()).calculate(cost, claim.days());
    }

    private void checkAmount(long amount) {
        if (amount <= 0 || amount > 999999999999L) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}

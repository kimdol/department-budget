package kr.budget.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.budget.claims.ApprovalRoute;
import kr.budget.claims.AttendanceRule;
import kr.budget.claims.BudgetMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PolicyService {
    public record Preview(long generation, long original, long candidate,
                          ApprovalRoute originalRoute, ApprovalRoute candidateRoute) {}
    public record Impact(String claimId, String title, long generation, long estimated, long original,
                         ApprovalRoute originalRoute, Long candidate, ApprovalRoute candidateRoute, String error) {}
    public record ImpactPage(List<Impact> claims, Long nextCursor) {}
    private final PolicyMapper policies;
    private final BudgetMapper budgets;
    private final kr.budget.closing.ClosingMapper periods;

    public PolicyService(PolicyMapper policies, BudgetMapper budgets, kr.budget.closing.ClosingMapper periods) {
        this.policies = policies;
        this.budgets = budgets;
        this.periods = periods;
    }

    public PolicyVersion active(String workspace, String category) {
        PolicyVersion initial = PolicyVersion.initial(category);
        PolicyVersion latest = policies.latest(workspace, category);
        return latest == null ? initial : latest;
    }

    public List<PolicyVersion> history(String workspace, String category) {
        var versions = new ArrayList<>(policies.history(workspace, category));
        versions.add(PolicyVersion.initial(category));
        return versions;
    }

    public Preview preview(String workspace, String category, String formula, long cost, int days) {
        return preview(workspace, category, formula, cost, days, null, null);
    }

    public Preview preview(String workspace, String category, String formula, long cost, int days, Long restoreGeneration, String attendanceRule) {
        PolicyVersion active = active(workspace, category);
        long original = active.calculate(cost, days);
        long candidate = candidate(workspace, category, formula, restoreGeneration, attendanceRule, active).calculate(cost, days);
        return new Preview(active.generation(), original, candidate,
                ApprovalRoute.forSupport(original), ApprovalRoute.forSupport(candidate));
    }

    @Transactional
    public String draft(String workspace, String category, long expected, String formula) {
        return draft(workspace, category, expected, formula, 180000, 1, null);
    }

    @Transactional(readOnly=true)
    public ImpactPage impact(String workspace, String category, String formula, Long restoreGeneration,
                             String attendanceRule, long generation, long before) {
        var active = active(workspace,category);
        checkGeneration(generation,active);
        var proposal = candidate(workspace,category,formula,restoreGeneration,attendanceRule,active);
        var samples = policies.samples(workspace,category,before);
        var rows = new ArrayList<Impact>();
        for (var sample : samples.stream().limit(50).toList()) {
            long original = new PolicyVersion(sample.policyGeneration(),sample.formula(),sample.evaluatorVersion())
                    .calculate(sample.estimated(),sample.days());
            Long amount = null;
            ApprovalRoute route = null;
            String error = null;
            try {
                amount = proposal.calculate(sample.estimated(),sample.days());
                route = ApprovalRoute.forSupport(amount);
            } catch (IllegalArgumentException invalid) {
                error = invalid.getMessage();
            }
            rows.add(new Impact(sample.claimId(),sample.title(),sample.policyGeneration(),sample.estimated(),
                    original,sample.approvalRoute(),amount,route,error));
        }
        return new ImpactPage(rows,samples.size() > 50 ? samples.get(49).inboxOrder() : null);
    }

    @Transactional
    public String draft(String workspace, String category, long expected, String formula, long cost, int days, Long restoreGeneration) {
        return draft(workspace, category, expected, formula, cost, days, restoreGeneration, null);
    }

    @Transactional
    public String draft(String workspace, String category, long expected, String formula, long cost, int days, Long restoreGeneration, String attendanceRule) {
        budgets.lockBalance(workspace);
        PolicyVersion current = active(workspace, category);
        checkGeneration(expected, current);
        var candidate = candidate(workspace, category, formula, restoreGeneration, attendanceRule, current);
        for (long referenceCost : new long[]{0, 180000, 225000, 300000}) candidate.calculate(referenceCost, 1);
        String id = UUID.randomUUID().toString();
        policies.draft(workspace, id, category, expected, formula, candidate.evaluatorVersion(),
                cost, days, current.calculate(cost, days), candidate.calculate(cost, days), candidate.attendanceRule());
        return id;
    }

    private PolicyVersion candidate(String workspace, String category, String formula, Long restoreGeneration, String attendanceRule, PolicyVersion active) {
        if (restoreGeneration == null) {
            String rule = attendanceRule == null ? active.attendanceRule() : attendanceRule;
            AttendanceRule.parse(rule);
            return new PolicyVersion(0, formula, PolicyVersion.DECIMAL_V2, rule);
        }
        return history(workspace, category).stream()
                .filter(version -> version.generation() == restoreGeneration && version.formula().equals(formula))
                .filter(version -> attendanceRule == null || version.attendanceRule().equals(attendanceRule))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("복구할 규정의 버전과 수식을 확인하세요."));
    }

    @Transactional
    public long publish(String workspace, String draftId, String expectedHash) {
        budgets.lockBalance(workspace);
        var draft = reviewedDraft(workspace,draftId,expectedHash);
        if (reservation(workspace,draft.category()) != null) throw new ResponseStatusException(HttpStatus.CONFLICT);
        long next = draft.baseGeneration() + 1;
        policies.publish(workspace,next,draft,expectedHash);
        return next;
    }

    private PolicyMapper.Scheduled reservation(String workspace, String category) {
        return policies.scheduled(workspace).stream().filter(s -> s.category().equals(category)).findFirst().orElse(null);
    }

    public String nextPeriod(String workspace) {
        return java.time.YearMonth.parse(periods.period(workspace).periodKey()).plusMonths(1).toString();
    }

    @Transactional
    public void schedule(String workspace, String draftId, String expectedHash, String targetPeriod) {
        budgets.lockBalance(workspace);
        var draft = reviewedDraft(workspace,draftId,expectedHash);
        if (!nextPeriod(workspace).equals(targetPeriod)) throw new ResponseStatusException(HttpStatus.CONFLICT);
        var item = new PolicyMapper.Scheduled(draft.category(),targetPeriod,draftId,expectedHash);
        var previous = reservation(workspace,draft.category());
        if (item.equals(previous)) return;
        if (previous != null) throw new ResponseStatusException(HttpStatus.CONFLICT);
        policies.schedule(workspace,item);
    }

    @Transactional
    public void cancelSchedule(String workspace, String draftId, String targetPeriod) {
        budgets.lockBalance(workspace);
        if (policies.unschedule(workspace,draftId,targetPeriod) != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT);
    }

    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void activateScheduled(String workspace, String targetPeriod) {
        for (var item : policies.scheduled(workspace)) {
            if (!item.targetPeriod().equals(targetPeriod)) throw new ResponseStatusException(HttpStatus.CONFLICT);
            var draft = reviewedDraft(workspace,item.draftId(),item.reviewHash());
            policies.publish(workspace,draft.baseGeneration()+1,draft,item.reviewHash());
            policies.unschedule(workspace,item.draftId(),targetPeriod);
        }
    }

    private PolicyMapper.Draft reviewedDraft(String workspace, String draftId, String expectedHash) {
        var draft = policies.findDraft(workspace, draftId);
        if (draft == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String reviewed = draft.getReviewHash();
        if (!reviewed.equals(expectedHash)) throw new ResponseStatusException(HttpStatus.CONFLICT);
        PolicyVersion current = active(workspace, draft.category());
        checkGeneration(draft.baseGeneration(), current);
        return draft;
    }

    private void checkGeneration(long expected, PolicyVersion active) {
        if (active.generation() != expected) throw new ResponseStatusException(HttpStatus.CONFLICT);
    }
}

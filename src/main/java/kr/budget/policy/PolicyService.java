package kr.budget.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.budget.claims.BudgetMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PolicyService {
    public record Preview(long generation, long original, long candidate) {}
    private final PolicyMapper policies;
    private final BudgetMapper budgets;

    public PolicyService(PolicyMapper policies, BudgetMapper budgets) {
        this.policies = policies;
        this.budgets = budgets;
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
        PolicyVersion active = active(workspace, category);
        return new Preview(active.generation(), active.calculate(cost, days),
                new PolicyVersion(active.generation(), formula).calculate(cost, days));
    }

    @Transactional
    public String draft(String workspace, String category, long expected, String formula) {
        budgets.lockBalance(workspace);
        PolicyVersion current = active(workspace, category);
        checkGeneration(expected, current);
        var candidate = new PolicyVersion(expected, formula);
        for (long cost : new long[]{0, 180000, 225000, 300000}) candidate.calculate(cost, 1);
        String id = UUID.randomUUID().toString();
        policies.draft(workspace, id, category, expected, formula);
        return id;
    }

    @Transactional
    public long publish(String workspace, String draftId) {
        budgets.lockBalance(workspace);
        var draft = policies.findDraft(workspace, draftId);
        if (draft == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        PolicyVersion current = active(workspace, draft.category());
        checkGeneration(draft.baseGeneration(), current);
        long next = current.generation() + 1;
        policies.publish(workspace, draft.category(), next, draft.formula());
        return next;
    }

    private void checkGeneration(long expected, PolicyVersion active) {
        if (active.generation() != expected) throw new ResponseStatusException(HttpStatus.CONFLICT);
    }
}

package kr.budget.claims;

import java.util.ArrayList;
import java.util.List;
import kr.budget.policy.EligibilityRule;
import static kr.budget.policy.EligibilityRule.Operator.*;
import static kr.budget.policy.EligibilityRule.Verdict.*;

public final class EvidencePolicy {
    public record Inputs(OrganizationType organization, String category, Long actual, Long receipt, boolean attendance) {}

    private static final EligibilityRule<Inputs> RECEIPT = new EligibilityRule.Group<>("영수증 확인",ALL,List.of(
            new EligibilityRule.Fact<Inputs>("영수증 제출",i -> i.receipt() == null ? MISSING : PASS),
            new EligibilityRule.Fact<Inputs>("실제 지출 대조",i -> {
                if (i.receipt() == null) return MISSING;
                return i.actual() == null || i.actual().equals(i.receipt()) ? PASS : FAIL;
            })));

    private static final EligibilityRule<Inputs> ATTENDANCE = new EligibilityRule.Group<>("참석 확인 요건",ANY,List.of(
            new EligibilityRule.Fact<Inputs>("병원 외 조직",i -> i.organization() == OrganizationType.HOSPITAL ? FAIL : PASS),
            new EligibilityRule.Fact<Inputs>("학회비 외 비목",i -> i.category().equals("CONFERENCE") ? FAIL : PASS),
            new EligibilityRule.Fact<Inputs>("참석 확인서 제출",i -> i.attendance() ? PASS : MISSING)));

    public record Evaluation(EligibilityRule.Decision receipt, EligibilityRule.Decision attendance, List<String> issues) {}

    public static Evaluation evaluate(Inputs input) {
        var receipt = RECEIPT.evaluate(input);
        var attendance = ATTENDANCE.evaluate(input);
        var issues = new ArrayList<String>();
        if (receipt.verdict() == MISSING) issues.add("지출 영수증이 필요합니다.");
        if (receipt.verdict() == FAIL) issues.add("영수증 금액과 실제 지출액이 다릅니다.");
        if (attendance.verdict() == MISSING) issues.add("병원 학회비는 참석 확인서가 필요합니다.");
        return new Evaluation(receipt,attendance,List.copyOf(issues));
    }

    private EvidencePolicy() {}
}

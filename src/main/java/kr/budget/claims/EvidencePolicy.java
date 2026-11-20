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
                if (i.receipt() == null || i.actual() == null) return MISSING;
                return i.actual().equals(i.receipt()) ? PASS : FAIL;
            })));

    private static final EligibilityRule<Inputs> ATTENDANCE = AttendanceRule.parse(AttendanceRule.DEFAULT);

    public record Evaluation(EligibilityRule.Decision receipt, EligibilityRule.Decision attendance, List<String> issues) {}

    public static Evaluation evaluate(Inputs input) {
        return evaluate(input, ATTENDANCE);
    }

    public static Evaluation evaluate(Inputs input, EligibilityRule<Inputs> attendanceRule) {
        var receipt = RECEIPT.evaluate(input);
        var attendance = attendanceRule.evaluate(input);
        var issues = new ArrayList<String>();
        if (receipt.verdict() == MISSING) {
            issues.add(input.receipt() == null ? "지출 영수증이 필요합니다." :
                    "실제 지출액을 제출해야 영수증 금액을 대조할 수 있습니다.");
        }
        if (receipt.verdict() == FAIL) issues.add("영수증 금액과 실제 지출액이 다릅니다.");
        if (attendance.verdict() == MISSING) issues.add(input.organization() == OrganizationType.HOSPITAL && input.category().equals("CONFERENCE")
                ? "병원 학회비는 참석 확인서가 필요합니다." : "참석 확인서가 필요합니다.");
        if (attendance.verdict() == FAIL) issues.add("조직·비목이 참석 확인 요건에 맞지 않습니다.");
        return new Evaluation(receipt,attendance,List.copyOf(issues));
    }

    private EvidencePolicy() {}
}

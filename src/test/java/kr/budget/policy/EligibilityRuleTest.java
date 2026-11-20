package kr.budget.policy;

import java.util.List;
import kr.budget.claims.EvidencePolicy;
import kr.budget.claims.AttendanceRule;
import kr.budget.claims.OrganizationType;
import org.junit.jupiter.api.Test;
import static kr.budget.policy.EligibilityRule.Operator.*;
import static kr.budget.policy.EligibilityRule.Verdict.*;
import static org.junit.jupiter.api.Assertions.*;

class EligibilityRuleTest {
    @Test
    void allAndAnyHandleEveryPairOfThreeValuedFacts() {
        for (var first : EligibilityRule.Verdict.values()) {
            for (var second : EligibilityRule.Verdict.values()) {
                List<EligibilityRule<String>> facts = List.of(new EligibilityRule.Fact<>("A",s -> first),
                        new EligibilityRule.Fact<>("B",s -> second));
                var all = new EligibilityRule.Group<>("all",ALL,facts).evaluate("");
                var any = new EligibilityRule.Group<>("any",ANY,facts).evaluate("");
                assertEquals(first == FAIL || second == FAIL ? FAIL : first == PASS && second == PASS ? PASS : MISSING,all.verdict());
                assertEquals(first == PASS || second == PASS ? PASS : first == FAIL && second == FAIL ? FAIL : MISSING,any.verdict());
                assertEquals(first == FAIL,all.skipped().contains("B"));
                assertEquals(first == PASS,any.skipped().contains("B"));
            }
        }
    }

    @Test
    void parsesOnlyBoundedTreesOfRegisteredAttendanceConditions() {
        var input = new EvidencePolicy.Inputs(OrganizationType.HOSPITAL,"CONFERENCE",180000L,180000L,true);
        var tree = AttendanceRule.parse("ALL(ATTACHED, ANY(OTHER_CATEGORY, OUTSIDE_HOSPITAL))");
        assertEquals(FAIL, tree.evaluate(input).verdict());
        for (String invalid : List.of("SELF","ANY()", "ATTACHED()", "ALL(ATTACHED,)",
                "ANY(".repeat(8)+"ATTACHED"+")".repeat(8), "ALL("+"ATTACHED,".repeat(62)+"ANY(ATTACHED))")) {
            assertThrows(IllegalArgumentException.class, () -> AttendanceRule.parse(invalid));
        }
    }

    @Test
    void receiptCannotConfirmAnUnsubmittedExpense() {
        var before = EvidencePolicy.evaluate(new EvidencePolicy.Inputs(OrganizationType.COMPANY,"EDUCATION",null,225000L,false));
        assertEquals(MISSING, before.receipt().verdict());
        assertEquals(List.of("실제 지출액을 제출해야 영수증 금액을 대조할 수 있습니다."), before.issues());
        var mismatch = EvidencePolicy.evaluate(new EvidencePolicy.Inputs(OrganizationType.COMPANY,"EDUCATION",220000L,225000L,false));
        assertEquals(FAIL, mismatch.receipt().verdict());
        assertEquals(List.of("영수증 금액과 실제 지출액이 다릅니다."), mismatch.issues());
    }

    @Test
    void bankConferenceSkipsAttendanceWhileHospitalRequestsTheDocument() {
        var bank = EvidencePolicy.evaluate(new EvidencePolicy.Inputs(OrganizationType.BANK,"CONFERENCE",225000L,225000L,false));
        var hospital = EvidencePolicy.evaluate(new EvidencePolicy.Inputs(OrganizationType.HOSPITAL,"CONFERENCE",225000L,225000L,false));
        assertTrue(bank.issues().isEmpty());
        assertTrue(bank.attendance().skipped().contains("참석 확인서 제출"));
        assertEquals(MISSING,hospital.attendance().verdict());
        assertEquals(List.of("병원 학회비는 참석 확인서가 필요합니다."),hospital.issues());
    }
}

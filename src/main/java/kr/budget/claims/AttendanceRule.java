package kr.budget.claims;

import java.util.ArrayList;
import kr.budget.policy.EligibilityRule;
import static kr.budget.policy.EligibilityRule.Verdict.*;

public final class AttendanceRule {
    public static final String DEFAULT = "ANY(OUTSIDE_HOSPITAL,OTHER_CATEGORY,ATTACHED)";
    private final String source;
    private int position;
    private int nodes;

    private AttendanceRule(String source) {
        this.source = source;
    }

    public static EligibilityRule<EvidencePolicy.Inputs> parse(String source) {
        if (source == null || source.length() > 512) throw new IllegalArgumentException("참석 조건은 512자까지 입력하세요.");
        var parser = new AttendanceRule(source);
        var tree = parser.node(1);
        parser.space();
        if (parser.position != source.length()) throw parser.error();
        return tree;
    }

    private EligibilityRule<EvidencePolicy.Inputs> node(int depth) {
        if (++nodes > 64 || depth > 8) throw new IllegalArgumentException("참석 조건은 8단계·64개 노드까지 허용됩니다.");
        space();
        int start = position;
        while (position < source.length() && (Character.isUpperCase(source.charAt(position)) || source.charAt(position)=='_')) position++;
        String name = source.substring(start,position);
        return switch (name) {
            case "OUTSIDE_HOSPITAL" -> new EligibilityRule.Fact<>("병원 외 조직", i -> i.organization()==OrganizationType.HOSPITAL ? FAIL : PASS);
            case "OTHER_CATEGORY" -> new EligibilityRule.Fact<>("학회비 외 비목", i -> i.category().equals("CONFERENCE") ? FAIL : PASS);
            case "ATTACHED" -> new EligibilityRule.Fact<>("참석 확인서 제출", i -> i.attendance() ? PASS : MISSING);
            case "ALL", "ANY" -> {
                if (!take('(')) throw error();
                var children = new ArrayList<EligibilityRule<EvidencePolicy.Inputs>>();
                do { children.add(node(depth+1)); } while (take(','));
                if (!take(')')) throw error();
                yield new EligibilityRule.Group<>(name.equals("ALL") ? "모두 충족" : "하나 이상 충족",
                        EligibilityRule.Operator.valueOf(name), children);
            }
            default -> throw error();
        };
    }

    private void space() {
        while (position < source.length() && Character.isWhitespace(source.charAt(position))) position++;
    }

    private boolean take(char value) {
        space();
        if (position >= source.length() || source.charAt(position)!=value) return false;
        position++;
        return true;
    }

    private IllegalArgumentException error() {
        return new IllegalArgumentException((position+1)+"번째 문자에서 등록된 조건 이름과 괄호를 확인하세요.");
    }
}

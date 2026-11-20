package kr.budget.policy;

import java.math.BigDecimal;

public record PolicyVersion(long generation, String formula,String evaluatorVersion,String attendanceRule) {
    @org.apache.ibatis.annotations.AutomapConstructor
    public PolicyVersion {}
    public PolicyVersion(long generation, String formula, String evaluatorVersion) {
        this(generation, formula, evaluatorVersion, kr.budget.claims.AttendanceRule.DEFAULT);
    }
    public static final String DECIMAL_V1 = "decimal-v1";
    public static final String DECIMAL_V2 = "decimal-v2";
    public static PolicyVersion initial(String category) {
        return new PolicyVersion(0, switch (category) {
            case "EDUCATION", "CONFERENCE" -> ExpenseFormula.EDUCATION;
            case "TRAVEL" -> ExpenseFormula.TRAVEL;
            default -> throw new IllegalArgumentException("등록되지 않은 비목입니다.");
        },DECIMAL_V1);
    }

    public long calculate(long cost, int days) {
        return switch (evaluatorVersion) {
            case DECIMAL_V1, DECIMAL_V2 -> ExpenseFormula.calculate(formula, new ExpenseFormula.Inputs(cost,
                    new BigDecimal("0.80"), 200000, days, 50000), DECIMAL_V2.equals(evaluatorVersion));
            default -> throw new IllegalStateException("저장된 수식 평가기 버전을 사용할 수 없습니다.");
        };
    }
}

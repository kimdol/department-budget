package kr.budget.policy;

import java.math.BigDecimal;

public record PolicyVersion(long generation, String formula) {
    public static PolicyVersion initial(String category) {
        return new PolicyVersion(0, switch (category) {
            case "EDUCATION", "CONFERENCE" -> ExpenseFormula.EDUCATION;
            case "TRAVEL" -> ExpenseFormula.TRAVEL;
            default -> throw new IllegalArgumentException("등록되지 않은 비목입니다.");
        });
    }

    public long calculate(long cost, int days) {
        return ExpenseFormula.calculate(formula, new ExpenseFormula.Inputs(cost,
                new BigDecimal("0.80"), 200000, days, 50000));
    }
}

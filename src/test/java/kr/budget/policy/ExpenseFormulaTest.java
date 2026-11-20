package kr.budget.policy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ExpenseFormulaTest {
    private ExpenseFormula.Inputs inputs(long cost) {
        return new ExpenseFormula.Inputs(cost, new BigDecimal("0.8"), 200000, 3, 50000);
    }

    @Test
    void preservesHistoricalEvaluationWhenNewTreeLimitsRejectLongExpressions() {
        String atLimit = "eligible_cost" + "*1".repeat(31);
        assertEquals(225000, new PolicyVersion(1, atLimit, "decimal-v2").calculate(225000, 1));
        String beyondLimit = atLimit + "*1";
        assertEquals(225000, new PolicyVersion(0, beyondLimit, "decimal-v1").calculate(225000, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new PolicyVersion(1, beyondLimit, "decimal-v2").calculate(225000, 1));
        String zeros = "0";
        for (int level = 0; level < 7; level++) zeros = "(" + zeros + "+" + zeros + ")";
        String wide = "eligible_cost*" + zeros;
        assertThrows(IllegalArgumentException.class,
                () -> new PolicyVersion(1, wide, "decimal-v2").calculate(225000, 1));
    }

    @Test
    void calculatesEducationCommitmentAndSettlement() {
        assertEquals(200000, ExpenseFormula.calculate(ExpenseFormula.EDUCATION, inputs(300000)));
        assertEquals(180000, ExpenseFormula.calculate(ExpenseFormula.EDUCATION, inputs(225000)));
    }

    @Test
    void comparesHospitalCandidateToOriginal() {
        assertEquals(162000, ExpenseFormula.calculate("min(eligible_cost * 0.9, event_cap)", inputs(180000)));
        assertEquals(144000, ExpenseFormula.calculate(ExpenseFormula.EDUCATION, inputs(180000)));
    }

    @Test
    void capsTravelByDaysAndActualCost() {
        assertEquals(150000, ExpenseFormula.calculate(ExpenseFormula.TRAVEL, inputs(225000)));
        assertEquals(10000, ExpenseFormula.calculate(ExpenseFormula.TRAVEL, inputs(10000)));
    }

    @ParameterizedTest
    @ValueSource(strings={"eligible_cost + days", "min(eligible_cost, days)"})
    void locatesIncompatibleOperand(String expression) {
        var error = assertThrows(IllegalArgumentException.class,
                () -> ExpenseFormula.calculate(expression, inputs(225000)));
        assertEquals((expression.indexOf("days") + 1) + "번째 문자: 단위가 일치하지 않습니다.", error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings={"eligible_cost + days", "eligible_cost / 0", "unknown", "eligible_cost * 2",
        "1.2.3 * eligible_cost", "floor(eligible_cost", "daily_cap", "eligible_cost garbage"})
    void rejectsInvalidAmounts(String expr) {
        assertThrows(IllegalArgumentException.class, () -> ExpenseFormula.calculate(expr, inputs(225000)));
    }
}

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
    @ValueSource(strings={"eligible_cost + days", "eligible_cost / 0", "unknown", "eligible_cost * 2",
        "1.2.3 * eligible_cost", "floor(eligible_cost", "daily_cap", "eligible_cost garbage"})
    void rejectsInvalidAmounts(String expr) {
        assertThrows(IllegalArgumentException.class, () -> ExpenseFormula.calculate(expr, inputs(225000)));
    }
}

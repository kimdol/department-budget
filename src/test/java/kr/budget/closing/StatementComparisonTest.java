package kr.budget.closing;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StatementComparisonTest {
    private final String header = "period,category,count,spent\r\n";
    private final PeriodClosing.Statement statement = new PeriodClosing.Statement("2026-12", "CLOSED", null,
            List.of(new ClosingMapper.Line("EDUCATION", 1, -30000)), List.of(), null);

    @Test
    void comparesSignedCorrectionsAndShowsMissingCategoriesOnBothSides() {
        assertTrue(StatementComparison.compare(statement, header + "2026-12,EDUCATION,1,-30000").isEmpty());
        var differences = StatementComparison.compare(statement, header + "2026-12,TRAVEL,0,0");
        assertEquals(List.of(new StatementComparison.Difference("EDUCATION", null, null, 1L, -30000L),
                new StatementComparison.Difference("TRAVEL", 0L, 0L, null, null)), differences);
    }

    @Test
    void rejectsDuplicateCategoryAndWrongPeriod() {
        String line = "2026-12,EDUCATION,1,-30000";
        assertThrows(IllegalArgumentException.class, () -> StatementComparison.compare(statement, header + line + "\n" + line));
        assertThrows(IllegalArgumentException.class, () -> StatementComparison.compare(statement, header + "2026-11,EDUCATION,1,0"));
    }
}

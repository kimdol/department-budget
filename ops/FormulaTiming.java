import java.math.BigDecimal;
import java.util.Locale;
import kr.budget.policy.ExpenseFormula;

public class FormulaTiming {
    public static void main(String[] args) {
        long reference = -1;
        for (int round = -5; round < 10; round++) {
            long start = System.nanoTime();
            long total = 0;
            for (int index = 0; index < 50000; index++) {
                var input = new ExpenseFormula.Inputs(100000 + index, BigDecimal.valueOf(8,1),200000,1,50000);
                total += ExpenseFormula.calculate(ExpenseFormula.EDUCATION,input);
            }
            long elapsed = System.nanoTime() - start;
            if (reference >= 0 && reference != total) throw new IllegalStateException("Inconsistent total");
            reference = total;
            if (round >= 0) System.out.printf(Locale.ROOT,"%d,%d,%d%n",round + 1,elapsed,total);
        }
    }
}

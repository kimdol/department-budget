package kr.budget.closing;

import java.math.BigDecimal;
import java.util.HashMap;
import javax.sql.DataSource;
import kr.budget.claims.Balance;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;
import org.springframework.stereotype.Service;

@Service
public class LedgerReconciliation {
    public record Difference(long committed, long spent) {
        public boolean isBalanced() { return committed == 0 && spent == 0; }
    }

    private final ClosingMapper ledger;
    private final boolean oracle;

    public LedgerReconciliation(ClosingMapper ledger, DataSource dataSource) throws MetaDataAccessException {
        this.ledger = ledger;
        this.oracle = "Oracle".equals(JdbcUtils.extractDatabaseMetaData(dataSource, metadata -> metadata.getDatabaseProductName()));
    }

    public Difference compare(String workspace, Balance balance) {
        if (oracle) {
            var values = new HashMap<String,Object>();
            values.put("workspace", workspace);
            values.put("committed", balance.committed());
            values.put("spent", balance.spent());
            ledger.compareOracle(values);
            return new Difference(((BigDecimal)values.get("committedDifference")).longValueExact(),
                    ((BigDecimal)values.get("spentDifference")).longValueExact());
        }
        var totals = ledger.totals(workspace);
        return new Difference(totals.reserved() - balance.committed(), totals.spent() - balance.spent());
    }
}

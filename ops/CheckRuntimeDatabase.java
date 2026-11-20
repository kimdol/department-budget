import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;

public class CheckRuntimeDatabase {
    public static void main(String[] args) {
        if (args.length != 1 || !Set.of("TCP", "TCPS").contains(args[0])) {
            throw new IllegalArgumentException("Expected database transport TCP or TCPS");
        }
        try (var db = DriverManager.getConnection(setting("BUDGET_DB_URL"), setting("BUDGET_DB_USER"),
                    setting("BUDGET_DB_PASSWORD")); var query = db.createStatement()) {
            try (var result = query.executeQuery("select sys_context('USERENV','NETWORK_PROTOCOL') from dual")) {
                if (!result.next() || !args[0].equalsIgnoreCase(result.getString(1))) {
                    throw new IllegalStateException("Unexpected database transport");
                }
            }
            String[] probes = {
                    "select allocation,committed,spent from budget_workspace where 1=0",
                    "select inbox_order,attendance_rule from expense_claim where 1=0",
                    "select committed_delta,spent_delta,period_key from budget_posting where 1=0",
                    "select reviewed_draft_id,review_hash from policy_publication where 1=0",
                    "select head_id,branch_id,amount from branch_allocation where 1=0",
                    "select source_period,target_period,review_hash from scheduled_policy where 1=0",
                    "select job_instance_id from batch_job_instance where 1=0"
            };
            for (String sql : probes) {
                try (var columns = query.executeQuery(sql)) {
                    if (columns.next()) throw new IllegalStateException("Expected metadata-only query");
                }
            }
            System.out.printf(Locale.ROOT,"Oracle JDBC %s | transport %s | schema queries %d%n",
                    db.getMetaData().getDriverVersion(),args[0],probes.length);
        } catch (SQLException failure) {
            System.err.printf("Database check failed: SQLState %s, vendor code %d%n",
                    failure.getSQLState(),failure.getErrorCode());
            System.exit(1);
        }
    }

    private static String setting(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + name);
        return value;
    }
}

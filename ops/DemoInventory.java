import java.sql.*;

/** Read-only inventory for the deployment schema. No visitor identifiers are printed. */
class DemoInventory {
    public static void main(String[] args) throws Exception {
        try (Connection db = DriverManager.getConnection(System.getenv("BUDGET_DB_URL"),
                System.getenv("BUDGET_DB_USER"), System.getenv("BUDGET_DB_PASSWORD"));
             Statement s = db.createStatement()) {
            s.execute("set transaction read only");
            count(s, "schema_allocated_bytes", "select coalesce(sum(bytes),0) from user_segments");
            count(s, "schema_data_quota_bytes", "select coalesce(max(max_bytes),0) from user_ts_quotas where tablespace_name='DATA'");
            try {
                count(s,"data_tablespace_used_bytes","select m.used_space*t.block_size from dba_tablespace_usage_metrics m join dba_tablespaces t on t.tablespace_name=m.tablespace_name where m.tablespace_name='DATA'");
            } catch(SQLException restricted) {
                System.out.println("data_tablespace_used_bytes=unavailable (Oracle code "+restricted.getErrorCode()+")");
            }
            count(s, "workspaces", "select count(*) from budget_workspace");
            count(s, "visitor_pairs", "select count(*) from budget_workspace where parent_workspace is not null");
            count(s, "claims", "select count(*) from expense_claim");
            count(s, "pending_deliveries", "select count(*) from portal_outbox where delivered=0");
            long tracked = count(s, "activity_columns", "select count(*) from user_tab_cols where table_name='BUDGET_WORKSPACE' and column_name='LAST_ACTIVE_AT'");
            if (tracked == 0) System.out.println("eligible_pairs=unknown (no last activity history)");
            else count(s, "eligible_pairs", "select count(*) from budget_workspace p join budget_workspace c on c.parent_workspace=p.workspace_id where p.last_active_at < current_timestamp - interval '30' day and p.period_state <> 'CLOSING' and c.period_state <> 'CLOSING' and not exists (select 1 from portal_outbox o where o.workspace_id in (p.workspace_id,c.workspace_id) and o.delivered=0)");
        }
    }
    static long count(Statement s,String label,String sql) throws SQLException {
        try(ResultSet r=s.executeQuery(sql)) { r.next(); long n=r.getLong(1); System.out.println(label+"="+n); return n; }
    }
}

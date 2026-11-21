package kr.budget.retention;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoRetention {
    private final JdbcTemplate db;
    private static final String ELIGIBLE = """
            from budget_workspace p join budget_workspace c on c.parent_workspace=p.workspace_id
            where p.last_active_at < current_timestamp - interval '30' day
            and p.period_state <> 'CLOSING' and c.period_state <> 'CLOSING'
            and not exists (select 1 from portal_outbox o where o.workspace_id in (p.workspace_id,c.workspace_id) and o.delivered=0)
            """;
    private static final List<String> TABLES = List.of("claim_version","claim_replacement","evidence_replacement",
            "claim_evidence","claim_review_event","submission_receipt","budget_posting","portal_outbox","expense_claim",
            "scheduled_policy","policy_publication","policy_draft","budget_statement_line","budget_statement_balance");

    public DemoRetention(JdbcTemplate db) { this.db = db; }

    public List<String> candidates() {
        return db.queryForList("select p.workspace_id "+ELIGIBLE+" order by p.last_active_at",String.class);
    }

    @Transactional
    public boolean removeIfExpired(String parent) {
        if (db.queryForList("select workspace_id from budget_workspace where workspace_id=? for update",String.class,parent).isEmpty()) return false;
        var children = db.queryForList("select c.workspace_id "+ELIGIBLE+" and p.workspace_id=?",String.class,parent);
        if (children.isEmpty()) return false;
        String child = children.getFirst();
        for (String space : List.of(child,parent)) {
            db.update("insert into demo_portal_cleanup(workspace_id) select distinct workspace_id from portal_outbox where workspace_id=?",space);
            db.update("insert into demo_receipt_cleanup(workspace_id,event_id) select workspace_id,event_id from portal_outbox where workspace_id=?",space);
            removeBatchHistory(space);
        }
        db.update("delete from branch_allocation where head_id=? and branch_id=?",parent,child);
        for (String table : TABLES) db.update("delete from "+table+" where workspace_id in (?,?)",parent,child);
        db.update("delete from budget_workspace where workspace_id=?",child);
        db.update("delete from budget_workspace where workspace_id=?",parent);
        return true;
    }

    private void removeBatchHistory(String space) {
        var jobs = db.queryForList("""
                select distinct j.job_instance_id from batch_job_instance j
                join batch_job_execution e on e.job_instance_id=j.job_instance_id
                join batch_job_execution_params p on p.job_execution_id=e.job_execution_id
                where j.job_name='departmentMonthEnd' and p.parameter_name='workspace' and p.parameter_value=?
                """,Long.class,space);
        for (Long job : jobs) {
            String executions="select job_execution_id from batch_job_execution where job_instance_id=?";
            db.update("delete from batch_step_execution_context where step_execution_id in (select step_execution_id from batch_step_execution where job_execution_id in ("+executions+"))",job);
            for(String table: List.of("batch_step_execution","batch_job_execution_context","batch_job_execution_params"))
                db.update("delete from "+table+" where job_execution_id in ("+executions+")",job);
            db.update("delete from batch_job_execution where job_instance_id=?",job);
            db.update("delete from batch_job_instance where job_instance_id=?",job);
        }
    }
}

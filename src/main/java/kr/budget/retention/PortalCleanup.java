package kr.budget.retention;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortalCleanup {
    private final JdbcTemplate db;
    private final String defaultsFile;

    public PortalCleanup(JdbcTemplate db, @Value("${budget.cleanup.mysql-file:/etc/department-budget/cleanup.cnf}") String defaultsFile) {
        this.db=db;
        this.defaultsFile=defaultsFile;
    }

    public List<String> pending() {
        return db.queryForList("select workspace_id from demo_portal_cleanup order by workspace_id",String.class);
    }

    @Transactional(rollbackFor=Exception.class)
    public void deliver(String space) throws IOException, InterruptedException {
        if(db.queryForList("select workspace_id from demo_portal_cleanup where workspace_id=? for update",String.class,space).isEmpty()) return;
        var events=db.queryForList("select event_id from demo_receipt_cleanup where workspace_id=?",String.class,space);
        StringBuilder sql=new StringBuilder("start transaction;\n");
        for(String event:events) sql.append("delete from budget_event_receipt where event_id='").append(UUID.fromString(event)).append("';\n");
        sql.append("delete from budget_claim_summary where workspace_id='").append(UUID.fromString(space)).append("';\ncommit;\n");
        Process mysql=new ProcessBuilder("mysql","--defaults-file="+defaultsFile,"--connect-timeout=5","--batch","budget")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            try(var input=mysql.getOutputStream()) { input.write(sql.toString().getBytes(StandardCharsets.US_ASCII)); }
            if(!mysql.waitFor(60,TimeUnit.SECONDS) || mysql.exitValue()!=0) throw new IOException("Portal cleanup was not acknowledged");
        } finally {mysql.destroyForcibly();}
        db.update("delete from demo_receipt_cleanup where workspace_id=?",space);
        db.update("delete from demo_portal_cleanup where workspace_id=?",space);
    }
}

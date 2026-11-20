package kr.budget.claims;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.junit.jupiter.api.Assertions.*;

class ScheduledPolicyMigrationTest {
    @Test
    void migratesExistingReservationAndRejectsTheLegacyPeriodUpdate() throws Exception {
        Resource[] scripts = new PathMatchingResourcePatternResolver().getResources("classpath*:db/V*.sql");
        Arrays.sort(scripts,Comparator.comparing(Resource::getFilename));
        try (Connection db = DriverManager.getConnection("jdbc:h2:mem:scheduled_upgrade;MODE=Oracle");
             var sql = db.createStatement()) {
            for (Resource script : scripts) {
                if (script.getFilename().startsWith("V021")) break;
                ScriptUtils.executeSqlScript(db,script);
            }
            sql.executeUpdate("insert into budget_workspace(workspace_id,period_state) values('w','CLOSED')");
            sql.executeUpdate("insert into policy_draft(workspace_id,draft_id,category,base_generation,formula) values('w','d','CONFERENCE',0,'eligible_cost')");
            sql.executeUpdate("insert into scheduled_policy(workspace_id,category,target_period,draft_id,review_hash) values('w','CONFERENCE','2026-12','d','review')");
            String legacy = "update budget_workspace set period_key='2026-12',period_state='OPEN' where workspace_id='w'";
            db.setAutoCommit(false);
            assertEquals(1,sql.executeUpdate(legacy));
            db.rollback();
            db.setAutoCommit(true);
            var migration = Arrays.stream(scripts).filter(s->s.getFilename().startsWith("V021")).findFirst().orElseThrow();
            ScriptUtils.executeSqlScript(db,migration);
            var failure = assertThrows(SQLException.class,()->sql.executeUpdate(legacy));
            assertTrue(failure.getSQLState().startsWith("23"));
            try (var row = sql.executeQuery("select source_period,target_period,draft_id,review_hash from scheduled_policy")) {
                assertTrue(row.next());
                assertEquals("2026-11",row.getString(1));
                assertEquals("2026-12",row.getString(2));
                assertEquals("d",row.getString(3));
                assertEquals("review",row.getString(4));
                assertFalse(row.next());
            }
        }
    }
}

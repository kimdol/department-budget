package kr.budget.claims;

import java.util.UUID;
import kr.budget.retention.PortalCleanup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEST_CLEANUP_MYSQL_CONFIG",matches=".+")
class PortalRetentionTest {
    @Test
    void missingCredentialKeepsTheQueueAndConfirmedRetryRemovesIt() throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:portalCleanup;DB_CLOSE_DELAY=-1","sa","");
        JdbcTemplate db=new JdbcTemplate(source);
        db.execute("create table demo_portal_cleanup(workspace_id varchar(36) primary key)");
        db.execute("create table demo_receipt_cleanup(workspace_id varchar(36),event_id varchar(36))");
        String space=UUID.fromString(System.getenv("TEST_CLEANUP_SPACE")).toString();
        String event=UUID.fromString(System.getenv("TEST_CLEANUP_EVENT")).toString();
        db.update("insert into demo_portal_cleanup values(?)",space);
        db.update("insert into demo_receipt_cleanup values(?,?)",space,event);
        String config=System.getenv("TEST_CLEANUP_MYSQL_CONFIG");
        var offline=new PortalCleanup(db,config+".missing");
        assertThrows(java.io.IOException.class,()->offline.deliver(space));
        assertEquals(java.util.List.of(space),offline.pending());
        var online=new PortalCleanup(db,config);
        online.deliver(space);
        assertTrue(online.pending().isEmpty());
        assertEquals(0,db.queryForObject("select count(*) from demo_receipt_cleanup",Integer.class));
        online.deliver(space);
    }
}

package kr.budget.retention;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name="budget.cleanup.enabled",havingValue="true")
public class DailyCleanup {
    private static final Logger log=LoggerFactory.getLogger(DailyCleanup.class);
    private final DemoRetention retention;
    private final PortalCleanup portal;

    public DailyCleanup(DemoRetention retention,PortalCleanup portal) { this.retention=retention;this.portal=portal; }

    @Scheduled(cron="0 15 3 * * *",zone="Asia/Seoul")
    public void run() {
        int deleted=0;
        for(String parent:retention.candidates()) {
            try { if(retention.removeIfExpired(parent)) deleted++; }
            catch(org.springframework.dao.DataAccessException error) {
                log.error("Demo cleanup rolled back a visitor pair",error);
            }
        }
        var pending=portal.pending();
        log.info("Demo cleanup removed {} visitor pairs; {} portal spaces pending",deleted,pending.size());
        for(String space:pending) {
            try { portal.deliver(space); }
            catch(InterruptedException interrupted) {Thread.currentThread().interrupt();return;}
            catch(java.io.IOException offline) {log.warn("Portal cleanup remains queued: {}",offline.getMessage());return;}
        }
    }
}

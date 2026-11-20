package kr.budget.portal;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface PortalOutbox {
    record Event(String eventId, String workspaceId, String claimId, int sequenceNo, String eventType, long amount) {}
    record Failure(String eventId, String claimId, String title, int sequenceNo, long amount, long attempts, String lastError) {}

    @Select("""
        select p.event_id,p.claim_id,c.title,p.sequence_no,p.amount,p.attempts,p.last_error
        from portal_outbox p join expense_claim c on c.workspace_id=p.workspace_id and c.claim_id=p.claim_id
        where p.workspace_id=#{workspace} and p.delivered=0 and p.last_error is not null
        order by p.created_at,p.event_id fetch first 50 rows only
        """)
    List<Failure> failures(String workspace);

    @Select("""
        select event_id,workspace_id,claim_id,sequence_no,event_type,amount from portal_outbox p
        where workspace_id=#{workspace} and event_id=#{eventId} and delivered=0 and last_error is not null
        and not exists (select 1 from portal_outbox q where q.claim_id=p.claim_id and q.sequence_no<p.sequence_no and q.delivered=0)
        """)
    Event retryable(String workspace, String eventId);

    @Insert("""
        insert into portal_outbox(event_id,workspace_id,claim_id,sequence_no,event_type,amount)
        values(#{eventId},#{workspaceId},#{claimId},#{sequenceNo},#{eventType},#{amount})
        """)
    void append(Event event);

    @Select("""
        select event_id,workspace_id,claim_id,sequence_no,event_type,amount from portal_outbox p
        where delivered=0 and not exists (select 1 from portal_outbox q
        where q.claim_id=p.claim_id and q.sequence_no<p.sequence_no and q.delivered=0)
        order by attempts,created_at,event_id fetch first 40 rows only
        """)
    List<Event> pending();

    @Update("update portal_outbox set delivered=1,attempts=attempts+1,last_error=null where event_id=#{id} and delivered=0")
    int acknowledged(String id);

    @Update("update portal_outbox set attempts=attempts+1,last_error=#{error} where event_id=#{id} and delivered=0")
    int failed(String id, String error);
}

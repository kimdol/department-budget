package kr.budget.portal;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface PortalOutbox {
    record Event(String eventId, String workspaceId, String claimId, int sequenceNo, String eventType, long amount) {}

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
    void acknowledged(String id);

    @Update("update portal_outbox set attempts=attempts+1,last_error=#{error} where event_id=#{id} and delivered=0")
    void failed(String id, String error);
}

package kr.budget.claims;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface BudgetMapper {
    @Update("update budget_workspace set parent_workspace=#{primaryId} where workspace_id=#{secondaryId}")
    void linkDepartments(String primaryId, String secondaryId);
    @Insert("insert into budget_workspace (workspace_id,period_key,organization_type) values (#{workspace},#{period},#{organization})")
    void open(String workspace, String period, OrganizationType organization);

    @Select("select organization_type from budget_workspace where workspace_id=#{workspace}")
    OrganizationType organization(String workspace);

    @Select("select period_state from budget_workspace where workspace_id=#{workspace}")
    String periodState(String workspace);

    @Select("select allocation, committed, spent from budget_workspace where workspace_id=#{workspace}")
    Balance balance(String workspace);

    @Select("select allocation, committed, spent from budget_workspace where workspace_id=#{workspace} for update")
    Balance lockBalance(String workspace);

    @Insert("""
        insert into expense_claim (claim_id, workspace_id, title, category, status, estimated, days, formula, policy_generation,evaluator_version,approval_route,inbox_order,attendance_rule)
        values (#{id}, #{workspace}, #{title}, #{category}, 'SUBMITTED', #{estimated}, #{days}, #{formula}, #{generation},#{version},#{route},claim_inbox_seq.NEXTVAL,#{attendanceRule})
        """)
    void submit(String id, String workspace, String title, String category, long estimated, int days, String formula, long generation,String version,ApprovalRoute route,String attendanceRule);

    String COLUMNS = "claim_id,title,category,status,estimated,days,formula,policy_generation,approved,actual,settled,review_round,review_note,evaluator_version,approval_route,inbox_order,attendance_rule,applicant_id";

    @Update("update expense_claim set status='FINANCE_PENDING' where workspace_id=#{workspace} and claim_id=#{id}")
    void forwardToFinance(String workspace,String id);

    @Select("select " + COLUMNS + " from expense_claim where workspace_id=#{workspace} and claim_id = " +
            "(select claim_id from submission_receipt where workspace_id=#{workspace} and request_key=#{key})")
    Claim previousSubmission(String workspace, String key);

    @Insert("insert into submission_receipt (workspace_id,request_key,claim_id) values (#{workspace},#{key},#{id})")
    void rememberSubmission(String workspace, String key, String id);

    @Select("select replacement_id from claim_replacement where workspace_id=#{workspace} and original_id=#{id}")
    String replacement(String workspace,String id);

    @Insert("insert into claim_replacement(workspace_id,original_id,replacement_id) values(#{workspace},#{id},#{replacement})")
    void linkReplacement(String workspace,String id,String replacement);

    record VersionKey(String seriesId,int versionNo) {}
    record VersionRow(String seriesId,int versionNo,String claimId,String title,long estimated,int days,String status) {}

    @Select("select series_id,version_no from claim_version where workspace_id=#{workspace} and claim_id=#{id}")
    VersionKey versionKey(String workspace,String id);

    @Insert("insert into claim_version(workspace_id,series_id,version_no,claim_id) values(#{workspace},#{series},#{version},#{id})")
    void addVersion(String workspace,String series,int version,String id);

    @Select("""
        select v.series_id,v.version_no,c.claim_id,c.title,c.estimated,c.days,c.status
        from claim_version v join expense_claim c on c.workspace_id=v.workspace_id and c.claim_id=v.claim_id
        where v.workspace_id=#{workspace} and v.series_id=#{series} order by v.version_no
        """)
    List<VersionRow> versions(String workspace,String series);

    @Select("select " + COLUMNS + " from expense_claim where workspace_id=#{workspace} and claim_id=#{id} for update")
    Claim lockClaim(String workspace, String id);

    @Select("select " + COLUMNS + " from expense_claim where workspace_id=#{workspace} and claim_id=#{id}")
    Claim findClaim(String workspace, String id);

    record InboxItem(String claimId, String title, String category, String status, long estimated,
                     long approved, Long actual, long settled, int reviewRound, String reviewNote,
                     ApprovalRoute approvalRoute, long inboxOrder) {}

    String INBOX_COLUMNS = "claim_id,title,category,status,estimated,approved,actual,settled,review_round,review_note,approval_route,inbox_order";

    @Select("select " + INBOX_COLUMNS + " from expense_claim where workspace_id=#{workspace} and inbox_order < #{before} order by inbox_order desc fetch first 51 rows only")
    List<InboxItem> inbox(String workspace,long before);

    @Select("select " + INBOX_COLUMNS + " from expense_claim where workspace_id=#{workspace} and status=#{status} and inbox_order < #{before} order by inbox_order desc fetch first 51 rows only")
    List<InboxItem> inboxByStatus(String workspace,long before,String status);

    @Select({"<script>", "select " + INBOX_COLUMNS + " from expense_claim",
            "where workspace_id=#{workspace} and applicant_id=#{memberId} and inbox_order &lt; #{before}",
            "<if test='status != null'>and status=#{status}</if>",
            "order by inbox_order desc fetch first 51 rows only", "</script>"})
    List<InboxItem> employeeInbox(String workspace,String memberId,long before,String status);

    default List<InboxItem> claims(String workspace) {
        return inbox(workspace,Long.MAX_VALUE).stream().limit(50).toList();
    }

    @Update("update expense_claim set status='APPROVED', approved=#{approved} where workspace_id=#{workspace} and claim_id=#{id}")
    void approve(String workspace, String id, long approved);

    @Update("update expense_claim set status='CANCELLED' where workspace_id=#{workspace} and claim_id=#{id}")
    void cancel(String workspace, String id);

    @Update("update expense_claim set status=#{status}, actual=#{actual}, review_round=review_round+1, review_note=null where workspace_id=#{workspace} and claim_id=#{id}")
    void review(String workspace, String id, long actual, String status);

    @Update("update expense_claim set status='IN_REVIEW', approved=#{amount} where workspace_id=#{workspace} and claim_id=#{id}")
    void increase(String workspace, String id, long amount);

    @Update("update expense_claim set status='NEEDS_RECEIPT', review_note=#{reason} where workspace_id=#{workspace} and claim_id=#{id}")
    void returnReview(String workspace, String id, String reason);

    @Insert("""
        insert into claim_review_event (workspace_id,claim_id,review_round,event_type,actor_role,amount,note,actor_member_id)
        values (#{workspace},#{id},#{round},#{type},#{actor},#{amount},#{note,jdbcType=VARCHAR},#{member})
        """)
    void recordEvent(String workspace,String id,int round,String type,String actor,long amount,String note,String member);

    default void event(String workspace,String id,int round,String type,String actor,long amount,String note) {
        String member = switch (actor) {
            case "EMPLOYEE" -> "STAFF_01";
            case "MANAGER" -> "HEAD_01";
            case "FINANCE" -> "FIN_01";
            default -> throw new IllegalArgumentException("알 수 없는 시연 역할입니다.");
        };
        recordEvent(workspace,id,round,type,actor,amount,note,member);
    }

    record ReviewEvent(int reviewRound, String eventType, String actorRole, Long amount,
                       String note, java.time.LocalDateTime recordedAt,String actorMemberId) {}

    @Select("""
        select review_round,event_type,actor_role,amount,note,recorded_at,actor_member_id from claim_review_event
        where workspace_id=#{workspace} and claim_id=#{id}
        order by review_round, recorded_at, case event_type when 'SUBMIT' then 0 when 'FORWARD_FINANCE' then 1
            when 'FINAL_APPROVAL' then 2 when 'SUBMIT_ACTUAL' then 3 when 'APPROVE_INCREASE' then 4 else 5 end
        """)
    List<ReviewEvent> history(String workspace, String id);

    @Update("update expense_claim set status='SETTLED', settled=#{settled} where workspace_id=#{workspace} and claim_id=#{id}")
    void settle(String workspace, String id, long settled);

    @Update("update budget_workspace set committed=committed+#{reserved}, spent=spent+#{spent} where workspace_id=#{workspace}")
    void changeBalance(String workspace, long reserved, long spent);

    @Insert("""
        insert into budget_posting (workspace_id,claim_id,kind,committed_delta,spent_delta,period_key)
        select workspace_id,#{id},#{kind},#{reserved},#{spent},period_key from budget_workspace where workspace_id=#{workspace}
        """)
    void post(String workspace, String id, String kind, long reserved, long spent);
}

package kr.budget.claims;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface BudgetMapper {
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
        insert into expense_claim (claim_id, workspace_id, title, category, status, estimated, days, formula, policy_generation,evaluator_version)
        values (#{id}, #{workspace}, #{title}, #{category}, 'SUBMITTED', #{estimated}, #{days}, #{formula}, #{generation},#{version})
        """)
    void submit(String id, String workspace, String title, String category, long estimated, int days, String formula, long generation,String version);

    String COLUMNS = "claim_id,title,category,status,estimated,days,formula,policy_generation,approved,actual,settled,review_round,review_note,evaluator_version";

    @Select("select " + COLUMNS + " from expense_claim where workspace_id=#{workspace} and claim_id = " +
            "(select claim_id from submission_receipt where workspace_id=#{workspace} and request_key=#{key})")
    Claim previousSubmission(String workspace, String key);

    @Insert("insert into submission_receipt (workspace_id,request_key,claim_id) values (#{workspace},#{key},#{id})")
    void rememberSubmission(String workspace, String key, String id);

    @Select("select " + COLUMNS + " from expense_claim where workspace_id=#{workspace} and claim_id=#{id} for update")
    Claim lockClaim(String workspace, String id);

    @Select("select " + COLUMNS + " from expense_claim where workspace_id=#{workspace} order by claim_id fetch first 50 rows only")
    List<Claim> claims(String workspace);

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
        insert into claim_review_event (workspace_id,claim_id,review_round,event_type,actor_role,amount,note)
        values (#{workspace},#{id},#{round},#{type},#{actor},#{amount},#{note,jdbcType=VARCHAR})
        """)
    void event(String workspace, String id, int round, String type, String actor, long amount, String note);

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

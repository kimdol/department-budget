package kr.budget.claims;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface BudgetMapper {
    @Insert("insert into budget_workspace (workspace_id) values (#{workspace})")
    void open(String workspace);

    @Select("select allocation, committed, spent from budget_workspace where workspace_id=#{workspace}")
    Balance balance(String workspace);

    @Select("select allocation, committed, spent from budget_workspace where workspace_id=#{workspace} for update")
    Balance lockBalance(String workspace);

    @Insert("""
        insert into expense_claim (claim_id, workspace_id, title, category, status, estimated, days, formula)
        values (#{id}, #{workspace}, #{title}, #{category}, 'SUBMITTED', #{estimated}, #{days}, #{formula})
        """)
    void submit(String id, String workspace, String title, String category, long estimated, int days, String formula);

    String COLUMNS = "claim_id,title,category,status,estimated,days,formula,approved,actual,settled";

    @Select("select " + COLUMNS + " from expense_claim where workspace_id=#{workspace} and claim_id=#{id} for update")
    Claim lockClaim(String workspace, String id);

    @Select("select " + COLUMNS + " from expense_claim where workspace_id=#{workspace} order by claim_id fetch first 50 rows only")
    List<Claim> claims(String workspace);

    @Update("update expense_claim set status='APPROVED', approved=#{approved} where workspace_id=#{workspace} and claim_id=#{id}")
    void approve(String workspace, String id, long approved);

    @Update("update expense_claim set status='IN_REVIEW', actual=#{actual} where workspace_id=#{workspace} and claim_id=#{id}")
    void review(String workspace, String id, long actual);

    @Update("update expense_claim set status='SETTLED', settled=#{settled} where workspace_id=#{workspace} and claim_id=#{id}")
    void settle(String workspace, String id, long settled);

    @Update("update budget_workspace set committed=committed+#{reserved}, spent=spent+#{spent} where workspace_id=#{workspace}")
    void changeBalance(String workspace, long reserved, long spent);

    @Insert("""
        insert into budget_posting (workspace_id,claim_id,kind,committed_delta,spent_delta)
        values (#{workspace},#{id},#{kind},#{reserved},#{spent})
        """)
    void post(String workspace, String id, String kind, long reserved, long spent);
}

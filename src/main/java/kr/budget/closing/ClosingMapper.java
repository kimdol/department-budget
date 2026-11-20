package kr.budget.closing;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ClosingMapper {
    record Period(String periodKey, String periodState) {}
    record Line(String category, long claimCount, long spent) {}
    record Totals(long reserved, long spent) {}
    record Posting(String claimId, String kind, long committedDelta, long spentDelta) {}

    @Select("select period_key, period_state from budget_workspace where workspace_id=#{workspace}")
    Period period(String workspace);

    @Select("select claim_id from expense_claim where workspace_id=#{workspace} and status not in ('SETTLED','CANCELLED') order by claim_id")
    List<String> pending(String workspace);

    @Select("""
        select coalesce(sum(committed_delta),0) reserved, coalesce(sum(spent_delta),0) spent
        from budget_posting where workspace_id=#{workspace}
        """)
    Totals totals(String workspace);

    @Update("update budget_workspace set period_state=#{state} where workspace_id=#{workspace}")
    void setState(String workspace, String state);

    @Insert("""
        insert into budget_statement_line (workspace_id,period_key,category,claim_count,spent)
        select c.workspace_id,#{period},c.category,count(*),sum(p.spent_delta)
        from expense_claim c join budget_posting p on p.claim_id=c.claim_id and p.workspace_id=c.workspace_id
        where c.workspace_id=#{workspace} and p.kind='SETTLEMENT' group by c.workspace_id,c.category
        """)
    void freezeLines(String workspace, String period);

    @Select("select category,claim_count,spent from budget_statement_line where workspace_id=#{workspace} and period_key=#{period} order by category")
    List<Line> lines(String workspace, String period);

    @Select("select claim_id,kind,committed_delta,spent_delta from budget_posting where workspace_id=#{workspace} and claim_id=#{id} order by posted_at,kind")
    List<Posting> postings(String workspace, String id);

    @Select("""
        select c.category,count(*) claim_count,sum(p.spent_delta) spent
        from expense_claim c join budget_posting p on p.claim_id=c.claim_id and p.workspace_id=c.workspace_id
        where c.workspace_id=#{workspace} and p.kind='SETTLEMENT' group by c.category order by c.category
        """)
    List<Line> currentLines(String workspace);
}

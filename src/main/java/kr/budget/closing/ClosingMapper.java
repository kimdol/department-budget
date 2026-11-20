package kr.budget.closing;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ClosingMapper {
    record Period(String periodKey, String periodState) {}
    record Line(String category, long claimCount, long spent) {}
    record Totals(long reserved, long spent) {}
    record Posting(String claimId, String kind, long committedDelta, long spentDelta, String periodKey, String originalKind) {}

    @Select("select period_key, period_state from budget_workspace where workspace_id=#{workspace}")
    Period period(String workspace);

    @Update("update budget_workspace set period_key=#{next},period_state='OPEN' where workspace_id=#{workspace}")
    void advance(String workspace,String next);

    @Insert("""
        insert into budget_posting(workspace_id,claim_id,kind,committed_delta,spent_delta,period_key,original_kind)
        values (#{workspace},#{id},#{kind},0,#{delta},#{period},'SETTLEMENT')
        """)
    void reverse(String workspace,String id,String kind,long delta,String period);

    @Select("select claim_id from expense_claim where workspace_id=#{workspace} and status not in ('SETTLED','CANCELLED') order by claim_id")
    List<String> pending(String workspace);

    @Select("""
        select coalesce(sum(committed_delta),0) reserved, coalesce(sum(spent_delta),0) spent
        from budget_posting where workspace_id=#{workspace}
        """)
    Totals totals(String workspace);

    @Update("""
        BEGIN
          SELECT NVL(SUM(committed_delta),0) - #{committed,jdbcType=NUMERIC},
                 NVL(SUM(spent_delta),0) - #{spent,jdbcType=NUMERIC}
          INTO #{committedDifference,mode=OUT,jdbcType=NUMERIC,javaType=java.math.BigDecimal},
               #{spentDifference,mode=OUT,jdbcType=NUMERIC,javaType=java.math.BigDecimal}
          FROM budget_posting WHERE workspace_id=#{workspace,jdbcType=VARCHAR};
        END;
        """)
    @Options(statementType=org.apache.ibatis.mapping.StatementType.CALLABLE)
    void compareOracle(java.util.Map<String,Object> values);

    @Update("update budget_workspace set period_state=#{state} where workspace_id=#{workspace}")
    void setState(String workspace, String state);

    @Insert("""
        insert into budget_statement_balance (workspace_id,period_key,allocation,committed,spent)
        select workspace_id,period_key,allocation,committed,spent from budget_workspace
        where workspace_id=#{workspace} and period_key=#{period}
        """)
    void freezeBalance(String workspace, String period);

    @Select("select allocation,committed,spent from budget_statement_balance where workspace_id=#{workspace} and period_key=#{period}")
    kr.budget.claims.Balance archivedBalance(String workspace, String period);

    @Insert("""
        insert into budget_statement_line (workspace_id,period_key,category,claim_count,spent)
        select c.workspace_id,#{period},c.category,count(distinct c.claim_id),sum(p.spent_delta)
        from expense_claim c join budget_posting p on p.claim_id=c.claim_id and p.workspace_id=c.workspace_id
        where c.workspace_id=#{workspace} and p.period_key=#{period} and (p.kind='SETTLEMENT' or p.original_kind='SETTLEMENT') group by c.workspace_id,c.category
        """)
    void freezeLines(String workspace, String period);

    @Select("select category,claim_count,spent from budget_statement_line where workspace_id=#{workspace} and period_key=#{period} order by category")
    List<Line> lines(String workspace, String period);

    @Select("select claim_id,kind,committed_delta,spent_delta,period_key,original_kind from budget_posting where workspace_id=#{workspace} and claim_id=#{id} order by posted_at,kind")
    List<Posting> postings(String workspace, String id);

    @Select("""
        select c.category,count(distinct c.claim_id) claim_count,sum(p.spent_delta) spent
        from expense_claim c join budget_posting p on p.claim_id=c.claim_id and p.workspace_id=c.workspace_id
        where c.workspace_id=#{workspace} and p.period_key=#{period} and (p.kind='SETTLEMENT' or p.original_kind='SETTLEMENT') group by c.category order by c.category
        """)
    List<Line> currentLines(String workspace,String period);

    record SourceClaim(String claimId,String title,long inboxOrder,long spent) {}

    @Select("""
        select c.claim_id,c.title,c.inbox_order,sum(p.spent_delta) spent
        from expense_claim c join budget_posting p on p.workspace_id=c.workspace_id and p.claim_id=c.claim_id
        where c.workspace_id=#{workspace} and c.category=#{category} and p.period_key=#{period}
          and (p.kind='SETTLEMENT' or p.original_kind='SETTLEMENT') and c.inbox_order < #{before}
        group by c.claim_id,c.title,c.inbox_order order by c.inbox_order desc fetch first 51 rows only
        """)
    List<SourceClaim> sourceClaims(String workspace,String period,String category,long before);
}

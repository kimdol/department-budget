package kr.budget.claims;

import java.util.List;
import java.util.UUID;
import kr.budget.closing.ClosingMapper;
import org.apache.ibatis.annotations.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BranchAllocation {
    public record Entry(String requestKey, String periodKey, long amount) {}
    public record Report(Balance head, Balance branch, List<Entry> recent) {}

    @Mapper
    public interface AllocationMapper {
        @Select("select workspace_id from budget_workspace where parent_workspace=#{head} and organization_type='BANK'")
        String branch(String head);
        @Select("select amount from branch_allocation where head_id=#{head} and request_key=#{key}")
        Long previous(String head,String key);
        @Update("update budget_workspace set allocation=allocation+#{delta} where workspace_id=#{workspace}")
        void adjust(String workspace,long delta);
        @Insert("insert into branch_allocation(head_id,request_key,branch_id,period_key,amount) values(#{head},#{key},#{branch},#{period},#{amount})")
        void append(String head,String key,String branch,String period,long amount);
        @Select("select request_key,period_key,amount from branch_allocation where head_id=#{head} order by recorded_at desc,request_key fetch first 20 rows only")
        List<Entry> recent(String head);
    }

    private final BudgetMapper budgets;
    private final ClosingMapper closing;
    private final AllocationMapper allocations;

    public BranchAllocation(BudgetMapper budgets, ClosingMapper closing, AllocationMapper allocations) {
        this.budgets = budgets;
        this.closing = closing;
        this.allocations = allocations;
    }

    private String branchOf(String head) {
        String branch = allocations.branch(head);
        if (branch == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return branch;
    }

    @Transactional
    public Report report(String head) {
        String branch = branchOf(head);
        return new Report(budgets.lockBalance(head),budgets.lockBalance(branch),allocations.recent(head));
    }

    @Transactional
    public void allocate(String head, UUID requestKey, long amount) {
        String branch = branchOf(head);
        var balance = budgets.lockBalance(head);
        Long previous = allocations.previous(head,requestKey.toString());
        if (previous != null) {
            if (previous != amount) throw new ResponseStatusException(HttpStatus.CONFLICT);
            return;
        }
        budgets.lockBalance(branch);
        var source = closing.period(head);
        var destination = closing.period(branch);
        if (!source.equals(destination) || !source.periodState().equals("OPEN"))
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        if (amount <= 0 || amount > balance.getAvailable()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        allocations.adjust(head,-amount);
        allocations.adjust(branch,amount);
        allocations.append(head,requestKey.toString(),branch,source.periodKey(),amount);
    }
}

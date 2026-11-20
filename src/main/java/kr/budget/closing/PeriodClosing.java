package kr.budget.closing;

import java.util.List;
import kr.budget.claims.Balance;
import kr.budget.claims.BudgetMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PeriodClosing {
    public record Statement(String period, String state, Balance balance, List<ClosingMapper.Line> lines,
                            List<String> pending, LedgerReconciliation.Difference difference) {}
    private final BudgetMapper budgets;
    private final ClosingMapper ledger;

    private final LedgerReconciliation reconciliation;
    public PeriodClosing(BudgetMapper budgets, ClosingMapper ledger, LedgerReconciliation reconciliation) {
        this.budgets = budgets;
        this.ledger = ledger;
        this.reconciliation = reconciliation;
    }

    @Transactional
    public Statement statement(String workspace) {
        Balance balance = budgets.lockBalance(workspace);
        var period = ledger.period(workspace);
        boolean closed = period.periodState().equals("CLOSED");
        if (closed) return archive(workspace, period.periodKey());
        return new Statement(period.periodKey(), period.periodState(), balance, ledger.currentLines(workspace, period.periodKey()),
                ledger.pending(workspace),reconciliation.compare(workspace,balance));
    }

    @Transactional
    public Statement advance(String workspace, String closedPeriod) {
        budgets.lockBalance(workspace);
        var current = ledger.period(workspace);
        if (ledger.archivedBalance(workspace, closedPeriod) == null) throw new ResponseStatusException(HttpStatus.CONFLICT);
        String next = java.time.YearMonth.parse(closedPeriod).plusMonths(1).toString();
        if (current.periodKey().equals(next)) return statement(workspace);
        if (!current.periodKey().equals(closedPeriod) || !current.periodState().equals("CLOSED"))
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        ledger.advance(workspace,next);
        return statement(workspace);
    }

    @Transactional
    public void reverse(String workspace, String id, int round, long amount, String expectedPeriod) {
        budgets.lockBalance(workspace);
        var period = ledger.period(workspace);
        var postings = ledger.postings(workspace,id);
        var original = postings.stream().filter(p -> p.kind().equals("SETTLEMENT")).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (round < 1 || amount < 1) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        String kind = "REV_" + round;
        var previous = postings.stream().filter(p -> p.kind().equals(kind)).findFirst();
        if (previous.isPresent()) {
            if (previous.get().spentDelta() == -amount && previous.get().periodKey().equals(expectedPeriod)) return;
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        if (!period.periodState().equals("OPEN") || !period.periodKey().equals(expectedPeriod)
                || original.periodKey().compareTo(expectedPeriod) >= 0
                || round != 1 + postings.stream().filter(p -> p.originalKind() != null).count())
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        long remaining = postings.stream().mapToLong(ClosingMapper.Posting::spentDelta).sum();
        if (amount > remaining) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        ledger.reverse(workspace,id,kind,-amount,expectedPeriod);
        budgets.changeBalance(workspace,0,-amount);
    }

    public Statement archive(String workspace, String period) {
        Balance frozen = ledger.archivedBalance(workspace, period);
        if (frozen == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return new Statement(period, "CLOSED", frozen, ledger.lines(workspace, period), List.of(),null);
    }

    @Transactional
    public ClosingMapper.Period prepare(String workspace) {
        Balance balance = budgets.lockBalance(workspace);
        var period = ledger.period(workspace);
        if (period.periodState().equals("OPEN")) {
            verify(workspace, balance);
            ledger.setState(workspace, "CLOSING");
        }
        return period;
    }

    @Transactional
    public void finish(String workspace, String expectedPeriod) {
        Balance balance = budgets.lockBalance(workspace);
        var period = ledger.period(workspace);
        if (!period.periodKey().equals(expectedPeriod)) throw new IllegalStateException("마감 기간이 변경됐습니다.");
        if (period.periodState().equals("CLOSED")) return;
        if (!period.periodState().equals("CLOSING")) throw new IllegalStateException("마감 준비가 필요합니다.");
        verify(workspace, balance);
        ledger.freezeLines(workspace, expectedPeriod);
        ledger.freezeBalance(workspace, expectedPeriod);
        long snapshot = ledger.lines(workspace, expectedPeriod).stream().mapToLong(ClosingMapper.Line::spent).sum();
        var previous = ledger.archivedBalance(workspace,java.time.YearMonth.parse(expectedPeriod).minusMonths(1).toString());
        long opening = previous == null ? 0 : previous.spent();
        if (snapshot + opening != balance.spent()) throw new IllegalStateException("보고서와 집행액이 일치하지 않습니다.");
        ledger.setState(workspace, "CLOSED");
    }

    private void verify(String workspace, Balance balance) {
        if (!ledger.pending(workspace).isEmpty() || balance.committed() != 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"진행 중인 신청을 먼저 처리하세요.");
        if (!reconciliation.compare(workspace,balance).isBalanced())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"원장 대사가 필요합니다.");
    }
}

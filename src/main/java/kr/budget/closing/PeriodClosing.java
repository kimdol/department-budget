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
    public record Statement(String period, String state, Balance balance, List<ClosingMapper.Line> lines, List<String> pending) {}
    private final BudgetMapper budgets;
    private final ClosingMapper ledger;

    public PeriodClosing(BudgetMapper budgets, ClosingMapper ledger) {
        this.budgets = budgets;
        this.ledger = ledger;
    }

    @Transactional
    public Statement statement(String workspace) {
        Balance balance = budgets.lockBalance(workspace);
        var period = ledger.period(workspace);
        boolean closed = period.periodState().equals("CLOSED");
        return new Statement(period.periodKey(), period.periodState(), balance,
                closed ? ledger.lines(workspace, period.periodKey()) : ledger.currentLines(workspace),
                ledger.pending(workspace));
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
        long snapshot = ledger.lines(workspace, expectedPeriod).stream().mapToLong(ClosingMapper.Line::spent).sum();
        if (snapshot != balance.spent()) throw new IllegalStateException("보고서와 집행액이 일치하지 않습니다.");
        ledger.setState(workspace, "CLOSED");
    }

    private void verify(String workspace, Balance balance) {
        var totals = ledger.totals(workspace);
        if (!ledger.pending(workspace).isEmpty() || balance.committed() != 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"진행 중인 신청을 먼저 처리하세요.");
        if (totals.reserved() != balance.committed() || totals.spent() != balance.spent())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"원장 대사가 필요합니다.");
    }
}

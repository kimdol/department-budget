package kr.budget.claims;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import kr.budget.policy.ExpenseFormula;

@Service
@Transactional
public class ClaimService {
    private final BudgetMapper mapper;

    public ClaimService(BudgetMapper mapper) {
        this.mapper = mapper;
    }

    public String open() {
        String workspace = UUID.randomUUID().toString();
        mapper.open(workspace);
        return workspace;
    }

    public String submit(String workspace, String title, String category, long estimated, int days) {
        checkAmount(estimated);
        if (title == null || title.isBlank() || title.length() > 100 || days < 1 || days > 365)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        String formula = switch (category) {
            case "EDUCATION" -> ExpenseFormula.EDUCATION;
            case "TRAVEL" -> ExpenseFormula.TRAVEL;
            default -> throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        };
        String id = UUID.randomUUID().toString();
        mapper.submit(id, workspace, title.strip(), category, estimated, days, formula);
        return id;
    }

    public void approve(String workspace, String id) {
        Balance balance = mapper.lockBalance(workspace);
        Claim claim = locked(workspace, id);
        if (!claim.status().equals("SUBMITTED")) return;
        long amount = amount(claim, claim.estimated());
        if (amount > balance.getAvailable()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        mapper.approve(workspace, id, amount);
        post(workspace, id, "APPROVAL", amount, 0);
    }

    public void review(String workspace, String id, long actual) {
        checkAmount(actual);
        Claim claim = locked(workspace, id);
        if (claim.actual() != null && claim.actual() == actual) return;
        require(claim, "APPROVED");
        if (amount(claim, actual) > claim.approved()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        mapper.review(workspace, id, actual);
    }

    public void settle(String workspace, String id) {
        mapper.lockBalance(workspace);
        Claim claim = locked(workspace, id);
        if (claim.status().equals("SETTLED")) return;
        require(claim, "IN_REVIEW");
        long amount = amount(claim, claim.actual());
        mapper.settle(workspace, id, amount);
        post(workspace, id, "SETTLEMENT", -claim.approved(), amount);
    }

    private Claim locked(String workspace, String id) {
        Claim claim = mapper.lockClaim(workspace, id);
        if (claim == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return claim;
    }

    private void require(Claim claim, String status) {
        if (!claim.status().equals(status)) throw new ResponseStatusException(HttpStatus.CONFLICT);
    }

    private void post(String workspace, String id, String kind, long reserved, long spent) {
        mapper.changeBalance(workspace, reserved, spent);
        mapper.post(workspace, id, kind, reserved, spent);
    }

    private long amount(Claim claim, long cost) {
        return ExpenseFormula.calculate(claim.formula(), new ExpenseFormula.Inputs(cost,
                new BigDecimal("0.80"), 200000, claim.days(), 50000));
    }

    private void checkAmount(long amount) {
        if (amount <= 0 || amount > 999999999999L) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}

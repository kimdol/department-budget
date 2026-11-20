package kr.budget.claims;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EvidenceService {
    public enum Type {
        RECEIPT("지출 영수증"), ATTENDANCE("참석 확인서");
        private final String label;
        Type(String label) { this.label = label; }
    }

    private final EvidenceMapper evidence;
    private final BudgetMapper budgets;

    public EvidenceService(EvidenceMapper evidence, BudgetMapper budgets) {
        this.evidence = evidence;
        this.budgets = budgets;
    }

    @Transactional
    public void attach(String workspace, String id, Type type, long amount) {
        if (amount < 1 || amount > 999999999999L) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        Claim claim = budgets.lockClaim(workspace, id);
        if (claim == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var previous = evidence.find(workspace, id, type.name());
        if (previous != null && previous.receiptAmount() == amount) return;
        if (!List.of("APPROVED","NEEDS_RECEIPT").contains(claim.status())) throw new ResponseStatusException(HttpStatus.CONFLICT);
        if (previous != null) {
            evidence.recordReplacement(workspace,id,type.name(),previous.receiptAmount(),amount,claim.reviewRound());
            evidence.replace(workspace,id,type.name(),amount);
            return;
        }
        evidence.attach(workspace, id, type.name(), amount);
    }

    @Transactional
    public List<String> outstanding(String workspace,String id) {
        return assessment(workspace,id).issues();
    }

    @Transactional
    public EvidencePolicy.Evaluation assessment(String workspace,String id) {
        Claim claim = budgets.lockClaim(workspace,id);
        if (claim == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return assessment(workspace,claim);
    }

    public List<String> outstanding(String workspace,Claim claim) {
        return assessment(workspace,claim).issues();
    }

    private EvidencePolicy.Evaluation assessment(String workspace,Claim claim) {
        var documents = list(workspace,claim.claimId());
        var receipt = documents.stream().filter(d -> d.evidenceType().equals("RECEIPT"))
                .map(EvidenceMapper.Document::receiptAmount).findFirst().orElse(null);
        boolean attendance = documents.stream().anyMatch(d -> d.evidenceType().equals("ATTENDANCE"));
        return EvidencePolicy.evaluate(new EvidencePolicy.Inputs(budgets.organization(workspace),claim.category(),
                claim.actual(),receipt,attendance));
    }

    public List<EvidenceMapper.Document> list(String workspace, String id) {
        return evidence.documents(workspace, id);
    }

    public String download(String workspace, String id, Type type) {
        var document = evidence.find(workspace, id, type.name());
        if (document == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return "부서예산 · 합성 증빙\n" + type.label + "\n신청번호 " + id + "\n기재 금액 " + document.receiptAmount() + "원\n";
    }
}

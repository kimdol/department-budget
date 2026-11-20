package kr.budget.web;

import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpSession;
import kr.budget.claims.BudgetMapper;
import kr.budget.claims.ClaimService;
import kr.budget.claims.EvidenceService;
import kr.budget.claims.EvidenceMapper;
import kr.budget.closing.PeriodClosing;
import kr.budget.closing.ClosingService;
import kr.budget.closing.ClosingMapper;
import java.util.List;
import org.springframework.http.ResponseEntity;
import kr.budget.policy.PolicyService;
import kr.budget.policy.PolicyMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class DemoPortal {
    public enum Role { EMPLOYEE, MANAGER, FINANCE }
    private final ClaimService service;
    private final BudgetMapper mapper;
    private final PolicyService policies;
    private final PolicyMapper policyMapper;
    private final EvidenceService evidence;
    private final PeriodClosing periods;
    private final ClosingService closing;
    private final ClosingMapper statements;

    public DemoPortal(ClaimService service, BudgetMapper mapper, PolicyService policies, PolicyMapper policyMapper,
            EvidenceService evidence, PeriodClosing periods, ClosingService closing, ClosingMapper statements) {
        this.service = service;
        this.mapper = mapper;
        this.policies = policies;
        this.policyMapper = policyMapper;
        this.evidence = evidence;
        this.periods = periods;
        this.closing = closing;
        this.statements = statements;
    }

    @GetMapping("/")
    public String home(CsrfToken token) {
        token.getToken();
        return "portal";
    }

    @PostMapping("/api/demo")
    @ResponseBody
    public Map<String, Object> start(HttpSession session) {
        synchronized (session) {
            if (session.getAttribute("workspace") == null) {
                session.setAttribute("workspace", service.open());
                session.setAttribute("role", Role.EMPLOYEE);
            }
        }
        return snapshot(session);
    }

    @PostMapping("/api/demo/role")
    @ResponseBody
    public void switchRole(@RequestParam Role role, HttpSession session) {
        workspace(session);
        session.setAttribute("role", role);
    }

    @GetMapping("/api/workspace")
    @ResponseBody
    public Map<String, Object> snapshot(HttpSession session) {
        String workspace = workspace(session);
        return Map.of("balance", mapper.balance(workspace), "claims", mapper.claims(workspace),
                "role", session.getAttribute("role"));
    }

    @PostMapping("/api/claims")
    @ResponseBody
    public Map<String, String> submit(HttpSession session, @RequestParam UUID requestKey, @RequestParam String title,
            @RequestParam String category, @RequestParam long estimated, @RequestParam(defaultValue="1") int days) {
        return Map.of("claimId", service.submit(authorized(session, Role.EMPLOYEE), requestKey, title, category, estimated, days));
    }

    @PostMapping("/api/claims/{id}/approve")
    @ResponseBody
    public void approve(HttpSession session, @PathVariable String id) {
        service.approve(authorized(session, Role.MANAGER), id);
    }

    @PostMapping("/api/claims/{id}/cancel")
    @ResponseBody
    public void cancel(HttpSession session, @PathVariable String id, @RequestParam boolean releaseApproval) {
        service.cancel(authorized(session, Role.EMPLOYEE), id, releaseApproval);
    }

    @GetMapping("/api/statement")
    @ResponseBody
    public PeriodClosing.Statement statement(HttpSession session) {
        return periods.statement(authorized(session, Role.FINANCE));
    }

    @GetMapping("/api/claims/{id}/postings")
    @ResponseBody
    public List<ClosingMapper.Posting> postings(HttpSession session, @PathVariable String id) {
        return statements.postings(authorized(session, Role.FINANCE), id);
    }

    @GetMapping("/api/statements/{period}")
    @ResponseBody
    public PeriodClosing.Statement archive(HttpSession session, @PathVariable String period) {
        return periods.archive(authorized(session, Role.FINANCE), period);
    }

    @PostMapping("/api/statement/close")
    @ResponseBody
    public PeriodClosing.Statement close(HttpSession session) {
        return closing.close(authorized(session, Role.FINANCE));
    }

    @PostMapping("/api/claims/{id}/review")
    @ResponseBody
    public void review(HttpSession session, @PathVariable String id, @RequestParam long actual,
            @RequestParam(defaultValue="0") int round) {
        service.review(authorized(session, Role.EMPLOYEE), id, actual, round);
    }

    @PostMapping("/api/claims/{id}/increase")
    @ResponseBody
    public void increase(HttpSession session, @PathVariable String id, @RequestParam int round) {
        service.increase(authorized(session, Role.MANAGER), id, round);
    }

    @PostMapping("/api/claims/{id}/return")
    @ResponseBody
    public void returnReview(HttpSession session, @PathVariable String id, @RequestParam int round, @RequestParam String reason) {
        boolean manager = session.getAttribute("role") == Role.MANAGER;
        service.returnReview(authorized(session, manager ? Role.MANAGER : Role.FINANCE), id, round, reason, manager);
    }

    @PostMapping("/api/claims/{id}/settle")
    @ResponseBody
    public void settle(HttpSession session, @PathVariable String id, @RequestParam(defaultValue="1") int round) {
        service.settle(authorized(session, Role.FINANCE), id, round);
    }

    @PostMapping("/api/claims/{id}/evidence")
    @ResponseBody
    public void attach(HttpSession session, @PathVariable String id, @RequestParam EvidenceService.Type type, @RequestParam long amount) {
        evidence.attach(authorized(session, Role.EMPLOYEE), id, type, amount);
    }

    @GetMapping("/api/claims/{id}/evidence")
    @ResponseBody
    public List<EvidenceMapper.Document> evidenceList(HttpSession session, @PathVariable String id) {
        return evidence.list(workspace(session), id);
    }

    @GetMapping(value="/api/claims/{id}/evidence/{type}", produces="text/plain;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<String> download(HttpSession session, @PathVariable String id, @PathVariable EvidenceService.Type type) {
        return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=expense-document.txt")
                .header("Cache-Control","no-store").body(evidence.download(workspace(session), id, type));
    }

    @GetMapping("/api/policies")
    @ResponseBody
    public Map<String, Object> policyHistory(HttpSession session, @RequestParam String category) {
        String workspace = workspace(session);
        return Map.of("versions", policies.history(workspace, category), "drafts", policyMapper.drafts(workspace, category));
    }

    @PostMapping("/api/policies/preview")
    @ResponseBody
    public PolicyService.Preview preview(HttpSession session, @RequestParam String category,
            @RequestParam String formula, @RequestParam long cost, @RequestParam int days) {
        return policies.preview(workspace(session), category, formula, cost, days);
    }

    @PostMapping("/api/policies/drafts")
    @ResponseBody
    public Map<String, String> draft(HttpSession session, @RequestParam String category,
            @RequestParam long generation, @RequestParam String formula) {
        return Map.of("draftId", policies.draft(authorized(session, Role.FINANCE), category, generation, formula));
    }

    @PostMapping("/api/policies/drafts/{id}/publish")
    @ResponseBody
    public Map<String, Long> publish(HttpSession session, @PathVariable String id) {
        return Map.of("generation", policies.publish(authorized(session, Role.MANAGER), id));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    @ResponseBody
    public Map<String, String> invalidInput(IllegalArgumentException error) {
        return Map.of("message", error.getMessage());
    }

    private String authorized(HttpSession session, Role required) {
        String workspace = workspace(session);
        if (session.getAttribute("role") != required) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return workspace;
    }

    private String workspace(HttpSession session) {
        if (session.getAttribute("workspace") instanceof String workspace) return workspace;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
}

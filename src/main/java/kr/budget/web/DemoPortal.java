package kr.budget.web;

import java.util.Map;
import java.util.UUID;
import kr.budget.portal.PortalReceipts;
import kr.budget.portal.PortalOutbox;
import kr.budget.portal.PortalDelivery;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import kr.budget.claims.BudgetMapper;
import kr.budget.claims.ClaimService;
import kr.budget.claims.EvidenceService;
import kr.budget.claims.AttendanceRule;
import kr.budget.claims.EvidencePolicy;
import kr.budget.claims.EvidenceMapper;
import kr.budget.closing.PeriodClosing;
import kr.budget.closing.ClosingService;
import kr.budget.closing.ClosingMapper;
import kr.budget.closing.StatementComparison;
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
    private final HttpServletRequest request;
    private final BudgetMapper mapper;
    private final PolicyService policies;
    private final PolicyMapper policyMapper;
    private final EvidenceService evidence;
    private final PeriodClosing periods;
    private final ClosingService closing;
    private final ClosingMapper statements;
    private final ObjectProvider<PortalReceipts> receipts;
    private final PortalOutbox outbox;
    private final ObjectProvider<PortalDelivery> delivery;

    public DemoPortal(ClaimService service, BudgetMapper mapper, PolicyService policies, PolicyMapper policyMapper,
            EvidenceService evidence, PeriodClosing periods, ClosingService closing, ClosingMapper statements,
            ObjectProvider<PortalReceipts> receipts, PortalOutbox outbox, ObjectProvider<PortalDelivery> delivery,
            HttpServletRequest request) {
        this.request = request;
        this.outbox = outbox;
        this.delivery = delivery;
        this.receipts = receipts;
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
    public Map<String, Object> start(HttpSession session,
            @RequestParam(defaultValue="COMPANY") kr.budget.claims.OrganizationType organization) {
        synchronized (session) {
            if (session.getAttribute("departments") == null) {
                request.changeSessionId();
                session.setAttribute("departments", service.openDepartments(organization));
                session.setAttribute("role-PRIMARY", Role.EMPLOYEE);
                session.setAttribute("role-SECONDARY", Role.EMPLOYEE);
            }
        }
        return snapshot(session,null);
    }

    @PostMapping("/api/demo/role")
    @ResponseBody
    public void switchRole(@RequestParam Role role, HttpSession session) {
        workspace(session);
        session.setAttribute("role-" + department(), role);
    }

    @GetMapping("/api/workspace")
    @ResponseBody
    public Map<String, Object> snapshot(HttpSession session,@RequestParam(required=false) Long before) {
        String workspace = workspace(session);
        var rows = mapper.inbox(workspace,before == null ? Long.MAX_VALUE : before);
        return Map.of("balance", mapper.balance(workspace), "claims", rows.stream().limit(50).toList(), "hasMore",rows.size() > 50,
                "role", role(session), "organization",mapper.organization(workspace),"portalEnabled",receipts.getIfAvailable()!=null);
    }

    @GetMapping("/api/claims/estimate")
    @ResponseBody
    public ClaimService.Estimate estimate(HttpSession session,@RequestParam String category,@RequestParam long estimated,
            @RequestParam int days) {
        return service.estimate(authorized(session,Role.EMPLOYEE),category,estimated,days);
    }

    @PostMapping("/api/claims")
    @ResponseBody
    public Map<String, String> submit(HttpSession session, @RequestParam UUID requestKey, @RequestParam String title,
            @RequestParam String category, @RequestParam long estimated, @RequestParam(defaultValue="1") int days,
            @RequestParam(required=false) Long expectedGeneration) {
        return Map.of("claimId", service.submit(authorized(session, Role.EMPLOYEE), requestKey, title, category, estimated, days, expectedGeneration));
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

    @PostMapping("/api/claims/{id}/finance-approval")
    @ResponseBody
    public void financeApproval(HttpSession session,@PathVariable String id) {
        service.financeApproval(authorized(session, Role.FINANCE),id);
    }

    @GetMapping("/api/statement")
    @ResponseBody
    public PeriodClosing.Statement statement(HttpSession session) {
        return periods.statement(authorized(session, Role.FINANCE));
    }

    @GetMapping("/api/claims/{id}/calculation")
    @ResponseBody
    public ClaimService.Calculation calculation(HttpSession session,@PathVariable String id) {
        return service.replay(workspace(session),id);
    }

    @GetMapping("/api/claims/{id}/history")
    @ResponseBody
    public List<BudgetMapper.ReviewEvent> history(HttpSession session, @PathVariable String id) {
        String workspace = workspace(session);
        if (mapper.findClaim(workspace,id) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return mapper.history(workspace,id);
    }

    @GetMapping("/api/portal/failures")
    @ResponseBody
    public List<PortalOutbox.Failure> portalFailures(HttpSession session) {
        return outbox.failures(authorized(session, Role.FINANCE));
    }

    @PostMapping("/api/portal/failures/{id}/retry")
    @ResponseBody
    public void retryPortalDelivery(HttpSession session, @PathVariable UUID id) {
        var event = outbox.retryable(authorized(session, Role.FINANCE), id.toString());
        if (event == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var sender = delivery.getIfAvailable();
        if (sender == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        if (!sender.retry(event)) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
    }

    @GetMapping("/api/claims/{id}/portal-receipt")
    @ResponseBody
    public ResponseEntity<PortalReceipts.Receipt> portalReceipt(HttpSession session,@PathVariable UUID id) {
        var workspace = UUID.fromString(authorized(session,Role.FINANCE));
        var client = receipts.getIfAvailable();
        if (client == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        var receipt = client.find(workspace,id);
        return receipt == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(receipt);
    }

    @GetMapping("/api/claims/{id}/postings")
    @ResponseBody
    public List<ClosingMapper.Posting> postings(HttpSession session, @PathVariable String id) {
        return statements.postings(authorized(session, Role.FINANCE), id);
    }

    @PostMapping("/api/statements/{period}/advance")
    @ResponseBody
    public PeriodClosing.Statement advance(HttpSession session,@PathVariable String period) {
        return periods.advance(authorized(session, Role.FINANCE), period);
    }

    @PostMapping("/api/claims/{id}/reverse")
    @ResponseBody
    public void reverse(HttpSession session,@PathVariable String id,@RequestParam int round,@RequestParam long amount,
            @RequestParam String period) {
        periods.reverse(authorized(session, Role.FINANCE),id,round,amount,period);
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

    @PostMapping("/api/statements/{period}/compare")
    @ResponseBody
    public List<StatementComparison.Difference> compareStatement(HttpSession session, @PathVariable String period,
            @RequestParam String csv) {
        return StatementComparison.compare(periods.archive(authorized(session, Role.FINANCE), period), csv);
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
        boolean manager = role(session) == Role.MANAGER;
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

    @GetMapping("/api/claims/{id}/evidence/assessment")
    @ResponseBody
    public kr.budget.claims.EvidencePolicy.Evaluation assessment(HttpSession session,@PathVariable String id) {
        return evidence.assessment(workspace(session),id);
    }

    @GetMapping("/api/claims/{id}/evidence/issues")
    @ResponseBody
    public List<String> evidenceIssues(HttpSession session,@PathVariable String id) {
        return evidence.outstanding(workspace(session),id);
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

    @PostMapping("/api/policies/attendance-preview")
    @ResponseBody
    public EvidencePolicy.Evaluation previewAttendance(HttpSession session, @RequestParam String expression,
            @RequestParam String category, @RequestParam boolean attached) {
        String workspace = authorized(session, Role.FINANCE);
        if (!List.of("EDUCATION","CONFERENCE","TRAVEL").contains(category))
            throw new IllegalArgumentException("등록되지 않은 비목입니다.");
        var example = new EvidencePolicy.Inputs(mapper.organization(workspace), category, 180000L, 180000L, attached);
        return EvidencePolicy.evaluate(example, AttendanceRule.parse(expression));
    }

    @PostMapping("/api/policies/preview")
    @ResponseBody
    public PolicyService.Preview preview(HttpSession session, @RequestParam String category,
            @RequestParam String formula, @RequestParam long cost, @RequestParam int days,
            @RequestParam(required=false) Long restoreGeneration, @RequestParam(required=false) String attendanceRule) {
        return policies.preview(workspace(session), category, formula, cost, days, restoreGeneration, attendanceRule);
    }

    @PostMapping("/api/policies/drafts")
    @ResponseBody
    public Map<String, String> draft(HttpSession session, @RequestParam String category,
            @RequestParam long generation, @RequestParam String formula,
            @RequestParam(defaultValue="180000") long cost, @RequestParam(defaultValue="1") int days,
            @RequestParam(required=false) Long restoreGeneration, @RequestParam(required=false) String attendanceRule) {
        return Map.of("draftId", policies.draft(authorized(session, Role.FINANCE), category, generation, formula, cost, days, restoreGeneration, attendanceRule));
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
        if (role(session) != required) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return workspace;
    }

    private String workspace(HttpSession session) {
        if (session.getAttribute("departments") instanceof ClaimService.Departments ids)
            return department().equals("PRIMARY") ? ids.primaryId() : ids.secondaryId();
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    private String department() {
        String selected = request.getParameter("department");
        if (selected == null || selected.equals("PRIMARY")) return "PRIMARY";
        if (selected.equals("SECONDARY")) return "SECONDARY";
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }

    private Role role(HttpSession session) {
        return (Role)session.getAttribute("role-" + department());
    }
}

package kr.budget.web;

import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpSession;
import kr.budget.claims.BudgetMapper;
import kr.budget.claims.ClaimService;
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

    public DemoPortal(ClaimService service, BudgetMapper mapper) {
        this.service = service;
        this.mapper = mapper;
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

    @PostMapping("/api/claims/{id}/review")
    @ResponseBody
    public void review(HttpSession session, @PathVariable String id, @RequestParam long actual) {
        service.review(authorized(session, Role.EMPLOYEE), id, actual);
    }

    @PostMapping("/api/claims/{id}/settle")
    @ResponseBody
    public void settle(HttpSession session, @PathVariable String id) {
        service.settle(authorized(session, Role.FINANCE), id);
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

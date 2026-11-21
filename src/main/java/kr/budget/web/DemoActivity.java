package kr.budget.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import kr.budget.claims.ClaimService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class DemoActivity extends OncePerRequestFilter {
    private final JdbcTemplate db;

    public DemoActivity(JdbcTemplate db) { this.db = db; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !(path.equals("/") || path.startsWith("/api/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("departments") instanceof ClaimService.Departments pair) {
            // Updating the same parent row serializes the beginning of a visit with its final expiry check.
            if (db.update("update budget_workspace set last_active_at=current_timestamp where workspace_id=?", pair.primaryId()) == 0) {
                session.invalidate();
                response.sendError(410);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}

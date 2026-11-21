package kr.budget.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnProperty(name = "budget.workspace-timing", havingValue = "true")
@Intercepts({
    @Signature(type = Executor.class, method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class})
})
public class WorkspaceTiming extends OncePerRequestFilter implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(WorkspaceTiming.class);
    private static final class Sample {
        String mappedStatement;
        final List<String> queries = new ArrayList<>();
    }
    private final ThreadLocal<Sample> current = new ThreadLocal<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equals(request.getMethod()) || !"/api/workspace".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = UUID.randomUUID().toString();
        Sample sample = new Sample();
        current.set(sample);
        response.setHeader("X-Request-Id", id);
        response.addHeader("Server-Timing", "trace;desc=\"" + id + "\"");
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            current.remove();
            log.info("workspace_timing {} {} {}", id, (System.nanoTime() - start) / 1000, String.join(",", sample.queries));
        }
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Sample sample = current.get();
        if (sample == null) return invocation.proceed();
        if (invocation.getTarget() instanceof Executor) {
            String previous = sample.mappedStatement;
            sample.mappedStatement = ((MappedStatement) invocation.getArgs()[0]).getId();
            try {
                return invocation.proceed();
            } finally {
                sample.mappedStatement = previous;
            }
        }
        long start = System.nanoTime();
        try {
            return invocation.proceed();
        } finally {
            sample.queries.add(sample.mappedStatement + "=" + (System.nanoTime() - start) / 1000);
        }
    }
}

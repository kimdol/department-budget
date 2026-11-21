package kr.budget.closing;

import java.sql.SQLException;
import java.util.List;
import kr.budget.claims.Balance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClosingServiceTest {
    private final String workspace = "finance-demo";
    private final String period = "2026-11";
    private final PeriodClosing periods = mock(PeriodClosing.class);
    private final JobLauncher launcher = mock(JobLauncher.class);
    private final Job job = mock(Job.class);
    private final ClosingService service = new ClosingService(periods, launcher, job);
    private final JobParameters parameters = new JobParametersBuilder()
            .addString("workspace", workspace).addString("period", period).toJobParameters();

    @BeforeEach
    void currentPeriodIsPreparedOnce() {
        when(periods.prepare(workspace)).thenReturn(new ClosingMapper.Period(period, "OPEN"));
    }

    @Test
    void retriesOracleSerializationFailureWithTheSameJobParameters() throws Exception {
        var execution = mock(JobExecution.class);
        when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(launcher.run(job, parameters)).thenThrow(oracleFailure(8177)).thenReturn(execution);
        var statement = new PeriodClosing.Statement(period, "CLOSED",
                new Balance(1_000_000, 0, 180_000), List.of(), List.of(), null);
        when(periods.statement(workspace)).thenReturn(statement);

        assertSame(statement, service.close(workspace));
        verify(launcher, times(2)).run(job, parameters);
        verify(periods).prepare(workspace);
    }

    @Test
    void leavesThePeriodRetryableAfterThreeSerializationFailures() throws Exception {
        when(launcher.run(job, parameters)).thenThrow(oracleFailure(8177));

        var failure = assertThrows(ResponseStatusException.class, () -> service.close(workspace));
        assertEquals(503, failure.getStatusCode().value());
        verify(launcher, times(3)).run(job, parameters);
        verify(periods).prepare(workspace);
    }

    @Test
    void doesNotRetryUnrelatedSqlFailures() throws Exception {
        var failure = oracleFailure(942);
        when(launcher.run(job, parameters)).thenThrow(failure);

        assertSame(failure, assertThrows(UncategorizedSQLException.class, () -> service.close(workspace)));
        verify(launcher).run(job, parameters);
    }

    private static UncategorizedSQLException oracleFailure(int errorCode) {
        return new UncategorizedSQLException("Create job instance", "INSERT INTO BATCH_JOB_INSTANCE",
                new SQLException("Oracle rejected the transaction", "72000", errorCode,
                        new Exception("Oracle driver details")));
    }
}

package kr.budget.closing;

import java.sql.SQLException;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClosingService {
    private final PeriodClosing closing;
    private final JobLauncher launcher;
    private final Job job;

    public ClosingService(PeriodClosing closing, JobLauncher launcher, Job departmentMonthEnd) {
        this.closing = closing;
        this.launcher = launcher;
        this.job = departmentMonthEnd;
    }

    public PeriodClosing.Statement close(String workspace) {
        var period = closing.prepare(workspace);
        if (!period.periodState().equals("CLOSED")) {
            var params = new JobParametersBuilder().addString("workspace",workspace)
                    .addString("period",period.periodKey()).toJobParameters();
            try {
                var execution = launch(params);
                if (execution.getStatus() != BatchStatus.COMPLETED)
                    throw new IllegalStateException("마감 배치가 완료되지 않았습니다.");
            } catch (JobInstanceAlreadyCompleteException completed) {
                return closing.statement(workspace);
            } catch (JobExecutionAlreadyRunningException running) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,"마감이 진행 중입니다.");
            } catch (JobExecutionException failure) {
                throw new IllegalStateException("마감 실행 상태를 확인하세요.", failure);
            }
        }
        return closing.statement(workspace);
    }

    private JobExecution launch(JobParameters params) throws JobExecutionException {
        for (int attempt = 1; ; attempt++) {
            try {
                return launcher.run(job, params);
            } catch (DataAccessException failure) {
                if (!(failure.getCause() instanceof SQLException sql) || sql.getErrorCode() != 8177) {
                    throw failure;
                }
                if (attempt == 3) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "마감 실행을 다시 시도하세요.", failure);
                }
            }
        }
    }
}

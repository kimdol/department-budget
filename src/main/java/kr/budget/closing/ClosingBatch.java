package kr.budget.closing;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class ClosingBatch {
    @Bean
    Step freezeStatement(JobRepository jobs, PlatformTransactionManager tx, PeriodClosing closing) {
        return new StepBuilder("freezeStatement", jobs).tasklet((contribution, context) -> {
            var params = contribution.getStepExecution().getJobParameters();
            closing.finish(params.getString("workspace"), params.getString("period"));
            return RepeatStatus.FINISHED;
        }, tx).build();
    }

    @Bean
    Job departmentMonthEnd(JobRepository jobs, Step freezeStatement) {
        return new JobBuilder("departmentMonthEnd", jobs).start(freezeStatement).build();
    }
}

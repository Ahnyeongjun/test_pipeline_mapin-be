package com.mapin.analysis.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class AnalysisJobConfig {

    @Bean
    public Job analysisJob(JobRepository jobRepository, Step analysisStep) {
        return new JobBuilder("analysisJob", jobRepository)
                .start(analysisStep)
                .build();
    }

    @Bean
    public Step analysisStep(JobRepository jobRepository, PlatformTransactionManager tm,
            AnalysisTasklet analysisTasklet) {
        return new StepBuilder("analysisStep", jobRepository)
                .tasklet(analysisTasklet, tm)
                .build();
    }
}

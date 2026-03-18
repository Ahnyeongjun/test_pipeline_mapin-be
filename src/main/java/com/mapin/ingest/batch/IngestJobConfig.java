package com.mapin.ingest.batch;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class IngestJobConfig {

    @Bean
    public Job ingestJob(JobRepository jobRepository, Step ingestStep) {
        return new JobBuilder("ingestJob", jobRepository)
                .start(ingestStep)
                .build();
    }

    @Bean
    public Step ingestStep(JobRepository jobRepository, PlatformTransactionManager tm,
            IngestTasklet ingestTasklet) {
        return new StepBuilder("ingestStep", jobRepository)
                .tasklet(ingestTasklet, tm)
                .build();
    }
}

package com.mapin.embedding.batch;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class EmbeddingJobConfig {

    @Bean
    public Job embeddingJob(JobRepository jobRepository, Step embeddingStep) {
        return new JobBuilder("embeddingJob", jobRepository)
                .start(embeddingStep)
                .build();
    }

    @Bean
    public Step embeddingStep(JobRepository jobRepository, PlatformTransactionManager tm,
            EmbeddingTasklet embeddingTasklet) {
        return new StepBuilder("embeddingStep", jobRepository)
                .tasklet(embeddingTasklet, tm)
                .build();
    }
}

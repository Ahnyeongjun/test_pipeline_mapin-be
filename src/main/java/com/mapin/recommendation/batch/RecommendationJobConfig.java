package com.mapin.recommendation.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class RecommendationJobConfig {

    @Bean
    public Job recommendationJob(JobRepository jobRepository, Step recommendationStep) {
        return new JobBuilder("recommendationJob", jobRepository)
                .start(recommendationStep)
                .build();
    }

    @Bean
    public Step recommendationStep(JobRepository jobRepository, PlatformTransactionManager tm,
            RecommendationTasklet recommendationTasklet) {
        return new StepBuilder("recommendationStep", jobRepository)
                .tasklet(recommendationTasklet, tm)
                .build();
    }
}

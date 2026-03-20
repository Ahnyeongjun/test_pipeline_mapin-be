package com.mapin.analysis.batch;

import com.mapin.analysis.client.PerspectiveAnalysisResult;
import com.mapin.analysis.client.PerspectiveClassifier;
import com.mapin.analysis.event.ContentAnalysisFailedEvent;
import com.mapin.analysis.event.ContentAnalyzedEvent;
import com.mapin.common.domain.Content;
import com.mapin.common.domain.ContentRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalysisTasklet implements Tasklet {

    private final ContentRepository contentRepository;
    private final PerspectiveClassifier perspectiveClassifier;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Long contentId = contribution.getStepExecution().getJobParameters().getLong("contentId");
        String source = contribution.getStepExecution().getJobParameters().getString("source", "USER");

        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다. id=" + contentId));

        String text = "[TITLE]\n%s\n\n[DESCRIPTION]\n%s".formatted(
                Objects.toString(content.getTitle(), ""), Objects.toString(content.getDescription(), ""));

        try {
            PerspectiveAnalysisResult result = perspectiveClassifier.classify(text);
            content.updatePerspective(result.category(), result.perspectiveLevel(), result.perspectiveStakeholder(),
                    result.keywords(), result.summary(), result.tone(), result.biasLevel(), result.isOpinionated());
            content.updatePipelineStatus("ANALYZED");
            contentRepository.save(content);

            log.info("[Analysis] contentId={} category={} level={} stakeholder={} source={}",
                    contentId, result.category(), result.perspectiveLevel(), result.perspectiveStakeholder(), source);

            eventPublisher.publishEvent(new ContentAnalyzedEvent(this, contentId, source));
        } catch (Exception e) {
            log.error("[Analysis][Saga] 분석 실패 contentId={}: {}", contentId, e.getMessage(), e);
            eventPublisher.publishEvent(new ContentAnalysisFailedEvent(this, contentId, source, e.getMessage()));
            throw e;
        }
        return RepeatStatus.FINISHED;
    }

}

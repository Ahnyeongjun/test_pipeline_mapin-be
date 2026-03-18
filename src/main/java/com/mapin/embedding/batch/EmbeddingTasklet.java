package com.mapin.embedding.batch;

import com.mapin.common.domain.Content;
import com.mapin.common.domain.ContentRepository;
import com.mapin.embedding.client.EmbeddingClient;
import com.mapin.embedding.client.VectorStoreClient;
import com.mapin.embedding.event.ContentEmbeddedEvent;
import java.util.List;
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
public class EmbeddingTasklet implements Tasklet {

    private final ContentRepository contentRepository;
    private final EmbeddingClient embeddingClient;
    private final VectorStoreClient vectorStoreClient;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Long contentId = contribution.getStepExecution().getJobParameters().getLong("contentId");
        String source = contribution.getStepExecution().getJobParameters().getString("source", "USER");

        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다. id=" + contentId));

        String text = "[TITLE]\n%s\n\n[DESCRIPTION]\n%s".formatted(
                nullSafe(content.getTitle()), nullSafe(content.getDescription()));

        List<Float> vector = embeddingClient.embed(text);

        // contentId를 Qdrant 포인트 ID로 사용 (조회 시 ID로 역참조 가능)
        vectorStoreClient.upsert(contentId, vector);

        content.updateEmbedding(embeddingClient.modelName(), String.valueOf(contentId));
        contentRepository.save(content);

        log.info("[Embedding] contentId={} model={} dim={}",
                contentId, embeddingClient.modelName(), vector.size());

        eventPublisher.publishEvent(new ContentEmbeddedEvent(this, contentId, source));
        return RepeatStatus.FINISHED;
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}

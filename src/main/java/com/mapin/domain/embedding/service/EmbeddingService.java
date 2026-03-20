package com.mapin.domain.embedding.service;

import com.mapin.domain.content.Content;
import com.mapin.domain.content.ContentRepository;
import com.mapin.infra.embedding.EmbeddingClient;
import com.mapin.infra.vectorstore.VectorStoreClient;
import com.mapin.domain.embedding.event.ContentEmbeddedEvent;
import com.mapin.domain.embedding.event.ContentEmbeddingFailedEvent;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final ContentRepository contentRepository;
    private final EmbeddingClient embeddingClient;
    private final VectorStoreClient vectorStoreClient;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void embed(Long contentId, String source) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다. id=" + contentId));

        String text = "[TITLE]\n%s\n\n[DESCRIPTION]\n%s".formatted(
                Objects.toString(content.getTitle(), ""), Objects.toString(content.getDescription(), ""));

        boolean vectorUpserted = false;
        try {
            List<Float> vector = embeddingClient.embed(text);
            vectorStoreClient.upsert(contentId, vector);
            vectorUpserted = true;

            content.updateEmbedding(embeddingClient.modelName(), String.valueOf(contentId));
            content.updatePipelineStatus("EMBEDDED");
            contentRepository.save(content);

            log.info("[Embedding] contentId={} model={} dim={}",
                    contentId, embeddingClient.modelName(), vector.size());

            eventPublisher.publishEvent(new ContentEmbeddedEvent(this, contentId, source));
        } catch (Exception e) {
            log.error("[Embedding][Saga] 임베딩 실패 contentId={} vectorUpserted={}: {}",
                    contentId, vectorUpserted, e.getMessage(), e);
            eventPublisher.publishEvent(
                    new ContentEmbeddingFailedEvent(this, contentId, source, e.getMessage(), vectorUpserted));
            throw e;
        }
    }
}

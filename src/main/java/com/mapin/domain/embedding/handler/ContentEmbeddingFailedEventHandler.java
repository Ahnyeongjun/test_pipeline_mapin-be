package com.mapin.domain.embedding.handler;

import com.mapin.domain.content.ContentRepository;
import com.mapin.infra.vectorstore.VectorStoreClient;
import com.mapin.domain.embedding.event.ContentEmbeddingFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentEmbeddingFailedEventHandler {

    private final ContentRepository contentRepository;
    private final VectorStoreClient vectorStoreClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(ContentEmbeddingFailedEvent event) {
        log.error("[Embedding][Saga] 임베딩 실패 contentId={} source={} vectorUpserted={} reason={}",
                event.getContentId(), event.getSource(), event.isVectorUpserted(), event.getReason());

        contentRepository.findById(event.getContentId()).ifPresentOrElse(
                content -> {
                    content.updatePipelineStatus("EMBEDDING_FAILED");
                    contentRepository.save(content);
                    log.info("[Embedding][Saga] pipelineStatus=EMBEDDING_FAILED contentId={}", event.getContentId());
                },
                () -> log.warn("[Embedding][Saga] 보상 처리 대상 콘텐츠 없음 contentId={}", event.getContentId())
        );

        if (event.isVectorUpserted()) {
            try {
                vectorStoreClient.delete(event.getContentId());
                log.info("[Embedding][Saga] Qdrant 벡터 삭제 완료 contentId={}", event.getContentId());
            } catch (Exception e) {
                log.warn("[Embedding][Saga] Qdrant 벡터 삭제 실패 contentId={}: {}", event.getContentId(), e.getMessage());
            }
        }
    }
}

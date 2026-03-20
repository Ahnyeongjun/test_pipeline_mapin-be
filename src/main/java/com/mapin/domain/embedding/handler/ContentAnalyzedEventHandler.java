package com.mapin.domain.embedding.handler;

import com.mapin.domain.analysis.event.ContentAnalyzedEvent;
import com.mapin.domain.embedding.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component("embeddingContentAnalyzedEventHandler")
@ConditionalOnProperty(name = "pipeline.recommendation.strategy", havingValue = "vector")
@RequiredArgsConstructor
@Slf4j
public class ContentAnalyzedEventHandler {

    private final EmbeddingService embeddingService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ContentAnalyzedEvent event) {
        Long contentId = event.getContentId();
        log.info("[EmbeddingHandler] ContentAnalyzedEvent 수신 contentId={} source={}", contentId, event.getSource());
        try {
            embeddingService.embed(contentId, event.getSource());
        } catch (Exception e) {
            log.error("[Embedding] 임베딩 실패 contentId={}", contentId, e);
        }
    }
}

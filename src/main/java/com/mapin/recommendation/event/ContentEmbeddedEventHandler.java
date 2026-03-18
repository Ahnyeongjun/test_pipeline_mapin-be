package com.mapin.recommendation.event;

import com.mapin.embedding.event.ContentEmbeddedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 'vector' 전략 전용 핸들러.
 * 임베딩 완료 후 벡터 기반 추천을 생성한다.
 */
@Component
@ConditionalOnProperty(name = "pipeline.recommendation.strategy", havingValue = "vector")
@RequiredArgsConstructor
@Slf4j
public class ContentEmbeddedEventHandler {

    private final RecommendationTrigger trigger;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ContentEmbeddedEvent event) {
        Long contentId = event.getContentId();
        log.info("[RecommendationHandler-vector] ContentEmbeddedEvent 수신 contentId={}", contentId);
        trigger.trigger(contentId);
    }
}

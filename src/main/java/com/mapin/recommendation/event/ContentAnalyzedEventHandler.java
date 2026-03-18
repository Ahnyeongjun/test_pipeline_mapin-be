package com.mapin.recommendation.event;

import com.mapin.analysis.event.ContentAnalyzedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 'db' 전략 전용 핸들러.
 * 벡터 임베딩 없이 분류 완료 즉시 추천을 생성한다.
 */
@Component
@ConditionalOnProperty(name = "pipeline.recommendation.strategy", havingValue = "db", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ContentAnalyzedEventHandler {

    private final RecommendationTrigger trigger;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ContentAnalyzedEvent event) {
        Long contentId = event.getContentId();
        log.info("[RecommendationHandler-db] ContentAnalyzedEvent 수신 contentId={}", contentId);
        trigger.trigger(contentId);
    }
}

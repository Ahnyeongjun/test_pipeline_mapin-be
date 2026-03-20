package com.mapin.domain.recommendation.handler;

import com.mapin.domain.embedding.event.ContentEmbeddedEvent;
import com.mapin.domain.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * vector 전략: 임베딩 완료 → 즉시 recommendation 실행 (USER/FALLBACK 모두)
 * source는 이벤트로 전달되어 RecommendationService에서 분기 처리
 */
@Component
@ConditionalOnProperty(name = "pipeline.recommendation.strategy", havingValue = "vector")
@RequiredArgsConstructor
@Slf4j
public class ContentEmbeddedEventHandler {

    private final RecommendationService recommendationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ContentEmbeddedEvent event) {
        Long contentId = event.getContentId();
        log.info("[RecommendationHandler] ContentEmbeddedEvent 수신 contentId={} source={}", contentId, event.getSource());
        try {
            recommendationService.recommend(contentId, event.getSource());
        } catch (Exception e) {
            log.error("[Recommendation] 추천 실패 contentId={}", contentId, e);
        }
    }
}

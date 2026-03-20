package com.mapin.domain.recommendation.handler;

import com.mapin.domain.analysis.event.ContentAnalyzedEvent;
import com.mapin.domain.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * db 전략: 분류 완료 → 즉시 recommendation 실행 (USER/FALLBACK 모두)
 * vector 전략일 때는 ContentEmbeddedEventHandler가 대신 처리
 * source는 이벤트로 전달되어 RecommendationService에서 분기 처리
 */
@Component("recommendationContentAnalyzedEventHandler")
@ConditionalOnProperty(name = "pipeline.recommendation.strategy", havingValue = "db", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ContentAnalyzedEventHandler {

    private final RecommendationService recommendationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ContentAnalyzedEvent event) {
        Long contentId = event.getContentId();
        log.info("[RecommendationHandler] ContentAnalyzedEvent 수신 contentId={} source={}", contentId, event.getSource());
        try {
            recommendationService.recommend(contentId, event.getSource());
        } catch (Exception e) {
            log.error("[Recommendation] 추천 실패 contentId={}", contentId, e);
        }
    }
}

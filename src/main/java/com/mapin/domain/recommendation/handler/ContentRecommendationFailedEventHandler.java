package com.mapin.domain.recommendation.handler;

import com.mapin.domain.content.ContentRepository;
import com.mapin.domain.recommendation.event.ContentRecommendationFailedEvent;
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
public class ContentRecommendationFailedEventHandler {

    private final ContentRepository contentRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(ContentRecommendationFailedEvent event) {
        log.error("[Recommendation][Saga] 추천 실패 contentId={} source={} reason={}",
                event.getContentId(), event.getSource(), event.getReason());
        contentRepository.findById(event.getContentId()).ifPresentOrElse(
                content -> {
                    content.updatePipelineStatus("RECOMMENDATION_FAILED");
                    contentRepository.save(content);
                    log.info("[Recommendation][Saga] pipelineStatus=RECOMMENDATION_FAILED contentId={}", event.getContentId());
                },
                () -> log.warn("[Recommendation][Saga] 보상 처리 대상 콘텐츠 없음 contentId={}", event.getContentId())
        );
    }
}

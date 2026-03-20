package com.mapin.common.saga;

import com.mapin.analysis.event.ContentAnalysisFailedEvent;
import com.mapin.common.domain.ContentRepository;
import com.mapin.embedding.client.VectorStoreClient;
import com.mapin.embedding.event.ContentEmbeddingFailedEvent;
import com.mapin.recommendation.event.ContentRecommendationFailedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Choreography Saga 보상 트랜잭션 핸들러.
 *
 * 각 파이프라인 단계의 실패 이벤트를 구독하여:
 * 1. Content.pipelineStatus를 FAILED 상태로 업데이트 (새 트랜잭션)
 * 2. 필요 시 외부 저장소 보상 처리 (벡터 삭제 등)
 *
 * AFTER_ROLLBACK 사용: Tasklet 트랜잭션이 롤백된 후 실행 → 새 트랜잭션에서 실패 상태 기록.
 */
@Component
@Slf4j
public class PipelineSagaFailureHandler {

    private final ContentRepository contentRepository;

    @Autowired(required = false)
    private VectorStoreClient vectorStoreClient;

    public PipelineSagaFailureHandler(ContentRepository contentRepository) {
        this.contentRepository = contentRepository;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAnalysisFailed(ContentAnalysisFailedEvent event) {
        log.error("[Saga] Analysis 실패 contentId={} source={} reason={}",
                event.getContentId(), event.getSource(), event.getReason());
        markFailed(event.getContentId(), "ANALYSIS_FAILED");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEmbeddingFailed(ContentEmbeddingFailedEvent event) {
        log.error("[Saga] Embedding 실패 contentId={} source={} reason={} vectorUpserted={}",
                event.getContentId(), event.getSource(), event.getReason(), event.isVectorUpserted());
        markFailed(event.getContentId(), "EMBEDDING_FAILED");

        if (event.isVectorUpserted() && vectorStoreClient != null) {
            try {
                vectorStoreClient.delete(event.getContentId());
                log.info("[Saga] Embedding 보상: Qdrant 벡터 삭제 완료 contentId={}", event.getContentId());
            } catch (Exception e) {
                log.warn("[Saga] Embedding 보상: Qdrant 벡터 삭제 실패 contentId={}: {}",
                        event.getContentId(), e.getMessage());
            }
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRecommendationFailed(ContentRecommendationFailedEvent event) {
        log.error("[Saga] Recommendation 실패 contentId={} source={} reason={}",
                event.getContentId(), event.getSource(), event.getReason());
        markFailed(event.getContentId(), "RECOMMENDATION_FAILED");
    }

    private void markFailed(Long contentId, String failedStatus) {
        contentRepository.findById(contentId).ifPresentOrElse(
                content -> {
                    content.updatePipelineStatus(failedStatus);
                    contentRepository.save(content);
                    log.info("[Saga] pipelineStatus 업데이트 contentId={} status={}", contentId, failedStatus);
                },
                () -> log.warn("[Saga] 보상 처리 대상 콘텐츠 없음 contentId={}", contentId)
        );
    }
}

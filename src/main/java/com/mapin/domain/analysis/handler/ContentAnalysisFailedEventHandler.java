package com.mapin.domain.analysis.handler;

import com.mapin.domain.analysis.event.ContentAnalysisFailedEvent;
import com.mapin.domain.content.ContentRepository;
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
public class ContentAnalysisFailedEventHandler {

    private final ContentRepository contentRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(ContentAnalysisFailedEvent event) {
        log.error("[Analysis][Saga] 분석 실패 contentId={} source={} reason={}",
                event.getContentId(), event.getSource(), event.getReason());
        contentRepository.findById(event.getContentId()).ifPresentOrElse(
                content -> {
                    content.updatePipelineStatus("ANALYSIS_FAILED");
                    contentRepository.save(content);
                    log.info("[Analysis][Saga] pipelineStatus=ANALYSIS_FAILED contentId={}", event.getContentId());
                },
                () -> log.warn("[Analysis][Saga] 보상 처리 대상 콘텐츠 없음 contentId={}", event.getContentId())
        );
    }
}

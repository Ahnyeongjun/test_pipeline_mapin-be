package com.mapin.domain.analysis.handler;

import com.mapin.domain.analysis.service.AnalysisService;
import com.mapin.domain.content.event.ContentIngestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentIngestedEventHandler {

    private final AnalysisService analysisService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ContentIngestedEvent event) {
        Long contentId = event.getContentId();
        log.info("[Analysis] ContentIngestedEvent 수신 contentId={} source={}", contentId, event.getSource());
        try {
            analysisService.analyze(contentId, event.getSource());
        } catch (Exception e) {
            log.error("[Analysis] 분석 실패 contentId={}", contentId, e);
        }
    }
}

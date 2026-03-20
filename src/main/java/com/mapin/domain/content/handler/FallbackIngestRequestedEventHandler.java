package com.mapin.domain.content.handler;

import com.mapin.domain.content.event.FallbackIngestRequestedEvent;
import com.mapin.domain.content.service.IngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class FallbackIngestRequestedEventHandler {

    private final IngestService ingestService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(FallbackIngestRequestedEvent event) {
        try {
            ingestService.ingest(event.getUrl(), "FALLBACK");
            log.info("[Ingest] FALLBACK ingest 완료 url={}", event.getUrl());
        } catch (Exception e) {
            log.warn("[Ingest] FALLBACK ingest 실패 url={}: {}", event.getUrl(), e.getMessage());
        }
    }
}

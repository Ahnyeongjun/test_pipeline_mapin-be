package com.mapin.domain.embedding.event;

import org.springframework.context.ApplicationEvent;

public class ContentEmbeddingFailedEvent extends ApplicationEvent {

    private final Long contentId;
    private final String source;
    private final String reason;
    private final boolean vectorUpserted;

    public ContentEmbeddingFailedEvent(Object source, Long contentId, String contentSource,
                                       String reason, boolean vectorUpserted) {
        super(source);
        this.contentId = contentId;
        this.source = contentSource;
        this.reason = reason;
        this.vectorUpserted = vectorUpserted;
    }

    public Long getContentId() { return contentId; }
    public String getSource() { return source; }
    public String getReason() { return reason; }

    /** true면 Qdrant에 이미 upsert된 벡터가 있어 보상 삭제가 필요하다. */
    public boolean isVectorUpserted() { return vectorUpserted; }
}

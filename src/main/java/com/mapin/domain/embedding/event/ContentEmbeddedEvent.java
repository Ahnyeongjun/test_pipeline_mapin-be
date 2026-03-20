package com.mapin.domain.embedding.event;

import org.springframework.context.ApplicationEvent;

public class ContentEmbeddedEvent extends ApplicationEvent {

    private final Long contentId;
    private final String source;

    public ContentEmbeddedEvent(Object source, Long contentId, String contentSource) {
        super(source);
        this.contentId = contentId;
        this.source = contentSource;
    }

    public Long getContentId() {
        return contentId;
    }

    public String getSource() {
        return source;
    }
}

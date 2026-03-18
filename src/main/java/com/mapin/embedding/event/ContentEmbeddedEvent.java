package com.mapin.embedding.event;

import org.springframework.context.ApplicationEvent;

public class ContentEmbeddedEvent extends ApplicationEvent {

    private final Long contentId;

    public ContentEmbeddedEvent(Object source, Long contentId) {
        super(source);
        this.contentId = contentId;
    }

    public Long getContentId() {
        return contentId;
    }
}

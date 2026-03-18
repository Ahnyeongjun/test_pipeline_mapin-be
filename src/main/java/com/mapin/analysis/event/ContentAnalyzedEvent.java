package com.mapin.analysis.event;

import org.springframework.context.ApplicationEvent;

public class ContentAnalyzedEvent extends ApplicationEvent {

    private final Long contentId;

    public ContentAnalyzedEvent(Object source, Long contentId) {
        super(source);
        this.contentId = contentId;
    }

    public Long getContentId() {
        return contentId;
    }
}

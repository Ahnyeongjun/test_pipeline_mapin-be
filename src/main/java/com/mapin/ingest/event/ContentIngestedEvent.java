package com.mapin.ingest.event;

import org.springframework.context.ApplicationEvent;

public class ContentIngestedEvent extends ApplicationEvent {

    private final Long contentId;

    public ContentIngestedEvent(Object source, Long contentId) {
        super(source);
        this.contentId = contentId;
    }

    public Long getContentId() {
        return contentId;
    }
}

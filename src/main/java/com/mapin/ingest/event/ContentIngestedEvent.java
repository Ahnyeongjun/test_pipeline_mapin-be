package com.mapin.ingest.event;

import org.springframework.context.ApplicationEvent;

public class ContentIngestedEvent extends ApplicationEvent {

    private final Long contentId;
    private final String source;

    public ContentIngestedEvent(Object source, Long contentId, String contentSource) {
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

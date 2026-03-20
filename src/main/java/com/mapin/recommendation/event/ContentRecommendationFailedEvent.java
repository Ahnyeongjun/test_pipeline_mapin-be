package com.mapin.recommendation.event;

import org.springframework.context.ApplicationEvent;

public class ContentRecommendationFailedEvent extends ApplicationEvent {

    private final Long contentId;
    private final String source;
    private final String reason;

    public ContentRecommendationFailedEvent(Object source, Long contentId, String contentSource, String reason) {
        super(source);
        this.contentId = contentId;
        this.source = contentSource;
        this.reason = reason;
    }

    public Long getContentId() { return contentId; }
    public String getSource() { return source; }
    public String getReason() { return reason; }
}

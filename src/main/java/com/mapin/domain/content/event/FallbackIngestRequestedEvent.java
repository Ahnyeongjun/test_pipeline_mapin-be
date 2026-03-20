package com.mapin.domain.content.event;

import org.springframework.context.ApplicationEvent;

public class FallbackIngestRequestedEvent extends ApplicationEvent {

    private final String url;

    public FallbackIngestRequestedEvent(Object source, String url) {
        super(source);
        this.url = url;
    }

    public String getUrl() {
        return url;
    }
}

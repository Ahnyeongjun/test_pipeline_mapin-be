package com.mapin.domain.content.service;

import com.mapin.domain.content.Content;
import com.mapin.domain.content.ContentRepository;
import com.mapin.infra.youtube.YoutubeMetadataClient;
import com.mapin.infra.youtube.YoutubeUrlParser;
import com.mapin.infra.youtube.YoutubeVideoMetadata;
import com.mapin.domain.content.event.ContentIngestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestServiceTest {

    @Mock private ContentRepository contentRepository;
    @Mock private YoutubeUrlParser youtubeUrlParser;
    @Mock private YoutubeMetadataClient youtubeMetadataClient;
    @Mock private ApplicationEventPublisher eventPublisher;

    private IngestService service;

    @BeforeEach
    void setUp() {
        service = new IngestService(contentRepository, youtubeUrlParser, youtubeMetadataClient, eventPublisher);
    }

    @Test
    @DisplayName("이미 저장된 URL이면 메타데이터를 다시 가져오지 않는다")
    void skipFetchWhenContentAlreadyExists() {
        when(youtubeUrlParser.extractVideoId(anyString())).thenReturn("abc123");
        when(youtubeUrlParser.canonicalize("abc123")).thenReturn("https://www.youtube.com/watch?v=abc123");

        Content existing = Content.builder()
                .canonicalUrl("https://www.youtube.com/watch?v=abc123")
                .platform("YOUTUBE").externalContentId("abc123")
                .title("기존 영상").description("").status("ACTIVE").source("USER").build();
        when(contentRepository.findByCanonicalUrl(anyString())).thenReturn(Optional.of(existing));
        when(contentRepository.save(any())).thenReturn(existing);

        service.ingest("https://www.youtube.com/watch?v=abc123", "USER");

        verify(youtubeMetadataClient, never()).fetch(anyString());
    }

    @Test
    @DisplayName("새 URL이면 메타데이터를 가져와서 저장한다")
    void fetchAndSaveWhenContentNotExists() {
        when(youtubeUrlParser.extractVideoId(anyString())).thenReturn("newvid");
        when(youtubeUrlParser.canonicalize("newvid")).thenReturn("https://www.youtube.com/watch?v=newvid");
        when(contentRepository.findByCanonicalUrl(anyString())).thenReturn(Optional.empty());

        YoutubeVideoMetadata meta = new YoutubeVideoMetadata(
                "newvid", "새 영상 제목", "설명", "https://thumb.jpg",
                "채널명", null, "22", "PT10M", 10000L);
        when(youtubeMetadataClient.fetch("newvid")).thenReturn(meta);

        Content saved = Content.builder()
                .canonicalUrl("https://www.youtube.com/watch?v=newvid")
                .platform("YOUTUBE").externalContentId("newvid")
                .title("새 영상 제목").description("설명").status("ACTIVE").source("USER").build();
        when(contentRepository.save(any())).thenReturn(saved);

        service.ingest("https://www.youtube.com/watch?v=newvid", "USER");

        verify(youtubeMetadataClient).fetch("newvid");
        // 1차: 신규 콘텐츠 저장, 2차: pipelineStatus 업데이트
        verify(contentRepository, times(2)).save(any(Content.class));
    }

    @Test
    @DisplayName("실행 완료 후 ContentIngestedEvent를 발행한다")
    void publishesEventAfterExecution() {
        when(youtubeUrlParser.extractVideoId(anyString())).thenReturn("abc123");
        when(youtubeUrlParser.canonicalize("abc123")).thenReturn("https://www.youtube.com/watch?v=abc123");

        Content existing = Content.builder()
                .canonicalUrl("https://www.youtube.com/watch?v=abc123")
                .platform("YOUTUBE").externalContentId("abc123")
                .title("영상").description("").status("ACTIVE").source("USER").build();
        when(contentRepository.findByCanonicalUrl(anyString())).thenReturn(Optional.of(existing));
        when(contentRepository.save(any())).thenReturn(existing);

        service.ingest("https://www.youtube.com/watch?v=abc123", "USER");

        ArgumentCaptor<ContentIngestedEvent> captor = ArgumentCaptor.forClass(ContentIngestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("USER");
    }

    @Test
    @DisplayName("FALLBACK source로 ingest하면 ContentIngestedEvent source가 FALLBACK이다")
    void fallbackSourceIsPreserved() {
        when(youtubeUrlParser.extractVideoId(anyString())).thenReturn("fallvid");
        when(youtubeUrlParser.canonicalize("fallvid")).thenReturn("https://www.youtube.com/watch?v=fallvid");

        Content existing = Content.builder()
                .canonicalUrl("https://www.youtube.com/watch?v=fallvid")
                .platform("YOUTUBE").externalContentId("fallvid")
                .title("영상").description("").status("ACTIVE").source("FALLBACK").build();
        when(contentRepository.findByCanonicalUrl(anyString())).thenReturn(Optional.of(existing));
        when(contentRepository.save(any())).thenReturn(existing);

        service.ingest("https://www.youtube.com/watch?v=fallvid", "FALLBACK");

        ArgumentCaptor<ContentIngestedEvent> captor = ArgumentCaptor.forClass(ContentIngestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("FALLBACK");
    }
}

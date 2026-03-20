package com.mapin.ingest.batch;

import com.mapin.common.domain.Content;
import com.mapin.common.domain.ContentRepository;
import com.mapin.ingest.client.YoutubeMetadataClient;
import com.mapin.ingest.client.YoutubeUrlParser;
import com.mapin.ingest.client.YoutubeVideoMetadata;
import com.mapin.ingest.event.ContentIngestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestTaskletTest {

    @Mock private ContentRepository contentRepository;
    @Mock private YoutubeUrlParser youtubeUrlParser;
    @Mock private YoutubeMetadataClient youtubeMetadataClient;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Mock private ChunkContext chunkContext;
    @Mock private StepContext stepContext;
    @Mock private StepContribution contribution;

    private IngestTasklet tasklet;

    @BeforeEach
    void setUp() {
        tasklet = new IngestTasklet(contentRepository, youtubeUrlParser, youtubeMetadataClient, eventPublisher);
    }

    private void givenJobParams(String url, String source) {
        Map<String, Object> params = source == null
                ? Map.of("url", url)
                : Map.of("url", url, "source", source);
        when(chunkContext.getStepContext()).thenReturn(stepContext);
        when(stepContext.getJobParameters()).thenReturn(params);
    }

    @Test
    @DisplayName("이미 저장된 URL이면 메타데이터를 다시 가져오지 않는다")
    void skipFetchWhenContentAlreadyExists() throws Exception {
        givenJobParams("https://www.youtube.com/watch?v=abc123", "USER");
        when(youtubeUrlParser.extractVideoId(anyString())).thenReturn("abc123");
        when(youtubeUrlParser.canonicalize("abc123")).thenReturn("https://www.youtube.com/watch?v=abc123");

        Content existing = Content.builder()
                .canonicalUrl("https://www.youtube.com/watch?v=abc123")
                .platform("YOUTUBE").externalContentId("abc123")
                .title("기존 영상").description("").status("ACTIVE").source("USER").build();
        when(contentRepository.findByCanonicalUrl(anyString())).thenReturn(Optional.of(existing));

        RepeatStatus status = tasklet.execute(contribution, chunkContext);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(youtubeMetadataClient, never()).fetch(anyString());
    }

    @Test
    @DisplayName("새 URL이면 메타데이터를 가져와서 저장한다")
    void fetchAndSaveWhenContentNotExists() throws Exception {
        givenJobParams("https://www.youtube.com/watch?v=newvid", "USER");
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

        tasklet.execute(contribution, chunkContext);

        verify(youtubeMetadataClient).fetch("newvid");
        // 1차: 신규 콘텐츠 저장, 2차: pipelineStatus 업데이트
        verify(contentRepository, times(2)).save(any(Content.class));
    }

    @Test
    @DisplayName("실행 완료 후 ContentIngestedEvent를 발행한다")
    void publishesEventAfterExecution() throws Exception {
        givenJobParams("https://www.youtube.com/watch?v=abc123", "USER");
        when(youtubeUrlParser.extractVideoId(anyString())).thenReturn("abc123");
        when(youtubeUrlParser.canonicalize("abc123")).thenReturn("https://www.youtube.com/watch?v=abc123");

        Content existing = Content.builder()
                .canonicalUrl("https://www.youtube.com/watch?v=abc123")
                .platform("YOUTUBE").externalContentId("abc123")
                .title("영상").description("").status("ACTIVE").source("USER").build();
        when(contentRepository.findByCanonicalUrl(anyString())).thenReturn(Optional.of(existing));

        tasklet.execute(contribution, chunkContext);

        ArgumentCaptor<ContentIngestedEvent> captor = ArgumentCaptor.forClass(ContentIngestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ContentIngestedEvent event = captor.getValue();
        assertThat(event.getSource()).isEqualTo("USER");
    }

    @Test
    @DisplayName("source 파라미터가 없으면 기본값 USER로 처리한다")
    void defaultSourceIsUser() throws Exception {
        // source 없는 파라미터 (getOrDefault 동작 확인)
        Map<String, Object> params = Map.of("url", "https://www.youtube.com/watch?v=abc123");
        when(chunkContext.getStepContext()).thenReturn(stepContext);
        when(stepContext.getJobParameters()).thenReturn(params);
        when(youtubeUrlParser.extractVideoId(anyString())).thenReturn("abc123");
        when(youtubeUrlParser.canonicalize("abc123")).thenReturn("https://www.youtube.com/watch?v=abc123");

        Content existing = Content.builder()
                .canonicalUrl("https://www.youtube.com/watch?v=abc123")
                .platform("YOUTUBE").externalContentId("abc123")
                .title("영상").description("").status("ACTIVE").source("USER").build();
        when(contentRepository.findByCanonicalUrl(anyString())).thenReturn(Optional.of(existing));

        tasklet.execute(contribution, chunkContext);

        ArgumentCaptor<ContentIngestedEvent> captor = ArgumentCaptor.forClass(ContentIngestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("USER");
    }
}

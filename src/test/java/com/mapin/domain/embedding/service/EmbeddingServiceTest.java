package com.mapin.domain.embedding.service;

import com.mapin.domain.content.Content;
import com.mapin.domain.content.ContentRepository;
import com.mapin.infra.embedding.EmbeddingClient;
import com.mapin.infra.vectorstore.VectorStoreClient;
import com.mapin.domain.embedding.event.ContentEmbeddedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock private ContentRepository contentRepository;
    @Mock private EmbeddingClient embeddingClient;
    @Mock private VectorStoreClient vectorStoreClient;
    @Mock private ApplicationEventPublisher eventPublisher;

    private EmbeddingService service;

    @BeforeEach
    void setUp() {
        service = new EmbeddingService(contentRepository, embeddingClient, vectorStoreClient, eventPublisher);
    }

    private Content buildContent() {
        return Content.builder()
                .canonicalUrl("https://www.youtube.com/watch?v=abc")
                .platform("YOUTUBE").externalContentId("abc")
                .title("테스트 영상").description("영상 설명입니다")
                .status("ACTIVE").source("USER").build();
    }

    @Test
    @DisplayName("임베딩 생성 후 벡터 저장소에 upsert하고 콘텐츠를 업데이트한다")
    void embedsAndSavesContent() {
        Content content = buildContent();
        when(contentRepository.findById(1L)).thenReturn(Optional.of(content));
        when(embeddingClient.embed(anyString())).thenReturn(List.of(0.1f, 0.2f));
        when(embeddingClient.modelName()).thenReturn("text-embedding-3-small");
        when(contentRepository.save(any())).thenReturn(content);

        service.embed(1L, "USER");

        verify(vectorStoreClient).upsert(eq(1L), anyList());
        verify(contentRepository).save(content);
    }

    @Test
    @DisplayName("임베딩 텍스트에 [TITLE]과 [DESCRIPTION]이 포함된다")
    void passesFormattedTextToEmbeddingClient() {
        Content content = buildContent();
        when(contentRepository.findById(1L)).thenReturn(Optional.of(content));
        when(embeddingClient.embed(anyString())).thenReturn(List.of(0.1f));
        when(embeddingClient.modelName()).thenReturn("model");
        when(contentRepository.save(any())).thenReturn(content);

        service.embed(1L, "USER");

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(embeddingClient).embed(textCaptor.capture());
        assertThat(textCaptor.getValue()).contains("[TITLE]", "테스트 영상", "[DESCRIPTION]", "영상 설명입니다");
    }

    @Test
    @DisplayName("title/description이 null이면 빈 문자열로 대체한다")
    void handlesNullTitleAndDescription() {
        Content content = Content.builder()
                .canonicalUrl("https://www.youtube.com/watch?v=xyz")
                .platform("YOUTUBE").externalContentId("xyz")
                .title(null).description(null)
                .status("ACTIVE").source("USER").build();
        when(contentRepository.findById(2L)).thenReturn(Optional.of(content));
        when(embeddingClient.embed(anyString())).thenReturn(List.of(0.1f));
        when(embeddingClient.modelName()).thenReturn("model");
        when(contentRepository.save(any())).thenReturn(content);

        service.embed(2L, "USER");

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(embeddingClient).embed(textCaptor.capture());
        assertThat(textCaptor.getValue()).contains("[TITLE]", "[DESCRIPTION]");
        assertThat(textCaptor.getValue()).doesNotContain("null");
    }

    @Test
    @DisplayName("vectorId는 contentId의 문자열 값으로 저장된다")
    void vectorIdIsStringOfContentId() {
        Content content = buildContent();
        when(contentRepository.findById(42L)).thenReturn(Optional.of(content));
        when(embeddingClient.embed(anyString())).thenReturn(List.of(0.1f));
        when(embeddingClient.modelName()).thenReturn("model");
        when(contentRepository.save(any())).thenReturn(content);

        service.embed(42L, "USER");

        verify(vectorStoreClient).upsert(eq(42L), anyList());
        assertThat(content.getVectorId()).isEqualTo("42");
    }

    @Test
    @DisplayName("임베딩 완료 후 ContentEmbeddedEvent를 발행하고 source를 전달한다")
    void publishesEventWithSource() {
        Content content = buildContent();
        when(contentRepository.findById(1L)).thenReturn(Optional.of(content));
        when(embeddingClient.embed(anyString())).thenReturn(List.of(0.1f));
        when(embeddingClient.modelName()).thenReturn("model");
        when(contentRepository.save(any())).thenReturn(content);

        service.embed(1L, "ADMIN");

        ArgumentCaptor<ContentEmbeddedEvent> captor = ArgumentCaptor.forClass(ContentEmbeddedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("ADMIN");
        assertThat(captor.getValue().getContentId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("콘텐츠를 찾을 수 없으면 예외를 던진다")
    void throwsWhenContentNotFound() {
        when(contentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.embed(999L, "USER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("임베딩 실패 시 ContentEmbeddingFailedEvent를 발행하고 예외를 rethrow한다 (vectorUpserted=false)")
    void publishesFailureEventWhenEmbedFails() {
        Content content = buildContent();
        when(contentRepository.findById(1L)).thenReturn(Optional.of(content));
        when(embeddingClient.embed(anyString())).thenThrow(new RuntimeException("API 오류"));

        assertThatThrownBy(() -> service.embed(1L, "USER"))
                .isInstanceOf(RuntimeException.class);

        ArgumentCaptor<com.mapin.domain.embedding.event.ContentEmbeddingFailedEvent> captor =
                ArgumentCaptor.forClass(com.mapin.domain.embedding.event.ContentEmbeddingFailedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().isVectorUpserted()).isFalse();
    }

    @Test
    @DisplayName("벡터 저장 후 save 실패 시 vectorUpserted=true로 이벤트를 발행한다")
    void publishesFailureEventWithVectorUpsertedTrueWhenSaveFails() {
        Content content = buildContent();
        when(contentRepository.findById(1L)).thenReturn(Optional.of(content));
        when(embeddingClient.embed(anyString())).thenReturn(List.of(0.1f));
        when(embeddingClient.modelName()).thenReturn("model");
        doThrow(new RuntimeException("DB 오류")).when(contentRepository).save(any());

        assertThatThrownBy(() -> service.embed(1L, "USER"))
                .isInstanceOf(RuntimeException.class);

        ArgumentCaptor<com.mapin.domain.embedding.event.ContentEmbeddingFailedEvent> captor =
                ArgumentCaptor.forClass(com.mapin.domain.embedding.event.ContentEmbeddingFailedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().isVectorUpserted()).isTrue();
    }
}

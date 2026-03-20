package com.mapin.domain.recommendation;

import com.mapin.domain.content.Content;
import com.mapin.domain.content.ContentRepository;
import com.mapin.infra.embedding.EmbeddingClient;
import com.mapin.infra.vectorstore.VectorStoreClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VectorRecommendationStrategyTest {

    @Mock private EmbeddingClient embeddingClient;
    @Mock private VectorStoreClient vectorStoreClient;
    @Mock private ContentRepository contentRepository;

    private VectorRecommendationStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new VectorRecommendationStrategy(embeddingClient, vectorStoreClient, contentRepository);
    }

    private Content buildContent(String title, String description) {
        return Content.builder()
                .canonicalUrl("https://www.youtube.com/watch?v=abc")
                .platform("YOUTUBE").externalContentId("abc")
                .title(title).description(description)
                .status("ACTIVE").source("USER").build();
    }

    private void setId(Content content, Long id) throws Exception {
        Field field = Content.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(content, id);
    }

    @Test
    @DisplayName("임베딩 텍스트에 [TITLE]과 [DESCRIPTION]이 포함된다")
    void passesFormattedTextToEmbeddingClient() {
        Content content = buildContent("테스트 제목", "테스트 설명");
        when(embeddingClient.embed(anyString())).thenReturn(List.of(0.1f));
        when(vectorStoreClient.search(anyList(), anyInt())).thenReturn(List.of());

        strategy.getCandidates(content);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(embeddingClient).embed(captor.capture());
        assertThat(captor.getValue()).contains("[TITLE]", "테스트 제목", "[DESCRIPTION]", "테스트 설명");
    }

    @Test
    @DisplayName("벡터 검색 결과가 없으면 빈 리스트를 반환한다")
    void returnsEmptyWhenNoVectorIds() {
        Content content = buildContent("제목", "설명");
        when(embeddingClient.embed(anyString())).thenReturn(List.of(0.1f));
        when(vectorStoreClient.search(anyList(), anyInt())).thenReturn(List.of());

        List<Content> result = strategy.getCandidates(content);

        assertThat(result).isEmpty();
        verify(contentRepository, never()).findAllByVectorIdIn(any());
    }

    @Test
    @DisplayName("벡터 검색 결과에서 자기 자신을 제외한 후보를 반환한다")
    void excludesSelfFromCandidates() throws Exception {
        Content source = buildContent("제목", "설명");
        Content other = buildContent("다른 제목", "다른 설명");
        setId(source, 1L);
        setId(other, 2L);

        when(embeddingClient.embed(anyString())).thenReturn(List.of(0.1f));
        when(vectorStoreClient.search(anyList(), anyInt())).thenReturn(List.of("1", "2"));
        when(contentRepository.findAllByVectorIdIn(anyList())).thenReturn(List.of(source, other));

        List<Content> result = strategy.getCandidates(source);

        assertThat(result).doesNotContain(source);
        assertThat(result).contains(other);
    }

    @Test
    @DisplayName("title/description이 null이면 빈 문자열로 처리한다")
    void handlesNullTitleAndDescription() {
        Content content = buildContent(null, null);
        when(embeddingClient.embed(anyString())).thenReturn(List.of(0.1f));
        when(vectorStoreClient.search(anyList(), anyInt())).thenReturn(List.of());

        strategy.getCandidates(content);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(embeddingClient).embed(captor.capture());
        assertThat(captor.getValue()).doesNotContain("null");
    }

    @Test
    @DisplayName("embeddingClient 예외 발생 시 빈 리스트를 반환한다")
    void returnsEmptyOnEmbeddingException() {
        Content content = buildContent("제목", "설명");
        when(embeddingClient.embed(anyString())).thenThrow(new RuntimeException("OpenAI 오류"));

        List<Content> result = strategy.getCandidates(content);

        assertThat(result).isEmpty();
        verify(vectorStoreClient, never()).search(any(), anyInt());
    }

    @Test
    @DisplayName("vectorStoreClient 예외 발생 시 빈 리스트를 반환한다")
    void returnsEmptyOnVectorStoreException() {
        Content content = buildContent("제목", "설명");
        when(embeddingClient.embed(anyString())).thenReturn(List.of(0.1f));
        when(vectorStoreClient.search(anyList(), anyInt())).thenThrow(new RuntimeException("Qdrant 오류"));

        List<Content> result = strategy.getCandidates(content);

        assertThat(result).isEmpty();
    }
}

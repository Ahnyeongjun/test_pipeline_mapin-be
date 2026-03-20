package com.mapin.embedding.batch;

import com.mapin.common.domain.Content;
import com.mapin.common.domain.ContentRepository;
import com.mapin.embedding.client.EmbeddingClient;
import com.mapin.embedding.client.VectorStoreClient;
import com.mapin.embedding.event.ContentEmbeddedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmbeddingTaskletTest {

    @Mock private ContentRepository contentRepository;
    @Mock private EmbeddingClient embeddingClient;
    @Mock private VectorStoreClient vectorStoreClient;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Mock private StepContribution contribution;
    @Mock private StepExecution stepExecution;
    @Mock private ChunkContext chunkContext;

    private EmbeddingTasklet tasklet;

    @BeforeEach
    void setUp() {
        tasklet = new EmbeddingTasklet(contentRepository, embeddingClient, vectorStoreClient, eventPublisher);
    }

    private void givenJobParams(Long contentId, String source) {
        JobParameters params = new JobParametersBuilder()
                .addLong("contentId", contentId)
                .addString("source", source)
                .toJobParameters();
        when(contribution.getStepExecution()).thenReturn(stepExecution);
        when(stepExecution.getJobParameters()).thenReturn(params);
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
    void embedsAndSavesContent() throws Exception {
        givenJobParams(1L, "USER");
        Content content = buildContent();
        when(contentRepository.findById(1L)).thenReturn(Optional.of(content));
        when(embeddingClient.embed(anyString())).thenReturn(List.of(0.1f, 0.2f));
        when(embeddingClient.modelName()).thenReturn("text-embedding-3-small");
        when(contentRepository.save(any())).thenReturn(content);

        RepeatStatus status = tasklet.execute(contribution, chunkContext);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(vectorStoreClient).upsert(eq(1L), anyList());
        verify(contentRepository).save(content);
    }

    @Test
    @DisplayName("임베딩 텍스트에 [TITLE]과 [DESCRIPTION]이 포함된다")
    void passesFormattedTextToEmbeddingClient() throws Exception {
        givenJobParams(1L, "USER");
        Content content = buildContent();
        when(contentRepository.findById(1L)).thenReturn(Optional.of(content));
        when(embeddingClient.embed(anyString())).thenReturn(List.of(0.1f));
        when(embeddingClient.modelName()).thenReturn("model");
        when(contentRepository.save(any())).thenReturn(content);

        tasklet.execute(contribution, chunkContext);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(embeddingClient).embed(textCaptor.capture());
        assertThat(textCaptor.getValue()).contains("[TITLE]", "테스트 영상", "[DESCRIPTION]", "영상 설명입니다");
    }

    @Test
    @DisplayName("title/description이 null이면 빈 문자열로 대체한다")
    void handlesNullTitleAndDescription() throws Exception {
        givenJobParams(2L, "USER");
        Content content = Content.builder()
                .canonicalUrl("https://www.youtube.com/watch?v=xyz")
                .platform("YOUTUBE").externalContentId("xyz")
                .title(null).description(null)
                .status("ACTIVE").source("USER").build();
        when(contentRepository.findById(2L)).thenReturn(Optional.of(content));
        when(embeddingClient.embed(anyString())).thenReturn(List.of(0.1f));
        when(embeddingClient.modelName()).thenReturn("model");
        when(contentRepository.save(any())).thenReturn(content);

        tasklet.execute(contribution, chunkContext);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(embeddingClient).embed(textCaptor.capture());
        assertThat(textCaptor.getValue()).contains("[TITLE]", "[DESCRIPTION]");
        assertThat(textCaptor.getValue()).doesNotContain("null");
    }

    @Test
    @DisplayName("vectorId는 contentId의 문자열 값으로 저장된다")
    void vectorIdIsStringOfContentId() throws Exception {
        givenJobParams(42L, "USER");
        Content content = buildContent();
        when(contentRepository.findById(42L)).thenReturn(Optional.of(content));
        when(embeddingClient.embed(anyString())).thenReturn(List.of(0.1f));
        when(embeddingClient.modelName()).thenReturn("model");
        when(contentRepository.save(any())).thenReturn(content);

        tasklet.execute(contribution, chunkContext);

        verify(vectorStoreClient).upsert(eq(42L), anyList());
        assertThat(content.getVectorId()).isEqualTo("42");
    }

    @Test
    @DisplayName("임베딩 완료 후 ContentEmbeddedEvent를 발행하고 source를 전달한다")
    void publishesEventWithSource() throws Exception {
        givenJobParams(1L, "ADMIN");
        Content content = buildContent();
        when(contentRepository.findById(1L)).thenReturn(Optional.of(content));
        when(embeddingClient.embed(anyString())).thenReturn(List.of(0.1f));
        when(embeddingClient.modelName()).thenReturn("model");
        when(contentRepository.save(any())).thenReturn(content);

        tasklet.execute(contribution, chunkContext);

        ArgumentCaptor<ContentEmbeddedEvent> captor = ArgumentCaptor.forClass(ContentEmbeddedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("ADMIN");
        assertThat(captor.getValue().getContentId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("콘텐츠를 찾을 수 없으면 예외를 던진다")
    void throwsWhenContentNotFound() {
        givenJobParams(999L, "USER");
        when(contentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tasklet.execute(contribution, chunkContext))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999");
    }
}

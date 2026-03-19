package com.mapin.analysis.batch;

import com.mapin.analysis.client.PerspectiveAnalysisResult;
import com.mapin.analysis.client.PerspectiveClassifier;
import com.mapin.analysis.event.ContentAnalyzedEvent;
import com.mapin.common.domain.Content;
import com.mapin.common.domain.ContentRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisTaskletTest {

    @Mock private ContentRepository contentRepository;
    @Mock private PerspectiveClassifier perspectiveClassifier;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Mock private StepContribution contribution;
    @Mock private StepExecution stepExecution;
    @Mock private ChunkContext chunkContext;

    private AnalysisTasklet tasklet;

    @BeforeEach
    void setUp() {
        tasklet = new AnalysisTasklet(contentRepository, perspectiveClassifier, eventPublisher);
    }

    private void givenJobParams(Long contentId, String source) {
        JobParameters params = new JobParametersBuilder()
                .addLong("contentId", contentId)
                .addString("source", source)
                .toJobParameters();
        when(contribution.getStepExecution()).thenReturn(stepExecution);
        when(stepExecution.getJobParameters()).thenReturn(params);
    }

    private Content buildContent(Long id) {
        Content content = Content.builder()
                .canonicalUrl("https://www.youtube.com/watch?v=abc")
                .platform("YOUTUBE").externalContentId("abc")
                .title("테스트 영상").description("영상 설명입니다")
                .status("ACTIVE").source("USER").build();
        // 리플렉션으로 id 설정 없이 mock 사용
        return content;
    }

    @Test
    @DisplayName("GPT 분류기에 제목+설명 형식의 텍스트를 전달한다")
    void passesFormattedTextToClassifier() throws Exception {
        givenJobParams(1L, "USER");

        Content content = buildContent(1L);
        when(contentRepository.findById(1L)).thenReturn(Optional.of(content));

        PerspectiveAnalysisResult result = new PerspectiveAnalysisResult(
                "politics", "pro", "government",
                List.of("정치", "정책"), "요약", "neutral", "low", true);
        when(perspectiveClassifier.classify(anyString())).thenReturn(result);
        when(contentRepository.save(any())).thenReturn(content);

        tasklet.execute(contribution, chunkContext);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(perspectiveClassifier).classify(textCaptor.capture());
        String text = textCaptor.getValue();
        assertThat(text).contains("[TITLE]");
        assertThat(text).contains("테스트 영상");
        assertThat(text).contains("[DESCRIPTION]");
        assertThat(text).contains("영상 설명입니다");
    }

    @Test
    @DisplayName("분석 결과로 콘텐츠를 업데이트하고 저장한다")
    void updatesAndSavesContent() throws Exception {
        givenJobParams(1L, "USER");

        Content content = buildContent(1L);
        when(contentRepository.findById(1L)).thenReturn(Optional.of(content));

        PerspectiveAnalysisResult result = new PerspectiveAnalysisResult(
                "economy", "con", "labor",
                List.of("임금", "고용"), "노동자 입장 영상", "passionate", "medium", true);
        when(perspectiveClassifier.classify(anyString())).thenReturn(result);
        when(contentRepository.save(any())).thenReturn(content);

        tasklet.execute(contribution, chunkContext);

        assertThat(content.getCategory()).isEqualTo("economy");
        assertThat(content.getPerspectiveLevel()).isEqualTo("con");
        assertThat(content.getPerspectiveStakeholder()).isEqualTo("labor");
        assertThat(content.getKeywords()).containsExactly("임금", "고용");
        verify(contentRepository).save(content);
    }

    @Test
    @DisplayName("분석 완료 후 ContentAnalyzedEvent를 발행한다")
    void publishesAnalyzedEvent() throws Exception {
        givenJobParams(1L, "USER");

        Content content = buildContent(1L);
        when(contentRepository.findById(1L)).thenReturn(Optional.of(content));

        PerspectiveAnalysisResult result = new PerspectiveAnalysisResult(
                "politics", "pro", "government",
                List.of(), "요약", "neutral", "low", false);
        when(perspectiveClassifier.classify(anyString())).thenReturn(result);
        when(contentRepository.save(any())).thenReturn(content);

        tasklet.execute(contribution, chunkContext);

        ArgumentCaptor<ContentAnalyzedEvent> captor = ArgumentCaptor.forClass(ContentAnalyzedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("USER");
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

    @Test
    @DisplayName("RepeatStatus.FINISHED를 반환한다")
    void returnsFinished() throws Exception {
        givenJobParams(1L, "USER");

        Content content = buildContent(1L);
        when(contentRepository.findById(1L)).thenReturn(Optional.of(content));

        PerspectiveAnalysisResult result = new PerspectiveAnalysisResult(
                "politics", "pro", "gov", List.of(), "요약", "neutral", "low", false);
        when(perspectiveClassifier.classify(anyString())).thenReturn(result);
        when(contentRepository.save(any())).thenReturn(content);

        RepeatStatus status = tasklet.execute(contribution, chunkContext);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
    }
}

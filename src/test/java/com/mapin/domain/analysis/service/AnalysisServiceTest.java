package com.mapin.domain.analysis.service;

import com.mapin.infra.perspective.PerspectiveAnalysisResult;
import com.mapin.infra.perspective.PerspectiveClassifier;
import com.mapin.domain.analysis.event.ContentAnalyzedEvent;
import com.mapin.domain.content.Content;
import com.mapin.domain.content.ContentRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    @Mock private ContentRepository contentRepository;
    @Mock private PerspectiveClassifier perspectiveClassifier;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AnalysisService service;

    @BeforeEach
    void setUp() {
        service = new AnalysisService(contentRepository, perspectiveClassifier, eventPublisher);
    }

    private Content buildContent() {
        return Content.builder()
                .canonicalUrl("https://www.youtube.com/watch?v=abc")
                .platform("YOUTUBE").externalContentId("abc")
                .title("테스트 영상").description("영상 설명입니다")
                .status("ACTIVE").source("USER").build();
    }

    @Test
    @DisplayName("GPT 분류기에 제목+설명 형식의 텍스트를 전달한다")
    void passesFormattedTextToClassifier() {
        Content content = buildContent();
        when(contentRepository.findById(1L)).thenReturn(Optional.of(content));

        PerspectiveAnalysisResult result = new PerspectiveAnalysisResult(
                "politics", "pro", "government",
                List.of("정치", "정책"), List.of("정치"), "요약", "neutral", true);
        when(perspectiveClassifier.classify(anyString())).thenReturn(result);
        when(contentRepository.save(any())).thenReturn(content);

        service.analyze(1L, "USER");

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
    void updatesAndSavesContent() {
        Content content = buildContent();
        when(contentRepository.findById(1L)).thenReturn(Optional.of(content));

        PerspectiveAnalysisResult result = new PerspectiveAnalysisResult(
                "economy", "con", "labor",
                List.of("임금", "고용"), List.of("임금"), "노동자 입장 영상", "passionate", true);
        when(perspectiveClassifier.classify(anyString())).thenReturn(result);
        when(contentRepository.save(any())).thenReturn(content);

        service.analyze(1L, "USER");

        assertThat(content.getCategory()).isEqualTo("economy");
        assertThat(content.getPerspectiveLevel()).isEqualTo("con");
        assertThat(content.getPerspectiveStakeholder()).isEqualTo("labor");
        assertThat(content.getKeywords()).containsExactly("임금", "고용");
        verify(contentRepository).save(content);
    }

    @Test
    @DisplayName("분석 완료 후 ContentAnalyzedEvent를 발행한다")
    void publishesAnalyzedEvent() {
        Content content = buildContent();
        when(contentRepository.findById(1L)).thenReturn(Optional.of(content));

        PerspectiveAnalysisResult result = new PerspectiveAnalysisResult(
                "politics", "pro", "government",
                List.of(), List.of(), "요약", "neutral", false);
        when(perspectiveClassifier.classify(anyString())).thenReturn(result);
        when(contentRepository.save(any())).thenReturn(content);

        service.analyze(1L, "USER");

        ArgumentCaptor<ContentAnalyzedEvent> captor = ArgumentCaptor.forClass(ContentAnalyzedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("USER");
    }

    @Test
    @DisplayName("콘텐츠를 찾을 수 없으면 예외를 던진다")
    void throwsWhenContentNotFound() {
        when(contentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.analyze(999L, "USER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("분석 실패 시 ContentAnalysisFailedEvent를 발행하고 예외를 rethrow한다")
    void publishesFailureEventWhenAnalysisFails() {
        Content content = buildContent();
        when(contentRepository.findById(1L)).thenReturn(Optional.of(content));
        when(perspectiveClassifier.classify(anyString())).thenThrow(new RuntimeException("GPT 오류"));

        assertThatThrownBy(() -> service.analyze(1L, "USER"))
                .isInstanceOf(RuntimeException.class);

        ArgumentCaptor<com.mapin.domain.analysis.event.ContentAnalysisFailedEvent> captor =
                ArgumentCaptor.forClass(com.mapin.domain.analysis.event.ContentAnalysisFailedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getContentId()).isEqualTo(1L);
        assertThat(captor.getValue().getReason()).contains("GPT 오류");
    }
}

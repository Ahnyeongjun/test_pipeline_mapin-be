package com.mapin.domain.analysis.service;

import com.mapin.infra.perspective.PerspectiveAnalysisResult;
import com.mapin.infra.perspective.PerspectiveClassifier;
import com.mapin.domain.analysis.event.ContentAnalysisFailedEvent;
import com.mapin.domain.analysis.event.ContentAnalyzedEvent;
import com.mapin.domain.content.Content;
import com.mapin.domain.content.ContentRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisService {

    private final ContentRepository contentRepository;
    private final PerspectiveClassifier perspectiveClassifier;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void analyze(Long contentId, String source) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다. id=" + contentId));

        String text = "[TITLE]\n%s\n\n[DESCRIPTION]\n%s".formatted(
                Objects.toString(content.getTitle(), ""), Objects.toString(content.getDescription(), ""));

        try {
            PerspectiveAnalysisResult result = perspectiveClassifier.classify(text);
            content.updatePerspective(result.category(), result.perspectiveLevel(), result.perspectiveStakeholder(),
                    result.keywords(), result.summary(), result.tone(), result.biasLevel(), result.isOpinionated());
            content.updatePipelineStatus("ANALYZED");
            contentRepository.save(content);

            log.info("[Analysis] contentId={} category={} level={} stakeholder={} source={}",
                    contentId, result.category(), result.perspectiveLevel(), result.perspectiveStakeholder(), source);

            eventPublisher.publishEvent(new ContentAnalyzedEvent(this, contentId, source));
        } catch (Exception e) {
            log.error("[Analysis][Saga] 분석 실패 contentId={}: {}", contentId, e.getMessage(), e);
            eventPublisher.publishEvent(new ContentAnalysisFailedEvent(this, contentId, source, e.getMessage()));
            throw e;
        }
    }
}

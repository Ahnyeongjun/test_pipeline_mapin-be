package com.mapin.content.application;

import com.mapin.content.domain.Content;
import com.mapin.content.domain.ContentRepository;
import com.mapin.content.dto.ContentPerspectiveResponse;
import com.mapin.content.dto.PerspectiveAnalysisResult;
import com.mapin.content.port.PerspectiveClassifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ContentPerspectiveAnalysisService {

    private final ContentRepository contentRepository;
    private final PerspectiveClassifier perspectiveClassifier;

    public ContentPerspectiveResponse analyze(Long contentId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다. id=" + contentId));

        String text = buildAnalysisText(content);
        PerspectiveAnalysisResult result = perspectiveClassifier.classify(text);

        content.updatePerspective(
                result.category(),
                result.perspectiveLevel(),
                result.perspectiveStakeholder()
        );

        return new ContentPerspectiveResponse(
                content.getId(),
                result.category(),
                result.perspectiveLevel(),
                result.perspectiveStakeholder()
        );
    }

    private String buildAnalysisText(Content content) {
        String title = content.getTitle() == null ? "" : content.getTitle();
        String description = content.getDescription() == null ? "" : content.getDescription();

        return """
                [TITLE]
                %s

                [DESCRIPTION]
                %s
                """.formatted(title, description);
    }
}

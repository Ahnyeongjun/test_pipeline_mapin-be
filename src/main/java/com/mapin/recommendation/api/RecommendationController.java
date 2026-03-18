package com.mapin.recommendation.api;

import com.mapin.common.domain.Content;
import com.mapin.common.domain.ContentRepository;
import com.mapin.recommendation.domain.ContentRecommendation;
import com.mapin.recommendation.domain.ContentRecommendationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contents")
@RequiredArgsConstructor
@Slf4j
public class RecommendationController {

    private final ContentRepository contentRepository;
    private final ContentRecommendationRepository recommendationRepository;

    @GetMapping("/{id}/recommendations")
    public ResponseEntity<List<RecommendationResponse>> getRecommendations(@PathVariable Long id) {
        contentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다. id=" + id));

        List<ContentRecommendation> relations = recommendationRepository
                .findBySourceContentIdOrderByScoreDesc(id);

        if (relations.isEmpty()) {
            log.info("[Recommendation] 관계 없음 contentId={}", id);
            return ResponseEntity.ok(List.of());
        }

        List<Long> targetIds = relations.stream()
                .map(ContentRecommendation::getTargetContentId)
                .toList();

        List<RecommendationResponse> result = contentRepository.findAllById(targetIds).stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(result);
    }

    private RecommendationResponse toResponse(Content c) {
        return new RecommendationResponse(
                c.getId(),
                c.getTitle(),
                c.getThumbnailUrl(),
                c.getCategory(),
                c.getPerspectiveLevel(),
                c.getPerspectiveStakeholder(),
                c.getCanonicalUrl()
        );
    }
}

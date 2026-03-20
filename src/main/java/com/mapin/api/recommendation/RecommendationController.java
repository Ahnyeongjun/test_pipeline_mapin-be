package com.mapin.api.recommendation;

import com.mapin.domain.content.Content;
import com.mapin.domain.content.ContentRepository;
import com.mapin.domain.recommendation.ContentRecommendation;
import com.mapin.domain.recommendation.ContentRecommendationRepository;
import com.mapin.api.recommendation.dto.RecommendationResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "콘텐츠를 찾을 수 없습니다. id=" + id));

        List<ContentRecommendation> relations = recommendationRepository
                .findBySourceContentIdOrderByScoreDesc(id);

        if (relations.isEmpty()) {
            log.info("[Recommendation] 관계 없음 contentId={}", id);
            return ResponseEntity.ok(List.of());
        }

        Map<Long, Integer> scoreByTargetId = relations.stream()
                .collect(Collectors.toMap(
                        ContentRecommendation::getTargetContentId,
                        ContentRecommendation::getScore));

        List<Long> targetIds = relations.stream()
                .map(ContentRecommendation::getTargetContentId)
                .toList();

        List<RecommendationResponse> result = contentRepository.findAllById(targetIds).stream()
                .sorted(Comparator.comparingInt(c -> -scoreByTargetId.getOrDefault(c.getId(), 0)))
                .map(c -> toResponse(c, scoreByTargetId.getOrDefault(c.getId(), 0)))
                .toList();

        return ResponseEntity.ok(result);
    }

    private RecommendationResponse toResponse(Content c, int score) {
        return new RecommendationResponse(
                c.getId(),
                c.getTitle(),
                c.getThumbnailUrl(),
                c.getCategory(),
                c.getPerspectiveLevel(),
                c.getPerspectiveStakeholder(),
                c.getCanonicalUrl(),
                score
        );
    }
}

package com.mapin.content.application;

import com.mapin.content.domain.Content;
import com.mapin.content.domain.ContentRepository;
import com.mapin.content.dto.ContentRecommendationResponse;
import com.mapin.content.dto.RecommendationCandidate;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 파이프라인 A: 벡터 DB 없이 SQL 기반 추천.
 * 같은 category + 다른 perspectiveStakeholder 조건으로 후보를 찾는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DbRecommendationService implements RecommendationStrategy {

    private final ContentRepository contentRepository;
    private final CandidateValidationService candidateValidationService;

    @Override
    public List<ContentRecommendationResponse> recommend(Content source, int topK) {
        if (source.getCategory() == null || source.getPerspectiveStakeholder() == null) {
            log.warn("Source content has no perspective info. contentId={}", source.getId());
            return List.of();
        }

        List<Content> candidates = contentRepository
                .findByCategoryAndPerspectiveStakeholderNotAndIdNot(
                        source.getCategory(),
                        source.getPerspectiveStakeholder(),
                        source.getId()
                );

        log.debug("DB recommendation: found {} candidates for contentId={} category={} stakeholder={}",
                candidates.size(), source.getId(), source.getCategory(), source.getPerspectiveStakeholder());

        return candidates.stream()
                .map(candidate -> candidateValidationService.validate(source, candidate, 1.0))
                .filter(RecommendationCandidate::qualified)
                .sorted(Comparator.comparing(RecommendationCandidate::finalScore).reversed())
                .limit(topK)
                .map(this::toResponse)
                .toList();
    }

    private ContentRecommendationResponse toResponse(RecommendationCandidate candidate) {
        Content c = candidate.content();
        return new ContentRecommendationResponse(
                c.getId(),
                c.getCanonicalUrl(),
                c.getTitle(),
                c.getThumbnailUrl(),
                c.getChannelTitle(),
                c.getPublishedAt(),
                c.getCategory(),
                c.getPerspectiveLevel(),
                c.getPerspectiveStakeholder(),
                candidate.topicSimilarity(),
                candidate.perspectiveDistance(),
                candidate.finalScore()
        );
    }
}

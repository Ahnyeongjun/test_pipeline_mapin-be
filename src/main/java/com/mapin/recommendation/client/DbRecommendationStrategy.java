package com.mapin.recommendation.client;

import com.mapin.common.domain.Content;
import com.mapin.common.domain.ContentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SQL 기반 추천: 같은 category + 다른 perspectiveStakeholder 로 필터링.
 * 벡터 DB 없이도 동작하는 기본 전략.
 */
@Component("dbRecommendation")
@RequiredArgsConstructor
@Slf4j
public class DbRecommendationStrategy implements RecommendationStrategy {

    private final ContentRepository contentRepository;

    @Override
    public List<Content> recommend(Content content, int limit) {
        if (content.getCategory() == null || content.getPerspectiveStakeholder() == null) {
            log.warn("[DbRecommend] category 또는 stakeholder 미분류 contentId={}", content.getId());
            return List.of();
        }

        List<Content> candidates = contentRepository
                .findByCategoryAndPerspectiveStakeholderNotAndIdNot(
                        content.getCategory(),
                        content.getPerspectiveStakeholder(),
                        content.getId()
                );

        return candidates.stream().limit(limit).toList();
    }
}

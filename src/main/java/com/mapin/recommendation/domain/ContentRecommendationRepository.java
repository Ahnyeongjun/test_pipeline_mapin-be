package com.mapin.recommendation.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentRecommendationRepository extends JpaRepository<ContentRecommendation, Long> {

    List<ContentRecommendation> findBySourceContentIdOrderByScoreDesc(Long sourceContentId);

    boolean existsBySourceContentIdAndTargetContentId(Long sourceContentId, Long targetContentId);
}

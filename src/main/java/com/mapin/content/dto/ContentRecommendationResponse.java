package com.mapin.content.dto;

import java.time.OffsetDateTime;

public record ContentRecommendationResponse(
        Long id,
        String canonicalUrl,
        String title,
        String thumbnailUrl,
        String channelTitle,
        OffsetDateTime publishedAt,
        String category,
        String perspectiveLevel,
        String perspectiveStakeholder,
        double topicSimilarity,
        int perspectiveDistance,
        double finalScore
) {
}

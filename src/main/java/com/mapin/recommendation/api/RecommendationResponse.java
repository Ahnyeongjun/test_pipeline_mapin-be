package com.mapin.recommendation.api;

public record RecommendationResponse(
        Long contentId,
        String title,
        String thumbnailUrl,
        String category,
        String perspectiveLevel,
        String perspectiveStakeholder,
        String canonicalUrl,
        int score
) {}

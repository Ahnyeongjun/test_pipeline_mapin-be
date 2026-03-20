package com.mapin.api.recommendation.dto;

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

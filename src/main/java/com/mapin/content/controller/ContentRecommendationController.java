package com.mapin.content.controller;

import com.mapin.content.application.ContentRecommendationOrchestrator;
import com.mapin.content.dto.ContentRecommendationResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
public class ContentRecommendationController {

    private final ContentRecommendationOrchestrator contentRecommendationOrchestrator;

    @GetMapping("/{contentId}/recommendations")
    public ResponseEntity<List<ContentRecommendationResponse>> recommend(
            @PathVariable Long contentId,
            @RequestParam(defaultValue = "3") int topK
    ) {
        return ResponseEntity.ok(contentRecommendationOrchestrator.recommend(contentId, topK));
    }
}

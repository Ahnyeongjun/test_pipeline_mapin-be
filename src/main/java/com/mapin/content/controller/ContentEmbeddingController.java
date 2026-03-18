package com.mapin.content.controller;

import com.mapin.content.application.ContentEmbeddingService;
import com.mapin.content.dto.ContentEmbeddingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
public class ContentEmbeddingController {

    private final ContentEmbeddingService contentEmbeddingService;

    @PostMapping("/{contentId}/embed")
    public ResponseEntity<ContentEmbeddingResponse> embed(@PathVariable Long contentId) {
        return ResponseEntity.ok(contentEmbeddingService.embed(contentId));
    }
}

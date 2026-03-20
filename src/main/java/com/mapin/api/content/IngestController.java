package com.mapin.api.content;

import com.mapin.infra.youtube.YoutubeUrlParser;
import com.mapin.api.content.dto.IngestRequest;
import com.mapin.api.content.dto.IngestResponse;
import com.mapin.domain.content.service.IngestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
@Slf4j
public class IngestController {

    private final IngestService ingestService;
    private final YoutubeUrlParser youtubeUrlParser;

    @PostMapping
    public ResponseEntity<IngestResponse> ingest(@Valid @RequestBody IngestRequest request) {
        String videoId = youtubeUrlParser.extractVideoId(request.url());
        String canonicalUrl = youtubeUrlParser.canonicalize(videoId);

        Long contentId = ingestService.ingest(canonicalUrl, "USER");
        log.info("[Ingest] 요청 처리 완료. url={}", canonicalUrl);

        return ResponseEntity.accepted().body(new IngestResponse(contentId, canonicalUrl));
    }
}

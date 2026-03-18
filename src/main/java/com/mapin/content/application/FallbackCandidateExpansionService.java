package com.mapin.content.application;

import com.mapin.content.domain.Content;
import com.mapin.content.domain.ContentRepository;
import com.mapin.content.dto.ContentResponse;
import com.mapin.content.port.YoutubeSearchClient;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FallbackCandidateExpansionService {

    private final SearchQueryGenerationService searchQueryGenerationService;
    private final YoutubeSearchClient youtubeSearchClient;
    private final ContentRepository contentRepository;
    private final ContentEmbeddingService contentEmbeddingService;
    private final ContentPerspectiveAnalysisService contentPerspectiveAnalysisService;
    private final ContentIngestService contentIngestService;

    /**
     * YouTube Search로 외부 후보를 수집하고 ingest + 분석까지 완료한다.
     * 파이프라인 A/B 공통 fallback.
     */
    public void expand(Content source) {
        log.info("Fallback expansion started for contentId={}", source.getId());
        List<String> queries = searchQueryGenerationService.generate(source);
        log.debug("Generated {} search queries for contentId={}: {}", queries.size(), source.getId(), queries);

        if (queries.isEmpty()) {
            log.warn("No search queries generated for contentId={} title='{}'", source.getId(), source.getTitle());
            return;
        }

        Set<String> candidateVideoIds = new LinkedHashSet<>();
        for (String query : queries) {
            List<String> videoIds = youtubeSearchClient.searchVideoIds(query, 5);
            log.debug("Query [{}] produced {} video ids", query, videoIds.size());
            candidateVideoIds.addAll(videoIds);
        }

        candidateVideoIds.remove(source.getExternalContentId());
        log.info("Collected {} unique candidate ids after filtering source", candidateVideoIds.size());

        for (String videoId : candidateVideoIds) {
            String url = "https://www.youtube.com/watch?v=" + videoId;
            log.info("Processing fallback candidate videoId={}", videoId);

            ContentResponse response = contentIngestService.ingestYoutubeUrl(url);
            Long contentId = response.id();

            Content candidate = contentRepository.findById(contentId)
                    .orElseThrow(() -> new IllegalStateException("후보 콘텐츠를 찾을 수 없습니다. id=" + contentId));

            if (candidate.getVectorId() == null || candidate.getVectorId().isBlank()) {
                contentEmbeddingService.embed(contentId);
            }

            if (candidate.getPerspectiveLevel() == null || candidate.getPerspectiveStakeholder() == null) {
                contentPerspectiveAnalysisService.analyze(contentId);
            }
        }

        log.info("Fallback expansion finished for source contentId={} (processed {} candidates)",
                source.getId(), candidateVideoIds.size());
    }
}

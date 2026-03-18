package com.mapin.content.application;

import com.mapin.content.domain.Content;
import com.mapin.content.domain.ContentRepository;
import com.mapin.content.dto.ContentEmbeddingResponse;
import com.mapin.content.port.EmbeddingClient;
import com.mapin.content.port.VectorStoreClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ContentEmbeddingService {

    private final ContentRepository contentRepository;
    private final EmbeddingClient embeddingClient;
    private final VectorStoreClient vectorStoreClient;

    public ContentEmbeddingResponse embed(Long contentId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다. id=" + contentId));

        String embeddingText = buildEmbeddingText(content);
        List<Float> vector = embeddingClient.embed(embeddingText);
        String vectorId = buildVectorId(content);
        String embeddingModel = embeddingClient.modelName();

        vectorStoreClient.upsert(
                vectorId,
                vector,
                Map.of(
                        "contentId", content.getId(),
                        "platform", content.getPlatform(),
                        "status", content.getStatus()
                )
        );

        content.updateEmbeddingInfo(embeddingText, embeddingModel, vectorId);

        return new ContentEmbeddingResponse(
                content.getId(),
                embeddingModel,
                vectorId,
                embeddingText
        );
    }

    private String buildEmbeddingText(Content content) {
        String title = content.getTitle() == null ? "" : content.getTitle();
        String description = content.getDescription() == null ? "" : content.getDescription();
        return title + "\n" + description;
    }

    private String buildVectorId(Content content) {
        String seed = "content:" + content.getId();
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }
}

package com.mapin.content.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentRepository extends JpaRepository<Content, Long> {

    Optional<Content> findByCanonicalUrl(String canonicalUrl);

    List<Content> findAllByVectorIdIn(List<String> vectorIds);

    List<Content> findByCategoryAndPerspectiveStakeholderNotAndIdNot(
            String category, String perspectiveStakeholder, Long excludeId);
}

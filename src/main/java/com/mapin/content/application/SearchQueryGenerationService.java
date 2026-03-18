package com.mapin.content.application;

import com.mapin.content.domain.Content;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SearchQueryGenerationService {

    public List<String> generate(Content source) {
        String base = normalizeBaseQuery(source.getTitle());

        List<String> queries = new ArrayList<>();
        queries.add(base);
        queries.add(base + " 원인 분석");
        queries.add(base + " 구조 문제");
        queries.add(base + " 전문가 해설");
        queries.add(base + " 시민 반응");

        List<String> distinctQueries = queries.stream().distinct().toList();
        if (distinctQueries.isEmpty()) {
            log.warn("SearchQueryGenerationService generated no queries for contentId={} title='{}'",
                    source.getId(), source.getTitle());
        }
        return distinctQueries;
    }

    private String normalizeBaseQuery(String title) {
        if (title == null || title.isBlank()) {
            log.warn("Source title is blank while generating search query");
            return "";
        }
        return title.trim();
    }
}

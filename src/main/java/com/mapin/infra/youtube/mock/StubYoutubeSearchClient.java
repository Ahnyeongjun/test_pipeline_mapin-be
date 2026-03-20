package com.mapin.infra.youtube;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class StubYoutubeSearchClient implements YoutubeSearchClient {

    @Override
    public List<String> searchVideoIds(String query, int maxResults) {
        return List.of();
    }
}

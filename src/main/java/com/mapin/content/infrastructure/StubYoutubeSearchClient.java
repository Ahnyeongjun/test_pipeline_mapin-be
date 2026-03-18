package com.mapin.content.infrastructure;

import com.mapin.content.port.YoutubeSearchClient;
import java.util.Collections;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class StubYoutubeSearchClient implements YoutubeSearchClient {

    @Override
    public List<String> searchVideoIds(String query, int maxResults) {
        return Collections.emptyList();
    }
}

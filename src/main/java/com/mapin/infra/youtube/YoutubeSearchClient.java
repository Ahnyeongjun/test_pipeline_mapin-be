package com.mapin.infra.youtube;

import java.util.List;

public interface YoutubeSearchClient {

    List<String> searchVideoIds(String query, int maxResults);
}

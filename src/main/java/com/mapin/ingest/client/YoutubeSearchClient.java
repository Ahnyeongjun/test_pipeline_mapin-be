package com.mapin.ingest.client;

import java.util.List;

public interface YoutubeSearchClient {

    List<String> searchVideoIds(String query, int maxResults);
}

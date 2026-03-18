package com.mapin.content.port;

import java.util.List;

public interface YoutubeSearchClient {

    List<String> searchVideoIds(String query, int maxResults);
}

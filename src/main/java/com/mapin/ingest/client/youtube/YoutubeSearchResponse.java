package com.mapin.ingest.client.youtube;

import java.util.List;

public record YoutubeSearchResponse(List<YoutubeSearchItem> items) {
}

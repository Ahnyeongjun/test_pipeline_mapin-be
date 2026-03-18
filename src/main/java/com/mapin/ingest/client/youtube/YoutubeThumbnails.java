package com.mapin.ingest.client.youtube;

import com.fasterxml.jackson.annotation.JsonProperty;

public record YoutubeThumbnails(
        @JsonProperty("default") YoutubeThumbnail defaultValue,
        YoutubeThumbnail medium,
        YoutubeThumbnail high) {
}

package com.mapin.content.dto.youtube;

import com.fasterxml.jackson.annotation.JsonProperty;

public record YoutubeThumbnails(
        @JsonProperty("default") YoutubeThumbnail defaultValue,
        YoutubeThumbnail medium,
        YoutubeThumbnail high
) {
}

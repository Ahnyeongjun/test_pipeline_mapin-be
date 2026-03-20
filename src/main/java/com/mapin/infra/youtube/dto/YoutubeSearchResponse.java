package com.mapin.infra.youtube.dto;

import java.util.List;

public record YoutubeSearchResponse(List<YoutubeSearchItem> items) {
}

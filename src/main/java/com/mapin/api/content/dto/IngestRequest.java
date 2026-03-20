package com.mapin.api.content.dto;

import jakarta.validation.constraints.NotBlank;

public record IngestRequest(@NotBlank(message = "url은 필수입니다.") String url) {
}

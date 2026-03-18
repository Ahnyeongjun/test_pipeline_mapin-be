package com.mapin.content.dto;

public record ContentPerspectiveResponse(
        Long id,
        String category,
        String perspectiveLevel,
        String perspectiveStakeholder
) {
}

package com.mapin.infra.perspective;

import java.util.List;

public record PerspectiveAnalysisResult(
        String category,
        String perspectiveLevel,
        String perspectiveStakeholder,
        List<String> keywords,
        String summary,
        String tone,
        String biasLevel,
        boolean isOpinionated) {
}

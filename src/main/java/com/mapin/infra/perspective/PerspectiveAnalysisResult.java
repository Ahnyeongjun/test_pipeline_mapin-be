package com.mapin.infra.perspective;

import java.util.List;

public record PerspectiveAnalysisResult(
        String category,
        String perspectiveLevel,
        String perspectiveStakeholder,
        List<String> keywords,
        List<String> coreKeywords,
        String summary,
        String tone,
        boolean isOpinionated) {
}

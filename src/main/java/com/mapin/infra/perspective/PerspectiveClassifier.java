package com.mapin.infra.perspective;

import com.mapin.infra.perspective.PerspectiveAnalysisResult;

public interface PerspectiveClassifier {

    PerspectiveAnalysisResult classify(String text);
}

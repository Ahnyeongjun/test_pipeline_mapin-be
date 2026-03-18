package com.mapin.content.port;

import com.mapin.content.dto.PerspectiveAnalysisResult;

public interface PerspectiveClassifier {

    PerspectiveAnalysisResult classify(String text);
}

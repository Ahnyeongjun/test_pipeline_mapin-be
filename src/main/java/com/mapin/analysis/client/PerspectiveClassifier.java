package com.mapin.analysis.client;

import com.mapin.analysis.client.PerspectiveAnalysisResult;

public interface PerspectiveClassifier {

    PerspectiveAnalysisResult classify(String text);
}

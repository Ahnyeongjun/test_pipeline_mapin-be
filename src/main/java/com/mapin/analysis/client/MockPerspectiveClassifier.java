package com.mapin.analysis.client;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class MockPerspectiveClassifier implements PerspectiveClassifier {

    @Override
    public PerspectiveAnalysisResult classify(String text) {
        return new PerspectiveAnalysisResult("경제", "사건", "정부");
    }
}

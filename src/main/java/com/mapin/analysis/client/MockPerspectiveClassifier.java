package com.mapin.analysis.client;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class MockPerspectiveClassifier implements PerspectiveClassifier {

    @Override
    public PerspectiveAnalysisResult classify(String text) {
        return new PerspectiveAnalysisResult("경제", "사건", "정부", List.of("금리", "한국은행", "통화정책"));
    }
}

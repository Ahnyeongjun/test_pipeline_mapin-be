package com.mapin.infra.perspective;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class MockPerspectiveClassifier implements PerspectiveClassifier {

    @Override
    public PerspectiveAnalysisResult classify(String text) {
        return new PerspectiveAnalysisResult("경제", "사건", "정부",
                List.of("금리", "한국은행", "통화정책"),
                "한국은행이 기준금리를 동결했다. 전문가들은 향후 인하 가능성을 논의 중이다.",
                "중립", "낮음", false);
    }
}

package com.mapin.content.infrastructure;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.mapin.content.dto.PerspectiveAnalysisResult;
import com.mapin.content.port.PerspectiveClassifier;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonSchemaLocalValidation;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.StructuredChatCompletion;
import com.openai.models.chat.completions.StructuredChatCompletionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class GptPerspectiveClassifier implements PerspectiveClassifier {

    private final OpenAIClient openAIClient;

    @Value("${openai.model:gpt-4.1}")
    private String model;

    @Override
    public PerspectiveAnalysisResult classify(String text) {
        StructuredChatCompletionCreateParams<PerspectiveSchema> params =
                StructuredChatCompletionCreateParams.<PerspectiveSchema>builder()
                        .model(toChatModel(model))
                        .addSystemMessage(systemPrompt())
                        .addUserMessage(userPrompt(text))
                        .responseFormat(PerspectiveSchema.class, JsonSchemaLocalValidation.YES)
                        .build();

        StructuredChatCompletion<PerspectiveSchema> completion = openAIClient.chat()
                .completions()
                .create(params);

        PerspectiveSchema schema = completion.choices().stream()
                .findFirst()
                .flatMap(choice -> choice.message().content())
                .orElseThrow(() -> new IllegalStateException("분류 결과가 비어 있습니다."));

        return new PerspectiveAnalysisResult(
                schema.category,
                schema.perspectiveLevel,
                schema.perspectiveStakeholder
        );
    }

    private String systemPrompt() {
        return """
                너는 유튜브 콘텐츠 관점 분류기다.
                입력된 제목과 설명을 읽고 반드시 지정된 스키마에 맞는 JSON만 반환한다.

                분류 기준:

                1) category
                허용값:
                정치, 경제, 사회, 생활/문화, IT/과학, 세계, 연예, 스포츠

                2) perspectiveLevel
                허용값:
                사건, 원인, 구조

                정의:
                - 사건: 지금 무슨 일이 일어났는가 중심
                - 원인: 왜 이런 일이 발생했는가 중심
                - 구조: 더 큰 시스템/구조적 문제 중심

                3) perspectiveStakeholder
                허용값:
                정부, 전문가, 시민, 기업, 국제

                정의:
                - 정부: 정부/공공기관 발표 및 입장 중심
                - 전문가: 전문가/학자/분석가 해설 중심
                - 시민: 일반 시민 경험/반응/현장 목소리 중심
                - 기업: 산업/기업 관점 중심
                - 국제: 해외/국제 시각 중심

                규칙:
                - 반드시 하나의 값만 선택
                - 애매하면 가장 지배적인 관점을 선택
                - 설명 문장은 출력하지 말고 구조화된 결과만 반환
                """;
    }

    private String userPrompt(String text) {
        return """
                아래 콘텐츠를 분류해줘.

                %s
                """.formatted(text);
    }

    private ChatModel toChatModel(String modelName) {
        return switch (modelName) {
            case "gpt-4.1" -> ChatModel.GPT_4_1;
            case "gpt-4.1-mini" -> ChatModel.GPT_4_1_MINI;
            case "gpt-4.1-nano" -> ChatModel.GPT_4_1_NANO;
            default -> throw new IllegalArgumentException("지원하지 않는 OpenAI 모델입니다: " + modelName);
        };
    }

    @JsonClassDescription("유튜브 콘텐츠 관점 분류 결과")
    static class PerspectiveSchema {

        @JsonPropertyDescription("정치, 경제, 사회, 생활/문화, IT/과학, 세계, 연예, 스포츠 중 하나")
        public String category;

        @JsonPropertyDescription("사건, 원인, 구조 중 하나")
        public String perspectiveLevel;

        @JsonPropertyDescription("정부, 전문가, 시민, 기업, 국제 중 하나")
        public String perspectiveStakeholder;
    }
}

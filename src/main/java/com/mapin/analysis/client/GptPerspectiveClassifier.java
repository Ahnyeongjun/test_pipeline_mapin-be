package com.mapin.analysis.client;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
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
        StructuredChatCompletionCreateParams<Schema> params =
                StructuredChatCompletionCreateParams.<Schema>builder()
                        .model(toChatModel(model))
                        .addSystemMessage(systemPrompt())
                        .addUserMessage(text)
                        .responseFormat(Schema.class, JsonSchemaLocalValidation.YES)
                        .build();

        StructuredChatCompletion<Schema> completion = openAIClient.chat().completions().create(params);

        Schema schema = completion.choices().stream().findFirst()
                .flatMap(c -> c.message().content())
                .orElseThrow(() -> new IllegalStateException("GPT 분류 결과가 비어 있습니다."));

        return new PerspectiveAnalysisResult(schema.category, schema.perspectiveLevel, schema.perspectiveStakeholder,
                schema.keywords, schema.summary, schema.tone, schema.biasLevel, schema.isOpinionated);
    }

    private String systemPrompt() {
        return """
                너는 유튜브 콘텐츠 관점 분류기다. 제목과 설명을 읽고 스키마에 맞는 JSON만 반환한다.

                category: 정치, 경제, 사회, 생활/문화, IT/과학, 세계, 연예, 스포츠 중 하나
                perspectiveLevel: 사건(무슨 일), 원인(왜 발생), 구조(시스템 문제) 중 하나
                perspectiveStakeholder: 정부, 전문가, 시민, 기업, 국제 중 하나
                keywords: 콘텐츠 핵심 주제를 대표하는 명사 3~5개 (유튜브 검색에 쓸 수 있도록 구체적으로)
                summary: 콘텐츠 핵심 내용을 1~2문장으로 요약
                tone: 중립, 비판적, 옹호, 경고, 분석 중 하나
                biasLevel: 낮음, 중간, 높음 중 하나 (한쪽 관점으로 치우친 정도)
                isOpinionated: 사실보도이면 false, 의견·논평·주장이면 true
                """;
    }

    private ChatModel toChatModel(String name) {
        return switch (name) {
            case "gpt-4.1" -> ChatModel.GPT_4_1;
            case "gpt-4.1-mini" -> ChatModel.GPT_4_1_MINI;
            case "gpt-4.1-nano" -> ChatModel.GPT_4_1_NANO;
            default -> throw new IllegalArgumentException("지원하지 않는 모델: " + name);
        };
    }

    @JsonClassDescription("관점 분류 결과")
    static class Schema {
        @JsonPropertyDescription("정치, 경제, 사회, 생활/문화, IT/과학, 세계, 연예, 스포츠 중 하나")
        public String category;
        @JsonPropertyDescription("사건, 원인, 구조 중 하나")
        public String perspectiveLevel;
        @JsonPropertyDescription("정부, 전문가, 시민, 기업, 국제 중 하나")
        public String perspectiveStakeholder;
        @JsonPropertyDescription("콘텐츠 핵심 주제를 대표하는 명사 3~5개 (유튜브 검색에 쓸 수 있도록 구체적으로)")
        public List<String> keywords;
        @JsonPropertyDescription("콘텐츠 핵심 내용을 1~2문장으로 요약")
        public String summary;
        @JsonPropertyDescription("중립, 비판적, 옹호, 경고, 분석 중 하나")
        public String tone;
        @JsonPropertyDescription("낮음, 중간, 높음 중 하나 (한쪽 관점으로 치우친 정도)")
        public String biasLevel;
        @JsonPropertyDescription("사실보도이면 false, 의견·논평·주장이면 true")
        public boolean isOpinionated;
    }
}

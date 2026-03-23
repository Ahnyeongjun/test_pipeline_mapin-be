package com.mapin.domain.recommendation.service;

import com.mapin.domain.content.Content;
import com.mapin.domain.content.ContentRepository;
import com.mapin.infra.youtube.YoutubeSearchClient;
import com.mapin.domain.content.event.FallbackIngestRequestedEvent;
import com.mapin.domain.recommendation.RecommendationStrategy;
import com.mapin.domain.recommendation.ContentRecommendation;
import com.mapin.domain.recommendation.ContentRecommendationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock private ContentRepository contentRepository;
    @Mock private ContentRecommendationRepository recommendationRepository;
    @Mock private RecommendationStrategy dbStrategy;
    @Mock private YoutubeSearchClient youtubeSearchClient;
    @Mock private ApplicationEventPublisher eventPublisher;

    private RecommendationService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationService(
                contentRepository,
                recommendationRepository,
                Map.of("dbRecommendation", dbStrategy),
                youtubeSearchClient,
                "db",
                eventPublisher
        );
    }

    private Content buildContent(String stakeholder, String level, String source) {
        return buildContent(stakeholder, level, source, List.of("정치", "정책"), List.of("정치"), "채널A");
    }

    private Content buildContent(String stakeholder, String level, String source,
                                  List<String> keywords, String channelTitle) {
        return buildContent(stakeholder, level, source, keywords, List.of(keywords.get(0)), channelTitle);
    }

    private Content buildContent(String stakeholder, String level, String source,
                                  List<String> keywords, List<String> coreKeywords, String channelTitle) {
        Content content = Content.builder()
                .canonicalUrl("https://www.youtube.com/watch?v=" + stakeholder)
                .platform("YOUTUBE").externalContentId("vid_" + stakeholder)
                .title("영상_" + stakeholder).description("").channelTitle(channelTitle)
                .status("ACTIVE").source(source).build();
        content.updatePerspective("politics", level, stakeholder,
                keywords, coreKeywords, "요약", "neutral", true);
        return content;
    }

    @Nested
    @DisplayName("calculateScore (간접 검증)")
    class CalculateScore {

        @Test
        @DisplayName("stakeholder가 같으면 관계를 저장하지 않는다 (score=0)")
        void noRelationWhenSameStakeholder() {
            Content source = buildContent("government", "pro", "USER");
            Content candidate = buildContent("government", "con", "USER");

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of(candidate));

            service.recommend(1L, "USER");

            ArgumentCaptor<List<ContentRecommendation>> captor = ArgumentCaptor.forClass(List.class);
            verify(recommendationRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).isEmpty();
        }

        @Test
        @DisplayName("stakeholder가 다르고 level도 다르면 score=2 로 저장한다")
        void score2WhenBothDifferent() {
            Content source = buildContent("government", "pro", "USER");
            Content target = buildContent("labor", "con", "USER");

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of(target));
            when(recommendationRepository.existsBySourceContentIdAndTargetContentId(any(), any()))
                    .thenReturn(false);

            service.recommend(1L, "USER");

            ArgumentCaptor<List<ContentRecommendation>> captor = ArgumentCaptor.forClass(List.class);
            verify(recommendationRepository).saveAll(captor.capture());
            List<ContentRecommendation> saved = captor.getValue();
            assertThat(saved).hasSize(2);
            assertThat(saved).allMatch(r -> r.getScore() == 2);
        }

        @Test
        @DisplayName("stakeholder만 다르고 level이 같으면 score=1 로 저장한다")
        void score1WhenOnlyStakeholderDifferent() {
            Content source = buildContent("government", "pro", "USER");
            Content target = buildContent("labor", "pro", "USER");

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of(target));
            when(recommendationRepository.existsBySourceContentIdAndTargetContentId(any(), any()))
                    .thenReturn(false);

            service.recommend(1L, "USER");

            ArgumentCaptor<List<ContentRecommendation>> captor = ArgumentCaptor.forClass(List.class);
            verify(recommendationRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).allMatch(r -> r.getScore() == 1);
        }

        @Test
        @DisplayName("tone이 다르면 score에 +1 보너스가 붙는다")
        void bonusForDifferentTone() {
            Content source = buildContent("government", "pro", "USER");
            source.updatePerspective("politics", "pro", "government",
                    List.of("정치", "정책"), List.of("정치"), "요약", "긍정적", true);

            Content target = buildContent("labor", "pro", "USER");
            target.updatePerspective("politics", "pro", "labor",
                    List.of("정치", "정책"), List.of("정치"), "요약", "비판적", true);

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of(target));
            when(recommendationRepository.existsBySourceContentIdAndTargetContentId(any(), any()))
                    .thenReturn(false);

            service.recommend(1L, "USER");

            ArgumentCaptor<List<ContentRecommendation>> captor = ArgumentCaptor.forClass(List.class);
            verify(recommendationRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).allMatch(r -> r.getScore() == 2);
        }

        @Test
        @DisplayName("isOpinionated가 다르면 score에 +1 보너스가 붙는다")
        void bonusForDifferentIsOpinionated() {
            Content source = buildContent("government", "pro", "USER");
            source.updatePerspective("politics", "pro", "government",
                    List.of("정치", "정책"), List.of("정치"), "요약", "neutral", false);

            Content target = buildContent("labor", "pro", "USER");
            target.updatePerspective("politics", "pro", "labor",
                    List.of("정치", "정책"), List.of("정치"), "요약", "neutral", true);

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of(target));
            when(recommendationRepository.existsBySourceContentIdAndTargetContentId(any(), any()))
                    .thenReturn(false);

            service.recommend(1L, "USER");

            ArgumentCaptor<List<ContentRecommendation>> captor = ArgumentCaptor.forClass(List.class);
            verify(recommendationRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).allMatch(r -> r.getScore() == 2);
        }

        @Test
        @DisplayName("이미 관계가 존재하면 중복 저장하지 않는다")
        void skipDuplicateRelation() {
            Content source = buildContent("government", "pro", "USER");
            Content target = buildContent("labor", "con", "USER");

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of(target));
            when(recommendationRepository.existsBySourceContentIdAndTargetContentId(any(), any()))
                    .thenReturn(true);

            service.recommend(1L, "USER");

            ArgumentCaptor<List<ContentRecommendation>> captor = ArgumentCaptor.forClass(List.class);
            verify(recommendationRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).isEmpty();
        }
    }

    @Nested
    @DisplayName("FALLBACK 콘텐츠 처리")
    class FallbackContent {

        @Test
        @DisplayName("FALLBACK: 같은 카테고리 USER 콘텐츠와 단방향 관계를 맺는다")
        void linkFallbackToUserContents() {
            Content fallback = buildContent("labor", "con", "FALLBACK");
            Content user = buildContent("government", "pro", "USER");

            when(contentRepository.findById(2L)).thenReturn(Optional.of(fallback));
            when(contentRepository.findByCoreKeywordsOverlapAndSource(anyString(), eq("USER"), any()))
                    .thenReturn(List.of(user));
            when(recommendationRepository.existsBySourceContentIdAndTargetContentId(any(), any()))
                    .thenReturn(false);

            service.recommend(2L, "FALLBACK");

            ArgumentCaptor<List<ContentRecommendation>> captor = ArgumentCaptor.forClass(List.class);
            verify(recommendationRepository).saveAll(captor.capture());
            List<ContentRecommendation> saved = captor.getValue();
            assertThat(saved).hasSize(1);
            assertThat(saved.get(0).getScore()).isGreaterThan(0);
        }

        @Test
        @DisplayName("FALLBACK: stakeholder가 null인 user 콘텐츠는 건너뛴다")
        void skipUserWithNullStakeholder() {
            Content fallback = buildContent("labor", "con", "FALLBACK");
            Content userNoStakeholder = Content.builder()
                    .canonicalUrl("https://www.youtube.com/watch?v=ns")
                    .platform("YOUTUBE").externalContentId("ns")
                    .title("미분류").description("").status("ACTIVE").source("USER").build();

            when(contentRepository.findById(2L)).thenReturn(Optional.of(fallback));
            when(contentRepository.findByCoreKeywordsOverlapAndSource(anyString(), eq("USER"), any()))
                    .thenReturn(List.of(userNoStakeholder));
            when(contentRepository.save(any())).thenReturn(fallback);

            service.recommend(2L, "FALLBACK");

            ArgumentCaptor<List<ContentRecommendation>> captor = ArgumentCaptor.forClass(List.class);
            verify(recommendationRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).isEmpty();
        }

        @Test
        @DisplayName("FALLBACK: coreKeywords가 null이면 관계를 맺지 않는다")
        void skipWhenFallbackCoreKeywordsNull() {
            Content fallback = Content.builder()
                    .canonicalUrl("https://www.youtube.com/watch?v=fb")
                    .platform("YOUTUBE").externalContentId("fb")
                    .title("fallback").description("").status("ACTIVE").source("FALLBACK").build();

            when(contentRepository.findById(2L)).thenReturn(Optional.of(fallback));
            when(contentRepository.save(any())).thenReturn(fallback);

            service.recommend(2L, "FALLBACK");

            verify(recommendationRepository, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("filterCandidates: 후보 필터링")
    class FilterCandidates {

        @Test
        @DisplayName("channelTitle이 null인 후보는 externalContentId 기반으로 채널을 구분한다")
        void usesExternalContentIdWhenChannelTitleNull() {
            Content source = buildContent("government", "pro", "USER");
            Content noChannel = Content.builder()
                    .canonicalUrl("https://www.youtube.com/watch?v=noCh")
                    .platform("YOUTUBE").externalContentId("noCh")
                    .title("채널없음").description("").channelTitle(null)
                    .status("ACTIVE").source("USER").build();
            noChannel.updatePerspective("politics", "con", "labor",
                    List.of("정치", "정책"), List.of("정치"), "요약", "neutral", true);

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of(noChannel));
            when(recommendationRepository.existsBySourceContentIdAndTargetContentId(any(), any()))
                    .thenReturn(false);

            service.recommend(1L, "USER");

            ArgumentCaptor<List<ContentRecommendation>> captor = ArgumentCaptor.forClass(List.class);
            verify(recommendationRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).isNotEmpty();
        }

        @Test
        @DisplayName("perspectiveStakeholder가 null인 후보는 제외한다")
        void excludeUnanalyzedCandidate() {
            Content source = buildContent("government", "pro", "USER");
            Content unanalyzed = Content.builder()
                    .canonicalUrl("https://www.youtube.com/watch?v=unanalyzed")
                    .platform("YOUTUBE").externalContentId("vid_unanalyzed")
                    .title("미분류").description("").status("ACTIVE").source("USER").build();

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of(unanalyzed));

            service.recommend(1L, "USER");

            ArgumentCaptor<List<ContentRecommendation>> captor = ArgumentCaptor.forClass(List.class);
            verify(recommendationRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).isEmpty();
        }

        @Test
        @DisplayName("후보의 keywords가 null이면 topic overlap을 true로 처리한다")
        void allowsCandidateWithNullKeywords() {
            Content source = buildContent("government", "pro", "USER",
                    List.of("정치", "정책"), "채널A");
            Content nullKeywords = Content.builder()
                    .canonicalUrl("https://www.youtube.com/watch?v=nk")
                    .platform("YOUTUBE").externalContentId("nk")
                    .title("키워드없음").description("").channelTitle("채널B")
                    .status("ACTIVE").source("USER").build();
            nullKeywords.updatePerspective("politics", "con", "labor",
                    null, List.of("정치"), "요약", "neutral", true);

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of(nullKeywords));
            when(recommendationRepository.existsBySourceContentIdAndTargetContentId(any(), any()))
                    .thenReturn(false);

            service.recommend(1L, "USER");

            ArgumentCaptor<List<ContentRecommendation>> captor = ArgumentCaptor.forClass(List.class);
            verify(recommendationRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).isNotEmpty();
        }

        @Test
        @DisplayName("같은 채널에서 여러 후보가 있으면 점수 높은 1개만 저장한다")
        void deduplicateByChannel() {
            Content source = buildContent("government", "pro", "USER");
            Content weak = buildContent("labor", "pro", "USER", List.of("정치", "정책"), "연합뉴스TV");
            Content strong = buildContent("citizen", "con", "USER", List.of("정치", "정책"), "연합뉴스TV");

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of(weak, strong));
            when(recommendationRepository.existsBySourceContentIdAndTargetContentId(any(), any()))
                    .thenReturn(false);

            service.recommend(1L, "USER");

            ArgumentCaptor<List<ContentRecommendation>> captor = ArgumentCaptor.forClass(List.class);
            verify(recommendationRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).hasSize(2);
            assertThat(captor.getValue()).allMatch(r -> r.getScore() == 2);
        }
    }

    @Test
    @DisplayName("추천 실패 시 ContentRecommendationFailedEvent를 발행하고 예외를 rethrow한다")
    void publishesFailureEventWhenRecommendFails() {
        Content source = buildContent("government", "pro", "USER");
        when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
        when(dbStrategy.getCandidates(source)).thenThrow(new RuntimeException("전략 오류"));

        assertThatThrownBy(() -> service.recommend(1L, "USER"))
                .isInstanceOf(RuntimeException.class);

        ArgumentCaptor<com.mapin.domain.recommendation.event.ContentRecommendationFailedEvent> captor =
                ArgumentCaptor.forClass(com.mapin.domain.recommendation.event.ContentRecommendationFailedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getContentId()).isEqualTo(1L);
        assertThat(captor.getValue().getReason()).contains("전략 오류");
    }

    @Nested
    @DisplayName("resolveStrategy")
    class ResolveStrategy {

        @Test
        @DisplayName("전략 빈이 없으면 dbRecommendation으로 fallback한다")
        void fallsBackToDbStrategyWhenBeanNotFound() {
            RecommendationService serviceWithUnknown = new RecommendationService(
                    contentRepository,
                    recommendationRepository,
                    Map.of("dbRecommendation", dbStrategy),
                    youtubeSearchClient,
                    "unknown",
                    eventPublisher
            );
            Content source = buildContent("government", "pro", "USER");
            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of());
            when(contentRepository.findByCoreKeywordsOverlapAndSource(anyString(), eq("FALLBACK"), any()))
                    .thenReturn(List.of(buildContent("expert", "con", "FALLBACK")));

            serviceWithUnknown.recommend(1L, "USER");

            verify(dbStrategy).getCandidates(source);
        }
    }

    @Nested
    @DisplayName("Fallback YouTube 검색")
    class FallbackSearch {

        @Test
        @DisplayName("반대관점 후보가 없을 때 YouTube 검색을 트리거한다")
        void triggersFallbackSearchWhenNoOppositeCandidate() {
            Content source = buildContent("government", "pro", "USER");
            Content sameStakeholder = buildContent("government", "con", "USER");

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of(sameStakeholder));
            when(youtubeSearchClient.searchVideoIds(anyString(), eq(10))).thenReturn(List.of());

            service.recommend(1L, "USER");

            verify(youtubeSearchClient).searchVideoIds(anyString(), eq(10));
        }

        @Test
        @DisplayName("fallback 검색 쿼리는 coreKeywords를 우선 사용한다")
        void fallbackQueryUsesCoreKeywords() {
            Content source = buildContent("government", "pro", "USER");
            source.updatePerspective("politics", "pro", "government",
                    List.of("금리", "한국은행", "통화정책", "기준금리", "물가"),
                    List.of("금리", "한국은행"),
                    "요약", "neutral", true);

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of());
            when(youtubeSearchClient.searchVideoIds(anyString(), eq(10))).thenReturn(List.of());

            service.recommend(1L, "USER");

            ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
            verify(youtubeSearchClient).searchVideoIds(queryCaptor.capture(), eq(10));
            String query = queryCaptor.getValue();
            assertThat(query).isEqualTo("금리 한국은행");
        }

        @Test
        @DisplayName("원본 영상 자신은 fallback 검색 결과에서 제외되어 이벤트가 발행되지 않는다")
        void excludesOriginalVideoFromFallback() {
            Content source = buildContent("government", "pro", "USER");

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of());
            when(youtubeSearchClient.searchVideoIds(anyString(), eq(10)))
                    .thenReturn(List.of("vid_government")); // 원본 영상 ID만 반환

            service.recommend(1L, "USER");

            // 원본 영상 제외 후 빈 리스트 → FallbackIngestRequestedEvent 발행 없음
            verify(eventPublisher, never()).publishEvent(any(FallbackIngestRequestedEvent.class));
        }

        @Test
        @DisplayName("동일 주제 FALLBACK이 이미 존재하면 YouTube 검색을 스킵한다")
        void skipFallbackSearchWhenFallbackAlreadyExists() {
            Content source = buildContent("government", "pro", "USER");
            Content existingFallback = buildContent("expert", "con", "FALLBACK");

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of());
            when(contentRepository.findByCoreKeywordsOverlapAndSource(anyString(), eq("FALLBACK"), any()))
                    .thenReturn(List.of(existingFallback));

            service.recommend(1L, "USER");

            verify(youtubeSearchClient, never()).searchVideoIds(anyString(), anyInt());
        }

        @Test
        @DisplayName("keywords가 null이면 category를 fallback 검색 쿼리로 사용한다")
        void useCategoryWhenKeywordsNull() {
            Content source = Content.builder()
                    .canonicalUrl("https://www.youtube.com/watch?v=nokw")
                    .platform("YOUTUBE").externalContentId("nokw")
                    .title("영상").description("").channelTitle("채널")
                    .status("ACTIVE").source("USER").build();
            source.updatePerspective("economy", "pro", "government",
                    null, null, "요약", "neutral", false);

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of());
            when(youtubeSearchClient.searchVideoIds(anyString(), eq(10))).thenReturn(List.of());

            service.recommend(1L, "USER");

            ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
            verify(youtubeSearchClient).searchVideoIds(queryCaptor.capture(), eq(10));
            assertThat(queryCaptor.getValue()).isEqualTo("economy");
        }

        @Test
        @DisplayName("fallback 검색 중 예외 발생 시 무시하고 정상 완료한다")
        void ignoresExceptionDuringFallbackSearch() {
            Content source = buildContent("government", "pro", "USER");

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of());
            when(youtubeSearchClient.searchVideoIds(anyString(), eq(10)))
                    .thenThrow(new RuntimeException("YouTube API 오류"));
            when(contentRepository.save(any())).thenReturn(source);

            // 예외 없이 정상 완료되어야 한다
            service.recommend(1L, "USER");

            verify(contentRepository).save(source);
        }

        @Test
        @DisplayName("fallback 검색 결과로 FallbackIngestRequestedEvent를 발행한다")
        void publishesFallbackIngestEvent() {
            Content source = buildContent("government", "pro", "USER");

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of());
            when(youtubeSearchClient.searchVideoIds(anyString(), eq(10)))
                    .thenReturn(List.of("vid_other1", "vid_other2"));

            service.recommend(1L, "USER");

            ArgumentCaptor<FallbackIngestRequestedEvent> captor =
                    ArgumentCaptor.forClass(FallbackIngestRequestedEvent.class);
            verify(eventPublisher, times(2)).publishEvent(captor.capture());
            assertThat(captor.getAllValues()).extracting(FallbackIngestRequestedEvent::getUrl)
                    .allMatch(url -> url.startsWith("https://www.youtube.com/watch?v="));
        }
    }
}

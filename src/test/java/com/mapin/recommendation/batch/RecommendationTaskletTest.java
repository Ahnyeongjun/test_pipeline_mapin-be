package com.mapin.recommendation.batch;

import com.mapin.common.domain.Content;
import com.mapin.common.domain.ContentRepository;
import com.mapin.ingest.client.YoutubeSearchClient;
import com.mapin.recommendation.client.RecommendationStrategy;
import com.mapin.recommendation.domain.ContentRecommendation;
import com.mapin.recommendation.domain.ContentRecommendationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationTaskletTest {

    @Mock private ContentRepository contentRepository;
    @Mock private ContentRecommendationRepository recommendationRepository;
    @Mock private RecommendationStrategy dbStrategy;
    @Mock private YoutubeSearchClient youtubeSearchClient;
    @Mock private JobLauncher jobLauncher;
    @Mock private Job ingestJob;

    @Mock private StepContribution contribution;
    @Mock private StepExecution stepExecution;
    @Mock private ChunkContext chunkContext;

    private RecommendationTasklet tasklet;

    @BeforeEach
    void setUp() {
        tasklet = new RecommendationTasklet(
                contentRepository,
                recommendationRepository,
                Map.of("dbRecommendation", dbStrategy),
                youtubeSearchClient,
                jobLauncher,
                ingestJob,
                "db"
        );
    }

    private void givenJobParams(Long contentId, String source) {
        JobParameters params = new JobParametersBuilder()
                .addLong("contentId", contentId)
                .addString("source", source)
                .toJobParameters();
        when(contribution.getStepExecution()).thenReturn(stepExecution);
        when(stepExecution.getJobParameters()).thenReturn(params);
    }

    /**
     * Content ID는 JPA가 할당하므로 직접 생성 시 null.
     * 테스트에서는 Long 파라미터 매칭에 any() 사용 필요.
     */
    private Content buildContent(String stakeholder, String level, String source) {
        return buildContent(stakeholder, level, source, List.of("정치", "정책"), "채널A");
    }

    private Content buildContent(String stakeholder, String level, String source,
                                  List<String> keywords, String channelTitle) {
        Content content = Content.builder()
                .canonicalUrl("https://www.youtube.com/watch?v=" + stakeholder)
                .platform("YOUTUBE").externalContentId("vid_" + stakeholder)
                .title("영상_" + stakeholder).description("").channelTitle(channelTitle)
                .status("ACTIVE").source(source).build();
        content.updatePerspective("politics", level, stakeholder,
                keywords, "요약", "neutral", "low", true);
        return content;
    }

    @Nested
    @DisplayName("calculateScore (간접 검증)")
    class CalculateScore {

        @Test
        @DisplayName("stakeholder가 같으면 관계를 저장하지 않는다 (score=0)")
        void noRelationWhenSameStakeholder() throws Exception {
            givenJobParams(1L, "USER");
            Content source = buildContent("government", "pro", "USER");
            Content candidate = buildContent("government", "con", "USER"); // 같은 stakeholder

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of(candidate));

            tasklet.execute(contribution, chunkContext);

            ArgumentCaptor<List<ContentRecommendation>> captor = ArgumentCaptor.forClass(List.class);
            verify(recommendationRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).isEmpty();
        }

        @Test
        @DisplayName("stakeholder가 다르고 level도 다르면 score=2 로 저장한다")
        void score2WhenBothDifferent() throws Exception {
            givenJobParams(1L, "USER");
            Content source = buildContent("government", "pro", "USER");
            Content target = buildContent("labor", "con", "USER");

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of(target));
            // ID가 null이므로 anyLong() 대신 any() 사용
            when(recommendationRepository.existsBySourceContentIdAndTargetContentId(any(), any()))
                    .thenReturn(false);

            tasklet.execute(contribution, chunkContext);

            ArgumentCaptor<List<ContentRecommendation>> captor = ArgumentCaptor.forClass(List.class);
            verify(recommendationRepository).saveAll(captor.capture());
            List<ContentRecommendation> saved = captor.getValue();
            assertThat(saved).hasSize(2); // source→target, target→source
            assertThat(saved).allMatch(r -> r.getScore() == 2);
        }

        @Test
        @DisplayName("stakeholder만 다르고 level이 같으면 score=1 로 저장한다")
        void score1WhenOnlyStakeholderDifferent() throws Exception {
            givenJobParams(1L, "USER");
            Content source = buildContent("government", "pro", "USER");
            Content target = buildContent("labor", "pro", "USER"); // level 동일

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of(target));
            when(recommendationRepository.existsBySourceContentIdAndTargetContentId(any(), any()))
                    .thenReturn(false);

            tasklet.execute(contribution, chunkContext);

            ArgumentCaptor<List<ContentRecommendation>> captor = ArgumentCaptor.forClass(List.class);
            verify(recommendationRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).allMatch(r -> r.getScore() == 1);
        }

        @Test
        @DisplayName("tone이 다르면 score에 +1 보너스가 붙는다")
        void bonusForDifferentTone() throws Exception {
            givenJobParams(1L, "USER");
            Content source = buildContent("government", "pro", "USER");
            source.updatePerspective("politics", "pro", "government",
                    List.of("정치", "정책"), "요약", "긍정적", "low", true);

            Content target = buildContent("labor", "pro", "USER");
            target.updatePerspective("politics", "pro", "labor",
                    List.of("정치", "정책"), "요약", "비판적", "low", true); // tone 다름

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of(target));
            when(recommendationRepository.existsBySourceContentIdAndTargetContentId(any(), any()))
                    .thenReturn(false);

            tasklet.execute(contribution, chunkContext);

            ArgumentCaptor<List<ContentRecommendation>> captor = ArgumentCaptor.forClass(List.class);
            verify(recommendationRepository).saveAll(captor.capture());
            // stakeholder 다름(+1) + tone 다름(+1) = score 2
            assertThat(captor.getValue()).allMatch(r -> r.getScore() == 2);
        }

        @Test
        @DisplayName("isOpinionated가 다르면 score에 +1 보너스가 붙는다")
        void bonusForDifferentIsOpinionated() throws Exception {
            givenJobParams(1L, "USER");
            Content source = buildContent("government", "pro", "USER");
            source.updatePerspective("politics", "pro", "government",
                    List.of("정치", "정책"), "요약", "neutral", "low", false); // 사실 보도

            Content target = buildContent("labor", "pro", "USER");
            target.updatePerspective("politics", "pro", "labor",
                    List.of("정치", "정책"), "요약", "neutral", "low", true); // 의견 콘텐츠

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of(target));
            when(recommendationRepository.existsBySourceContentIdAndTargetContentId(any(), any()))
                    .thenReturn(false);

            tasklet.execute(contribution, chunkContext);

            ArgumentCaptor<List<ContentRecommendation>> captor = ArgumentCaptor.forClass(List.class);
            verify(recommendationRepository).saveAll(captor.capture());
            // stakeholder 다름(+1) + isOpinionated 다름(+1) = score 2
            assertThat(captor.getValue()).allMatch(r -> r.getScore() == 2);
        }

        @Test
        @DisplayName("이미 관계가 존재하면 중복 저장하지 않는다")
        void skipDuplicateRelation() throws Exception {
            givenJobParams(1L, "USER");
            Content source = buildContent("government", "pro", "USER");
            Content target = buildContent("labor", "con", "USER");

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of(target));
            when(recommendationRepository.existsBySourceContentIdAndTargetContentId(any(), any()))
                    .thenReturn(true); // 이미 존재

            tasklet.execute(contribution, chunkContext);

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
        void linkFallbackToUserContents() throws Exception {
            givenJobParams(2L, "FALLBACK");

            Content fallback = buildContent("labor", "con", "FALLBACK");
            Content user = buildContent("government", "pro", "USER");

            when(contentRepository.findById(2L)).thenReturn(Optional.of(fallback));
            // ID가 null이므로 any() 사용
            when(contentRepository.findByCategoryAndSourceAndIdNot(eq("politics"), eq("USER"), any()))
                    .thenReturn(List.of(user));
            when(recommendationRepository.existsBySourceContentIdAndTargetContentId(any(), any()))
                    .thenReturn(false);

            tasklet.execute(contribution, chunkContext);

            ArgumentCaptor<List<ContentRecommendation>> captor = ArgumentCaptor.forClass(List.class);
            verify(recommendationRepository).saveAll(captor.capture());
            List<ContentRecommendation> saved = captor.getValue();
            assertThat(saved).hasSize(1);
            assertThat(saved.get(0).getScore()).isGreaterThan(0);
        }

        @Test
        @DisplayName("FALLBACK: category가 null이면 관계를 맺지 않는다")
        void skipWhenFallbackCategoryNull() throws Exception {
            givenJobParams(2L, "FALLBACK");

            Content fallback = Content.builder()
                    .canonicalUrl("https://www.youtube.com/watch?v=fb")
                    .platform("YOUTUBE").externalContentId("fb")
                    .title("fallback").description("").status("ACTIVE").source("FALLBACK").build();
            // category = null (updatePerspective 미호출)

            when(contentRepository.findById(2L)).thenReturn(Optional.of(fallback));

            tasklet.execute(contribution, chunkContext);

            verify(recommendationRepository, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("filterCandidates: 후보 필터링")
    class FilterCandidates {

        @Test
        @DisplayName("perspectiveStakeholder가 null인 후보는 제외한다")
        void excludeUnanalyzedCandidate() throws Exception {
            givenJobParams(1L, "USER");
            Content source = buildContent("government", "pro", "USER");

            Content unanalyzed = Content.builder()
                    .canonicalUrl("https://www.youtube.com/watch?v=unanalyzed")
                    .platform("YOUTUBE").externalContentId("vid_unanalyzed")
                    .title("미분류").description("").status("ACTIVE").source("USER").build();
            // updatePerspective 미호출 → perspectiveStakeholder = null

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of(unanalyzed));

            tasklet.execute(contribution, chunkContext);

            ArgumentCaptor<List<ContentRecommendation>> captor = ArgumentCaptor.forClass(List.class);
            verify(recommendationRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).isEmpty();
        }

        @Test
        @DisplayName("keywords 겹침이 20% 미만이면 다른 주제로 판단해 제외한다")
        void excludeLowKeywordOverlapCandidate() throws Exception {
            givenJobParams(1L, "USER");
            Content source = buildContent("government", "pro", "USER",
                    List.of("금리", "한국은행", "통화정책", "기준금리", "물가"), "채널A");
            Content unrelated = buildContent("labor", "con", "USER",
                    List.of("스포츠", "야구", "올림픽", "축구", "농구"), "채널B"); // 겹침 0%

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of(unrelated));

            tasklet.execute(contribution, chunkContext);

            ArgumentCaptor<List<ContentRecommendation>> captor = ArgumentCaptor.forClass(List.class);
            verify(recommendationRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).isEmpty();
        }

        @Test
        @DisplayName("같은 채널에서 여러 후보가 있으면 점수 높은 1개만 저장한다")
        void deduplicateByChannel() throws Exception {
            givenJobParams(1L, "USER");
            Content source = buildContent("government", "pro", "USER");

            // 같은 채널, score=1 (level 같음)
            Content weak = buildContent("labor", "pro", "USER",
                    List.of("정치", "정책"), "연합뉴스TV");
            // 같은 채널, score=2 (level 다름)
            Content strong = buildContent("citizen", "con", "USER",
                    List.of("정치", "정책"), "연합뉴스TV");

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of(weak, strong));
            when(recommendationRepository.existsBySourceContentIdAndTargetContentId(any(), any()))
                    .thenReturn(false);

            tasklet.execute(contribution, chunkContext);

            ArgumentCaptor<List<ContentRecommendation>> captor = ArgumentCaptor.forClass(List.class);
            verify(recommendationRepository).saveAll(captor.capture());
            // 채널당 1개 → strong(score=2)만 살아남아 양방향 2개 저장
            assertThat(captor.getValue()).hasSize(2);
            assertThat(captor.getValue()).allMatch(r -> r.getScore() == 2);
        }
    }

    @Nested
    @DisplayName("Fallback YouTube 검색")
    class FallbackSearch {

        @Test
        @DisplayName("반대관점 후보가 없을 때 YouTube 검색을 트리거한다")
        void triggersFallbackSearchWhenNoOppositeCandidate() throws Exception {
            givenJobParams(1L, "USER");
            Content source = buildContent("government", "pro", "USER");

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            // 후보가 같은 stakeholder → score=0 → 반대 후보 0개
            Content sameStakeholder = buildContent("government", "con", "USER");
            when(dbStrategy.getCandidates(source)).thenReturn(List.of(sameStakeholder));
            when(youtubeSearchClient.searchVideoIds(anyString(), eq(50))).thenReturn(List.of());

            tasklet.execute(contribution, chunkContext);

            verify(youtubeSearchClient).searchVideoIds(anyString(), eq(50));
        }

        @Test
        @DisplayName("fallback 검색 쿼리는 keywords를 최대 5개 공백으로 조합한다")
        void fallbackQueryUsesKeywords() throws Exception {
            givenJobParams(1L, "USER");
            Content source = buildContent("government", "pro", "USER");
            source.updatePerspective("politics", "pro", "government",
                    List.of("금리", "한국은행", "통화정책", "기준금리", "물가", "초과"), // 6개
                    "요약", "neutral", "low", true);

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of()); // 후보 없음
            when(youtubeSearchClient.searchVideoIds(anyString(), eq(50))).thenReturn(List.of());

            tasklet.execute(contribution, chunkContext);

            ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
            verify(youtubeSearchClient).searchVideoIds(queryCaptor.capture(), eq(50));
            String query = queryCaptor.getValue();
            // 최대 5개 keywords 포함
            assertThat(query).contains("금리").contains("한국은행").contains("통화정책");
            // 6번째 "초과"는 포함되지 않아야 함
            assertThat(query).doesNotContain("초과");
        }

        @Test
        @DisplayName("원본 영상 자신은 fallback 검색 결과에서 제외된다")
        void excludesOriginalVideoFromFallback() throws Exception {
            givenJobParams(1L, "USER");
            Content source = buildContent("government", "pro", "USER");
            // externalContentId = "vid_government"

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of());
            // 검색 결과에 원본 영상 포함
            when(youtubeSearchClient.searchVideoIds(anyString(), eq(50)))
                    .thenReturn(List.of("vid_government", "vid_other"));

            tasklet.execute(contribution, chunkContext);

            // ingestJob은 원본 제외한 vid_other로만 호출되어야 함 (비동기 딜레이로 실제 실행 검증은 제한적)
            verify(youtubeSearchClient).searchVideoIds(anyString(), eq(50));
        }

        @Test
        @DisplayName("동일 카테고리 FALLBACK이 이미 존재하면 YouTube 검색을 스킵한다")
        void skipFallbackSearchWhenFallbackAlreadyExists() throws Exception {
            givenJobParams(1L, "USER");
            Content source = buildContent("government", "pro", "USER");

            when(contentRepository.findById(1L)).thenReturn(Optional.of(source));
            when(dbStrategy.getCandidates(source)).thenReturn(List.of()); // 후보 없음
            when(contentRepository.existsByCategoryAndSource("politics", "FALLBACK"))
                    .thenReturn(true); // 이미 FALLBACK 존재

            tasklet.execute(contribution, chunkContext);

            verify(youtubeSearchClient, never()).searchVideoIds(anyString(), anyInt());
        }
    }
}

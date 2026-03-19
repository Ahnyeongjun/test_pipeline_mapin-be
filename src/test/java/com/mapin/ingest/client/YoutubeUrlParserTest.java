package com.mapin.ingest.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YoutubeUrlParserTest {

    private YoutubeUrlParser parser;

    @BeforeEach
    void setUp() {
        parser = new YoutubeUrlParser();
    }

    @Nested
    @DisplayName("youtube.com URL 파싱")
    class YoutubeComUrl {

        @Test
        @DisplayName("watch?v= 형식에서 videoId 추출")
        void extractFromWatchUrl() {
            String videoId = parser.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
            assertThat(videoId).isEqualTo("dQw4w9WgXcQ");
        }

        @Test
        @DisplayName("쿼리 파라미터가 여러 개일 때도 v= 추출")
        void extractFromWatchUrlWithMultipleParams() {
            String videoId = parser.extractVideoId("https://www.youtube.com/watch?list=PL123&v=dQw4w9WgXcQ&t=30s");
            assertThat(videoId).isEqualTo("dQw4w9WgXcQ");
        }

        @Test
        @DisplayName("쿼리 파라미터가 없으면 예외")
        void throwsWhenNoQuery() {
            assertThatThrownBy(() -> parser.extractVideoId("https://www.youtube.com/watch"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("v= 파라미터가 없으면 예외")
        void throwsWhenNoVideoIdParam() {
            assertThatThrownBy(() -> parser.extractVideoId("https://www.youtube.com/watch?list=PL123"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("youtu.be 단축 URL 파싱")
    class YoutubeBeUrl {

        @Test
        @DisplayName("youtu.be/videoId 형식에서 videoId 추출")
        void extractFromShortUrl() {
            String videoId = parser.extractVideoId("https://youtu.be/dQw4w9WgXcQ");
            assertThat(videoId).isEqualTo("dQw4w9WgXcQ");
        }

        @Test
        @DisplayName("youtu.be 경로가 비어있으면 예외")
        void throwsWhenEmptyPath() {
            assertThatThrownBy(() -> parser.extractVideoId("https://youtu.be/"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("잘못된 URL 처리")
    class InvalidUrl {

        @Test
        @DisplayName("지원하지 않는 도메인이면 예외")
        void throwsForUnsupportedDomain() {
            assertThatThrownBy(() -> parser.extractVideoId("https://vimeo.com/12345"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("host가 없는 URL이면 예외")
        void throwsForUrlWithoutHost() {
            assertThatThrownBy(() -> parser.extractVideoId("not-a-url"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("URL 정규화")
    class Canonicalize {

        @Test
        @DisplayName("videoId로 정규 URL 생성")
        void canonicalizeVideoId() {
            String url = parser.canonicalize("dQw4w9WgXcQ");
            assertThat(url).isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        }
    }
}

package com.mapin.content.application;

import java.net.URI;
import org.springframework.stereotype.Component;

@Component
public class YoutubeUrlParser {

    public String extractVideoId(String url) {
        try {
            URI uri = URI.create(url);

            if (uri.getHost() == null) {
                throw new IllegalArgumentException("유효하지 않은 URL입니다.");
            }

            String host = uri.getHost().toLowerCase();

            if (host.contains("youtu.be")) {
                String path = uri.getPath();
                if (path == null || path.length() <= 1) {
                    throw new IllegalArgumentException("videoId를 찾을 수 없습니다.");
                }
                return path.substring(1);
            }

            if (host.contains("youtube.com")) {
                String query = uri.getQuery();
                if (query == null) {
                    throw new IllegalArgumentException("videoId를 찾을 수 없습니다.");
                }
                for (String param : query.split("&")) {
                    String[] parts = param.split("=", 2);
                    if (parts.length == 2 && parts[0].equals("v")) {
                        return parts[1];
                    }
                }
            }

            throw new IllegalArgumentException("지원하지 않는 YouTube URL 형식입니다.");
        } catch (Exception e) {
            throw new IllegalArgumentException("YouTube URL 파싱에 실패했습니다.", e);
        }
    }

    public String canonicalize(String videoId) {
        return "https://www.youtube.com/watch?v=" + videoId;
    }
}

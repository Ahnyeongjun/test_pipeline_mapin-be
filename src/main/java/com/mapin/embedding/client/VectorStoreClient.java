package com.mapin.embedding.client;

import java.util.List;

public interface VectorStoreClient {

    /**
     * 벡터를 저장하고 포인트 ID를 반환한다.
     *
     * @param id     콘텐츠 ID (Qdrant 포인트 ID로 사용)
     * @param vector 임베딩 벡터
     */
    void upsert(long id, List<Float> vector);

    /**
     * 유사 벡터를 검색하여 포인트 ID 목록을 반환한다.
     *
     * @param vector 쿼리 벡터
     * @param topK   반환할 최대 개수
     */
    List<String> search(List<Float> vector, int topK);
}

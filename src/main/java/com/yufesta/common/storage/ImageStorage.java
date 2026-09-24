package com.yufesta.common.storage;

/**
 * 이미지 객체 저장소. 키를 넣고 공개 URL을 받는다. 구현은 실행 환경 어댑터 둘: S3(운영)·로컬 파일(dev·test)
 */
public interface ImageStorage {

    /** key에 저장하고 공개 URL을 돌려준다. 같은 키는 덮어쓴다 */
    String store(String key, byte[] content, String contentType);

    /** 없는 키도 조용히 지나간다(교체 실패 뒤 정리 등 멱등 호출) */
    void delete(String key);
}

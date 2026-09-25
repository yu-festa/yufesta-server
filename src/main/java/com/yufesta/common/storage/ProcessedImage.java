package com.yufesta.common.storage;

/** 리사이즈 결과. 카드용(긴 변 1600px)과 목록용 썸네일(긴 변 400px), 둘 다 JPEG */
public record ProcessedImage(byte[] large, byte[] thumbnail) {

    public static final String CONTENT_TYPE = "image/jpeg";
}

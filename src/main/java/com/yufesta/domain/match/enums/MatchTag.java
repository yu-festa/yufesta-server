package com.yufesta.domain.match.enums;

/**
 * 인스타팅 관심 태그. 고정 10개(FR-MT-10). DB에는 enum 이름이 저장되므로 이름을 바꾸면 데이터 마이그레이션이 필요하다
 */
public enum MatchTag {

    ALCOHOL("술"),
    PERFORMANCE("공연"),
    SPORTS("운동"),
    GAME("게임"),
    CAFE("카페"),
    MOVIE("영화"),
    MUSIC("음악"),
    PHOTO("사진"),
    PET("반려동물"),
    ETC("기타");

    private final String label;

    MatchTag(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

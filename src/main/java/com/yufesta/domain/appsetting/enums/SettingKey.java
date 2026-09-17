package com.yufesta.domain.appsetting.enums;

/**
 * app_settings에 존재하는 키 목록. 값은 DB에 있고 여기에는 키 이름과 타입만 둔다.
 * 키를 추가하면 db/dev/data.sql 시드에도 같은 키를 넣는다(SettingKeyTest가 둘의 일치를 검사)
 */
public enum SettingKey {

    MATCH_WEIGHT_TAG("match.weight.tag", SettingType.DECIMAL),
    MATCH_WEIGHT_SLOT("match.weight.slot", SettingType.DECIMAL),
    MATCH_WEIGHT_AGE_SAME("match.weight.age_same", SettingType.DECIMAL),
    MATCH_WEIGHT_AGE_ADJACENT("match.weight.age_adjacent", SettingType.DECIMAL),
    MATCH_MAX_PARTNERS("match.max_partners", SettingType.INT),
    REPORT_BLOCK_THRESHOLD("report.block_threshold", SettingType.INT),
    REPORT_HIDE_THRESHOLD("report.hide_threshold", SettingType.INT),
    NICKNAME_ADJECTIVES("nickname.adjectives", SettingType.LIST),
    NICKNAME_ANIMALS("nickname.animals", SettingType.LIST),
    FILTER_BANNED_WORDS("filter.banned_words", SettingType.LIST),
    FILTER_LLM_ENABLED("filter.llm.enabled", SettingType.BOOLEAN),
    FILTER_LLM_THRESHOLD("filter.llm.threshold", SettingType.DECIMAL),
    RATELIMIT_CHEER_ANON_PER_MINUTE("ratelimit.cheer.anon_per_minute", SettingType.INT),
    RATELIMIT_CHEER_IP_PER_MINUTE("ratelimit.cheer.ip_per_minute", SettingType.INT),
    RATELIMIT_LOSTITEM_PER_MINUTE("ratelimit.lostitem.per_minute", SettingType.INT),
    ADMIN_ALLOWLIST("admin.allowlist", SettingType.LIST);

    private final String key;
    private final SettingType type;

    SettingKey(String key, SettingType type) {
        this.key = key;
        this.type = type;
    }

    public String key() {
        return key;
    }

    public SettingType type() {
        return type;
    }
}

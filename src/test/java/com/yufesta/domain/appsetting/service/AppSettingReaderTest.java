package com.yufesta.domain.appsetting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.appsetting.entity.AppSetting;
import com.yufesta.domain.appsetting.enums.SettingKey;
import com.yufesta.domain.appsetting.repository.AppSettingRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppSettingReaderTest {

    private static final Instant T0 = Instant.parse("2026-10-08T07:00:00Z");

    // db/dev/data.sql 시드와 같은 값
    private static final Map<String, String> SEED = Map.ofEntries(
            Map.entry("match.weight.tag", "1"),
            Map.entry("match.weight.slot", "2"),
            Map.entry("match.weight.age_same", "1"),
            Map.entry("match.weight.age_adjacent", "0.5"),
            Map.entry("match.max_partners", "3"),
            Map.entry("report.block_threshold", "2"),
            Map.entry("report.hide_threshold", "2"),
            Map.entry("nickname.adjectives", "수줍은,신난,느긋한,씩씩한,조용한"),
            Map.entry("nickname.animals", "펭귄,수달,고양이,판다,돌고래"),
            Map.entry("filter.banned_words", ""),
            Map.entry("filter.llm.enabled", "1"),
            Map.entry("filter.llm.threshold", "0.5"),
            Map.entry("ratelimit.cheer.anon_per_minute", "1"),
            Map.entry("ratelimit.cheer.ip_per_minute", "10"),
            Map.entry("ratelimit.lostitem.per_minute", "1"),
            Map.entry("admin.allowlist", "")
    );

    @Mock
    private AppSettingRepository appSettingRepository;

    @Mock
    private Clock clock;

    private AppSettingReader reader;

    @BeforeEach
    void setUp() {
        reader = new AppSettingReader(appSettingRepository, clock);
    }

    @Test
    void 시드_값을_타입별로_읽는다() {
        givenRows(SEED);
        when(clock.instant()).thenReturn(T0);

        assertThat(reader.getInt(SettingKey.MATCH_MAX_PARTNERS)).isEqualTo(3);
        assertThat(reader.getDecimal(SettingKey.MATCH_WEIGHT_AGE_ADJACENT)).isEqualByComparingTo(new BigDecimal("0.5"));
        assertThat(reader.getBoolean(SettingKey.FILTER_LLM_ENABLED)).isTrue();
        assertThat(reader.getString(SettingKey.NICKNAME_ADJECTIVES)).isEqualTo("수줍은,신난,느긋한,씩씩한,조용한");
        assertThat(reader.getList(SettingKey.NICKNAME_ANIMALS)).containsExactly("펭귄", "수달", "고양이", "판다", "돌고래");
        assertThat(reader.getList(SettingKey.FILTER_BANNED_WORDS)).isEmpty();
        assertThat(reader.getList(SettingKey.ADMIN_ALLOWLIST)).isEmpty();
    }

    @Test
    void 목록은_항목을_trim하고_빈_항목을_버린다() {
        givenRows(Map.of("admin.allowlist", " KAKAO:1 , ,GOOGLE:2,"));
        when(clock.instant()).thenReturn(T0);

        assertThat(reader.getList(SettingKey.ADMIN_ALLOWLIST)).containsExactly("KAKAO:1", "GOOGLE:2");
    }

    @Test
    void 삼십초_안에는_DB를_한_번만_읽고_지나면_다시_읽는다() {
        givenRows(SEED);
        when(clock.instant()).thenReturn(T0, T0.plusSeconds(10), T0.plusSeconds(29), T0.plusSeconds(30));

        reader.getInt(SettingKey.MATCH_MAX_PARTNERS);
        reader.getDecimal(SettingKey.MATCH_WEIGHT_TAG);
        reader.getList(SettingKey.NICKNAME_ANIMALS);
        verify(appSettingRepository, times(1)).findAll();

        reader.getInt(SettingKey.MATCH_MAX_PARTNERS);
        verify(appSettingRepository, times(2)).findAll();
    }

    @Test
    void invalidate_뒤에는_바로_다시_읽는다() {
        givenRows(SEED);
        when(clock.instant()).thenReturn(T0);

        reader.getInt(SettingKey.MATCH_MAX_PARTNERS);
        reader.invalidate();
        reader.getInt(SettingKey.MATCH_MAX_PARTNERS);

        verify(appSettingRepository, times(2)).findAll();
    }

    @Test
    void 키가_없으면_APP_SETTING_NOT_FOUND를_던진다() {
        givenRows(Map.of("match.max_partners", "3"));
        when(clock.instant()).thenReturn(T0);

        assertThatThrownBy(() -> reader.getInt(SettingKey.REPORT_BLOCK_THRESHOLD))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.APP_SETTING_NOT_FOUND);
    }

    @Test
    void 값_형식이_틀리면_APP_SETTING_INVALID를_던진다() {
        givenRows(Map.of("match.max_partners", "three", "filter.llm.enabled", "yes"));
        when(clock.instant()).thenReturn(T0);

        assertThatThrownBy(() -> reader.getInt(SettingKey.MATCH_MAX_PARTNERS))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.APP_SETTING_INVALID);
        assertThatThrownBy(() -> reader.getBoolean(SettingKey.FILTER_LLM_ENABLED))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.APP_SETTING_INVALID);
    }

    private void givenRows(Map<String, String> rows) {
        List<AppSetting> entities = rows.entrySet().stream()
                .map(entry -> new AppSetting(entry.getKey(), entry.getValue(), "테스트"))
                .toList();
        when(appSettingRepository.findAll()).thenReturn(entities);
    }
}

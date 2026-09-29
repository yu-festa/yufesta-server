package com.yufesta.domain.moderation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.appsetting.enums.SettingKey;
import com.yufesta.domain.appsetting.service.AppSettingReader;
import com.yufesta.domain.cheer.enums.ModerationStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentModerationServiceTest {

    @Mock
    private AppSettingReader appSettingReader;

    @Mock
    private OpenAiModerationClient openAiModerationClient;

    @Mock
    private KoreanContentPolicy koreanContentPolicy;

    @InjectMocks
    private ContentModerationService contentModerationService;

    @Test
    void 전화번호와_이메일_URL_인스타_아이디는_저장_전에_차단한다() {
        for (String content : List.of(
                "010-1234-5678로 연락 주세요",
                "hello@example.com",
                "https://example.com",
                "@yufesta2026"
        )) {
            assertThatThrownBy(() -> contentModerationService.validateLocal(content))
                    .isInstanceOf(CustomException.class)
                    .extracting(exception -> ((CustomException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.CONTENT_NOT_ALLOWED);
        }
    }

    @Test
    void 설정된_금칙어를_포함하면_차단한다() {
        when(appSettingReader.getList(SettingKey.FILTER_BANNED_WORDS)).thenReturn(List.of("금칙어"));
        when(koreanContentPolicy.findViolation("이 문장에는 금칙어가 있어요", List.of("금칙어")))
                .thenReturn(Optional.of(LocalModerationViolation.BANNED_WORD));

        assertThatThrownBy(() -> contentModerationService.validateLocal("이 문장에는 금칙어가 있어요"))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONTENT_NOT_ALLOWED);
    }

    @Test
    void LLM_검사가_꺼져_있으면_외부_API_호출_없이_통과한다() {
        when(appSettingReader.getBoolean(SettingKey.FILTER_LLM_ENABLED)).thenReturn(false);

        ModerationStatus result = contentModerationService.moderateByLlm("축제 파이팅!");

        assertThat(result).isEqualTo(ModerationStatus.PASSED);
        verify(openAiModerationClient, never()).exceedsThreshold(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void LLM_점수가_임계값_이상이면_차단한다() {
        when(appSettingReader.getBoolean(SettingKey.FILTER_LLM_ENABLED)).thenReturn(true);
        when(appSettingReader.getDecimal(SettingKey.FILTER_LLM_THRESHOLD)).thenReturn(new BigDecimal("0.5"));
        when(openAiModerationClient.exceedsThreshold("위험한 문장", new BigDecimal("0.5"))).thenReturn(true);

        assertThatThrownBy(() -> contentModerationService.moderateByLlm("위험한 문장"))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONTENT_NOT_ALLOWED);
    }

    @Test
    void LLM_호출이_실패하면_SKIPPED로_저장하도록_표시한다() {
        when(appSettingReader.getBoolean(SettingKey.FILTER_LLM_ENABLED)).thenReturn(true);
        when(appSettingReader.getDecimal(SettingKey.FILTER_LLM_THRESHOLD)).thenReturn(new BigDecimal("0.5"));
        when(openAiModerationClient.exceedsThreshold("축제 파이팅!", new BigDecimal("0.5")))
                .thenThrow(new OpenAiModerationException("timeout"));

        assertThat(contentModerationService.moderateByLlm("축제 파이팅!"))
                .isEqualTo(ModerationStatus.SKIPPED);
    }
}

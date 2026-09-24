package com.yufesta.common.nickname;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yufesta.domain.appsetting.enums.SettingKey;
import com.yufesta.domain.appsetting.service.AppSettingReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NicknameGeneratorTest {

    @Mock
    private AppSettingReader appSettingReader;

    @InjectMocks
    private NicknameGenerator nicknameGenerator;

    @Test
    void 설정된_형용사와_동물을_조합해_닉네임을_생성한다() {
        when(appSettingReader.getList(SettingKey.NICKNAME_ADJECTIVES)).thenReturn(List.of("수줍은", "씩씩한"));
        when(appSettingReader.getList(SettingKey.NICKNAME_ANIMALS)).thenReturn(List.of("펭귄", "판다"));

        String nickname = nicknameGenerator.generate();

        assertThat(nickname).isIn("수줍은 펭귄", "수줍은 판다", "씩씩한 펭귄", "씩씩한 판다");
    }
}

package com.yufesta.common.nickname;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.appsetting.enums.SettingKey;
import com.yufesta.domain.appsetting.service.AppSettingReader;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

/** 운영 설정의 형용사와 동물을 조합해 익명 표시명을 만든다 */
@Service
public class NicknameGenerator {

    private final AppSettingReader appSettingReader;

    public NicknameGenerator(AppSettingReader appSettingReader) {
        this.appSettingReader = appSettingReader;
    }

    /**
     * 게시물마다 새 익명 표시명을 생성한다(FR-AN-01, 02).
     * @throws CustomException APP_SETTING_INVALID
     */
    public String generate() {
        List<String> adjectives = appSettingReader.getList(SettingKey.NICKNAME_ADJECTIVES);
        List<String> animals = appSettingReader.getList(SettingKey.NICKNAME_ANIMALS);
        if (adjectives.isEmpty() || animals.isEmpty()) {
            throw new CustomException(ErrorCode.APP_SETTING_INVALID);
        }
        return pick(adjectives) + " " + pick(animals);
    }

    private static String pick(List<String> words) {
        return words.get(ThreadLocalRandom.current().nextInt(words.size()));
    }
}

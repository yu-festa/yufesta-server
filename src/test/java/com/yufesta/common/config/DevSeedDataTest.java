package com.yufesta.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.yufesta.domain.appsetting.repository.AppSettingRepository;
import com.yufesta.domain.match.enums.RoundStatus;
import com.yufesta.domain.match.repository.MatchRoundRepository;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

/**
 * dev 시드 SQL이 문법 오류 없이 실행되고, 두 번 실행해도 행이 늘지 않는지 H2(MySQL 모드)로 확인
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DevSeedDataTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private AppSettingRepository appSettingRepository;

    @Autowired
    private MatchRoundRepository matchRoundRepository;

    @Test
    void 시드는_설정_16행과_회차_2행을_넣고_다시_실행해도_중복되지_않는다() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("db/dev/data.sql"));

        populator.execute(dataSource);
        populator.execute(dataSource);

        assertThat(appSettingRepository.count()).isEqualTo(16);
        assertThat(appSettingRepository.findById("admin.allowlist"))
                .get()
                .extracting(setting -> setting.getSettingValue())
                .isEqualTo("");
        assertThat(appSettingRepository.findById("match.max_partners"))
                .get()
                .extracting(setting -> setting.getSettingValue())
                .isEqualTo("3");
        assertThat(matchRoundRepository.findAllByOrderBySeqAsc())
                .extracting(round -> round.getSeq(), round -> round.getStatus())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, RoundStatus.SCHEDULED),
                        org.assertj.core.groups.Tuple.tuple(2, RoundStatus.SCHEDULED)
                );
    }
}

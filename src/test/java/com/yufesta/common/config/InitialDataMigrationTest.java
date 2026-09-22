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
 * 초기 데이터 마이그레이션(V2)이 문법 오류 없이 실행되고 기대한 행을 넣는지 H2(MySQL 모드)로 확인.
 * V1은 MySQL 전용 문법이라 여기서 실행하지 않고, 스키마는 Hibernate가 엔티티로 만든다
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InitialDataMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private AppSettingRepository appSettingRepository;

    @Autowired
    private MatchRoundRepository matchRoundRepository;

    @Test
    void 초기_데이터는_설정_16행과_회차_2행을_넣는다() {
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V2__initial_data.sql")).execute(dataSource);

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

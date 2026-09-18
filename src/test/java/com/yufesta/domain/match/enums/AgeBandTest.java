package com.yufesta.domain.match.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AgeBandTest {

    @Test
    void 이웃한_나이대만_인접이다() {
        assertThat(AgeBand.A19_21.isAdjacentTo(AgeBand.A22_24)).isTrue();
        assertThat(AgeBand.A22_24.isAdjacentTo(AgeBand.A19_21)).isTrue();
        assertThat(AgeBand.A25_27.isAdjacentTo(AgeBand.A28_PLUS)).isTrue();
        assertThat(AgeBand.A19_21.isAdjacentTo(AgeBand.A25_27)).isFalse();
        assertThat(AgeBand.A19_21.isAdjacentTo(AgeBand.A19_21)).isFalse();
    }

    @Test
    void ERD_저장값으로_변환한다() {
        assertThat(AgeBand.fromValue("28+")).isEqualTo(AgeBand.A28_PLUS);
        assertThat(AgeBand.A19_21.value()).isEqualTo("19-21");
        assertThatThrownBy(() -> AgeBand.fromValue("30+")).isInstanceOf(IllegalArgumentException.class);
    }
}

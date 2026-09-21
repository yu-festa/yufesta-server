package com.yufesta.domain.match.engine;

import com.yufesta.domain.match.enums.AgeBand;
import com.yufesta.domain.match.enums.EntryType;
import com.yufesta.domain.match.enums.Gender;
import com.yufesta.domain.match.enums.MatchTag;
import java.util.Set;
import lombok.Builder;

/**
 * 엔진 입력 한 명. 엔티티가 아니라 계산에 필요한 값만 담는다.
 * wantedSlotId는 호출 측이 FR-MT-05 조건을 검증해 통과한 것만 넣고, 아니면 null이다
 */
@Builder
public record Candidate(
        Long applicationId,
        Long userId,
        Gender gender,
        Set<MatchTag> tags,
        Long wantedSlotId,
        AgeBand ageBand,
        EntryType entryType
) {

    public Candidate {
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }

    boolean isCarried() {
        return entryType == EntryType.CARRIED;
    }
}

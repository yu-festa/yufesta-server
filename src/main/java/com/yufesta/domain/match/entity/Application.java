package com.yufesta.domain.match.entity;

import com.yufesta.common.entity.BaseTimeEntity;
import com.yufesta.domain.match.enums.AgeBand;
import com.yufesta.domain.match.enums.EntryType;
import com.yufesta.domain.match.enums.Gender;
import com.yufesta.domain.match.enums.MatchTag;
import com.yufesta.domain.timetable.entity.TimetableSlot;
import com.yufesta.domain.user.entity.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인스타팅 신청. 회원당 회차 1건, 인스타 ID는 회차 내 유니크(FR-MT-13). 취소는 canceled_at 소프트 삭제.
 * 보고 싶은 공연(wantedSlot)은 회차 발표 이후 시작 공연만 허용하며(FR-MT-05) 검증은 서비스가 한다
 */
@Getter
@Entity
@Table(
        name = "applications",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_app_user_round", columnNames = {"user_id", "round_id"}),
                @UniqueConstraint(name = "uk_app_round_insta", columnNames = {"round_id", "instagram_id"})
        },
        indexes = {
                @Index(name = "idx_app_round_gender", columnList = "round_id, gender"),
                @Index(name = "idx_app_source", columnList = "source_application_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false)
    private MatchRound round;

    @Column(nullable = false, length = 30)
    private String instagramId;

    @Column(nullable = false, length = 16)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 1)
    private Gender gender;

    @Convert(converter = AgeBandConverter.class)
    @Column(length = 5)
    private AgeBand ageBand;

    @Column(length = 40)
    private String intro;

    // 보고 싶은 공연(선택). 공연이 삭제되면 비워진다(FK SET NULL·ApplicationRepository.detachWantedSlot)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wanted_slot_id")
    private TimetableSlot wantedSlot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EntryType entryType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_application_id")
    private Application sourceApplication;

    @Column(nullable = false, length = 20)
    private String termsVersion;

    @Column(nullable = false, length = 20)
    private String privacyVersion;

    @Column(nullable = false)
    private boolean ageConfirmed;

    @Column(nullable = false)
    private LocalDateTime agreedAt;

    private LocalDateTime canceledAt;

    // application_tags(application_id, tag)에 enum 이름으로 저장. 신청 삭제 시 함께 삭제된다
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "application_tags", joinColumns = @JoinColumn(name = "application_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "tag", nullable = false, length = 20)
    private Set<MatchTag> tags = new HashSet<>();

    @Builder
    private Application(
            User user,
            MatchRound round,
            String instagramId,
            String nickname,
            Gender gender,
            AgeBand ageBand,
            Set<MatchTag> tags,
            String intro,
            TimetableSlot wantedSlot,
            EntryType entryType,
            Application sourceApplication,
            String termsVersion,
            String privacyVersion,
            boolean ageConfirmed,
            LocalDateTime agreedAt
    ) {
        this.user = user;
        this.round = round;
        this.instagramId = instagramId;
        this.nickname = nickname;
        this.gender = gender;
        this.ageBand = ageBand;
        this.tags = tags == null ? new HashSet<>() : new HashSet<>(tags);
        this.intro = intro;
        this.wantedSlot = wantedSlot;
        this.entryType = entryType == null ? EntryType.NEW : entryType;
        this.sourceApplication = sourceApplication;
        this.termsVersion = termsVersion;
        this.privacyVersion = privacyVersion;
        this.ageConfirmed = ageConfirmed;
        this.agreedAt = agreedAt;
    }

    public boolean isCanceled() {
        return canceledAt != null;
    }

    // 마감 전 수정(FR-MT-14). 동의 항목은 바뀌지 않는다
    public void update(
            String instagramId,
            String nickname,
            Gender gender,
            AgeBand ageBand,
            Set<MatchTag> tags,
            String intro
    ) {
        this.instagramId = instagramId;
        this.nickname = nickname;
        this.gender = gender;
        this.ageBand = ageBand;
        this.tags.clear();
        this.tags.addAll(tags);
        this.intro = intro;
    }

    /** 다른 회차로 복사(이월·재참여)하거나 배치가 점수에 쓸 때: 그 회차 규칙(FR-MT-05)을 만족하면 공연, 아니면 null */
    public TimetableSlot wantedSlotFor(MatchRound target) {
        return wantedSlot != null && target.allowsWantedSlot(wantedSlot.getStartAt()) ? wantedSlot : null;
    }

    // 보고 싶은 공연 변경(null이면 선택 없음). 회차 규칙 검증은 서비스에서 끝내고 들어온다
    public void changeWantedSlot(TimetableSlot wantedSlot) {
        this.wantedSlot = wantedSlot;
    }

    // 재신청 시 동의를 다시 기록한다(FR-MT-11·12)
    public void agree(String termsVersion, String privacyVersion, boolean ageConfirmed, LocalDateTime agreedAt) {
        this.termsVersion = termsVersion;
        this.privacyVersion = privacyVersion;
        this.ageConfirmed = ageConfirmed;
        this.agreedAt = agreedAt;
    }

    public void cancel(LocalDateTime now) {
        this.canceledAt = now;
    }

    // 취소 후 재신청은 uk_app_user_round 때문에 새 행이 아니라 기존 행을 되살린다
    public void restore() {
        this.canceledAt = null;
    }
}

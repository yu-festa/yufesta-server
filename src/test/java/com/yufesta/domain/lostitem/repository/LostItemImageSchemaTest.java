package com.yufesta.domain.lostitem.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yufesta.common.config.ClockConfig;
import com.yufesta.common.config.JpaConfig;
import com.yufesta.domain.lostitem.entity.LostItem;
import com.yufesta.domain.lostitem.entity.LostItemImage;
import com.yufesta.domain.lostitem.enums.LostItemKind;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.enums.UserRole;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/** 분실물 이미지 1장 제약과 게시글 연결을 H2(MySQL 모드)로 고정 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, ClockConfig.class})
class LostItemImageSchemaTest {

    @org.springframework.beans.factory.annotation.Autowired
    private LostItemRepository lostItemRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private LostItemImageRepository lostItemImageRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.boot.jpa.test.autoconfigure.TestEntityManager em;

    @Test
    void 게시글당_이미지는_한장만_저장할_수_있고_게시글에서_조회된다() {
        User user = em.persist(User.builder()
                .provider(OAuthProvider.KAKAO).providerUserId("kakao-user").role(UserRole.USER)
                .loginAt(LocalDateTime.of(2026, 9, 26, 12, 0))
                .build());
        LostItem lostItem = em.persist(LostItem.builder()
                .kind(LostItemKind.FOUND)
                .description("검은색 카드지갑")
                .placeText("중앙도서관 앞")
                .displayName("수줍은 펭귄")
                .author(user)
                .build());
        lostItemImageRepository.saveAndFlush(LostItemImage.builder()
                .lostItem(lostItem)
                .imageUrl("https://cdn.test/lost-items/1/wallet-1600.jpg")
                .thumbnailUrl("https://cdn.test/lost-items/1/wallet-thumb.jpg")
                .build());
        em.clear();

        LostItem found = lostItemRepository.findById(lostItem.getId()).orElseThrow();

        assertThat(found.getImage().getThumbnailUrl()).isEqualTo("https://cdn.test/lost-items/1/wallet-thumb.jpg");
        assertThatThrownBy(() -> lostItemImageRepository.saveAndFlush(LostItemImage.builder()
                .lostItem(found)
                .imageUrl("https://cdn.test/lost-items/1/other-1600.jpg")
                .thumbnailUrl("https://cdn.test/lost-items/1/other-thumb.jpg")
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

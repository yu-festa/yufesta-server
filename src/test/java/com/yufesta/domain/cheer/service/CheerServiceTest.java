package com.yufesta.domain.cheer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yufesta.common.nickname.NicknameGenerator;
import com.yufesta.domain.cheer.dto.request.CreateCheerRequest;
import com.yufesta.domain.cheer.dto.response.CheerResponse;
import com.yufesta.domain.cheer.entity.Cheer;
import com.yufesta.domain.cheer.enums.ModerationStatus;
import com.yufesta.domain.cheer.repository.CheerRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CheerServiceTest {

    @Mock
    private CheerRepository cheerRepository;

    @Mock
    private AnonymousKeyService anonymousKeyService;

    @Mock
    private NicknameGenerator nicknameGenerator;

    @InjectMocks
    private CheerService cheerService;

    @Test
    void 최신순_공개_응원_메시지를_조회하고_내_메시지를_표시한다() {
        Cheer newest = cheer(2L, "축제 최고예요!", "신난 수달", "mine-hash", LocalDateTime.of(2026, 10, 2, 15, 0));
        Cheer older = cheer(1L, "다 같이 즐겨요!", "수줍은 펭귄", "other-hash", LocalDateTime.of(2026, 10, 2, 14, 0));
        when(anonymousKeyService.hash("mine-key")).thenReturn("mine-hash");
        when(cheerRepository.findAllByHiddenFalseOrderByCreatedAtDesc(any())).thenReturn(List.of(newest, older));

        List<CheerResponse> result = cheerService.getCheers("mine-key", 20);

        assertThat(result)
                .extracting(CheerResponse::content, CheerResponse::mine)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("축제 최고예요!", true),
                        org.assertj.core.groups.Tuple.tuple("다 같이 즐겨요!", false)
                );
    }

    @Test
    void 비로그인_익명_키로_응원_메시지를_작성한다() {
        when(anonymousKeyService.hash("new-key")).thenReturn("new-hash");
        when(nicknameGenerator.generate()).thenReturn("씩씩한 판다");
        when(cheerRepository.save(any(Cheer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CheerResponse result = cheerService.create("new-key", new CreateCheerRequest("축제 파이팅!"));

        assertThat(result)
                .extracting(CheerResponse::content, CheerResponse::displayName, CheerResponse::mine)
                .containsExactly("축제 파이팅!", "씩씩한 판다", true);
        ArgumentCaptor<Cheer> captor = ArgumentCaptor.forClass(Cheer.class);
        verify(cheerRepository).save(captor.capture());
        assertThat(captor.getValue())
                .extracting(Cheer::getContent, Cheer::getDisplayName, Cheer::getWriterKeyHash, Cheer::getModerationStatus)
                .containsExactly("축제 파이팅!", "씩씩한 판다", "new-hash", ModerationStatus.PASSED);
    }

    private Cheer cheer(Long id, String content, String displayName, String writerKeyHash, LocalDateTime createdAt) {
        Cheer cheer = Cheer.builder()
                .content(content)
                .displayName(displayName)
                .writerKeyHash(writerKeyHash)
                .build();
        ReflectionTestUtils.setField(cheer, "id", id);
        ReflectionTestUtils.setField(cheer, "createdAt", createdAt);
        return cheer;
    }
}

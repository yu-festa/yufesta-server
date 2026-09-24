package com.yufesta.domain.cheer.service;

import com.yufesta.common.nickname.NicknameGenerator;
import com.yufesta.domain.cheer.dto.request.CreateCheerRequest;
import com.yufesta.domain.cheer.dto.response.CheerResponse;
import com.yufesta.domain.cheer.entity.Cheer;
import com.yufesta.domain.cheer.repository.CheerRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공개 응원 메시지 조회와 익명 작성을 처리 */
@Service
@Transactional(readOnly = true)
public class CheerService {

    private final CheerRepository cheerRepository;
    private final AnonymousKeyService anonymousKeyService;
    private final NicknameGenerator nicknameGenerator;

    public CheerService(
            CheerRepository cheerRepository,
            AnonymousKeyService anonymousKeyService,
            NicknameGenerator nicknameGenerator
    ) {
        this.cheerRepository = cheerRepository;
        this.anonymousKeyService = anonymousKeyService;
        this.nicknameGenerator = nicknameGenerator;
    }

    /** 숨김 처리되지 않은 응원 메시지를 최신순으로 조회한다(FR-CH-01). */
    public List<CheerResponse> getCheers(String anonymousKey, int size) {
        String myWriterKeyHash = anonymousKey == null ? null : anonymousKeyService.hash(anonymousKey);
        return cheerRepository.findAllByHiddenFalseOrderByCreatedAtDesc(PageRequest.of(0, size))
                .stream()
                .map(cheer -> CheerResponse.from(cheer, myWriterKeyHash))
                .toList();
    }

    /**
     * 익명 키 해시와 자동 생성 닉네임으로 응원 메시지를 저장한다(FR-CH-02, FR-AN-01~03).
     */
    @Transactional
    public CheerResponse create(String anonymousKey, CreateCheerRequest request) {
        String writerKeyHash = anonymousKeyService.hash(anonymousKey);
        Cheer cheer = Cheer.builder()
                .content(request.content().trim())
                .displayName(nicknameGenerator.generate())
                .writerKeyHash(writerKeyHash)
                .build();
        return CheerResponse.from(cheerRepository.save(cheer), writerKeyHash);
    }
}

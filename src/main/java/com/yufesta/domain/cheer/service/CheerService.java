package com.yufesta.domain.cheer.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
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

    /**
     * 콘텐츠 신고 전 대상 응원 메시지를 잠그고 신고 가능 여부를 확인한다.
     * <p>잠금은 상위 신고 트랜잭션이 끝날 때까지 유지된다.
     * @throws CustomException CONTENT_REPORT_TARGET_NOT_FOUND, CONTENT_NOT_REPORTABLE
     */
    @Transactional
    public void lockReportableForReport(Long cheerId) {
        Cheer cheer = getCheerForUpdate(cheerId);
        if (cheer.isHidden()) {
            throw new CustomException(ErrorCode.CONTENT_NOT_REPORTABLE);
        }
    }

    /**
     * 신고 수를 원자적으로 늘리고 임계값에 도달하면 숨긴다.
     * <p>호출 전 lockReportableForReport로 같은 행을 잠가야 한다.
     */
    @Transactional
    public void incrementReportCountAndHideIfThreshold(Long cheerId, int hideThreshold) {
        cheerRepository.incrementReportCount(cheerId);
        Cheer cheer = getCheerForUpdate(cheerId);
        if (cheer.getReportCount() >= hideThreshold) {
            cheer.hide();
        }
    }

    private Cheer getCheerForUpdate(Long cheerId) {
        return cheerRepository.findByIdForUpdate(cheerId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_REPORT_TARGET_NOT_FOUND));
    }
}

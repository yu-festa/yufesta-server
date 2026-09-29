package com.yufesta.domain.notice.service;

import com.yufesta.common.cache.PublicCacheEvictor;
import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.notice.dto.request.CreateNoticeRequest;
import com.yufesta.domain.notice.dto.request.UpdateNoticeRequest;
import com.yufesta.domain.notice.dto.response.NoticeResponse;
import com.yufesta.domain.notice.entity.Notice;
import com.yufesta.domain.notice.repository.NoticeRepository;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.service.UserService;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공지 공개 조회를 처리
 */
@Service
@Transactional(readOnly = true)
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final UserService userService;
    private final PublicCacheEvictor cacheEvictor;

    public NoticeService(
            NoticeRepository noticeRepository,
            UserService userService,
            PublicCacheEvictor cacheEvictor
    ) {
        this.noticeRepository = noticeRepository;
        this.userService = userService;
        this.cacheEvictor = cacheEvictor;
    }

    /**
     * 공지를 최신순으로 조회한다(FR-NT-01).
     */
    public List<NoticeResponse> getNotices(int size) {
        return noticeRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, size))
                .stream()
                .map(NoticeResponse::from)
                .toList();
    }

    /**
     * 공지 상세를 조회한다(FR-NT-01).
     * @throws CustomException NOTICE_NOT_FOUND
     */
    public NoticeResponse getNotice(Long noticeId) {
        return NoticeResponse.from(getNoticeOrThrow(noticeId));
    }

    /**
     * 운영자 공지를 등록하고 작성자를 기록한다(FR-NT-01).
     * @throws CustomException UNAUTHORIZED, USER_NOT_FOUND
     */
    @Transactional
    public NoticeResponse create(Long userId, CreateNoticeRequest request) {
        User user = requireUser(userId);
        Notice notice = Notice.builder()
                .title(request.title())
                .body(request.body())
                .banner(request.banner())
                .createdBy(user)
                .build();
        NoticeResponse response = NoticeResponse.from(noticeRepository.save(notice));
        cacheEvictor.evictNotices(response.id());
        return response;
    }

    /**
     * 인스타팅 회차 발표와 같은 트랜잭션에서 시스템 공지를 생성한다.
     * <p>스케줄러 발표에는 운영자 사용자가 없으므로 작성자는 비워 두고, 긴급 배너로는 노출하지 않는다.
     */
    @Transactional
    public NoticeResponse createMatchResultPublished(int roundSeq) {
        Notice notice = Notice.builder()
                .title("인스타팅 %d회차 매칭 결과 발표".formatted(roundSeq))
                .body("인스타팅 %d회차 매칭 결과가 발표되었습니다. 내 프로필에서 결과를 확인해 주세요."
                        .formatted(roundSeq))
                .banner(false)
                .createdBy(null)
                .build();
        NoticeResponse response = NoticeResponse.from(noticeRepository.save(notice));
        cacheEvictor.evictNotices(response.id());
        return response;
    }

    /**
     * 운영자 공지의 제목, 본문, 긴급 배너 노출 여부를 수정한다(FR-NT-01, 03).
     * @throws CustomException UNAUTHORIZED, NOTICE_NOT_FOUND
     */
    @Transactional
    public NoticeResponse update(Long userId, Long noticeId, UpdateNoticeRequest request) {
        requireLogin(userId);
        Notice notice = getNoticeOrThrow(noticeId);
        notice.update(request.title(), request.body(), request.banner());
        cacheEvictor.evictNotices(noticeId);
        return NoticeResponse.from(notice);
    }

    /**
     * 운영자 공지를 물리 삭제한다(FR-NT-01).
     * <p>공지는 다른 도메인에서 참조하지 않아 삭제 후 연결 데이터가 남지 않는다.
     * @throws CustomException UNAUTHORIZED, NOTICE_NOT_FOUND
     */
    @Transactional
    public void delete(Long userId, Long noticeId) {
        requireLogin(userId);
        noticeRepository.delete(getNoticeOrThrow(noticeId));
        cacheEvictor.evictNotices(noticeId);
    }

    private Notice getNoticeOrThrow(Long noticeId) {
        return noticeRepository.findById(noticeId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTICE_NOT_FOUND));
    }

    private User requireUser(Long userId) {
        requireLogin(userId);
        return userService.getUser(userId);
    }

    private static void requireLogin(Long userId) {
        if (userId == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
    }
}

package com.yufesta.domain.notice.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.notice.dto.response.NoticeResponse;
import com.yufesta.domain.notice.entity.Notice;
import com.yufesta.domain.notice.repository.NoticeRepository;
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

    public NoticeService(NoticeRepository noticeRepository) {
        this.noticeRepository = noticeRepository;
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
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTICE_NOT_FOUND));
        return NoticeResponse.from(notice);
    }
}

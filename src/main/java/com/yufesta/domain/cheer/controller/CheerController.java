package com.yufesta.domain.cheer.controller;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.cheer.controller.api.CheerApi;
import com.yufesta.domain.cheer.dto.request.CreateCheerRequest;
import com.yufesta.domain.cheer.dto.response.CheerResponse;
import com.yufesta.domain.cheer.service.AnonymousKeyService;
import com.yufesta.domain.cheer.service.CheerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** 응원 메시지 공개 API. 명세는 CheerApi */
@RestController
public class CheerController implements CheerApi {

    private final CheerService cheerService;
    private final AnonymousKeyService anonymousKeyService;

    public CheerController(CheerService cheerService, AnonymousKeyService anonymousKeyService) {
        this.cheerService = cheerService;
        this.anonymousKeyService = anonymousKeyService;
    }

    @Override
    public ApiResponse<List<CheerResponse>> getCheers(HttpServletRequest request, int size) {
        String anonymousKey = anonymousKeyService.extract(request).orElse(null);
        return ApiResponse.success(cheerService.getCheers(anonymousKey, size));
    }

    @Override
    public ResponseEntity<ApiResponse<CheerResponse>> createCheer(
            HttpServletRequest request,
            HttpServletResponse response,
            CreateCheerRequest cheerRequest
    ) {
        String anonymousKey = anonymousKeyService.resolve(request, response);
        CheerResponse cheer = cheerService.create(anonymousKey, cheerRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(HttpStatus.CREATED, "응원 메시지를 등록했습니다.", cheer));
    }
}

package com.yufesta.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** ALB가 전달한 실제 클라이언트 IP를 속도 제한 키에 사용한다. */
@Component
public class ClientIpResolver {

    /**
     * 운영 ECS는 ALB 보안 그룹에서만 접근되므로 ALB가 추가한 X-Forwarded-For의 첫 IP를 신뢰한다.
     * 로컬처럼 헤더가 없을 때는 서블릿 연결 주소를 사용한다.
     */
    public String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }
}

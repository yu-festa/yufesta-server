package com.yufesta.common.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

final class HttpSessionCleaner {

    private HttpSessionCleaner() {
    }

    // state를 보관한 임시 세션을 제거
    static void invalidate(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}

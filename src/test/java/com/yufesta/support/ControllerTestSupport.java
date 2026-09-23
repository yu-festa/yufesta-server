package com.yufesta.support;

import com.yufesta.common.config.ClockConfig;
import com.yufesta.common.config.CorsConfig;
import com.yufesta.common.security.config.SecurityConfig;
import com.yufesta.common.security.oauth2.OAuth2ProviderUserIdExtractor;
import com.yufesta.domain.user.repository.UserRepository;
import com.yufesta.domain.user.service.UserLoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * @WebMvcTest 공통 지원. 실제 SecurityConfig(인가 규칙·CSRF·401/403 JSON)를 슬라이스에 넣고
 * 보안 설정이 요구하는 협력 빈은 mock으로 채운다. 컨트롤러 테스트는 이 클래스를 상속한다
 */
@ActiveProfiles("test")
@Import({SecurityConfig.class, CorsConfig.class, ClockConfig.class})
@MockitoBean(types = {
        ClientRegistrationRepository.class,
        UserLoginService.class,
        OAuth2ProviderUserIdExtractor.class,
        UserRepository.class
})
public abstract class ControllerTestSupport {

    @Autowired
    protected MockMvc mockMvc;
}

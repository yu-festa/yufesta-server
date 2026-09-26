package com.yufesta.common.config;

import com.yufesta.common.exception.error.ErrorResponse;
import com.yufesta.common.security.config.AuthProperties;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Set;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger 문서 공통 설정: 쿠키 인증 스키마, 전 API 공통 오류 응답, public/admin 그룹.
 * 로그인 필요 여부는 SecurityConfig의 URL 규칙(§7)을 그대로 따라 경로·메서드로 판단하므로 api 인터페이스마다 반복해서 적지 않는다
 */
@Configuration
public class OpenApiConfig {

    public static final String COOKIE_AUTH = "cookieAuth";

    private static final String ERROR_SCHEMA_REF = "#/components/schemas/ErrorResponse";
    private static final String ADMIN_PREFIX = "/api/v1/admin/";
    // 비로그인 쓰기(§7): 응원 메시지 작성·삭제, 서비스 오픈 알림 구독·취소, 로그아웃
    private static final Set<String> PUBLIC_WRITE_PATHS = Set.of(
            "/api/v1/cheers",
            "/api/v1/cheers/{id}",
            "/api/v1/auth/logout",
            "/api/v1/open-notifications/subscriptions"
    );

    @Bean
    public OpenAPI openApi(AuthProperties authProperties) {
        Components components = new Components()
                .addSecuritySchemes(COOKIE_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name(authProperties.cookie().name())
                        .description("소셜 로그인 후 발급되는 HttpOnly 쿠키. 브라우저가 자동으로 보내므로 별도 입력이 없다. "
                                + "쓰기 요청은 GET /api/v1/auth/csrf 후 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 함께 보낸다"));

        return new OpenAPI()
                .info(new Info()
                        .title("YU FESTA API")
                        .version("v1")
                        .description("영남대 2026 가을 대동제 API. 성공 응답은 {status, message, data}, 오류 응답은 {status, code, message, errors[]}. "
                                + "시각은 KST, 오프셋 없는 yyyy-MM-dd'T'HH:mm:ss"))
                .components(components);
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/api/v1/**")
                .pathsToExclude(ADMIN_PREFIX + "**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .pathsToMatch(ADMIN_PREFIX + "**")
                .build();
    }

    // 모든 그룹의 모든 operation에 공통 오류 응답과 쿠키 인증 표시를 붙인다. 인터페이스가 직접 적은 응답(409 등)은 덮어쓰지 않는다.
    // 스키마 등록도 여기서 한다: springdoc이 컨트롤러를 훑어 components.schemas를 새로 만들기 때문에 OpenAPI 빈에 미리 넣은 스키마는 사라진다
    @Bean
    public GlobalOpenApiCustomizer commonResponsesCustomizer() {
        return openApi -> {
            registerErrorSchemas(openApi);
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) ->
                    pathItem.readOperationsMap().forEach((method, operation) -> decorate(path, method, operation)));
        };
    }

    // ErrorResponse는 컨트롤러 반환 타입이 아니라 예외 핸들러에서만 쓰여 자동 등록되지 않는다. 참조 타입(ValidationError)까지 함께 넣는다
    private static void registerErrorSchemas(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        ResolvedSchema resolved = ModelConverters.getInstance()
                .resolveAsResolvedSchema(new AnnotatedType(ErrorResponse.class).resolveAsRef(false));
        resolved.referencedSchemas.forEach(openApi.getComponents()::addSchemas);
        openApi.getComponents().addSchemas("ErrorResponse", resolved.schema);
    }

    private static void decorate(String path, PathItem.HttpMethod method, Operation operation) {
        boolean admin = path.startsWith(ADMIN_PREFIX);
        boolean loginRequired = admin || requiresLogin(path, method);

        putIfAbsent(operation, "400", "INVALID_INPUT_VALUE, INVALID_REQUEST_BODY, MISSING_REQUEST_PARAMETER, INVALID_PARAMETER_TYPE");
        if (loginRequired) {
            putIfAbsent(operation, "401", "UNAUTHORIZED (로그인 필요)");
            operation.addSecurityItem(new SecurityRequirement().addList(COOKIE_AUTH));
        }
        if (admin) {
            putIfAbsent(operation, "403", "FORBIDDEN (STAFF/OWNER 아님)");
        }
        if (path.contains("{")) {
            putIfAbsent(operation, "404", "RESOURCE_NOT_FOUND 계열 (도메인별 *_NOT_FOUND)");
        }
        putIfAbsent(operation, "500", "INTERNAL_SERVER_ERROR");
    }

    // SecurityConfig §7 규칙의 요약: 쓰기는 로그인(비로그인 쓰기 예외 제외), 읽기는 /me로 끝나는 본인 자원만 로그인
    private static boolean requiresLogin(String path, PathItem.HttpMethod method) {
        if (method != PathItem.HttpMethod.GET) {
            return !PUBLIC_WRITE_PATHS.contains(path);
        }
        return path.endsWith("/me");
    }

    private static void putIfAbsent(Operation operation, String status, String description) {
        if (operation.getResponses().containsKey(status)) {
            return;
        }
        operation.getResponses().addApiResponse(status, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA_REF)))));
    }
}

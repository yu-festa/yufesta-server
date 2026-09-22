# YU FESTA 서버 — Claude 작업 지침

영남대 2026 가을 대동제(10/2 하루) 축제 웹 서비스의 API 서버. Spring Boot 4 + MySQL 8.
요구사항 원문은 `docs/srs.md`(SRS v1.6), 스키마 원문은 `docs/erd.sql`·`docs/erd.md`(ERD v1.2)다. `docs/`는 저장소에 포함되며 변경은 PR로 리뷰한다.
이 문서와 원문이 다르면 원문을 따르고 이 문서를 고친다. 원문에 없는 동작은 만들지 않는다.

## 0. 작업 순서

1. 기능을 만들기 전에 `docs/srs.md`에서 해당 FR 번호를 찾아 읽는다.
2. 테이블·컬럼은 `docs/erd.sql`을 따른다. 스키마를 바꾸면 `docs/erd.sql`·`docs/erd.md`를 함께 고친다(Flyway 도입 후에는 마이그레이션도). 새 테이블의 초기 데이터는 `db/dev/data.sql`에 추가한다.
3. 새 코드는 `common`, `domain/user`, `domain/auth`의 기존 스타일을 따른다. 충돌 시 이 문서 > 기존 코드.
4. 변경 후 `./gradlew test` 통과. 컴파일 경고를 새로 만들지 않는다.
5. 모호하면 구현하지 말고 질문한다. 추측으로 요구사항을 확장하지 않는다.
6. 커밋·푸시·브랜치 생성은 사람이 직접 한다. Claude는 git 쓰기 명령(add, commit, push, branch, stash 등)을 실행하지 않는다. 대신 작업 단위 하나(기능·리팩터링·설정)가 끝나고 `./gradlew test`가 통과하면 "지금 커밋할 시점"이라고 알리고, 바뀐 파일 목록과 커밋 메시지 초안(`feat|fix|chore|refactor|test|docs: 한국어 요약`)을 제시한다. 한 커밋에 한 가지 변경만 담기도록 작업을 끊는다.

## 1. 스택과 버전 (build.gradle 기준)

| 항목 | 값 |
|---|---|
| Java | 17 (toolchain). 21 문법(패턴 매칭 switch, 가상 스레드 등) 사용 금지 |
| Spring Boot | 4.1.1 (`io.spring.dependency-management` 1.1.7). Security·JPA·Validation·Actuator·OAuth2 Client는 Boot BOM 관리, 버전 명시 없음 |
| 웹 | `spring-boot-starter-webmvc` (Boot 4에서 `-web` 대신 이 이름) |
| Lombok | Boot BOM 관리(명시 버전 없음). `compileOnly` + `annotationProcessor` |
| DB | MySQL 8.4 (`compose.yml`), 드라이버 `com.mysql:mysql-connector-j`. 테스트는 H2 `MODE=MySQL` |
| 문서 | `springdoc-openapi-starter-webmvc-ui` 3.0.3, dev 프로필에서만 노출 |
| 빌드 | Gradle 9.7.1 wrapper. 항상 `./gradlew` 사용 |

버전은 `build.gradle`과 `gradle-wrapper.properties`에 적힌 것만 기록한다. 바뀌면 이 표를 같은 커밋에서 갱신한다. BOM이 관리하는 라이브러리 버전을 코드나 문서에 직접 적지 않는다.

Boot 4에서 달라진 것. 기억이나 검색 결과에 있는 Boot 3 코드를 그대로 쓰지 말 것:

- 테스트 목 빈: `@MockBean`·`@SpyBean` 삭제됨 → `@MockitoBean`, `@MockitoSpyBean` (`org.springframework.test.context.bean.override.mockito`)
- 테스트 슬라이스 스타터 분리: `spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-security-test` (build.gradle에 있음)
- Jackson 3: `ObjectMapper`는 `tools.jackson.databind.ObjectMapper`. 어노테이션(`@JsonFormat`, `@JsonProperty`, `@JsonInclude`)은 그대로 `com.fasterxml.jackson.annotation`
- Security: 람다 DSL만. `requestMatchers(...)`는 PathPattern 기반이며 `AntPathRequestMatcher`는 없다
- `@Nullable`은 JSpecify(`org.jspecify.annotations.Nullable`)
- Hibernate: 부울 컬럼 기본 매핑이 `bit`이므로 ERD의 `TINYINT(1)`과 맞추려면 `hibernate.type.preferred_boolean_jdbc_type=TINYINT` 설정이 필요하다(5장)

## 2. 실행·검증

```bash
cp .env.example .env            # 값 채우기 (JWT_SECRET 32바이트 이상)
docker compose up -d mysql      # 앱은 IDE나 bootRun으로. compose의 app 서비스는 통합 확인용
./gradlew bootRun               # 기본 프로필 dev
./gradlew test                  # H2, 프로필 test
./gradlew build
```

- Swagger: http://localhost:8080/swagger-ui/index.html , 스펙 `/v3/api-docs`
- 헬스: `/actuator/health` (ALB 헬스체크 대상, 상세 비노출 유지)
- 로그인 시작: 브라우저에서 `GET /oauth2/authorization/{kakao|google}?redirect=/match/apply` (fetch가 아니라 페이지 이동)
- 프로필: `application.yml`(공통) / `-dev`(로컬·compose) / `-prod`(ECS) / `-test`(H2). 시크릿은 전부 환경변수. yml에 실제 값 커밋 금지
- dev 초기 데이터: 기동 시 `db/dev/data.sql`이 `app_settings` 초기값을 넣는다(있는 행은 건너뜀). `admin.allowlist`는 비어 있으므로 운영자 테스트는 `UPDATE app_settings SET setting_value='KAKAO:<providerUserId>' WHERE setting_key='admin.allowlist';` 후 다시 로그인(최초 로그인 시 role 부여)

## 3. 패키지 구조

```
com.yufesta
├─ common                         도메인에 속하지 않는 것만
│  ├─ entity        BaseTimeEntity(createdAt, updatedAt, JPA Auditing)
│  ├─ exception     CustomException, GlobalExceptionHandler, error/{ErrorCode, ErrorResponse, ValidationError}
│  ├─ response      ApiResponse<T>
│  ├─ logging       RequestLoggingFilter
│  └─ security      config/{SecurityConfig, AuthProperties}, jwt/*, oauth2/*
└─ domain
   └─ <도메인>
      ├─ controller
      │  ├─ api                  <Domain>Api, Admin<Domain>Api  ← Swagger 명세 인터페이스
      │  ├─ <Domain>Controller   공개 API, implements <Domain>Api
      │  └─ Admin<Domain>Controller  운영자 API (/api/v1/admin/**)
      ├─ service
      ├─ repository
      ├─ entity
      ├─ enums
      ├─ scheduler              @Scheduled 진입점(match의 RoundScheduler). 시각 판단만 하고 전이는 서비스를 부른다
      └─ dto
         ├─ request              *Request (record)
         └─ response             *Response (record)
```

- 새 공통 설정(Clock, CORS, OpenAPI, Redis, S3 등)은 `common/config`, SSE 기반은 `common/sse`에 둔다.
- 도메인 ↔ 테이블: `user`(users) · `auth`(테이블 없음) · `appsetting`(app_settings) · `match`(match_rounds, applications, application_tags, matches, blocks) · `timetable`(timetable_slots) · `place`(places, place_events) · `club`(clubs) · `notice`(notices) · `lostitem`(lost_items) · `cheer`(cheers) · `report`(content_reports) · `nickname`(테이블 없음) · `moderation`(콘텐츠 필터, 테이블 없음) · `photo`(festival_photos)
- 운영자 API는 별도 `admin` 패키지를 만들지 않고 각 도메인의 `Admin<Domain>Controller`에 둔다. URL은 `/api/v1/admin/<도메인>`.

## 4. 계층 규칙

**Controller**
- 하는 일: 요청 바인딩, `@Valid`, 로그인 사용자 추출, 서비스 호출, `ApiResponse`로 감싸기. 이게 전부다. 조건문·계산·엔티티 접근 금지.
- `@RestController`. `@RequestMapping("/api/v1/...")`과 메서드 매핑은 api 인터페이스 쪽에 둔다. 컨트롤러 메서드는 `@Override`만 붙인다.
- 응답: 성공은 `ApiResponse.success(data)` / 생성은 `ResponseEntity.status(CREATED).body(ApiResponse.of(HttpStatus.CREATED, "...", data))` / 본문 없는 성공은 `@ResponseStatus(NO_CONTENT)` + `void`.
- 엔티티를 절대 반환하지 않는다. 항상 response record.
- 로그인 사용자: `@AuthenticationPrincipal Long userId`. principal 타입이 `Long`이며 비로그인이면 `null`이 들어온다. 로그인 필수 여부는 SecurityConfig의 URL 규칙으로 강제하고, 서비스에서도 `userId == null`이면 `UNAUTHORIZED`로 방어한다.

**api 인터페이스 (Swagger 명세)**
- 매핑 어노테이션(`@GetMapping` 등)과 파라미터 어노테이션(`@RequestBody`, `@Valid`, `@PathVariable`, `@RequestParam`, `@AuthenticationPrincipal`)까지 전부 인터페이스에 쓴다. Spring MVC는 인터페이스의 매핑·파라미터 어노테이션을 상속한다. 구현체 시그니처는 인터페이스와 동일하게, 어노테이션은 반복하지 않는다(드리프트 방지).
- 클래스에 `@Tag(name, description)`, 메서드에 `@Operation(summary, description)`. `description`에 FR 번호와 인증 필요 여부를 적는다.
- 이름 충돌 주의: springdoc의 `io.swagger.v3.oas.annotations.responses.ApiResponse`와 우리 `common.response.ApiResponse`가 같은 이름이다. api 인터페이스에서는 swagger 쪽을 FQN(`@io.swagger.v3.oas.annotations.responses.ApiResponse(...)`)으로 쓴다.
- 공통 오류(400/401/403/404/500)는 `OpenApiConfig`의 `OperationCustomizer`로 전 API에 일괄 추가한다. 인터페이스에는 도메인 고유 오류(예: 409 인스타 ID 중복)만 적는다.
- `@AuthenticationPrincipal` 파라미터에는 `@Parameter(hidden = true)`.
- 쿠키 인증 스키마: `OpenApiConfig`에 `@SecurityScheme(name = "cookieAuth", type = APIKEY, in = COOKIE, paramName = "access_token")`, 로그인 필요 API에 `@SecurityRequirement(name = "cookieAuth")`.

```java
@Tag(name = "Notice", description = "공지 (FR-NT)")
@RequestMapping("/api/v1/notices")
public interface NoticeApi {

    @Operation(summary = "공지 목록", description = "최신순. 로그인 불필요. FR-NT-01")
    @GetMapping
    ApiResponse<List<NoticeResponse>> getNotices(
            @Parameter(description = "가져올 개수(1~50)") @RequestParam(defaultValue = "20") int size
    );

    @Operation(summary = "공지 상세", description = "FR-NT-01")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOTICE_NOT_FOUND")
    @GetMapping("/{noticeId}")
    ApiResponse<NoticeResponse> getNotice(@PathVariable Long noticeId);
}

@RestController
public class NoticeController implements NoticeApi {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @Override
    public ApiResponse<List<NoticeResponse>> getNotices(int size) {
        return ApiResponse.success(noticeService.getNotices(size));
    }

    @Override
    public ApiResponse<NoticeResponse> getNotice(Long noticeId) {
        return ApiResponse.success(noticeService.getNotice(noticeId));
    }
}
```

**Service**
- `@Service`, 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드에만 `@Transactional`. 트랜잭션 경계는 서비스 public 메서드 하나.
- 다른 도메인의 Repository를 주입하지 않는다. 다른 도메인이 필요하면 그 도메인의 Service를 호출한다(예: `match`가 공연 슬롯 존재를 확인할 때 `TimetableService.getSlot(id)`).
- 엔티티 → response record 변환은 서비스 안(트랜잭션 안)에서 끝낸다. `open-in-view=false`라 컨트롤러에서 지연 로딩하면 `LazyInitializationException`이다.
- 규칙 위반은 전부 `throw new CustomException(ErrorCode.X)`. `IllegalArgumentException`·`IllegalStateException`을 컨트롤러 밖으로 흘리지 않는다.
- 입력 정규화(인스타 ID `@` 제거·소문자, 공백 trim)는 서비스에서. 검증 어노테이션은 형식만 본다.
- 생성자 주입만. 필드 주입(`@Autowired` 필드) 금지. `@RequiredArgsConstructor` + `private final`은 허용.
- 현재 시각은 `Clock` 빈을 주입받아 `LocalDateTime.now(clock)`. 도메인 코드에 `LocalDateTime.now()`·`Instant.now()`·`System.currentTimeMillis()` 직접 호출 금지(5장).

**Repository**
- `JpaRepository<Entity, Long>`. 쿼리 메서드 이름이 길어지면 `@Query` JPQL로. 네이티브 쿼리는 매칭 배치 집계처럼 JPQL로 불가능한 경우만, 주석에 이유를 쓴다.
- 조회 결과가 목록이고 연관을 쓰면 `JOIN FETCH` 또는 `@EntityGraph`, 아니면 DTO 프로젝션. N+1을 만들지 않는다.
- 벌크 UPDATE/DELETE는 `@Modifying(clearAutomatically = true)`.
- 존재 확인은 `existsBy...`, 단건은 `Optional` 반환 후 서비스에서 `orElseThrow(() -> new CustomException(X_NOT_FOUND))`.

**Entity**
- `BaseTimeEntity` 상속(모든 엔티티). ERD v1.2부터 모든 테이블에 `created_at`·`updated_at`이 있다(`application_tags` 제외).
- `@Getter`, `@NoArgsConstructor(access = PROTECTED)`. `@Setter`·`@Data`·`@ToString`·`@EqualsAndHashCode` 금지.
- 생성은 `@Builder`를 붙인 private 생성자 또는 정적 팩토리. 상태 변경은 의도가 드러나는 메서드(`cancel(now)`, `hide()`, `markResolved()`)로만.
- 연관은 `@ManyToOne(fetch = LAZY)` + `@JoinColumn(name = "<ERD 컬럼명>")`. `@OneToMany` 양방향은 꼭 필요할 때만, `cascade`·`orphanRemoval`은 같은 애그리거트 안(예: Application ↔ 태그)에서만.
- 열거형은 `domain/<d>/enums`에 두고 `@Enumerated(EnumType.STRING)`. DB에는 enum 이름(대문자)이 저장된다. ERD v1.2의 허용값 표기도 대문자다.
- `application_tags`는 엔티티가 아니라 `Application`의 `@ElementCollection(fetch = LAZY) @CollectionTable(name = "application_tags") Set<String> tags`.
- 컬럼명은 ERD와 동일하게. 필드명 camelCase → 컬럼 snake_case 자동 변환을 믿는다(`users.matching_blocked_at`처럼 ERD v1.2는 기존 엔티티 이름을 따랐다).
- 유니크 제약은 DDL에도 있고 `@Table(uniqueConstraints)`에도 선언한다(테스트 H2가 같은 제약을 갖도록).
- `TEXT` 컬럼은 `@Column(columnDefinition = "TEXT")`, 좌표는 `BigDecimal` + `precision = 10, scale = 7`, 점수는 `BigDecimal`.

```java
@Getter
@Entity
@Table(name = "notices")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private boolean isBanner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Builder
    private Notice(String title, String body, boolean isBanner, User createdBy) {
        this.title = title;
        this.body = body;
        this.isBanner = isBanner;
        this.createdBy = createdBy;
    }

    public void update(String title, String body, boolean isBanner) {
        this.title = title;
        this.body = body;
        this.isBanner = isBanner;
    }
}
```

**DTO (record)**
- 요청·응답 모두 `record` + Lombok `@Builder`. 클래스 DTO 금지. 이름은 `<동사><도메인>Request`(`ApplyMatchRequest`, `UpdateNoticeRequest`), `<도메인>Response`, `<도메인>SummaryResponse`.
- 요청 record 컴포넌트에 Bean Validation(`@NotBlank`, `@Size`, `@Pattern`, `@NotNull`)과 `@Schema(description, example)`를 붙인다. 컨트롤러 파라미터에는 `@Valid`.
- 응답 record는 `public static XResponse from(Entity e)` 정적 팩토리를 갖는다. 목록은 `ApiResponse<List<X>>` 또는 커서 페이지 record `PageResponse<X>(items, nextCursor, hasNext)`(`common/response`에 추가).
- 공개 응답에 `userId`, `provider`, `providerUserId`, 신청 닉네임 등 식별 정보를 절대 넣지 않는다. 작성자 표시는 `displayName`과 `isMine`(boolean)만 (NFR-SC-07).
- 시각은 `LocalDateTime`을 `yyyy-MM-dd'T'HH:mm:ss` 그대로 내보낸다. 모든 시각은 KST이며 오프셋을 붙이지 않는다. 프론트는 `serverNow`와 같은 기준으로 계산한다.
- JSON은 camelCase, enum은 이름 문자열 그대로. 예외: `AgeBand`는 ERD 저장값(`19-21`, `28+`)을 JSON에도 쓴다(`@JsonValue`).

```java
@Builder
public record CreateNoticeRequest(
        @Schema(description = "제목", example = "우천 시 공연 안내") @NotBlank @Size(max = 100) String title,
        @Schema(description = "본문") @NotBlank String body,
        @Schema(description = "홈 상단 긴급 배너 노출 여부", example = "false") boolean isBanner
) {
}

@Builder
public record NoticeResponse(Long id, String title, String body, boolean isBanner, LocalDateTime createdAt) {

    public static NoticeResponse from(Notice notice) {
        return NoticeResponse.builder()
                .id(notice.getId())
                .title(notice.getTitle())
                .body(notice.getBody())
                .isBanner(notice.isBanner())
                .createdAt(notice.getCreatedAt())
                .build();
    }
}
```

## 5. 데이터·트랜잭션·시간

**스키마 관리 (Flyway는 인스타팅 완료 후·인프라 작업 전에 도입)**
- 도입 전까지: dev는 `ddl-auto: update`, prod는 `validate`. dev 초기 데이터는 `src/main/resources/db/dev/data.sql`(`INSERT IGNORE`, `spring.sql.init`이 dev 프로필에서만 실행). 엔티티가 ERD에서 벗어나면 `docs/erd.sql`을 같은 커밋에서 고친다.
- 도입 시 아래를 적용한다.
- `spring-boot-starter-flyway` + `org.flywaydb:flyway-mysql` 추가(둘 다 Boot BOM 관리). `src/main/resources/db/migration/V1__init.sql`은 `docs/erd.sql`에서 만든다.
- dev·prod 모두 `spring.jpa.hibernate.ddl-auto: validate`. `update`·`create`는 금지(테스트 프로필의 H2 `create-drop`만 예외, test 프로필은 `spring.flyway.enabled: false`).
- 스키마 변경은 새 `V{n}__{설명}.sql`만 추가. 적용된 마이그레이션 파일은 수정하지 않는다.
- `validate`가 통과하도록 필요한 설정: `spring.jpa.properties.hibernate.type.preferred_boolean_jdbc_type: TINYINT`(TINYINT(1) ↔ boolean), TEXT는 `columnDefinition`, enum 컬럼은 VARCHAR(ERD v1.2는 `gender`도 VARCHAR(1)). `CHAR` 타입은 쓰지 않는다.

**동시성**
- 중복 방지는 "선검사 + DB 유니크 제약" 이중으로. 유니크 위반 `DataIntegrityViolationException`은 서비스에서 잡아 도메인 `CustomException`(409)으로 바꾼다. 선검사만 믿지 않는다(발표 직전 동시 신청).
- 상태 전이(회차 open→closed→published, 신고 누적 제재)는 `@Lock(LockModeType.PESSIMISTIC_WRITE)` 조회 후 변경하거나, `UPDATE ... WHERE status = :expected` 조건부 갱신으로 원자적으로 처리한다.
- 카운터(`report_count`)는 `UPDATE ... SET report_count = report_count + 1`로 증가시킨다. 읽고 더해서 저장하지 않는다.
- 스케줄 작업은 인스턴스가 2개 이상이어도 한 번만 실행되어야 한다. 회차 전이는 서비스의 회차 행 `PESSIMISTIC_WRITE` 잠금 + 상태 전이 검사(`MatchRound.transition`)가 이를 보장하므로 분산 락 없이 `@Scheduled`만 쓴다. 늦게 온 인스턴스는 `MATCH_ROUND_INVALID_STATUS`를 받고 건너뛴다. 작업은 멱등하게(`executed_at`·`published_at`이 있으면 스킵). 분산 락(ShedLock·Redis SET NX·Redisson)은 헛수고 한 번을 줄일 뿐 정확성에 기여하지 않아 도입하지 않았다(13장 1).

**시간 (타임존 사고 방지)**
- `ClockConfig`에 `Clock.system(ZoneId.of("Asia/Seoul"))` 빈. 서비스는 `Clock` 주입. 테스트는 `Clock.fixed(...)`.
- JPA Auditing도 같은 Clock을 쓰도록 `DateTimeProvider` 빈을 등록하고 `@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")`.
- 컨테이너 JVM 기본 타임존은 UTC다. Dockerfile ENTRYPOINT에 `-Duser.timezone=Asia/Seoul`, compose·RDS의 MySQL은 `TZ=Asia/Seoul`(파라미터 그룹 `time_zone=Asia/Seoul`). JDBC URL의 `serverTimezone=Asia/Seoul` 유지.
- 회차 마감·발표 판정은 반드시 서버 Clock 기준. 클라이언트가 보낸 시각을 믿지 않는다. 홈·상태 API는 `serverNow`를 함께 내려준다(FR-MT-52).

```java
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock));
    }
}
```

**삭제 정책**
- 게시물은 `is_hidden`, 신청은 `canceled_at` 소프트 삭제. 물리 삭제는 (1) 신고 누적 제재 시 해당 회차 신청 삭제(FR-MT-41), (2) 축제 후 개인정보 파기 배치(`role = 'USER'`인 users만, CASCADE) 두 곳뿐.

## 6. 예외·응답

- 성공 본문은 `ApiResponse<T>{status, message, data}`, 오류 본문은 `ErrorResponse{status, code, message, errors[]}`. 이 두 형태 외의 응답을 만들지 않는다.
- `ErrorCode`는 `<도메인>_<상황>` 이름으로 추가하고 도메인별 주석 블록에 모은다. 예: `MATCH_ROUND_NOT_OPEN`(409), `APPLICATION_ALREADY_EXISTS`(409), `APPLICATION_INSTAGRAM_DUPLICATE`(409), `NOTICE_NOT_FOUND`(404), `CONTENT_NOT_ALLOWED`(400), `RATE_LIMITED`(429), `USER_WRITE_BANNED`(403), `USER_MATCHING_BLOCKED`(403), `PHOTO_TOO_LARGE`(413).
- HTTP 상태 기준: 입력 형식 오류 400 / 미로그인 401 / 권한·제재 403 / 없음 404 / 상태 충돌·중복 409 / 속도 제한 429. 규칙 위반을 500으로 내지 않는다.
- 메시지는 한국어, 사용자에게 보여줄 문장. 스택·SQL·내부 식별자를 메시지에 넣지 않는다. 필터에 걸린 내용은 사유를 알려주지 않고 "등록할 수 없는 내용이에요"(FR-CH-03).
- 로그: 5xx만 `error`+스택. 4xx는 `warn` 없이 `info` 이하. 토큰·쿠키·인스타 ID·providerUserId는 어떤 레벨에도 남기지 않는다.
- 인증 실패 401·인가 실패 403은 SecurityConfig의 `exceptionHandling`에서 `ErrorResponse` JSON으로 낸다. 로그인 페이지 리다이렉트 금지(API 서버다).
- `GlobalExceptionHandler` 매핑: `HttpMessageNotReadableException`→INVALID_REQUEST_BODY, `MissingServletRequestParameterException`→MISSING_REQUEST_PARAMETER, `MethodArgumentTypeMismatchException`→INVALID_PARAMETER_TYPE, `HttpRequestMethodNotSupportedException`→METHOD_NOT_ALLOWED, `ConstraintViolationException`·`HandlerMethodValidationException`(Spring 6.1+ 컨트롤러 파라미터 제약)→INVALID_INPUT_VALUE, `MaxUploadSizeExceededException`→PHOTO_TOO_LARGE. 컨트롤러 밖(필터)에서 난 예외는 `ErrorResponseController`(`/error`)가 같은 `ErrorResponse`로 낸다.

## 7. 인증·인가

- 인증 상태 = HttpOnly·Secure·SameSite=Lax 쿠키 `access_token`의 JWT(uid, iat, exp). Authorization 헤더·localStorage 방식은 만들지 않는다(FR-AUTH-07). JWT에 개인정보를 넣지 않는다.
- 소셜 로그인은 Spring Security OAuth2 Client가 서버에서 코드 교환. 동의 항목은 식별자만(카카오 `id`, 구글 `sub`). 카카오·구글 계정은 연동하지 않는다(별도 회원).
- 운영자 role은 로그인 시 `app_settings.admin.allowlist`로만 부여된다. role을 바꾸는 API·엔드포인트를 만들지 않는다. STAFF는 콘텐츠·신고 관리, OWNER는 회차 발표 확정·설정 변경까지.
- SecurityConfig 목표 상태(순서가 의미 있음, 먼저 매칭되는 규칙이 이긴다):

```
permitAll   : /oauth2/**, /login/**, /actuator/health, /swagger-ui/**, /v3/api-docs/** (dev), GET /api/v1/auth/csrf, POST /api/v1/auth/logout
OWNER       : /api/v1/admin/match/rounds/*/publish, /api/v1/admin/settings/**
STAFF|OWNER : /api/v1/admin/**
permitAll   : GET /api/v1/**            (읽기 비로그인, SSE 포함)
permitAll   : POST /api/v1/cheers, DELETE /api/v1/cheers/*   (응원 메시지는 비로그인 작성, 익명 키로 관리)
authenticated: 나머지 /api/v1/**        (쓰기)
denyAll     : anyRequest
```

- CSRF는 켜져 있다(`CookieCsrfTokenRepository`). 쓰기 요청은 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 보내야 하며, 프론트는 최초 1회 `GET /api/v1/auth/csrf`를 호출한다. 이 규칙을 API 명세에 적는다.
- 작성 금지(`write_banned_at`)·매칭 차단(`matching_blocked_at`)은 토큰이 아니라 쓰기 시점에 DB로 확인한다(FR-AUTH-08).
- 운영자 API 호출은 접근 로그에 userId가 남아야 한다(NFR-SC-05). `RequestLoggingFilter`가 MDC의 `userId`를 함께 찍도록 확장한다.

## 8. 도메인 불변 규칙 (SRS 요약 — 위반 금지)

**인스타팅 `match`** (FR-MT, 부록 A)
- 회차는 2행(seq 1·2). 상태 `SCHEDULED → OPEN → CLOSED → PUBLISHED`. `close_at = publish_at − 10분`, 2회차 `open_at = 1회차 publish_at`. 열린 회차는 항상 하나. 초기값: 1회차 open_at = 사전 오픈(며칠 전), 발표 10/2 16:00 / 2회차 발표 20:00. 시각은 `match_rounds` 행에 있고 운영자가 수정한다. 코드에 시각을 하드코딩하지 않는다.
- 신청·수정·취소는 회차가 OPEN이고 `now < close_at`일 때만. 그 외 `MATCH_ROUND_NOT_OPEN`.
- 회원당 회차 1건(`uk_app_user_round`), 인스타 ID 회차 내 유니크(`uk_app_round_insta`). 인스타 ID 정규화: trim → 선행 `@` 제거 → 소문자 → `^[a-z0-9._]{1,30}$` 검사. 닉네임 2~8자, 성별 M/F 필수, 태그 ≤3(고정 10개 목록 외 거부), 소개 ≤40자.
- 동의 없이는 저장 불가: `terms_version`, `privacy_version`, `age_confirmed=1`, `agreed_at` 기록(FR-MT-11·12).
- `matching_blocked_at`이 있는 회원은 신청 거부(`USER_MATCHING_BLOCKED`).
- **보고 싶은 공연(FR-MT-05)**: `slot.start_at >= round.publish_at`인 공연만 허용. 신청·수정 시 검증하고, 배치에서도 재검증해 위반 슬롯은 점수 계산에서 제외(타임테이블 변경 대비).
- **이월 모델(FR-MT-03)**: 회차 발표 트랜잭션에서 미매칭 신청을 다음 회차 행으로 복사(`entry_type = CARRIED`, `source_application_id` = 원본, 태그 포함, `wanted_slot_id`는 FR-MT-05 조건을 만족할 때만 유지). 1회차 매칭자의 "2회차도 참여"도 같은 복사(`REJOIN`, `POST /api/v1/match/applications/rejoin`). 이미 직접 신청한 사용자는 `uk_app_user_round`로 건너뛴다. `join_next_round` 컬럼은 없다. 복사 후 사용자는 2회차 신청 수정 화면(`PATCH /applications/me`)에서 내용을 확인·수정한다. 프론트는 재참여 201 응답 직후와 이월 안내(FR-MT-35)에서 이 화면을 미리 채운 상태로 연다. 타임테이블 연동 시 복사되는 `wanted_slot_id`가 조건을 어기면 `null`로 비우고 응답에 재선택 필요 표시를 넣는다.
- 배치 풀 = `round_id = 해당 회차 AND canceled_at IS NULL AND users.matching_blocked_at IS NULL`. 이전 회차를 조회하지 않는다.
- 점수 = 공통 태그 × `match.weight.tag` + 같은 공연 × `match.weight.slot` + 나이대 동일 × `match.weight.age_same` 또는 인접 × `match.weight.age_adjacent`. 가중치는 `AppSettingReader`로 읽는다. 하드코딩 금지.
- 제외: 차단 관계(양방향), 이전 회차에 이미 매칭된 쌍. 이성 간에만.
- 1차 1:1 그리디(점수 내림차순, 동점은 신청 id 오름차순으로 결정적이게). 2차: 다수 측 잔여자를 소수 측 전원에게 라운드 로빈으로 최대 N명까지. N = `match.max_partners`(기본 3). 회차마다 `min(상한, ceil(다수/소수))`로 계산하는 동적 N은 제안 단계(SRS 8.2 미결 12)이므로 결정 전에는 구현하지 않는다. 다수 측 우선순위: `entry_type = CARRIED` > 신규.
- `matches`는 방향성 2행(A→B, B→A) 저장. 결과 조회는 `application_id = 내 신청` 한 번으로.
- 발표 전에는 `matches`가 있어도 API가 절대 반환하지 않는다. 기준은 `match_rounds.published_at IS NOT NULL`(FR-MT-04). 결과는 본인 것만(FR-MT-31), 카드는 점수 내림차순.
- 매칭 엔진은 스프링에 의존하지 않는 순수 클래스(`MatchingEngine`)로 만들고 단위 테스트로 성비·차단·이전 회차·N 상한 케이스를 고정한다. 500명 배치 30초 이내(NFR-PF-03).
- 마감·발표 스케줄: `close_at`에 상태 CLOSED + 배치 실행 + `executed_at`, `publish_at`에 PUBLISHED + `published_at`. 실패 시 운영자 수동 실행 API 필수(NFR-AV-03). 다중 인스턴스에서 한 번만 실행(5장). 구현은 `RoundScheduler`: 10초마다 seq 순으로 시각이 지난 첫 단계 하나만 실행(한 tick에 한 단계, 발표가 다음 회차를 열기 때문). 실패는 로그만 남기고 다음 tick이 재시도. `app.scheduler.enabled`(test 프로필 false, 로컬 `SCHEDULER_ENABLED`)로 끈다.
- 신고(`blocks`): 신고자–대상 쌍 1건(`uk_blocks_pair`). 같은 트랜잭션에서 대상 신고 수 ≥ `report.block_threshold`면 `users.matching_blocked_at` 설정 + 대상의 현재 회차 신청 삭제. 신고자 결과 화면에서는 대상 카드를 즉시 제외. 신고 사실을 대상에게 노출하지 않는다. 제재 판정은 `MatchReportService.applySanction` 한 곳: 운영자 `CONFIRM`이 있거나 유효 신고 수(`DISMISS` 제외) ≥ 임계면 차단, 아니면 해제(FR-MT-42). 대상 회원 행을 `PESSIMISTIC_WRITE`로 먼저 잠그고 `READ_COMMITTED`로 집계한다. 현재 회차가 `CLOSED`(배치 후)면 `matches`가 참조하므로 신청을 지우지 않고 풀 조건(`matching_blocked_at IS NULL`)이 거른다. 삭제된 신청은 해제돼도 복구하지 않는다.
- 홈 블록: `GET /api/v1/match/summary` 한 번으로 `serverNow`, 현재·다음 회차(seq·status·openAt·closeAt·publishAt), 신청자 수, 로그인 시 내 상태(NONE/APPLIED/MATCHED/UNMATCHED, 다음 회차 신청 존재 여부).
- SSE는 매칭 상태 화면만(`GET /api/v1/sse/match`). 이벤트: `applicant-count`, `round-closed`, `round-published`(로그인 연결에만). keepalive 25초. 다중 인스턴스 팬아웃은 Redis Pub/Sub. 발표 이벤트는 "결과를 다시 조회하라"는 신호만 보내고 결과 데이터를 SSE로 싣지 않는다.

**타임테이블 `timetable`** (FR-TT)
- 공개 응답에 계산 필드 포함: `effectiveStartAt = start_at + delay_minutes`, `isLive`, `isChanged(changed_from_start != null)`. LIVE 판정: `is_live_override = 1`인 슬롯이 있으면 그것만, 없으면 `effectiveStartAt ≤ now < end_at + delay`.
- 운영자 시간 변경 시 최초 1회만 `changed_from_start`에 원래 시각을 기록(덮어쓰지 않음). override는 한 번에 한 슬롯만(다른 슬롯은 해제).

**지도 `place`** (FR-MAP)
- 카테고리 `STAGE/BOOTH/TOILET/AMENITY/INFO`. 공개 목록은 `is_active = 1`만. 거리 계산은 클라이언트(위치는 서버에 오지 않는다, NFR-SC-06). 화장실 목록 기본 정렬은 이름순.

**라인업 `club`**, **공지 `notice`**, **축제 사진 `photo`**
- 등록·수정은 운영자만. 공개 목록은 `sort_order`, 공지는 최신순. 긴급 배너는 `is_banner = 1` 중 최신 1건.
- 사진 업로드: multipart, 장당 10MB 이하, 다중. 서버가 긴 변 1600px 리사이즈본과 썸네일을 만들고 원본은 별도 보관(FR-PH-05). EXIF 회전 반영. S3 키 `photos/{yyyyMMdd}/{uuid}-{original|1600|thumb}.jpg`. 응답 URL은 CloudFront 도메인.

**분실물 `lostitem`, 응원 메시지 `cheer`** (FR-LF, FR-CH, FR-AN, FR-CF)
- 분실물: 읽기 비로그인, 쓰기 로그인. `write_banned_at`이면 `USER_WRITE_BANNED`. 회원당 분당 1건 속도 제한.
- 응원 메시지: **읽기·쓰기 모두 비로그인**(FR-CH-02). 작성자를 uid로 기록하지 않는다(로그인 상태여도). 첫 작성 시 서버가 익명 키 쿠키(`anon_key`, UUID, HttpOnly, 30일)를 발급하고 DB에는 SHA-256 해시(`writer_key_hash`)만 저장. 속도 제한은 익명 키당 분당 1건 + IP당 분당 10건(설정값). 삭제(FR-CH-04, C)와 `isMine`은 익명 키 일치로 판정. 신고는 로그인 필요.
- 저장 시 서버가 닉네임을 생성해 `display_name`에 고정. 게시물마다 새로 생성, uid·익명 키·시각에서 유도 금지(FR-AN-02). 단어 목록은 `nickname.adjectives`·`nickname.animals` 설정값.
- **콘텐츠 필터(3.11)** — 응원 메시지 내용, 분실물 설명·장소에 저장 전 동기 적용. ① 정규식(전화번호, 이메일, URL, `@아이디`, "카톡"/"인스타"/"디엠" + 아이디 형태) ② 금칙어(`filter.banned_words`) ③ OpenAI Moderation API(`omni-moderation-latest`, `filter.llm.enabled`, `filter.llm.threshold`, 타임아웃 2초). 어느 단계든 걸리면 `CONTENT_NOT_ALLOWED`(사유 비노출). ③ 실패 시 ①②를 통과한 글은 `moderation_status = SKIPPED`로 저장(fail-open)하고 운영자 검토 목록에 노출. 외부 API에는 본문만 보내고 uid·익명 키·IP·닉네임을 보내지 않는다. 로그에는 카테고리·점수만.
- 속도 제한 초과는 `RATE_LIMITED`(429). Redis `INCR` + TTL 60초.
- 공개 응답은 `displayName`, `isMine`만. `author_user_id`·`writer_key_hash`는 운영자 API에서만.
- 신고(`content_reports`)는 (target_type, target_id, reporter) 유니크. 같은 트랜잭션에서 `report_count + 1`, `≥ report.hide_threshold`면 `is_hidden = 1`. 운영자 등록 글(`is_official = 1`)은 신고·숨김 대상 아님, 표시명 "종합 안내소".

**설정 `appsetting`**
- 알려진 키만 허용하는 `enum SettingKey`와 타입별 getter(`getInt`, `getDecimal`, `getList`)를 가진 `AppSettingReader`. 30초 캐시. 변경 API는 OWNER만, 변경 시 캐시 무효화.

**개인정보**
- 수집 항목은 SRS NFR-SC-01로 한정. 축제 후 7일 내 파기 배치(`role = 'USER'` 조건 필수). 로그에 개인정보 금지.

## 9. API 경로 규약

`/api/v1/<자원>` 복수형 kebab-case. 운영자는 `/api/v1/admin/<자원>`. 본인 자원은 `/me`.

| 메서드·경로 | 설명 | 인증 |
|---|---|---|
| GET /api/v1/match/summary | 홈 인스타팅 블록 | 선택 |
| POST /api/v1/match/applications | 신청 | 필수 |
| GET / PATCH / DELETE /api/v1/match/applications/me | 내 신청 조회·수정·취소 | 필수 |
| POST /api/v1/match/applications/rejoin | 2회차 재참여(1회차 신청 복사, FR-MT-03) | 필수 |
| GET /api/v1/match/results/me | 내 결과(발표 후) | 필수 |
| POST /api/v1/match/reports | 매칭 상대 신고 | 필수 |
| GET /api/v1/sse/match | 매칭 상태 SSE | 선택 |
| GET /api/v1/timetable | 공연 목록(+LIVE 계산) | - |
| GET /api/v1/places, /api/v1/places/{id} | 장소 | - |
| GET /api/v1/clubs, /api/v1/clubs/{id} | 라인업 | - |
| GET /api/v1/notices, /api/v1/notices/{id}, /api/v1/notices/banner | 공지 | - |
| GET / POST /api/v1/lost-items, PATCH /api/v1/lost-items/{id}/resolve, DELETE /api/v1/lost-items/{id} | 분실물 | 쓰기 필수 |
| GET / POST /api/v1/cheers, DELETE /api/v1/cheers/{id} | 응원 메시지 | 불필요(익명 키) |
| POST /api/v1/reports | 콘텐츠 신고 | 필수 |
| GET /api/v1/photos | 축제 사진 | - |
| GET /api/v1/auth/csrf, POST /api/v1/auth/logout, GET /api/v1/auth/me | 인증 | - / 필수 |
| /api/v1/admin/match/rounds/** (배치 실행·확인·publish·시각 수정) | 회차 운영 | STAFF, publish는 OWNER |
| /api/v1/admin/match/reports/** | 매칭 신고 처리 | STAFF |
| /api/v1/admin/timetable/**, /admin/places/**, /admin/clubs/**, /admin/notices/**, /admin/photos/** | 콘텐츠 운영 | STAFF |
| /api/v1/admin/lost-items/**, /admin/cheers/** (익명 키 일괄 숨김·차단 포함), /admin/reports/**, /admin/moderation/skipped, /admin/users/{id}/write-ban | 게시물·신고·제재·필터 검토 | STAFF |
| /api/v1/admin/settings/** | 설정 | OWNER |

목록 조회는 `size`(기본 20, 최대 50)와 커서(`cursor`) 방식. 오프셋 페이지네이션은 운영자 화면에만.

## 10. 주석

- 모든 클래스 위에 `/** 한 줄 요약 */`(기존 관례).
- 서비스 public 메서드마다 `/** */`: 무엇을 하는지 한 줄, 검증하는 규칙과 FR 번호, `@throws CustomException` 뒤에 던질 수 있는 ErrorCode 나열. 트랜잭션 경계가 특이하면 적는다.
- 컨트롤러 메서드는 api 인터페이스의 `@Operation`이 문서다. 구현체에 주석을 반복하지 않는다.
- 로직 블록 위 `//` 주석은 "왜"만. 코드를 다시 읽어주는 주석("리스트를 순회한다")은 쓰지 않는다. 자명한 getter·생성자에는 주석 없음.
- 어노테이션과 메서드 선언 사이에 주석을 끼우지 않는다. Javadoc은 어노테이션 위에.
- 한국어로 통일. 임시 코드는 `// TODO(#이슈번호): 내용`.

```java
/**
 * 인스타팅 신청을 등록한다.
 * <p>검증: 회차 접수 중(FR-MT-02), 매칭 차단 회원 아님(FR-MT-41), 회차당 1건·인스타 ID 회차 내 유니크(FR-MT-13),
 * 필수 동의 3종(FR-MT-11). 인스타 ID는 정규화 후 저장한다.
 * @throws CustomException MATCH_ROUND_NOT_OPEN, USER_MATCHING_BLOCKED, APPLICATION_ALREADY_EXISTS,
 *         APPLICATION_INSTAGRAM_DUPLICATE, TIMETABLE_SLOT_NOT_FOUND
 */
@Transactional
public ApplicationResponse apply(Long userId, ApplyMatchRequest request) {
```

## 11. 테스트

- 서비스: `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks`(기존 스타일). 메서드명은 한글로 `상황_결과()` (예: `마감_후_신청하면_MATCH_ROUND_NOT_OPEN을_던진다`).
- 리포지토리·제약: `@DataJpaTest`(H2 MySQL 모드). 유니크 제약과 커스텀 쿼리는 여기서 검증.
- 컨트롤러: `@WebMvcTest(controllers = X.class)` + `@MockitoBean` 서비스, `src/test/java/com/yufesta/support/ControllerTestSupport` 상속(실제 SecurityConfig·CorsConfig를 슬라이스에 넣고 보안 협력 빈은 mock). principal이 `Long`이라 `@WithMockUser`로는 `userId`가 null이 된다. `src/test/java/com/yufesta/support/WithMockLoginUser`(`id`·`role` 지정)를 쓴다.
- 시간 의존 로직은 `Clock.fixed`. `Thread.sleep`으로 시간을 맞추지 않는다.
- 매칭 엔진: 순수 단위 테스트. 최소 케이스 — 1:1 완전 매칭, 성비 2:1에서 전원 배정, N 상한 초과 시 미매칭 발생, 차단 쌍 제외, 이전 회차 쌍 제외, 동점 결정성, 한쪽 0명.
- 새 기능에는 서비스 단위 테스트가 반드시 포함된다. 커버리지 수치는 강제하지 않는다.
- 라이브러리 추가는 사람이 결정한다. 합의된 후보: `spring-boot-starter-flyway`+`flyway-mysql`, `spring-boot-starter-data-redis`, `software.amazon.awssdk:s3`, `net.coobird:thumbnailator`, ShedLock(Redis provider). 이 밖의 라이브러리는 제안만 하고 추가하지 않는다.

## 12. 현재 상태와 우선 보완 항목

완료: BaseTimeEntity, ErrorCode/CustomException/GlobalExceptionHandler, ApiResponse, RequestLoggingFilter, OAuth2(카카오·구글) + 쿠키 JWT, AppSetting 허용 목록으로 STAFF 부여, dev/prod/test 프로필, compose(MySQL 8.4), Dockerfile, Swagger(dev).

아래는 코드를 읽고 확인한 미완 항목이다. 처리되면 이 목록에서 지운다.

4. OAuth `state`·redirect가 HttpSession(메모리)에 있음 — ECS 2 task에서 콜백이 다른 인스턴스로 오면 실패. 쿠키 기반 `AuthorizationRequestRepository`로 교체(권장) 또는 ALB 고정 세션
8. Flyway 미도입 — 인스타팅 완료 후, 인프라 작업 전에 5장대로 도입(`V1__init.sql`은 그 시점의 `docs/erd.sql`에서 생성)
9. Redis 없음(SSE 팬아웃·속도 제한) — 도입은 측정(발표 순간 부하 테스트) 후 결정. 스케줄 락은 필요 없음이 확인됨(5장)
10. `OpenApiConfig`(쿠키 보안 스키마, 공통 오류 응답, 그룹 public/admin) 없음
12. 인앱 브라우저(인스타그램·카카오톡) 로그인 검증 — 구글은 인앱 웹뷰에서 차단됨. 인앱 감지 시 프론트가 구글 버튼 대신 "외부 브라우저로 열기" 안내
13. `ErrorCode`에 공통 코드만 있고 도메인 코드가 없음 — 각 도메인 첫 작업에서 6장 규칙대로 추가
14. `RequestLoggingFilter`에 MDC `userId`·`X-Request-Id` 없음

## 13. 개선 후보 (MVP 이후)

축제(10/2) 전에는 8장 규칙대로 가장 단순한 방식으로 만든다. 아래는 그 다음 단계에서 교체하거나 덧붙일 수 있는 지점이며, 각 항목은 "지금 방식 → 개선"과 해결하는 문제를 적었다.

두 가지 규칙
- **미리 구현하지 않는다.** 요청받지 않으면 이 항목들을 선제 도입하지 않는다. 지금 필요한 건 단순한 구현 하나다.
- **갈아끼울 수 있게 만든다.** 아래 항목이 나중에 들어올 자리는 인터페이스 하나 또는 클래스 하나 뒤에 둔다. 예: 회차 이벤트 발행은 `RoundEventPublisher`(지금 구현은 Redis Pub/Sub 하나), 필터 외부 호출은 `ModerationClient`(지금은 OpenAI 하나), 배치 트리거는 `RoundScheduler`(지금은 `@Scheduled` + DB 상태 전이, 락 없음), 매칭 계산은 순수 클래스 `MatchingEngine`. 추상화는 이 정도까지만 하고 구현체를 두 개 만들지 않는다.

1. **회차 배치 트리거**: `@Scheduled` + DB 상태 전이(락 없음) → EventBridge Scheduler → SQS → 컨슈머. 정확히 한 번 실행, 가시성 타임아웃 기반 재시도, DLQ. 발표 시각 배치 실패(NFR-AV-03)를 사람 개입 없이 복구. 분산 락(ShedLock JDBC·Redis SET NX·Redisson)은 비교 후 보류: 행 잠금 + 전이 검사가 이미 정확히 한 번을 보장해 늦은 인스턴스의 대기 1회만 줄인다.
2. **발표 이벤트 팬아웃**: Redis Pub/Sub → Redis Streams 또는 SNS+SQS. 연결이 끊긴 클라이언트의 `Last-Event-ID` 재전송(SRS 4.3)과 이벤트 유실 방지.
3. **Outbox 패턴**: 매칭 결과 커밋과 발표 이벤트 발행을 한 트랜잭션에 묶기. 결과는 저장됐는데 SSE가 안 나가는 불일치 제거.
4. **결과 조회 프리워밍**: 배치 직후 결과 카드를 Redis에 저장해 두고 발표 순간 캐시에서 응답. 발표 직후 1,000명 동시 조회 p95 1초(NFR-PF-01)를 DB 부하 없이 달성.
5. **매칭 알고리즘**: 그리디 → 최대 가중 매칭(헝가리안 또는 Blossom). 회차별 평균 점수·매칭률을 운영 대시보드에 지표로.
6. **회차 상태 머신 명시화**: 문자열 상태 비교 → 허용 전이표를 가진 enum(`RoundStatus.transitionTo`). 잘못된 전이를 컴파일·테스트 단계에서 차단.
7. **속도 제한 고도화**: Redis INCR → Bucket4j(토큰 버킷) 또는 Redis Lua 슬라이딩 윈도우. 회원 기준 외에 IP 기준 전역 제한.
8. **이미지 파이프라인 비동기화**: 서버 동기 리사이즈 → 원본만 S3에 올리고 SQS → Lambda(리사이즈) → 상태 갱신. 업로드 응답 시간 단축, 대량 업로드 대응.
9. **관측성**: Micrometer + CloudWatch. 발표 시각 응답 p95, SSE 연결 수 게이지, 배치 소요 시간, 로그인 실패율 알람(NFR-AV-05). JSON 구조화 로그와 `X-Request-Id` 전파.
10. **부하 테스트 자동화**: k6로 "발표 직후 1,000명 결과 조회 + SSE 1,000 연결" 시나리오를 스크립트로 두고 결과를 README에 기록.
11. **테스트 인프라**: H2 MySQL 모드 → Testcontainers MySQL(Boot 4 `@ServiceConnection`). H2와 MySQL의 타입·락 동작 차이 제거.
12. **무중단 배포**: ECS 롤링 → 블루/그린(CodeDeploy). 당일 배포 금지(NFR-AV-01)를 파이프라인 규칙으로.
13. **개인정보 파기 자동화**: 수동 배치 → 축제 종료 + 7일에 자동 실행, 파기 건수 리포트 생성·보관(NFR-SC-02).
14. **운영자 감사 로그**: 접근 로그 → 운영자 행위(회차 발표, 숨김, 제재, 설정 변경)를 별도 테이블에 기록.
15. **읽기 API 캐시**: 타임테이블·장소·라인업에 `Cache-Control`/ETag와 CloudFront 캐시. 변경 시 무효화.
16. **강제 로그아웃**: 서버 폐기 목록 없음(FR-AUTH-08) → Redis 거부 목록. 제재 계정을 즉시 차단.

## 14. 하지 말 것

- 엔티티에 `@Setter`/`@Data`, 컨트롤러에서 엔티티 반환, 컨트롤러에 비즈니스 로직
- 다른 도메인 Repository 주입, 서비스 밖에서 지연 로딩
- 도메인 코드에서 `LocalDateTime.now()` 직접 호출, 기기·클라이언트 시각 신뢰
- prod에서 `ddl-auto: update/create`, 적용된 마이그레이션 파일 수정
- JWT·로그·공개 응답에 개인정보(providerUserId, 인스타 ID, 작성자 uid)
- role 변경 API, 발표 전 결과 반환, 신청 닉네임을 익명 게시물에 사용
- `System.out.println`, `printStackTrace`, 빈 `catch`
- Boot 3 방식(`@MockBean`, `spring-boot-starter-web`, `AntPathRequestMatcher`, `com.fasterxml.jackson.databind.ObjectMapper`)
- 합의 없는 라이브러리 추가, 요청 없는 커밋·푸시·브랜치 생성, 13장 개선 항목의 선제 구현

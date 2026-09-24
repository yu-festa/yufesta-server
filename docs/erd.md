# YU FESTA ERD 설명서 v1.4

기준: SRS v1.7 · DB: MySQL 8.x · DDL: `docs/erd.sql`

v1.4 변경 요약 (2026-09-24, 지도 장소 카테고리 개편)
- **장소 카테고리 통일**: `places.category`를 `STAGE`(공연장) / `TOILET`(화장실) / `DELIVERY_ZONE`(배달존) 세 값으로 정리
- **기존 데이터 안전 전환**: V5에서 기존 `BOOTH`·`AMENITY`·`INFO` 행은 `DELIVERY_ZONE`으로 정규화하되, 실제 배달존 확인 전까지 비노출 처리

v1.3 변경 요약 (2026-09-24, 총동연 타임라인 반영)
- **타임테이블 구분 확장**: `timetable_slots.slot_type`에 `EVENT` 추가(개회식·총장님 연설·가요제처럼 출연 동아리가 없는 순서)
- **타임테이블 초기 데이터**: `db/migration/V3__timetable_initial.sql`. 무대 STAGE 장소 1건(없을 때만) + 공연 13건. 팔찌 배부·입장 안내는 공지로

v1.2 변경 요약 (2026-09-17, 2차 회의 반영)
- **응원 메시지 비로그인 작성**: `cheers.author_user_id` 제거, 익명 키 해시(`writer_key_hash`) 추가. users와의 FK 없음
- **콘텐츠 필터 상태**: `cheers`·`lost_items`에 `moderation_status`(PASSED/SKIPPED) 추가. LLM 모더레이션 API 실패 시 SKIPPED
- **2회차 이월 신청 자동 생성**: `applications.join_next_round` 제거, `entry_type`(NEW/CARRIED/REJOIN)·`source_application_id`(자기 참조 FK) 추가. 이월·재참여자는 2회차 신청 행이 복사 생성된다
- **회차 초기값**: 1회차 사전 오픈~10/2 15:50 마감·16:00 발표, 2회차 16:00~19:50 마감·20:00 발표(축제 2026-10-02. `db/migration/V2__initial_data.sql`과 동일)
- **보고 싶은 공연 제한**: 해당 회차 발표 시각 이후 시작 공연만(앱 검증, FR-MT-05)
- 스프링 엔티티와 정합: `users.blocked_at` → `matching_blocked_at`, `gender` CHAR(1) → VARCHAR(1), 모든 테이블에 `created_at`·`updated_at`, 열거형 값은 대문자 enum 이름
- 설정 키 추가: 금칙어, LLM 필터 활성화·임계값, 속도 제한 3종

## ERDCloud로 가져오기

1. ERDCloud에서 새 ERD 생성 → 상단 **가져오기 > SQL** → DB 종류 **MySQL** → `erd.sql` 내용 붙여넣기
2. 컬럼 `COMMENT`가 **논리명(한글)** 으로 들어가고, `FOREIGN KEY`가 관계선으로 그려집니다
3. 가져온 뒤 확인할 것: 관계선 21개(아래 관계 표), `users`·`applications`·`matches`가 중앙에 오도록 배치. `cheers`는 어느 테이블과도 선이 없는 것이 정상

## 설계 규칙

| 규칙 | 내용 |
|---|---|
| PK | 모든 테이블 `id BIGINT UNSIGNED AUTO_INCREMENT` (`app_settings`만 문자열 키, `application_tags`만 복합 키) |
| 운영자 | 별도 테이블 없이 `users.role`(`USER` / `STAFF` / `OWNER`). 운영자도 소셜 로그인으로 인증하고, `app_settings.admin.allowlist`에 있는 계정이 로그인하면 role을 부여한다. **role은 사용자 API로 변경 불가** |
| 열거형 | `ENUM` 대신 `VARCHAR` + 주석에 허용값. 저장 값은 애플리케이션 enum 이름 그대로 **대문자**(`KAKAO`, `OPEN`, `FOUND`). JPA `@Enumerated(STRING)`과 일치 |
| 불리언 | `TINYINT(1)` 0/1. JPA는 `hibernate.type.preferred_boolean_jdbc_type=TINYINT`로 맞춘다 |
| 시각 | `DATETIME`, 서버 기준 KST 저장. **모든 테이블에 `created_at`·`updated_at`**(JPA `BaseTimeEntity` 상속 전제). `application_tags`만 예외(컬렉션 테이블) |
| 소프트 삭제 | 게시물은 `is_hidden`, 신청은 `canceled_at`으로 표시. 물리 삭제는 파기 배치와 신고 누적 제재(신청 삭제)에서만 |
| 외래키 삭제 정책 | 개인정보 파기 시 `users` 행을 지우면 신청·매칭·신고는 **CASCADE**, 게시물 작성자는 **SET NULL**(글은 남고 작성자만 끊김) |
| 익명 작성 | 응원 메시지는 회원과 연결하지 않는다. 브라우저 쿠키의 익명 키를 SHA-256 해시해 `writer_key_hash`에 저장하고 속도 제한·운영자 일괄 숨김에만 쓴다 |
| 파기 대상 | `users`(**`role = 'USER'`만**), `applications`, `application_tags`, `matches`, `blocks`, `content_reports.reporter_user_id`, `lost_items.author_user_id`, `cheers.writer_key_hash`(NULL 처리) |
| 보존 대상 | `users`(운영자 행), `places`, `place_events`, `clubs`, `timetable_slots`, `notices`, `festival_photos`, `app_settings`, `match_rounds` |

## 테이블 관계 (외래키 21개)

| 부모 | 자식 | 카디널리티 | 삭제 정책 |
|---|---|---|---|
| users | applications | 1 : N (회차마다 1건) | CASCADE |
| users | blocks (reporter) | 1 : N | CASCADE |
| users | blocks (target) | 1 : N | CASCADE |
| users | lost_items | 1 : N | SET NULL |
| users | content_reports | 1 : N | SET NULL |
| match_rounds | applications | 1 : N | RESTRICT |
| match_rounds | matches | 1 : N | RESTRICT |
| match_rounds | blocks | 1 : N | SET NULL |
| applications | applications (source, 2회차 원본) | 1 : 0..1 | SET NULL |
| applications | application_tags | 1 : N (최대 3) | CASCADE |
| applications | matches (본인) | 1 : N (최대 N) | CASCADE |
| applications | matches (상대) | 1 : N | CASCADE |
| timetable_slots | applications (보고 싶은 공연) | 1 : N | SET NULL |
| places | timetable_slots (무대) | 1 : N | RESTRICT |
| places | place_events | 1 : N | CASCADE |
| places | festival_photos | 1 : N | SET NULL |
| clubs | timetable_slots | 1 : N | SET NULL |
| clubs | festival_photos | 1 : N | SET NULL |
| users (운영자) | clubs · notices · festival_photos (created_by / uploaded_by) | 1 : N | SET NULL |

`cheers`는 외래키가 없다(v1.1의 users → cheers 관계 제거).

---

## 1. users — 회원(운영자 포함)

소셜 로그인으로 생성되는 계정. 카카오와 구글 계정은 연동하지 않으므로 같은 사람이라도 제공자가 다르면 별도 행이다. 개인정보는 제공자 식별자뿐이다. 운영자도 같은 테이블에서 `role`로 구분한다.

| 컬럼 | 한글명 | 타입 | NULL | 역할 |
|---|---|---|---|---|
| id | 회원 ID | BIGINT UNSIGNED | N | PK. JWT 페이로드의 uid |
| provider | 로그인 제공자 | VARCHAR(10) | N | `KAKAO` / `GOOGLE` |
| provider_user_id | 제공자 계정 식별자 | VARCHAR(191) | N | 카카오 `id` 또는 구글 `sub`. (provider, provider_user_id) 유니크 |
| role | 역할 | VARCHAR(10) | N | `USER`(기본) / `STAFF`(콘텐츠·신고 관리) / `OWNER`(회차 발표·설정 권한). 허용 목록 계정의 최초 로그인 시 서버가 부여, 사용자 API로는 변경 불가 |
| display_name | 운영자 표시 이름 | VARCHAR(30) | Y | 운영 화면·감사 로그 표시용. 일반 회원은 NULL |
| matching_blocked_at | 매칭 제외 시각 | DATETIME | Y | 값이 있으면 이후 모든 회차 신청 차단(신고 누적 제재). v1.1의 `blocked_at` |
| write_banned_at | 작성 금지 시각 | DATETIME | Y | 값이 있으면 분실물 작성·신고 차단(운영자 부여). 응원 메시지는 비로그인이라 익명 키 차단으로 대신한다 |
| last_login_at | 최근 로그인 시각 | DATETIME | Y | 운영 통계용 |
| created_at / updated_at | 가입·수정 시각 | DATETIME | N | created_at = 최초 로그인 시각 |

## 2. 운영자 — `users.role`로 통합 (별도 테이블 없음)

운영자 전용 테이블을 두지 않는다. 운영자는 자기 카카오/구글 계정으로 로그인하고, `app_settings.admin.allowlist`(`PROVIDER:provider_user_id` 목록)에 있으면 로그인 시 `role`이 `STAFF` 또는 `OWNER`로 설정된다.

- 비밀번호·아이디 관리가 없어 구현·보안 부담이 줄고, 작성자 FK가 `users` 하나로 통일된다
- 운영자 API는 서버에서 `role IN ('STAFF','OWNER')`를 검사하고, 회차 발표·설정 변경은 `OWNER`만
- 파기 배치는 `role = 'USER'`인 행만 삭제한다
- 운영자 인증을 ID/비밀번호로 확정하게 되면 이 절만 `admin_users` 테이블로 되돌리면 된다(v1.0 참고)

## 3. places — 지도 장소(핀)

지도의 모든 핀. 화장실은 `TOILET` 카테고리로 독립되어 "화장실만 보기"와 거리순 목록에 쓰인다.

| 컬럼 | 한글명 | 타입 | NULL | 역할 |
|---|---|---|---|---|
| id | 장소 ID | BIGINT UNSIGNED | N | PK. `?focus=` 파라미터 값 |
| name | 장소명 | VARCHAR(50) | N | 핀·시트 제목 |
| category | 카테고리 | VARCHAR(20) | N | `STAGE`(공연장) / `TOILET`(화장실) / `DELIVERY_ZONE`(배달존) |
| lat | 위도 | DECIMAL(10,7) | N | 카카오맵 좌표. 현장 조사로 수집 |
| lng | 경도 | DECIMAL(10,7) | N | |
| description | 설명 | VARCHAR(200) | Y | 바텀시트 본문 |
| building | 건물명 | VARCHAR(50) | Y | 실내 위치 안내(예: 상경관) |
| floor | 층·세부 위치 | VARCHAR(20) | Y | 예: "1층 서편" |
| sort_order | 표시 순서 | INT | N | 내 위치 없을 때 목록 정렬 |
| is_active | 노출 여부 | TINYINT(1) | N | 당일 폐쇄된 시설 숨김 |
| created_at / updated_at | 생성·수정 시각 | DATETIME | N | |

## 4. place_events — 장소 진행 이벤트

바텀시트의 장소 이벤트 목록. 장소 하나에 여러 이벤트.

| 컬럼 | 한글명 | 타입 | NULL | 역할 |
|---|---|---|---|---|
| id | 장소 이벤트 ID | BIGINT UNSIGNED | N | PK |
| place_id | 장소 ID | BIGINT UNSIGNED | N | FK → places |
| name | 이벤트명 | VARCHAR(50) | N | 예: "타로 동아리 별자리" |
| time_text | 진행 시간 표기 | VARCHAR(30) | N | 표시용 문자열(예: "16:00 – 21:00") |
| sort_order | 표시 순서 | INT | N | |
| created_at / updated_at | 생성·수정 시각 | DATETIME | N | |

## 5. clubs — 라인업 동아리

초기 데이터는 `V4__club_initial.sql`(동아리 제공 자료 9건 + 타임테이블 CLUB 공연 연결, 하단 INSERT/UPDATE와 동일). `photo_url`은 업로드 API 뒤에 채운다.

동아리 소개 카드. 사진·소개는 동아리 제공분만 사용한다.

| 컬럼 | 한글명 | 타입 | NULL | 역할 |
|---|---|---|---|---|
| id | 동아리 ID | BIGINT UNSIGNED | N | PK |
| name | 동아리명 | VARCHAR(50) | N | |
| intro | 소개 | VARCHAR(200) | N | 2~3줄 소개 |
| genre | 장르 | VARCHAR(30) | Y | |
| signature_song | 대표곡 | VARCHAR(50) | Y | |
| instagram_url | 인스타그램 링크 | VARCHAR(200) | Y | 카드의 외부 링크 |
| photo_url | 대표 사진 URL | VARCHAR(500) | Y | S3/CloudFront URL |
| sort_order | 표시 순서 | INT | N | 캐러셀·목록 순서 |
| created_by | 등록 운영자 ID | BIGINT UNSIGNED | Y | FK → users(role=STAFF/OWNER) |
| created_at / updated_at | 생성·수정 시각 | DATETIME | N | |

## 6. timetable_slots — 공연 타임테이블

공연 한 건. 순서 변경·지연·LIVE 수동 지정을 모두 이 테이블로 표현한다.
초기 데이터는 `V3__timetable_initial.sql`(무대 STAGE 장소 1건 + 총동연 타임라인 13건, 하단 INSERT와 동일). 팔찌 배부·입장 안내는 공지로 다룬다.

| 컬럼 | 한글명 | 타입 | NULL | 역할 |
|---|---|---|---|---|
| id | 공연 슬롯 ID | BIGINT UNSIGNED | N | PK. 신청의 "보고 싶은 공연" 참조 |
| sort_order | 공연 순서 | INT | N | 목록 순서. 순서 변경 시 갱신 |
| title | 공연명(출연자명) | VARCHAR(50) | N | 동아리 연결이 없는 초청 공연도 표시 가능 |
| slot_type | 구분 | VARCHAR(10) | N | `CLUB` / `GUEST` / `EVENT`(개회식·연설·가요제처럼 출연 동아리가 없는 순서. v1.3) |
| start_at | 시작 시각 | DATETIME | N | LIVE 판정·홈 "다음 공연" 계산 기준. **회차별 선택 가능 여부 판정(start_at ≥ 회차 publish_at)** |
| end_at | 종료 시각 | DATETIME | N | |
| stage_place_id | 무대 장소 ID | BIGINT UNSIGNED | N | FK → places(category=STAGE) |
| club_id | 동아리 ID | BIGINT UNSIGNED | Y | FK → clubs. 라인업 카드 연결 |
| changed_from_start | 변경 전 시작 시각 | DATETIME | Y | 값이 있으면 "순서 변경됨" 뱃지 + 취소선 표시 |
| delay_minutes | 지연 분 | INT | Y | 운영자 입력. "10분 지연" 표시 |
| is_live_override | 운영자 지정 진행 중 | TINYINT(1) | Y | 1이면 시계 판정보다 우선해 LIVE 표시(협의 항목) |
| created_at / updated_at | 생성·수정 시각 | DATETIME | N | |

## 7. match_rounds — 매칭 회차

하루 2행(1회차·2회차). 상태 전이는 `SCHEDULED → OPEN → CLOSED → PUBLISHED`. 2회차 `open_at`은 1회차 `publish_at`과 같다. 시각은 운영자 화면에서 수정한다(FR-ADM-02).

| 회차 | open_at | close_at | publish_at |
|---|---|---|---|
| 1 | 사전 오픈 시각(홍보 시작, 초기값 9/25 00:00) | 10/2 15:50 | 10/2 16:00 |
| 2 | 10/2 16:00 | 10/2 19:50 | 10/2 20:00 |

| 컬럼 | 한글명 | 타입 | NULL | 역할 |
|---|---|---|---|---|
| id | 회차 ID | BIGINT UNSIGNED | N | PK |
| seq | 회차 번호 | TINYINT UNSIGNED | N | 1, 2. 유니크 |
| open_at | 접수 시작 시각 | DATETIME | N | 1회차는 며칠 전. 홈 카운트다운 "다음 회차 시작까지" |
| close_at | 접수 마감 시각 | DATETIME | N | publish_at − 10분. 이후 신청·수정·취소 차단 |
| publish_at | 발표 시각 | DATETIME | N | 이 시각 전엔 결과 비노출(FR-MT-04). 보고 싶은 공연 선택 하한(FR-MT-05) |
| status | 상태 | VARCHAR(12) | N | `SCHEDULED` / `OPEN` / `CLOSED` / `PUBLISHED` |
| executed_at | 배치 실행 시각 | DATETIME | Y | 매칭 배치 완료 기록. 값이 있으면 재실행 스킵(멱등) |
| published_at | 발표 확정 시각 | DATETIME | Y | 실제 공개 기준. 이 시점에 다음 회차 이월 신청을 생성 |
| created_at / updated_at | 생성·수정 시각 | DATETIME | N | |

## 8. applications — 인스타팅 신청

회원이 회차마다 1건. 약관 동의 이력도 여기에 함께 저장한다. 신고 누적 제재 시 이 행이 삭제된다.

**이월 모델(v1.2)**: 1회차 발표 시점에 미매칭자의 1회차 신청을 복사해 2회차 행을 자동 생성하고(`entry_type = CARRIED`), 1회차 매칭자가 "2회차도 참여"를 누르면 같은 방식으로 생성한다(`REJOIN`). 복사 시 `wanted_slot_id`는 2회차 `publish_at` 이후 시작 공연일 때만 유지하고 아니면 NULL. 태그(`application_tags`)도 복사한다. 이렇게 하면 2회차 풀은 `round_id = 2` 한 번으로 조회되고, 수정·취소·유니크 제약이 일반 신청과 동일하게 적용된다.

| 컬럼 | 한글명 | 타입 | NULL | 역할 |
|---|---|---|---|---|
| id | 신청 ID | BIGINT UNSIGNED | N | PK. 매칭 결과의 기준 키 |
| user_id | 회원 ID | BIGINT UNSIGNED | N | FK → users. (user_id, round_id) 유니크 |
| round_id | 회차 ID | BIGINT UNSIGNED | N | FK → match_rounds |
| instagram_id | 인스타 ID(정규화) | VARCHAR(30) | N | `@` 제거·소문자. (round_id, instagram_id) 유니크 → 도용·이중 신청 방지. 발표 후 상대에게 공개 |
| nickname | 닉네임 | VARCHAR(16) | N | 사용자 입력 2~8자. 상대 카드에 표시 |
| gender | 성별 | VARCHAR(1) | N | `M` / `F`. 풀 분리 기준 |
| age_band | 나이대 | VARCHAR(5) | Y | `19-21` / `22-24` / `25-27` / `28+`. 점수식 입력 |
| wanted_slot_id | 보고 싶은 공연 슬롯 ID | BIGINT UNSIGNED | Y | FK → timetable_slots. 같은 공연 +2점. **해당 회차 publish_at 이후 시작 공연만 허용(앱 검증)** |
| intro | 한 줄 소개 | VARCHAR(40) | Y | 상대 카드에 표시 |
| entry_type | 신청 유형 | VARCHAR(10) | N | `NEW`(직접 신청) / `CARRIED`(이전 회차 미매칭 자동 생성) / `REJOIN`(이전 회차 매칭자 재참여). 화면 안내·다수 측 우선순위(CARRIED 우선)에 사용 |
| source_application_id | 원본 신청 ID | BIGINT UNSIGNED | Y | FK → applications(자기 참조). CARRIED/REJOIN일 때 1회차 신청. 원본 삭제 시 NULL |
| terms_version | 동의한 이용약관 버전 | VARCHAR(20) | N | 동의 이력. 복사 시 원본 값 유지 |
| privacy_version | 동의한 개인정보 방침 버전 | VARCHAR(20) | N | 동의 이력 |
| age_confirmed | 19세 이상 확인 | TINYINT(1) | N | 필수 동의 |
| agreed_at | 약관 동의 시각 | DATETIME | N | 동의 이력 |
| canceled_at | 취소 시각 | DATETIME | Y | 값이 있으면 배치에서 제외(마감 전 취소). 이월 신청 "해제"도 이 값 |
| created_at / updated_at | 신청·수정 시각 | DATETIME | N | 마감 전 수정 허용 |

## 9. application_tags — 신청 관심 태그

태그를 정규화한 테이블. "공통 태그 수"를 JOIN으로 계산하기 위해 배열 대신 행으로 둔다. JPA에서는 엔티티가 아니라 `Application`의 `@ElementCollection`이다.

| 컬럼 | 한글명 | 타입 | NULL | 역할 |
|---|---|---|---|---|
| application_id | 신청 ID | BIGINT UNSIGNED | N | FK → applications. 복합 PK |
| tag | 관심 태그 | VARCHAR(20) | N | 고정 10개 목록 중 하나. 신청당 최대 3행(앱에서 검증) |

## 10. matches — 매칭 결과

**방향성 행**: 배치가 A↔B 매칭을 만들면 (A→B), (B→A) 두 행을 넣는다. 그러면 "내 상대 목록"이 `application_id = 나`로 단순 조회된다. 1:N은 소수 측 한 사람에게 행 N개.

| 컬럼 | 한글명 | 타입 | NULL | 역할 |
|---|---|---|---|---|
| id | 매칭 ID | BIGINT UNSIGNED | N | PK |
| round_id | 회차 ID | BIGINT UNSIGNED | N | FK → match_rounds |
| application_id | 본인 신청 ID | BIGINT UNSIGNED | N | FK → applications |
| partner_application_id | 상대 신청 ID | BIGINT UNSIGNED | N | FK → applications. 상대 카드 데이터 원천 |
| score | 매칭 점수 | DECIMAL(5,2) | N | 점수식 결과. 카드 정렬(점수 순) |
| assign_pass | 배정 단계 | TINYINT UNSIGNED | N | 1 = 1차 1:1, 2 = 2차 1:N 추가 배정. 운영 통계용 |
| created_at / updated_at | 생성·수정 시각 | DATETIME | N | |

## 11. blocks — 매칭 신고·차단

신고자→대상 관계. 같은 쌍은 1건만(유니크). 대상별 행 수가 `report.block_threshold`에 도달하면 제재.

| 컬럼 | 한글명 | 타입 | NULL | 역할 |
|---|---|---|---|---|
| id | 매칭 신고 ID | BIGINT UNSIGNED | N | PK |
| reporter_user_id | 신고자 회원 ID | BIGINT UNSIGNED | N | FK → users |
| target_user_id | 대상 회원 ID | BIGINT UNSIGNED | N | FK → users. 이후 회차 점수식에서 제외 |
| round_id | 신고 발생 회차 ID | BIGINT UNSIGNED | Y | FK → match_rounds |
| reason | 사유 | VARCHAR(20) | N | `PROFILE`(불쾌한 프로필) / `FAKE`(허위·성별 위조) / `OTHER` |
| detail | 상세 내용 | VARCHAR(500) | Y | |
| reviewed_at | 운영자 검토 시각 | DATETIME | Y | |
| decision | 처리 결과 | VARCHAR(10) | Y | `CONFIRM`(제재 확정) / `DISMISS`(기각) |
| created_at / updated_at | 신고·수정 시각 | DATETIME | N | |

## 12. cheers — 응원 메시지 (비로그인 작성)

로그인 없이 쓰는 익명 게시물. 회원과 연결하는 컬럼이 없다. 작성자 구분은 브라우저 쿠키의 익명 키를 해시한 값으로만 하며, 속도 제한(분당 1건)·본인 삭제(FR-CH-04, C)·운영자 일괄 숨김·작성 차단에 쓴다. `display_name`은 저장 시 자동 생성되어 고정된다.

| 컬럼 | 한글명 | 타입 | NULL | 역할 |
|---|---|---|---|---|
| id | 응원 메시지 ID | BIGINT UNSIGNED | N | PK |
| content | 메시지 내용 | VARCHAR(40) | N | 40자 제한. 콘텐츠 필터 통과분만 저장 |
| display_name | 자동 생성 닉네임 | VARCHAR(20) | N | "형용사 동물". 공개 API에 노출되는 유일한 작성자 정보 |
| writer_key_hash | 익명 키 해시 | VARCHAR(64) | Y | 쿠키 익명 키의 SHA-256(hex 64자). 공개 API 비노출. `isMine` 판정·일괄 숨김·차단 키. 파기 시 NULL |
| moderation_status | 필터 상태 | VARCHAR(10) | N | `PASSED`(3단계 모두 통과) / `SKIPPED`(LLM 단계 실패로 정규식·금칙어만 통과, 운영자 검토 목록 대상) |
| report_count | 신고 누적 수 | INT UNSIGNED | N | content_reports 수를 비정규화. `report.hide_threshold` 도달 시 숨김 |
| is_hidden | 숨김 여부 | TINYINT(1) | N | 운영자 숨김 또는 자동 숨김 |
| created_at / updated_at | 작성·수정 시각 | DATETIME | N | created_at = 티커 정렬 |

## 13. lost_items — 분실물 게시

사용자 작성(로그인 필수, 익명 표시)과 운영자 등록(종합 안내소)이 한 테이블에 있다. 작성자는 모두 `author_user_id`이고, 운영자 등록 글은 `is_official = 1`로 구분한다.

| 컬럼 | 한글명 | 타입 | NULL | 역할 |
|---|---|---|---|---|
| id | 분실물 게시 ID | BIGINT UNSIGNED | N | PK |
| kind | 구분 | VARCHAR(5) | N | `FOUND`(주웠어요) / `LOST`(찾아요) |
| description | 물품 설명 | VARCHAR(100) | N | 콘텐츠 필터(연락처 패턴 포함) 통과분만 저장 |
| place_text | 발견·분실 장소 | VARCHAR(50) | N | 자유 입력. 콘텐츠 필터 대상 |
| occurred_at | 발견·분실 시각 | DATETIME | Y | |
| status | 상태 | VARCHAR(10) | N | `OPEN`(보관 중/찾는 중) / `RESOLVED`(해결) |
| display_name | 자동 생성 닉네임 | VARCHAR(20) | N | 운영자 등록 글은 "종합 안내소" |
| author_user_id | 작성자 회원 ID | BIGINT UNSIGNED | Y | FK → users. 운영자 등록 시 운영자의 users.id. 파기 시 NULL |
| is_official | 운영자 등록 여부 | TINYINT(1) | N | 1이면 안내소 등록 글(표시명 "종합 안내소", 신고·숨김·필터 대상 제외) |
| moderation_status | 필터 상태 | VARCHAR(10) | N | `PASSED` / `SKIPPED`(cheers와 동일) |
| report_count | 신고 누적 수 | INT UNSIGNED | N | 자동 숨김 판정 |
| is_hidden | 숨김 여부 | TINYINT(1) | N | |
| created_at / updated_at | 작성·수정 시각 | DATETIME | N | |

## 14. content_reports — 콘텐츠 신고

응원 메시지·분실물 신고(로그인 필요). 대상 테이블이 둘이라 다형 참조(`target_type` + `target_id`)로 두고, FK 대신 앱에서 존재를 검증한다.

| 컬럼 | 한글명 | 타입 | NULL | 역할 |
|---|---|---|---|---|
| id | 콘텐츠 신고 ID | BIGINT UNSIGNED | N | PK |
| target_type | 대상 유형 | VARCHAR(10) | N | `CHEER` / `LOST_ITEM` |
| target_id | 대상 게시물 ID | BIGINT UNSIGNED | N | cheers.id 또는 lost_items.id |
| reporter_user_id | 신고자 회원 ID | BIGINT UNSIGNED | Y | FK → users. (target_type, target_id, reporter) 유니크 → 1인 1회 |
| reason | 사유 | VARCHAR(20) | N | |
| reviewed_at | 운영자 검토 시각 | DATETIME | Y | |
| created_at / updated_at | 신고·수정 시각 | DATETIME | N | |

## 15. notices — 공지

| 컬럼 | 한글명 | 타입 | NULL | 역할 |
|---|---|---|---|---|
| id | 공지 ID | BIGINT UNSIGNED | N | PK |
| title | 제목 | VARCHAR(100) | N | |
| body | 본문 | TEXT | N | |
| is_banner | 긴급 배너 노출 여부 | TINYINT(1) | N | 1이면 홈 상단 배너(최신 1건 노출) |
| created_by | 작성 운영자 ID | BIGINT UNSIGNED | Y | FK → users(role=STAFF/OWNER) |
| created_at / updated_at | 작성·수정 시각 | DATETIME | N | |

## 16. festival_photos — 축제 사진

운영진 게시 사진. 세 가지 URL은 업로드 파이프라인(원본 보관 → 리사이즈 → 썸네일) 산출물이다.

| 컬럼 | 한글명 | 타입 | NULL | 역할 |
|---|---|---|---|---|
| id | 축제 사진 ID | BIGINT UNSIGNED | N | PK |
| image_url | 리사이즈 이미지 URL | VARCHAR(500) | N | 확대 보기용(긴 변 1600px) |
| original_url | 원본 이미지 URL | VARCHAR(500) | N | 보관용, 화면 미사용 |
| thumbnail_url | 썸네일 URL | VARCHAR(500) | N | 목록·홈 섹션용 |
| caption | 캡션 | VARCHAR(60) | Y | |
| category | 카테고리 | VARCHAR(10) | N | `STAGE` / `BOOTH` / `SCENE` / `CAMPUS` |
| place_id | 연결 장소 ID | BIGINT UNSIGNED | Y | FK → places. 지도 바텀시트에 표시 |
| club_id | 연결 동아리 ID | BIGINT UNSIGNED | Y | FK → clubs. 라인업 카드에 표시 |
| sort_order | 표시 순서 | INT | N | |
| uploaded_by | 업로드 운영자 ID | BIGINT UNSIGNED | Y | FK → users(role=STAFF/OWNER) |
| is_hidden | 숨김 여부 | TINYINT(1) | N | |
| created_at / updated_at | 업로드·수정 시각 | DATETIME | N | |

## 17. app_settings — 앱 설정

코드 수정 없이 바꿔야 하는 값들. DDL 끝의 INSERT가 초기값이다. 회차 시각은 여기가 아니라 `match_rounds` 행에서 관리한다.

| 컬럼 | 한글명 | 타입 | NULL | 역할 |
|---|---|---|---|---|
| setting_key | 설정 키 | VARCHAR(50) | N | PK. 예: `match.weight.tag` |
| setting_value | 설정 값 | VARCHAR(500) | N | 문자열로 저장, 앱에서 파싱 |
| description | 설명 | VARCHAR(100) | Y | 운영자 화면 표시용 |
| created_at / updated_at | 생성·수정 시각 | DATETIME | N | |

초기 키 목록

| 키 | 초기값 | 용도 |
|---|---|---|
| match.weight.tag / slot / age_same / age_adjacent | 1 / 2 / 1 / 0.5 | 점수식 가중치 |
| match.max_partners | 3 | 소수 측 1인당 최대 배정 수 N |
| report.block_threshold | 2 | 매칭 신고 누적 제재 기준 |
| report.hide_threshold | 2 | 콘텐츠 신고 누적 자동 숨김 기준 |
| nickname.adjectives / nickname.animals | 단어 목록 | 자동 닉네임 |
| filter.banned_words | (빈 값) | 금칙어 목록, 쉼표 구분 |
| filter.llm.enabled | 1 | LLM 모더레이션 API 사용 여부. 장애 시 0으로 |
| filter.llm.threshold | 0.5 | 카테고리 점수 차단 임계값(FR-CF-06 측정 후 조정) |
| ratelimit.cheer.anon_per_minute | 1 | 응원 메시지 익명 키당 분당 작성 수 |
| ratelimit.cheer.ip_per_minute | 10 | 응원 메시지 IP당 분당 작성 수(공용 와이파이 고려) |
| ratelimit.lostitem.per_minute | 1 | 분실물 회원당 분당 작성 수 |
| admin.allowlist | PROVIDER:id 목록 | 운영자 승격 계정 |

---

## SRS 엔티티와의 대응

| SRS 6.1 | 테이블 | 비고 |
|---|---|---|
| User | users | `kakaoId` → `provider + provider_user_id`로 일반화. `blockedAt` → `matching_blocked_at` |
| MatchRound | match_rounds | 초기 2행 INSERT 포함 |
| Application | applications + application_tags | `tags[]`를 정규화. `joinNextRound` → `entry_type` + `source_application_id`(이월 신청 행 생성) |
| Match | matches | 방향성 2행 저장으로 "대칭 처리" 확정 |
| Block | blocks | |
| TimetableSlot | timetable_slots | `title` 추가(동아리 없는 초청 공연용) |
| Club | clubs | |
| Place | places + place_events | `activeEvents[]`를 정규화 |
| FestivalPhoto | festival_photos | |
| Notice | notices | |
| LostItem | lost_items | `moderationStatus` 추가 |
| Cheer | cheers | **`authorUserId` 없음**. `writerKeyHash`, `moderationStatus` |
| ContentReport | content_reports | |
| AdminUser | users.role | 별도 테이블 대신 role 통합 |
| AppSetting | app_settings | 필터·속도 제한 키 추가 |

## 구현 시 유의점

- **회차 풀 조회**: `applications WHERE round_id = ? AND canceled_at IS NULL` + `users.matching_blocked_at IS NULL`. v1.1의 "이전 회차 합집합"은 없어졌다. 이월은 발표 시점에 행을 만들어 두는 것으로 해결
- **이월 신청 생성**: 회차 발표 트랜잭션 안에서 미매칭 신청을 조회해 다음 회차 행을 INSERT(`entry_type = CARRIED`, `source_application_id` = 원본). `wanted_slot_id`는 `timetable_slots.start_at >= 다음 회차.publish_at`일 때만 복사. `application_tags`도 복사. (user_id, round_id) 유니크가 있으므로 이미 직접 신청한 사용자는 건너뛴다
- **재참여**: "2회차도 참여" API는 같은 복사 로직을 `entry_type = REJOIN`으로 실행. 2회차 마감 후에는 거부
- **보고 싶은 공연 검증**: 신청·수정 시 `slot.start_at >= round.publish_at`이 아니면 거부(FR-MT-05). 배치에서도 재검증해 조건 위반 슬롯은 점수 계산에서 제외(타임테이블 변경 대비)
- **공통 태그 수**: `application_tags` 자기 조인으로 `COUNT(*)`
- **발표 전 비노출**: `matches`는 배치 직후 저장되지만 API는 `match_rounds.published_at IS NOT NULL`일 때만 반환
- **신고 누적**: `blocks` INSERT 트랜잭션 안에서 대상 카운트 → 임계값이면 `users.matching_blocked_at` 갱신 + 해당 회차 `applications` 삭제
- **report_count**: `content_reports` INSERT와 같은 트랜잭션에서 `report_count = report_count + 1`. 재계산 배치로 정합성 보정 가능
- **익명 키**: 응원 메시지 첫 작성 시 서버가 UUID 쿠키(`anon_key`, HttpOnly, 30일)를 발급. 저장은 SHA-256 해시만. 속도 제한은 Redis `INCR anon:{hash}` TTL 60초 + `INCR ip:{ip}` TTL 60초. 운영자 차단은 Redis 집합 또는 `app_settings`가 아닌 별도 캐시 키로(차단 목록이 길어지면 테이블 분리 검토)
- **콘텐츠 필터 파이프라인**: 정규식 → 금칙어(`filter.banned_words`) → LLM API(`filter.llm.enabled`). LLM 단계 타임아웃 2초, 실패 시 `moderation_status = 'SKIPPED'`로 저장하고 운영자 검토 목록에 노출. 외부 API에는 본문만 전송
- **인덱스**: 발표 순간 조회(`matches.application_id`), 회차별 풀(`applications(round_id, gender)`), 이월 추적(`applications.source_application_id`), 티커(`cheers.created_at`), 익명 키 일괄 처리(`cheers.writer_key_hash`)
- **운영자 role 부여**: 로그인 콜백에서 `(provider, provider_user_id)`가 `admin.allowlist`에 있으면 role 설정. role을 바꾸는 사용자 API·엔드포인트는 만들지 않는다
- **운영자 권한 검사**: 운영자 API에서 JWT의 uid로 `users.role` 조회(캐시 가능). 회차 발표·설정 변경은 `OWNER`만
- **파기 배치**: `DELETE FROM users WHERE role = 'USER' ...` — 운영자 행이 지워지지 않게 조건 필수. `cheers.writer_key_hash`는 `UPDATE ... SET writer_key_hash = NULL`
- **타임존**: 모든 DATETIME은 KST. 애플리케이션 서버 JVM·MySQL 세션 타임존을 Asia/Seoul로 고정하지 않으면 회차 마감 판정이 9시간 어긋난다

## 개정 이력

| 버전 | 일자 | 내용 |
|---|---|---|
| v1.0 | 2026-09-15 | SRS v1.5 기준 초안. 16 테이블 + admin_users |
| v1.1 | 2026-09-15 | admin_users 제거, `users.role`로 통합 |
| v1.2 | 2026-09-17 | 응원 메시지 비로그인(작성자 FK 제거, 익명 키 해시), 필터 상태 컬럼, 이월 신청 자동 생성(`entry_type`·`source_application_id`), 회차 초기값 16:00/20:00, `matching_blocked_at`·VARCHAR(1)·감사 시각 통일·대문자 열거형(스프링 엔티티 정합), 설정 키 6종 추가 |
| v1.3 | 2026-09-24 | `timetable_slots.slot_type`에 `EVENT` 추가, 타임테이블 초기 데이터(V3: 무대 1·공연 13) |
| v1.4 | 2026-09-24 | `places.category`를 공연장·화장실·배달존으로 개편하고 V5에서 기존 카테고리를 안전하게 비노출 전환 |

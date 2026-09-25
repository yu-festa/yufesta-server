import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { BASE_URL, JWT_SECRET, USER_COUNT, USER_ID_FROM, allowBusinessStatuses, csrfToken } from '../lib/common.js';
import { authHeaders } from '../lib/token.js';

// 6) 쓰기 부하(마감 직전): 신청자 APPLICANTS명(기본 200)이 CSRF 발급 → 신청 → 수정을 한다.
//    동시 실행 수는 CONCURRENCY로 조절한다. 같은 200건이라도 몇 명이 겹치느냐에 따라 응답 시간이 완전히 달라지므로
//    현실적인 값(기본 50)과 최악(200, 전원 동시)을 나눠 재고 비교한다.
//    유니크 제약(회차당 1건·인스타 ID)과 잠금 경합을 본다. 회차가 OPEN이고 마감 전일 때만 의미가 있다.
//    409(이미 신청함)는 규칙대로 동작한 것이므로 실패로 세지 않는다.
//      k6 run load/scenarios/write-apply.js                     # 동시 50
//      CONCURRENCY=200 k6 run load/scenarios/write-apply.js     # 전원 동시(썬더링 허드)
// 이미 신청한 회원은 409(APPLICATION_ALREADY_EXISTS)가 정상이다. 시드가 신청을 만들어 두므로 대부분 409가 된다
allowBusinessStatuses();

const CONCURRENCY = Number(__ENV.CONCURRENCY || 50);
const APPLICANTS = Number(__ENV.APPLICANTS || 200);

export const options = {
  scenarios: {
    apply_burst: {
      // 전체 APPLICANTS건을 CONCURRENCY명이 나눠 처리한다. 동시 실행 수가 곧 실험 변수다
      executor: 'shared-iterations',
      vus: CONCURRENCY,
      iterations: APPLICANTS,
      maxDuration: '3m',
    },
  },
  thresholds: {
    // 위 allowBusinessStatuses()로 409를 정상 처리했으므로 남은 실패는 5xx·401·403뿐이다
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1500'],
  },
};

export default function () {
  // 반복마다 다른 회원을 쓴다(__VU는 동시 실행 수만큼만 있어 겹친다). iterationInTest는 테스트 전체에서 고유하다
  const userId = USER_ID_FROM + (exec.scenario.iterationInTest % USER_COUNT);
  const headers = authHeaders(userId, JWT_SECRET);
  const token = csrfToken(headers);
  const writeHeaders = Object.assign({}, headers, {
    'Content-Type': 'application/json',
    'X-XSRF-TOKEN': token,
    Cookie: `${headers.Cookie}; XSRF-TOKEN=${token}`,
  });

  const body = JSON.stringify({
    instagramId: `load.${userId}`,
    nickname: `부하${userId}`,
    gender: userId % 2 === 0 ? 'M' : 'F',
    ageBand: '22-24',
    tags: ['MUSIC'],
    intro: '부하 테스트',
    termsVersion: 'v1',
    privacyVersion: 'v1',
    ageConfirmed: true,
  });

  // 이 시나리오는 회차가 OPEN이고 마감 전일 때만 의미가 있다(마감 후에는 전부 409 MATCH_ROUND_NOT_OPEN)
  const created = http.post(`${BASE_URL}/api/v1/match/applications`, body, {
    headers: writeHeaders,
    tags: { endpoint: '/match/applications' },
  });
  check(created, { '신청이 201 또는 409(중복)': (r) => r.status === 201 || r.status === 409 });

  const patched = http.patch(`${BASE_URL}/api/v1/match/applications/me`, body, {
    headers: writeHeaders,
    tags: { endpoint: '/match/applications/me' },
  });
  check(patched, { '수정이 5xx가 아니다': (r) => r.status < 500 });
}

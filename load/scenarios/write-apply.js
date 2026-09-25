import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, JWT_SECRET, USER_COUNT, USER_ID_FROM, allowBusinessStatuses, csrfToken } from '../lib/common.js';
import { authHeaders } from '../lib/token.js';

// 6) 쓰기 부하(마감 직전): 200명이 30초 안에 CSRF 발급 → 신청 → 수정을 한다.
//    유니크 제약(회차당 1건·인스타 ID)과 잠금 경합을 본다. 회차가 OPEN이고 마감 전일 때만 의미가 있다.
//    409(이미 신청함)는 규칙대로 동작한 것이므로 실패로 세지 않는다.
// 이미 신청한 회원은 409(APPLICATION_ALREADY_EXISTS)가 정상이다. 시드가 신청을 만들어 두므로 대부분 409가 된다
allowBusinessStatuses();

export const options = {
  scenarios: {
    apply_burst: {
      executor: 'per-vu-iterations',
      vus: 200,
      iterations: 1,
      maxDuration: '1m',
    },
  },
  thresholds: {
    // 위 allowBusinessStatuses()로 409를 정상 처리했으므로 남은 실패는 5xx·401·403뿐이다
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1500'],
  },
};

export default function () {
  const userId = USER_ID_FROM + ((__VU - 1) % USER_COUNT);
  const headers = authHeaders(userId, JWT_SECRET);
  const token = csrfToken(headers);
  const writeHeaders = Object.assign({}, headers, {
    'Content-Type': 'application/json',
    'X-XSRF-TOKEN': token,
    Cookie: `${headers.Cookie}; XSRF-TOKEN=${token}`,
  });

  const body = JSON.stringify({
    instagramId: `load.${userId}`,
    nickname: `부하${__VU}`,
    gender: __VU % 2 === 0 ? 'M' : 'F',
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

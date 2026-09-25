import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, JWT_SECRET, USER_COUNT, USER_ID_FROM, allowBusinessStatuses } from '../lib/common.js';
import { authHeaders } from '../lib/token.js';

// 3) 발표 스파이크: NFR-PF-01(발표 직후 1,000명 결과 조회 p95 1초)을 그대로 재현한다.
//    10초 안에 1,000 VU가 붙어 각자 결과를 한 번 조회하고, 이어 3분간 홈을 5초 주기로 폴링한다.
//    발표(publish) 직후에 실행해야 의미가 있다.
// 신청이 없는 회원은 404, 발표 전이면 409. 규칙대로 동작한 것이므로 오류율에서 제외한다
allowBusinessStatuses();

export const options = {
  scenarios: {
    result_burst: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 1000 }, // 발표 알림을 보고 몰려드는 구간
        { duration: '3m', target: 1000 },  // 결과 확인 후 홈 폴링이 이어지는 구간
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{endpoint:/match/results/me}': ['p(95)<1000'],
    'http_req_duration{endpoint:/match/summary}': ['p(95)<1000'],
  },
};

export default function () {
  const userId = USER_ID_FROM + ((__VU - 1) % USER_COUNT);
  const headers = authHeaders(userId, JWT_SECRET);

  if (__ITER === 0) {
    // 발표 순간의 결과 조회. 404(신청 없음)·409(발표 전)도 서버가 정상 처리한 것이므로 실패로 보지 않는다
    const result = http.get(`${BASE_URL}/api/v1/match/results/me`, {
      headers,
      tags: { endpoint: '/match/results/me' },
    });
    check(result, { '결과 조회가 5xx가 아니다': (r) => r.status < 500 });
  } else {
    const summary = http.get(`${BASE_URL}/api/v1/match/summary`, {
      headers,
      tags: { endpoint: '/match/summary' },
    });
    check(summary, { '홈 폴링 200': (r) => r.status === 200 });
  }
  sleep(5); // 프론트 폴링 주기
}

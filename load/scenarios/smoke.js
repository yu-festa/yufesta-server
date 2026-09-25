import { sleep } from 'k6';
import { BASE_URL, JWT_SECRET, USER_ID_FROM, allowBusinessStatuses, get } from '../lib/common.js';
import { authHeaders } from '../lib/token.js';
import http from 'k6/http';
import { check } from 'k6';

// 1) 연기 테스트: 모든 읽기 엔드포인트와 토큰·CSRF가 실제로 동작하는지 1분만 확인한다.
// 본 시나리오를 돌리기 전에 항상 먼저 실행한다.
export const options = {
  vus: 5,
  duration: '1m',
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<1000'] },
};

// 내 신청이 없으면 404가 정상이다
allowBusinessStatuses();

export default function () {
  get('/api/v1/match/summary');
  get('/api/v1/match/slots');
  get('/api/v1/timetable');
  get('/api/v1/clubs');
  get('/api/v1/places');
  get('/api/v1/notices');

  if (JWT_SECRET && USER_ID_FROM > 0) {
    const headers = authHeaders(USER_ID_FROM, JWT_SECRET);
    const me = http.get(`${BASE_URL}/api/v1/auth/me`, { headers, tags: { endpoint: '/auth/me' } });
    check(me, { '토큰으로 로그인 상태 확인': (r) => r.status === 200 });
    const mine = http.get(`${BASE_URL}/api/v1/match/applications/me`, { headers, tags: { endpoint: '/applications/me' } });
    check(mine, { '내 신청 조회 200 또는 404': (r) => r.status === 200 || r.status === 404 });
  }
  sleep(1);
}

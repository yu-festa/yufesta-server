import http from 'k6/http';
import { check } from 'k6';

// 모든 시나리오가 공유하는 설정. 대상·비밀은 명령줄 -e 로 받는다.
export const BASE_URL = __ENV.BASE_URL || 'https://api.yufesta.com';
export const JWT_SECRET = __ENV.JWT_SECRET || '';
// seed.sql이 만든 합성 회원 수. 회원 ID는 USER_ID_FROM 부터 연속이라고 본다
export const USER_COUNT = Number(__ENV.USER_COUNT || 1000);
export const USER_ID_FROM = Number(__ENV.USER_ID_FROM || 0);

// NFR-PF-01: 발표 직후 결과 조회 p95 1초. 오류율 1% 미만
export const THRESHOLDS = {
  http_req_failed: ['rate<0.01'],
  http_req_duration: ['p(95)<1000'],
};

// VU·반복마다 다른 합성 회원을 쓴다(캐시·커넥션이 한 회원에 쏠리지 않게)
export function pickUserId() {
  if (USER_ID_FROM > 0) {
    return USER_ID_FROM + ((__VU * 1000 + __ITER) % USER_COUNT);
  }
  return null;
}

export function get(path, params = {}) {
  const response = http.get(`${BASE_URL}${path}`, { tags: { endpoint: path }, ...params });
  check(response, { [`${path} 2xx`]: (r) => r.status >= 200 && r.status < 300 });
  return response;
}

// k6는 기본적으로 200~399만 성공으로 본다. 규칙대로 동작한 4xx(신청 없음 404, 중복·발표 전 409)를
// 실패로 세면 오류율 임계값이 거짓으로 깨진다. 이 시나리오들에서만 허용 목록을 넓힌다.
// 401·403·5xx는 그대로 실패로 남긴다(인증이 깨졌거나 서버가 죽은 것이므로 반드시 보여야 한다).
export function allowBusinessStatuses() {
  http.setResponseCallback(http.expectedStatuses({ min: 200, max: 399 }, 404, 409));
}

// 쓰기 요청 전에 CSRF 토큰을 받아 온다. 쿠키 자(jar)에 담기므로 헤더로 되돌려 보낸다
export function csrfToken(headers) {
  const response = http.get(`${BASE_URL}/api/v1/auth/csrf`, { headers, tags: { endpoint: '/auth/csrf' } });
  const cookie = response.cookies['XSRF-TOKEN'];
  return cookie && cookie.length > 0 ? cookie[0].value : '';
}

import { sleep } from 'k6';
import http from 'k6/http';
import { BASE_URL, JWT_SECRET, THRESHOLDS, get, pickUserId } from '../lib/common.js';
import { authHeaders } from '../lib/token.js';

// 2) 평균 부하(load) · 7) 장시간(soak). 축제 당일 읽기 믹스를 초당 고정 건수로 흘린다.
//    RATE(기본 300 req/s)와 DURATION(기본 10m)으로 두 용도를 겸한다. soak은 RATE 절반에 30m.
//    도착률 기반(arrival-rate)이라 서버가 느려져도 부하가 줄지 않아 한계가 드러난다.
const RATE = Number(__ENV.RATE || 300);
const DURATION = __ENV.DURATION || '10m';
// 요약 요청 가운데 로그인 상태로 보내는 비율. 당일 홈을 폴링하는 사람은 대부분 로그인 상태라 실제는 1에 가깝다.
// 로그인 요청은 인증과 내 상태 계산이 붙어 비로그인보다 무겁다(README 2026-09-28). 기본 0.5는 이전 측정과 맞춘 값이다
const LOGIN_RATIO = Number(__ENV.LOGIN_RATIO || 0.5);

export const options = {
  scenarios: {
    read_mix: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.max(50, Math.ceil(RATE / 2)),
      maxVUs: Math.max(200, RATE * 3),
    },
  },
  thresholds: THRESHOLDS,
};

export default function () {
  // 홈 폴링이 가장 잦다(1,000명이 5초 주기로 부르면 상시 200 req/s)
  const dice = Math.random();
  const userId = pickUserId();
  const headers = JWT_SECRET && userId && Math.random() < LOGIN_RATIO ? authHeaders(userId, JWT_SECRET) : {};

  if (dice < 0.4) {
    http.get(`${BASE_URL}/api/v1/match/summary`, { headers, tags: { endpoint: '/match/summary' } });
  } else if (dice < 0.6) {
    get('/api/v1/timetable');
  } else if (dice < 0.7) {
    get('/api/v1/clubs');
  } else if (dice < 0.8) {
    get('/api/v1/places');
  } else if (dice < 0.9) {
    get('/api/v1/notices');
  } else {
    get('/api/v1/cheers?size=20');
  }
  sleep(0.1);
}

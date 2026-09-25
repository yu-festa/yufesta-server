import http from 'k6/http';
import { BASE_URL, JWT_SECRET, pickUserId } from '../lib/common.js';
import { authHeaders } from '../lib/token.js';

// 4) 스트레스: 읽기 믹스를 300 → 600 → 1,200 → 2,400 req/s로 단계마다 3분씩 올린다.
//    임계값(p95 1초·오류 1%)이 처음 깨지는 단계가 현재 스펙의 한계다. 그때 CloudWatch·액추에이터 지표를 같이 본다.
export const options = {
  scenarios: {
    stress: {
      executor: 'ramping-arrival-rate',
      startRate: 300,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 3000,
      stages: [
        { duration: '3m', target: 300 },
        { duration: '30s', target: 600 },
        { duration: '3m', target: 600 },
        { duration: '30s', target: 1200 },
        { duration: '3m', target: 1200 },
        { duration: '30s', target: 2400 },
        { duration: '3m', target: 2400 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  // 단계별로 끊어 보려면 임계값을 abortOnFail 없이 둔다(끝까지 돌리고 요약에서 판독)
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
  const userId = pickUserId();
  const headers = JWT_SECRET && userId && Math.random() < 0.5 ? authHeaders(userId, JWT_SECRET) : {};
  const dice = Math.random();

  if (dice < 0.5) {
    http.get(`${BASE_URL}/api/v1/match/summary`, { headers, tags: { endpoint: '/match/summary' } });
  } else if (dice < 0.75) {
    http.get(`${BASE_URL}/api/v1/timetable`, { tags: { endpoint: '/timetable' } });
  } else {
    http.get(`${BASE_URL}/api/v1/clubs`, { tags: { endpoint: '/clubs' } });
  }
}

import http from 'k6/http';
import { BASE_URL, JWT_SECRET, USER_COUNT, USER_ID_FROM } from '../lib/common.js';
import { authHeaders } from '../lib/token.js';

// 5) 한계점 탐색: 결과 조회 하나만 100 req/s에서 시작해 2분마다 200씩 올린다.
//    임계값을 넘는 순간 중단(abortOnFail)하므로 "결과 조회가 몇 req/s까지 버티는지"가 바로 나온다.
//    Redis 결과 프리워밍 도입 여부를 이 숫자로 판단한다.
export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-arrival-rate',
      startRate: 100,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 4000,
      stages: [
        { duration: '2m', target: 100 },
        { duration: '2m', target: 300 },
        { duration: '2m', target: 500 },
        { duration: '2m', target: 700 },
        { duration: '2m', target: 900 },
        { duration: '2m', target: 1100 },
      ],
    },
  },
  thresholds: {
    http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '30s' }],
    http_req_duration: [{ threshold: 'p(95)<1000', abortOnFail: true, delayAbortEval: '30s' }],
  },
};

export default function () {
  const userId = USER_ID_FROM + (__ITER % USER_COUNT);
  http.get(`${BASE_URL}/api/v1/match/results/me`, {
    headers: authHeaders(userId, JWT_SECRET),
    tags: { endpoint: '/match/results/me' },
  });
}

import crypto from 'k6/crypto';
import encoding from 'k6/encoding';

// 서버(JwtTokenProvider)와 같은 형식의 access_token을 만든다.
// 서버는 HS256 서명과 exp, uid 클레임만 본다. 합성 회원 1,000명으로 로그인 상태를 흉내 내기 위한 것이며
// 비밀은 파일에 두지 않고 실행 셸의 JWT_SECRET 환경변수로만 넘긴다.
function base64url(value) {
  return encoding.b64encode(value, 'rawurl');
}

export function accessToken(userId, secret, ttlSeconds = 7200) {
  const now = Math.floor(Date.now() / 1000);
  const header = base64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = base64url(JSON.stringify({ iat: now, exp: now + ttlSeconds, uid: userId }));
  const signingInput = `${header}.${payload}`;
  const signature = crypto.hmac('sha256', secret, signingInput, 'base64rawurl');
  return `${signingInput}.${signature}`;
}

// 쿠키 인증이라 헤더로 직접 붙인다(k6 쿠키 자에 의존하지 않아 VU마다 다른 회원을 쓰기 쉽다)
export function authHeaders(userId, secret) {
  return { Cookie: `access_token=${accessToken(userId, secret)}` };
}

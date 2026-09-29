// 다른 API 영향 측정 (S3). upload.js 와 별도 프로세스로 동시에 실행한다. 사용법은 README.md.
// 자기 사용자·빈 앨범을 만들어 쓰므로 업로드 진행과 관계없이 기준과 부하 중의 조건이 같다.
import http from 'k6/http';
import { check, fail } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

export const options = {
  scenarios: {
    list: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.LIST_RPS || 10),
      timeUnit: '1s',
      duration: __ENV.LIST_DURATION || '60s',
      preAllocatedVUs: 20,
      // 서버가 느려지면 응답을 기다리는 VU 가 늘어난다. 도착 속도를 지키려면 넉넉해야 한다.
      maxVUs: 200,
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'max'],
};

export function setup() {
  const runId = Date.now();
  const signup = http.post(`${BASE_URL}/api/auth/signup`, JSON.stringify({
    email: `lt-list-${runId}@facepick.test`, password: 'loadtest-1234', nickname: 'ltlist',
  }), { headers: { 'Content-Type': 'application/json' } });
  if (signup.status >= 300) fail(`signup → ${signup.status} ${signup.body}`);
  const token = signup.json().accessToken;
  const album = http.post(`${BASE_URL}/api/albums`, JSON.stringify({ title: `list ${runId}` }), {
    headers: { 'Content-Type': 'application/json', Authorization: token },
  });
  if (album.status >= 300) fail(`album → ${album.status} ${album.body}`);
  return { token, albumId: album.json().albumId };
}

export default function (data) {
  const res = http.get(`${BASE_URL}/api/albums/${data.albumId}/photos?size=30`, {
    headers: { Authorization: data.token },
    tags: { name: 'photo-list' },
  });
  check(res, { '목록 200': (r) => r.status === 200 });
}

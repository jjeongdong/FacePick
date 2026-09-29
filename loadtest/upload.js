// 업로드 부하 측정 (S1·S2·S4). 사용법은 README.md.
import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { open as openFile, SeekMode } from 'k6/experimental/fs';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const VUS = Number(__ENV.VUS || 1);
const USERS = Number(__ENV.USERS || VUS);
const PER_VU = Number(__ENV.PER_VU || 20);
const RETRY_AFTER_SECONDS = Number(__ENV.RETRY_AFTER_SECONDS || 0);

const manifest = JSON.parse(open('./photos/manifest.json'));
if (manifest.length < VUS * PER_VU) {
  throw new Error(`사진이 ${VUS * PER_VU}장 필요한데 ${manifest.length}장뿐입니다. prepare_photos.py 로 더 만드세요.`);
}
// open() 은 init 에서 모든 VU 가 같은 파일을 열어야 하고 VU 마다 메모리에 복사한다.
// fs 모듈은 파일 내용을 VU 사이에 한 벌만 두므로 100장 × VU 수만큼 메모리가 불어나지 않는다.
const photos = await Promise.all(
  manifest.slice(0, VUS * PER_VU).map(async (entry) =>
    Object.assign({}, entry, { handle: await openFile(`./photos/${entry.file}`) })),
);

async function readAll(handle, size) {
  const buffer = new Uint8Array(size);
  await handle.seek(0, SeekMode.Start);
  let offset = 0;
  while (offset < size) {
    const read = await handle.read(buffer.subarray(offset));
    if (read === null) break;
    offset += read;
  }
  return buffer.buffer;
}

const completeDuration = new Trend('complete_duration', true);
const completeOk = new Rate('complete_ok');
const uploadTotal = new Trend('upload_total_duration', true);
const retryOk = new Rate('retry_ok');

export const options = {
  scenarios: {
    upload: { executor: 'per-vu-iterations', vus: VUS, iterations: 1, maxDuration: '60m' },
  },
  setupTimeout: '2m',
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'max'],
};

function api(method, path, body, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = token;
  const res = http.request(method, `${BASE_URL}${path}`, body === null ? null : JSON.stringify(body), {
    headers,
    tags: { name: path.replace(/\d+/g, ':id') },
  });
  if (res.status >= 300) fail(`${method} ${path} → ${res.status} ${res.body}`);
  return res.json();
}

export function setup() {
  const runId = Date.now();
  const tokens = [];
  for (let i = 0; i < USERS; i++) {
    const user = api('POST', '/api/auth/signup', {
      email: `lt-${runId}-${i}@facepick.test`,
      password: 'loadtest-1234',
      nickname: `lt${i}`,
    });
    tokens.push(user.accessToken);
  }
  const album = api('POST', '/api/albums', { title: `loadtest ${runId}` }, tokens[0]);
  for (let i = 1; i < USERS; i++) {
    api('POST', '/api/albums/join', { inviteCode: album.inviteCode }, tokens[i]);
  }
  console.log(`albumId=${album.albumId} users=${USERS} vus=${VUS} perVu=${PER_VU}`);
  return { albumId: album.albumId, tokens };
}

function complete(photoId, token) {
  return http.post(`${BASE_URL}/api/photos/${photoId}/complete`, null, {
    headers: { Authorization: token },
    // 동기 방식은 썸네일(30초)·얼굴 분석(60초) 타임아웃까지 기다릴 수 있어 k6 기본 60초보다 길게 둔다.
    timeout: '180s',
    tags: { name: 'complete' },
  });
}

export default async function (data) {
  const token = data.tokens[(__VU - 1) % USERS];
  const myFiles = photos.slice((__VU - 1) * PER_VU, __VU * PER_VU);
  const startedAt = Date.now();
  const uploads = api('POST', `/api/albums/${data.albumId}/photos/uploads`, {
    files: myFiles.map((f) => ({ contentHash: f.sha256, byteSize: f.size, contentType: 'image/jpeg' })),
  }, token);
  const byHash = {};
  for (const file of uploads.files) byHash[file.contentHash] = file;

  const failed = [];
  for (const f of myFiles) {
    const target = byHash[f.sha256];
    if (target.status === 'UPLOAD_REQUIRED') {
      const put = http.put(target.uploadUrl, await readAll(f.handle, f.size), {
        headers: { 'Content-Type': 'image/jpeg' },
        tags: { name: 'storage-put' },
      });
      check(put, { 'PUT 200': (r) => r.status === 200 });
    }
    const res = complete(target.photoId, token);
    completeDuration.add(res.timings.duration);
    completeOk.add(res.status === 200);
    if (res.status !== 200) failed.push(target.photoId);
  }
  uploadTotal.add(Date.now() - startedAt);

  if (RETRY_AFTER_SECONDS > 0 && failed.length > 0) {
    console.log(`VU ${__VU}: complete 실패 ${failed.length}장. ${RETRY_AFTER_SECONDS}초 안에 워커를 켜 주세요.`);
    sleep(RETRY_AFTER_SECONDS);
    for (const photoId of failed) retryOk.add(complete(photoId, token).status === 200);
  }
}

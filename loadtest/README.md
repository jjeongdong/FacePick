# 동기(HTTP) vs 이벤트(Kafka) 측정

설계: `docs/superpowers/specs/2026-09-29-sync-vs-event-pipeline-design.md` (로컬 문서). 결과는 `RESULTS.md`.

## 준비 (한 번)

1. `brew install k6`
2. 얼굴이 나온 JPEG 약 20장을 `loadtest/source/` 에 넣고 `python3 loadtest/prepare_photos.py 100`
3. 워커 노트북: `git pull && cd face-worker && uv sync`, `.env` 에 `HTTP_PORT=8092`. 노트북 IP 는 `ipconfig getifaddr en0`.
4. 맥: `cd thumbnail-worker && uv sync`
5. 측정 중에는 8080 백엔드를 꺼 두는 것을 권한다 (outbox 를 함께 발행해 event 수치가 흔들린다).

## 모드별 실행

| | event | sync |
|---|---|---|
| 백엔드 (맥) | `./gradlew bootRun --args='--spring.profiles.active=local --server.port=8081'` | `./gradlew bootRun --args='--spring.profiles.active=local --server.port=8081 --facepick.pipeline.mode=sync --facepick.pipeline.face-url=http://<노트북IP>:8092'` |
| thumbnail-worker (맥) | `uv run python -m thumbnail_worker` | `uv run python -m thumbnail_worker --http` |
| face-worker (노트북) | `uv run python -m face_worker` | `uv run python -m face_worker --http` |

S3 의 작은 서버 조건은 백엔드 인자에 `--server.tomcat.threads.max=20` 을 더한다.

## 시나리오 (`loadtest/` 에서, 모드마다)

결과 원문은 `results/` 에 남긴다: `mkdir -p results`.

- **S1-① 1명 100장, 동시 4** — 터미널 A: `./wait_analyzed.sh 100` → 터미널 B: `VUS=4 USERS=1 PER_VU=25 k6 run upload.js | tee results/s1a-<모드>.txt`
- **S1-② 5명 20장씩** — A: `./wait_analyzed.sh 100` → B: `VUS=5 USERS=5 PER_VU=20 k6 run upload.js | tee results/s1b-<모드>.txt`
- **S2 워커 장애** — face-worker 를 끈 채 `VUS=1 PER_VU=20 RETRY_AFTER_SECONDS=90 k6 run upload.js | tee results/s2-<모드>.txt`, k6 가 "워커를 켜 주세요" 를 출력하면 90초 안에 face-worker 를 켠다. 끝나면 `results.sql` 로 20장 모두 분석됐는지 확인 (event 는 재시도 없이 분석돼야 한다).
- **S3 다른 API 영향** — 기준: `k6 run list.js | tee results/s3-base-<모드>.txt`. 부하: 터미널 A `k6 run list.js | tee results/s3-load-<모드>.txt` 를 켜고 곧바로 B 에서 S1-② 를 실행. `photo-list` 의 p(95) 를 비교한다.
- **S4 분석 완료 시간** — S1 의 `wait_analyzed.sh` 출력 초.

`complete_duration`·`complete_ok`·`upload_total_duration`·`retry_ok` 가 이 스크립트가 만든 지표다. `wait_analyzed.sh` 는 psql 을 1초마다 불러 ±1~2초 오차가 있다.

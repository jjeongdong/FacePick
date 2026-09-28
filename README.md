# facepick

여러 명이 함께 찍은 사진을 AI가 인물별로 분류해, 각자 자기 사진만 받아 가는 서비스.

## 구조

```
facepick/
├── infra/              # 내 컴퓨터: PostgreSQL(pgvector) · S3 스토리지(SeaweedFS) · Kafka
├── backend/            # 내 컴퓨터: Spring Boot 4 (Java 21) API 서버. 컨벤션은 backend/CLAUDE.md
│   └── src/main/java/com/back/facepick/
│       ├── album/      # BC: 앨범 생성·조회
│       ├── photo/      # BC: 업로드 URL 발급, 업로드 완료 → photo.uploaded 발행, 사진 조회·다운로드
│       ├── person/     # BC: 인물 목록, 인물별 사진 (얼굴 데이터 포함)
│       └── global/     # error, response, persistence, config
│       (각 BC 는 presentation / application / domain / infrastructure)
├── thumbnail-worker/   # 내 컴퓨터: Python. photo.uploaded → 썸네일·미리보기 → photo.preview_ready
└── face-worker/        # 워커 노트북: Python + InsightFace. photo.preview_ready → 얼굴 분석 → 인물 배정
```

## 포트

| 서비스 | 포트 |
| --- | --- |
| PostgreSQL | 5433 (기존 5432와 충돌 방지) |
| S3 스토리지 (SeaweedFS) | 9000 |
| Kafka | 9092 |
| backend | 8080 |

## 실행

```bash
# 인프라
cd infra && cp .env.example .env && docker compose up -d

# 백엔드
cd backend && ./gradlew bootRun

# 워커 (각 폴더에서)
cp .env.example .env && uv sync && uv run main.py
```

## 참고

- MinIO 커뮤니티판은 이미지 배포가 중단되어 S3 호환인 SeaweedFS를 쓴다. 베타부터 Cloudflare R2로 전환 예정.
- 워커 노트북에서 접속하려면 `infra/.env`의 `HOST_ADDR`를 내 컴퓨터 IP로 바꾼다 (Kafka advertised.listeners).

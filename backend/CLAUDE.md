# facepick 백엔드

여러 명이 함께 찍은 사진을 AI 가 인물별로 분류해, 각자 자기 사진만 받아 가는 서비스의 메인 API 서버.
Spring Boot 4.1 · Java 21 · JPA · PostgreSQL(pgvector) · Flyway · Kafka · S3 호환 스토리지(SeaweedFS → R2).
코드 컨벤션은 catchmate 백엔드와 같다.

## 명령어
- 전체 검사: `./gradlew build` (spotlessCheck + test)
- 포맷: `./gradlew spotlessApply` — 커밋 전 필수
- 아키텍처 검사: `./gradlew test --tests 'com.back.facepick.architecture.*'`
- 로컬 인프라: `cd ../infra && docker compose up -d` (PostgreSQL 5433, S3 9000, Kafka 9092)

## 아키텍처 한눈에
- DDD 4계층, 도메인 우선 패키지: `{bc}/{presentation,application,domain,infrastructure}` + `global`
- 의존: Presentation → Application → Domain ← Infrastructure
- BC: `album`(앨범), `photo`(사진·업로드), `person`(인물·얼굴). 아키텍처 검사는 `global` 을 뺀 최상위 패키지를 자동으로 BC 로 잡는다

## 절대 규칙
1. 타 BC 는 `{Bc}QueryApi`, `application/dto/api`, `domain/event` 만 사용한다. 타 BC 상태 변경은 이벤트로.
2. 타 BC 엔티티는 ID 로만 참조한다. 타 BC 테이블 JOIN 금지 (측정 근거가 있는 예외만).
3. 비즈니스 규칙은 엔티티에. Service 는 흐름만.
4. `@Transactional` 은 Application 계층 public 메서드에만, 메서드마다.
5. DTO 는 `record`, 흐름은 Request → Command → Result.
6. 에러마다 전용 예외 클래스 + BC 별 ErrorCode enum. 도메인은 `HttpStatus` 를 모른다.
7. Lombok 은 빈 `@RequiredArgsConstructor`/`@Slf4j`, 엔티티 `@Getter`/`@NoArgsConstructor(PROTECTED)` 만.
8. `var`, `TODO`, 와일드카드 import, `Optional.get()`, `System.out` 금지.
9. 주석·로그·커밋 메시지는 한국어. 주석은 "왜"만.
10. `main` 직접 커밋 금지. 브랜치 작업 후 `git merge --no-ff`.

## facepick 고유 사항
- 스키마는 Flyway(`src/main/resources/db/migration`)로만 바꾼다. Python 워커(`../thumbnail-worker`, `../face-worker`)가 같은 테이블을 읽고 쓰므로 `ddl-auto` 는 `validate` 로 둔다.
- 워커가 쓰는 테이블·컬럼(썸네일 키, 얼굴, 인물)을 바꿀 때는 워커 코드도 같이 확인한다.
- Kafka 토픽: `photo.uploaded`(백엔드 발행 → 썸네일 워커), `photo.preview_ready`(썸네일 워커 발행 → 얼굴 분석 워커). 메시지 키는 `album_id`.
- 이벤트 발행은 트랜잭션 커밋 뒤에 한다. 워커가 아직 커밋되지 않은 행을 읽지 않게 하기 위해서다.

## 규칙 문서 (`.claude/rules/`)
| 파일 | 로드 시점 | 내용 |
|---|---|---|
| `architecture.md` | 항상 | 계층, 패키지, 접미사, BC 경계, global |
| `git.md` | 항상 | 커밋, 브랜치, 머지 |
| `presentation.md` | `presentation/` 편집 | Controller, Request, URL, 상태 코드, 페이징, 에러 응답 |
| `application.md` | `application/` 편집 | Service, QueryApi, 트랜잭션, DTO, 이벤트 리스너 |
| `domain.md` | `domain/` 편집 | 엔티티, Repository 인터페이스, 에러 코드·예외, 이벤트 |
| `infrastructure.md` | `infrastructure/` 편집 | Repository 구현, 쿼리 |
| `code-style.md` | `*.java` 편집 | 포맷, Lombok, Java 문법, 이름, 로깅, 주석 |
| `testing.md` | `src/test/` 편집 | 테스트 전략·스타일 |

규칙이 다루지 않거나 규칙끼리 충돌하는 상황에서는 임의로 판단하지 말고 사용자에게 묻는다.

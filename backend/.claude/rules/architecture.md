# 아키텍처 규칙

## 계층 (DDD 4계층)

| 계층 | 패키지 | 책임 |
|---|---|---|
| Presentation | `{bc}.presentation` | HTTP 수신, 형식 검증, Request → Command, 응답 |
| Application | `{bc}.application` | 유스케이스 흐름, 트랜잭션, 타 BC 조회 조합, 이벤트 발행 |
| Domain | `{bc}.domain` | 엔티티, 비즈니스 규칙, Repository 인터페이스, ErrorCode·예외, 이벤트 |
| Infrastructure | `{bc}.infrastructure` | Repository 구현, JPA, Kafka·S3 연동, 외부 API |

- 의존: `Presentation → Application → Domain ← Infrastructure`
- Domain 은 다른 계층과 `org.springframework.web|http|data` 를 import 하지 않는다. `jakarta.persistence` 만 허용.
- Presentation 이 Domain 에서 쓸 수 있는 것은 enum 뿐 (Request 필드 타입). 엔티티·예외·Repository 금지.
- Repository 는 DIP: `domain/{Entity}Repository`(인터페이스) ← `infrastructure/{Entity}RepositoryImpl` + `infrastructure/{Entity}JpaRepository`.
- 도메인 모델 = JPA 엔티티.

## 패키지

```
{bc}/
├── presentation/  {Bc}Controller, Admin{Bc}Controller, {Bc}ApiDocs, dto/request/
├── application/   {Bc}CommandService, {Bc}QueryService, {Bc}QueryApi, dto/{command,result,api}/, event/
├── domain/        엔티티, {Entity}Repository, {Bc}ErrorCode, exception/, event/
└── infrastructure/ {Entity}RepositoryImpl, {Entity}JpaRepository, 기술 연동
global/            config, error, response, persistence, security, infrastructure
```

## 예약된 클래스 접미사 (ArchUnit 이 위치를 검사)

| 접미사 | 위치 |
|---|---|
| `CommandService`, `QueryService`, `QueryApi` | `application` |
| `Controller`, `ApiDocs` | `presentation` |
| `Request` | `presentation.dto.request` |
| `Command` / `Result` / `Info` | `application.dto.command` / `.dto.result` / `.dto.api` |
| `Listener` | `application.event` |
| `Event` | `domain.event` |
| `Exception` | `domain.exception` |
| `ErrorCode` | `domain` |
| `RepositoryImpl`, `JpaRepository` | `infrastructure` |

이 접미사를 다른 용도의 클래스 이름에 쓰지 않는다. 중첩 클래스(예: Result 안의 `WriterInfo` record)는 검사 대상이 아니다.

## 바운디드 컨텍스트(BC) 경계

각 최상위 패키지(`album`, `photo`, `person` …)는 독립 BC 다. ArchUnit·소스 규칙 검사는 `global` 을 뺀 최상위 패키지를 `BoundedContexts` 가 자동으로 찾아 모두 검사한다 — 새 BC 는 패키지를 만드는 순간 검사 대상이다.

- 타 BC 에서 쓸 수 있는 것: `{bc}.application.{Bc}QueryApi`, `{bc}.application.dto.api..`, `{bc}.domain.event..` 뿐.
- 타 BC 엔티티는 ID 로만 참조. BC 간 `@ManyToOne`/`@OneToOne` 금지.
- 타 BC 호출은 조회만 (`QueryApi`). 타 BC 상태 변경은 도메인 이벤트로 — 발행 측은 수신 측을 모른다.
- 양방향 **명령** 호출 금지 — 상태 변경은 한쪽을 이벤트로. 조회는 `QueryApi` 로 양방향 허용하되, `QueryApi` 는 타 BC 를 의존하지 않는다 (`application.dto.api` 제외, ArchUnit 이 검사).
- 타 BC 테이블 SQL JOIN 금지. 예외는 성능 문제가 측정으로 확인된 경우만: 클래스에 사유·측정 근거 주석 + `BoundedContexts.CROSS_CONTEXT_ALLOWLIST` 에 FQCN 등록.

### `{Bc}QueryApi`
- 인터페이스 없이 구체 클래스 하나. 반환은 `dto/api/{Domain}Info` record (엔티티 반환 금지).
- 기본 제공: `getInfo(Long id)`, `getInfos(Collection<Long> ids) → Map<Long, {Domain}Info>`.
- public 메서드는 Javadoc 필수 — 타 BC 와의 계약이다.
- 자기 Controller 용 `CommandService`/`QueryService` 는 타 BC 에서 호출하지 않는다.
- 자기 BC 의 Repository·도메인과 `global`, 타 BC 의 `dto/api` 만 쓴다. 타 BC `QueryApi` 를 주입하는 것은 `QueryService`·`CommandService` 뿐이라 빈 순환이 생기지 않는다.

## global
- 비즈니스 로직(특정 BC 개념) 금지. `global` → BC import 금지.
- 두 개 이상의 BC 가 쓰기 전에는 `global` 로 올리지 않는다.
- `global` 은 BC 의 어느 계층에서든 사용할 수 있다. 계층 의존 규칙은 BC 패키지에만 적용된다 (예: Application Service 가 `global/infrastructure` 의 업로드 기능 호출 가능).
- `common` 같은 별도 공용 패키지를 만들지 않는다. BC 공용은 `global` 에 둔다.

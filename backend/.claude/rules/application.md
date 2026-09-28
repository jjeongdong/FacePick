---
paths:
  - "**/application/**"
---

# Application 계층 규칙

## 클래스
- `{Bc}CommandService`: 생성·수정·삭제·도메인 동작 (자기 Controller 용).
- `{Bc}QueryService`: 자기 Controller 용 조회와 화면 응답 조립.
- `{Bc}QueryApi`: 타 BC 용 공개 조회. 반환은 `dto/api/{Domain}Info`. public 메서드 Javadoc 필수.
- 빈은 `@Service` + `@RequiredArgsConstructor`, 필드는 `private final`.

## 역할
- 흐름만: 조회 → 도메인 메서드 호출 → 저장/이벤트 발행. if-throw 같은 비즈니스 규칙은 엔티티에.
- 검증하지 않는다 (형식은 Request, 규칙은 엔티티).
- 타 BC 정보가 필요한 규칙은 `QueryApi` 로 조회한 값을 도메인 메서드 인자로 넘긴다.

```java
@Transactional
public void acceptEnroll(Long userId, Long enrollId) {
    Enroll enroll = enrollRepository.getById(enrollId);
    BoardInfo board = boardQueryApi.getInfo(enroll.getBoardId());
    enroll.accept(userId, board.writerId());
    eventPublisher.publishEvent(new EnrollAcceptedEvent(enroll.getId(), enroll.getBoardId(), enroll.getUserId()));
}
```

## 트랜잭션
- `@Transactional` 은 이 계층(Service, QueryApi, 이벤트 리스너)의 public 메서드에만, 메서드마다.
- 조회는 `@Transactional(readOnly = true)`. 클래스 레벨 금지.

## 메서드 이름
| 동작 | 이름 |
|---|---|
| 생성 / 수정 / 삭제 | `createBoard` / `updateBoard` / `deleteBoard` |
| 단건 / 목록 | `getBoard` / `getBoards`, `getMyBoards` |
| 도메인 동작 | `acceptEnroll`, `liftUpBoard` |

- 동의어 금지: `register`, `save`, `modify`, `remove`, `fetch`, `retrieve`. `find` 는 Repository·QueryApi 의 Optional 반환 전용.

## DTO
| 종류 | 위치 | 이름 |
|---|---|---|
| Command | `dto/command` | `{Domain}{Action}Command` |
| Result | `dto/result` | `{Domain}{Action}Result`, `{Domain}DetailResult` |
| Info | `dto/api` | `{Domain}Info` |

- 전부 `record`, Lombok 금지. Controller 가 Result 를 그대로 응답한다.
- 변환은 DTO 안: `Result.from(단일 재료)`, `Result.of(복수 재료)`. Mapper 클래스·MapStruct 금지.

## 화면용 조합 조회 (QueryService)
- 자기 BC 엔티티를 조회하고, 타 BC 데이터는 `QueryApi.getInfos(ids)` 로 한 번에 모아 조합한다. 쿼리 수는 BC 수만큼 고정.
- 반복문 안에서 QueryApi·Repository 호출 금지.

## 이벤트 리스너
- 위치 `application/event/`, 이름 `{수신Bc}{이벤트명}Listener` (`ChatEnrollAcceptedListener`), 메서드명 `handle`.
- 기본: `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` + `@Transactional(propagation = Propagation.REQUIRES_NEW)`. 수신 측 실패가 발행 측을 롤백하지 않는다.
- 본문은 자기 BC Service 호출 한 줄.
- 반드시 처리돼야 하는 작업은 Outbox 로 재시도.
- 같은 트랜잭션(`BEFORE_COMMIT`)은 꼭 필요할 때만, 사유 주석 필수.

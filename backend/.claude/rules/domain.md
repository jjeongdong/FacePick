---
paths:
  - "**/domain/**"
---

# Domain 계층 규칙

## 엔티티
1. Lombok 은 `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 만.
2. 생성은 정적 팩토리 `create(...)` 로만. 생성자는 `private`. 파라미터가 많아도 `create` 유지.
3. 생성 시 검증은 `create()` 안에서.
4. 상태 변경은 의미를 드러내는 메서드 (`liftUp(now)`, `accept(...)`). `update()`, `changeStatus(status)`, public setter 금지.
5. `Long id`, `@GeneratedValue(strategy = GenerationType.IDENTITY)`, 컬럼명 `{테이블 단수}_id`.
6. enum 은 `@Enumerated(EnumType.STRING)`.
7. 생성·수정 시각은 `BaseTimeEntity` 상속.
8. 소프트 삭제는 `deletedAt` + `@SQLRestriction("deleted_at IS NULL")`.
9. 같은 BC 내 연관관계는 단방향 `@ManyToOne` 우선. 타 BC 는 ID 필드로만.
10. 모든 연관관계 `fetch = FetchType.LAZY`.
11. 테이블명 복수형 snake_case, 컬럼 snake_case.
12. `LocalDateTime.now()` 금지 — 시각은 인자로 받는다.

```java
public static Board create(Long writerId, String title, int maxPerson, LocalDateTime now) {
    if (maxPerson < MIN_PERSON) {
        throw new BoardInvalidMaxPersonException();
    }
    return new Board(writerId, title, maxPerson, now);
}

public void liftUp(LocalDateTime now) { ... }
```

## 비즈니스 규칙
- 규칙(권한, 상태 전이, 정원 등)은 엔티티 메서드 안에. 타 BC 값이 필요하면 인자로 받는다 (`accept(Long requesterId, Long boardWriterId)`).
- 형식 검증과 겹치는 규칙도 엔티티가 최종 방어선이다.

## Repository 인터페이스
- 이름은 도메인 언어 (`findActiveByUserId`). Spring Data 파생 쿼리명은 `JpaRepository` 에만.
- 반드시 있어야 하면 `getById()` (구현체가 NotFound 예외), 없을 수 있으면 `findBy…()` → `Optional`, 목록은 `List`.
- Spring Data 타입(`Page`, `Pageable`, `Slice`)을 쓰지 않는다. 페이징은 `List` 조회 메서드와 별도 `count` 메서드로.

## 에러 코드와 예외
- `{Bc}ErrorCode` enum (`domain` 패키지) implements `global.error.ErrorCode`. 값은 `ErrorType` + 한국어 메시지.
- `ErrorType`: `INVALID`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `INTERNAL`, `EXTERNAL`(외부 서비스 장애, 502). `HttpStatus` 사용 금지.
- 상수 이름 `{DOMAIN}_{SITUATION}` (`BOARD_NOT_FOUND`, `ENROLL_ALREADY_ACCEPTED`). `BAD_REQUEST` 같은 모호한 이름 금지.
- 에러마다 전용 예외 클래스: `domain/exception/{상수 PascalCase}Exception extends BusinessException`. `BusinessException` 은 추상 클래스라 직접 던질 수 없다.
- BC 무관 공통 코드는 `global.error.GlobalErrorCode` (`INVALID_INPUT`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `INTERNAL_SERVER_ERROR`).

```java
public class BoardNotFoundException extends BusinessException {
    public BoardNotFoundException() {
        super(BoardErrorCode.BOARD_NOT_FOUND);
    }
}
```

## 도메인 이벤트
- 이름 `{Domain}{과거분사}Event` (`EnrollAcceptedEvent`), `record`, 위치 `domain/event/`.
- 필드는 ID·원시값만. 엔티티를 담지 않는다.
- 발행은 Application Service 에서. 엔티티 안에서 발행하지 않는다.

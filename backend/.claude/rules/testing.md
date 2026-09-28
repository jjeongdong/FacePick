---
paths:
  - "src/test/**"
---

# 테스트 규칙

## 무엇을 테스트하나
| 대상 | 종류 | 도구 | 필수 여부 |
|---|---|---|---|
| Domain 엔티티 | 순수 단위 | JUnit + AssertJ (Spring·Mock 없음) | 필수 — `create()` 와 상태 변경 규칙마다 |
| Application Service | 단위 | Mockito | 필수 — 분기, 예외, 이벤트 발행 |
| Repository | 통합 | `@DataJpaTest` + Testcontainers PostgreSQL(pgvector) | 커스텀 쿼리(QueryDSL, `@Query`)만 |
| Controller | 슬라이스 | `@WebMvcTest` | 선택 |
| 동시성·이벤트·외부 연동 | 통합 | `@SpringBootTest` + Testcontainers | 락, 재시도, Outbox 등 위험한 곳 |

- H2 금지.

## 작성 스타일
1. 클래스 `{대상}Test`, 대상과 같은 패키지.
2. 메서드명 영어 camelCase + `@DisplayName` 한국어.
3. 본문은 `// given`, `// when`, `// then` (또는 `// when & then`).
4. AssertJ 만. 예외는 `assertThatThrownBy(...).isInstanceOf(XxxException.class)`.
5. 테스트 하나에 동작 하나. 같은 메서드의 여러 경우는 `@Nested` 로 묶는다.
   - 예외: `@DataJpaTest` 테스트에서는 `@Nested` 를 쓰지 않는다. 중첩 클래스마다 컨텍스트가 따로 떠서, 바깥 클래스 필드의 `TestEntityManager` 가 테스트 트랜잭션을 보지 못한다. 묶음은 `@DisplayName` 앞에 `"조건 조회 - "` 처럼 붙인다.
6. 픽스처는 `src/test/java/com/back/facepick/{bc}/fixture/{Entity}Fixture`.
7. 시각은 고정값 (`LocalDateTime.of(2026, 1, 1, 12, 0)`).
8. Mockito 는 BDD 스타일: `given(...).willReturn(...)`, `then(mock).should().method(...)`.

```java
@Nested
@DisplayName("신청 수락")
class Accept {
    @Test
    @DisplayName("게시글 작성자가 아니면 수락할 수 없다")
    void throwsWhenRequesterIsNotWriter() {
        // given
        Enroll enroll = EnrollFixture.pending();

        // when & then
        assertThatThrownBy(() -> enroll.accept(OTHER_USER_ID, WRITER_ID))
                .isInstanceOf(EnrollNotBoardWriterException.class);
    }
}
```

## 아키텍처 테스트
- `com.back.facepick.architecture` 의 테스트는 컨벤션 강제 장치다. 테스트를 통과시키려고 규칙을 약화하지 말고 코드를 고친다.
- `com.back.archfixture` 는 규칙 검증용 픽스처로, 일부 클래스는 고의로 규칙을 어긴다. 고치지 않는다.

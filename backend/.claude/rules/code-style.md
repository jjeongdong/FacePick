---
paths:
  - "**/*.java"
---

# 코드 스타일 규칙

## 포맷
- Spotless + Palantir Java Format. 커밋 전 `./gradlew spotlessApply`. 손으로 정렬하지 않는다.
- 와일드카드 import 금지.

## Lombok
| 대상 | 허용 |
|---|---|
| Spring 빈 | `@RequiredArgsConstructor`, `@Slf4j` |
| 엔티티 | `@Getter`, `@NoArgsConstructor(access = PROTECTED)` |
| DTO(record) | 없음 |

전역 금지: `@Data`, `@Setter`, `@Builder`, `@AllArgsConstructor`, `@Value`(lombok), `@SneakyThrows`.

## Java
1. 생성자 주입만. `@Autowired` 필드 주입 금지.
2. `var` 금지.
3. `Optional` 은 반환 타입에만 (필드·파라미터·컬렉션 금지). `Optional.get()` 금지 → `orElseThrow()`.
4. Stream 수집은 `.toList()`. 중첩 Stream 대신 for.
5. null 반환 금지 — `Optional` 또는 빈 컬렉션.
6. 지역 변수·파라미터에 `final` 금지 (필드만).
7. 객체 비교는 `Objects.equals()`.
8. 날짜·시간은 `LocalDateTime`/`LocalDate`. `Date`, `Calendar` 금지. 외부 라이브러리(jjwt 등)가 요구할 때만 Infrastructure 안에서 변환한다.
9. DTO·이벤트는 `record`.

## 이름
1. 컬렉션은 복수형, `List` 접미사 금지 (`boards`, `preferredClubIds`).
2. boolean 지역 변수·메서드는 `is/has/can`. 필드는 접두사 없이 (`completed` + `isCompleted()`).
3. 타 BC ID 는 `{대상}Id` (`writerId`).
4. 약어 금지 (`id`, `url`, `dto` 제외): `request`(O) `req`(X), `count`(O) `cnt`(X).
5. Map 은 `{값}By{키}` (`usersById`).
6. 상수 `UPPER_SNAKE_CASE`, 매직 넘버 금지.

## 로깅
1. `@Slf4j` 만. `System.out`/`System.err` 금지.
2. 플레이스홀더 사용, 문자열 연결 금지. 형식 `설명 key={}, key={}`, 한국어.
3. 예외는 마지막 인자로: `log.error("FCM 발송 실패 notificationId={}", id, e)`.
4. 던질 거면 로그 남기지 않는다. 처리하는 곳에서 한 번만.
5. 개인정보 금지: 토큰, 이메일, 전화번호, 서명 URL, 얼굴 임베딩.
6. 레벨: `ERROR` 사람이 확인해야 하는 장애 / `WARN` 시스템이 스스로 처리한 비정상 / `INFO` 중요 비즈니스 이벤트·배치 / `DEBUG` 개발용.
7. `BusinessException` 은 로그 없음. 서버 원인(`INTERNAL`·`EXTERNAL`, `isServerFault()`)만 `ERROR`.

## 주석
1. 한국어. "무엇"이 아니라 "왜".
2. 필수: 예외 규칙을 쓸 때 (같은 트랜잭션 이벤트, 타 BC JOIN, 네이티브 쿼리), 직관과 다른 코드 (락, 재시도, 순서 의존, 의도적 비효율).
3. Javadoc 은 선택. `QueryApi` public 메서드만 필수.
4. 금지: 주석 처리된 코드, `TODO`, 변경 이력·작성자·날짜.

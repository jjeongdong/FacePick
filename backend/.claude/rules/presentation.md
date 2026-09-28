---
paths:
  - "**/presentation/**"
---

# Presentation 계층 규칙

## Controller
1. 반환 타입은 항상 `ResponseEntity<T>`.
2. 로그인 사용자는 `@AuthUser Long userId`. (인증 도입 전까지는 해당 없음)
3. 로직 금지. 할 일은 `request.toCommand()` → Service 호출 1회 → `ResponseEntity` 생성뿐.
4. 자기 BC 의 `{Bc}CommandService`/`{Bc}QueryService` 만 주입.
5. 메서드명 = 호출하는 Service 메서드명 (`createBoard` → `boardCommandService.createBoard`).
6. Request Body 는 `@Valid @RequestBody`.
7. 관리자 API 는 `Admin{Bc}Controller` 로 분리.
8. Swagger 어노테이션(`@Tag`, `@Operation`, `@Parameter`)은 `{Bc}ApiDocs` 인터페이스에만. Controller 는 `implements {Bc}ApiDocs`.

```java
@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardController implements BoardApiDocs {
    private final BoardCommandService boardCommandService;

    @PostMapping
    public ResponseEntity<BoardCreateResult> createBoard(@AuthUser Long userId, @Valid @RequestBody BoardCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(boardCommandService.createBoard(userId, request.toCommand()));
    }
}
```

## Request DTO
- `record`, 위치 `presentation/dto/request`, 이름 `{Domain}{Action}Request`.
- 형식 검증(null, 길이, 범위)은 Bean Validation 으로 여기서. 비즈니스 규칙은 엔티티에.
- `toCommand()` 인스턴스 메서드로 Command 변환.
- Application 은 Request 를 모른다.

## URL
1. `/api` 로 시작, 클래스 `@RequestMapping` 에만 기재.
   - 예외: 한 Controller 가 두 리소스 경로에 걸치면(`/api/boards/{boardId}/enrolls` 와 `/api/enrolls/**`) 클래스 매핑은 `/api` 까지만 둔다.
2. 복수 명사, 여러 단어는 kebab-case (`/chat-rooms`).
3. 목록은 컬렉션 경로 (`/list`, `/all` 금지).
4. 중첩은 최대 2단계 (`/api/boards/{boardId}/enrolls`).
5. CRUD 로 표현 안 되는 동작은 `POST` + 동사 하위 경로 (`POST /api/enrolls/{enrollId}/accept`).
6. 본인 리소스는 `/me` (`GET /api/users/me`).
7. 전체 수정 `PUT`, 부분 수정 `PATCH`.
8. 경로 변수는 `{도메인Id}` (`{id}` 금지).
9. 관리자 API 는 `/api/admin/...`.

## 상태 코드
| 동작 | 코드 | 본문 |
|---|---|---|
| 조회 | 200 | Result |
| 생성 | 201 | Result (id 포함). `Location` 헤더 없음 |
| 수정 | 200 | Result |
| 삭제 / 결과 없음 | 204 | 없음 |

## 페이징
- 무한스크롤·누적 목록(게시글, 채팅, 알림) = 커서. 페이지 번호 화면(관리자, 공지, 문의) = 오프셋.
- 응답은 `global/response` 의 `OffsetPageResult<T>` `{content, page, size, totalElements, hasNext}` / `CursorPageResult<T>` `{content, nextCursor, hasNext}`.
- 요청: 오프셋 `page`, `size`(기본 20, 최대 100) / 커서 `cursor`, `size`. `nextCursor` 는 정렬 키를 인코딩한 문자열 하나.

## 에러 응답
- 본문은 `{ "code": "BOARD_NOT_FOUND", "message": "존재하지 않는 게시글입니다." }` 뿐.
- Bean Validation 실패는 `INVALID_INPUT`, `message` 는 첫 필드 에러 메시지.
- HTTP 상태 변환은 `global.error.ErrorHttpStatus` 로만 (`ErrorType` → 상태 코드). 쓰는 곳은 `GlobalExceptionHandler` 와 보안 필터 핸들러뿐.
- HTTP 전용 코드: 없는 URL `NOT_FOUND`(404), 파라미터 누락·타입 불일치 `INVALID_INPUT`(400), 지원하지 않는 메서드 `METHOD_NOT_ALLOWED`(405). 그 밖에 Spring 이 상태를 정한 요청 오류(업로드 용량 초과 413, Content-Type 불일치 415 등)는 그 상태 + `INVALID_INPUT`.
- 로그: 4xx 는 남기지 않는다. 5xx(`INTERNAL`·`EXTERNAL`)만 `ERROR`.

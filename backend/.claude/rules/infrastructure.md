---
paths:
  - "**/infrastructure/**"
---

# Infrastructure 계층 규칙

## Repository 구현
- `{Entity}RepositoryImpl implements {Entity}Repository` 가 `{Entity}JpaRepository extends JpaRepository` 에 위임.
- `getById()` 는 여기서 `orElseThrow({Entity}NotFoundException::new)`.
- 목록 반환은 `List`, null 금지.

```java
@Repository
@RequiredArgsConstructor
public class BoardRepositoryImpl implements BoardRepository {
    private final BoardJpaRepository boardJpaRepository;

    @Override
    public Board getById(Long boardId) {
        return boardJpaRepository.findById(boardId).orElseThrow(BoardNotFoundException::new);
    }
}
```

## 쿼리
1. 동적 쿼리는 QueryDSL, 이 계층 구현체 안에만. (QueryDSL 은 동적 쿼리가 처음 필요할 때 도입한다)
2. `@Query` JPQL 은 `JpaRepository` 에만. 네이티브 쿼리는 사유 주석 필수.
3. N+1 은 fetch join 또는 `@EntityGraph`. 반복문 안 Repository 호출 금지 — `findAllByIdIn(ids)`.
4. 벌크 수정·삭제는 `@Modifying(clearAutomatically = true)`.
5. 타 BC 테이블 JOIN 금지. 예외는 측정 근거가 있을 때만 — 사유·측정 근거 주석 + `BoundedContexts.CROSS_CONTEXT_ALLOWLIST` 등록.

## 기술 연동
- Kafka 프로듀서, S3 클라이언트, 외부 API 클라이언트는 이 계층에 둔다. 여러 BC 가 공유하는 것만 `global/infrastructure`.
- `@Transactional` 을 붙이지 않는다 (Application 계층 전용).

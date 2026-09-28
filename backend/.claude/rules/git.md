# Git 규칙

## 커밋 메시지
```
type(scope): 한국어 요약 (50자 이내, 마침표 없음)

본문: 무엇이 아니라 "왜" 바꿨는지 (72자 줄바꿈)
```
- type: `feat` 새 기능 / `fix` 버그 / `refactor` 동작 불변 구조 변경 / `perf` 성능 / `test` 테스트만 / `docs` 문서·주석 / `style` 포맷만 / `build` Gradle·의존성 / `chore` 그 외 설정.
- scope: BC 이름 또는 `global`. 여러 개면 쉼표 (`enroll,chat`).
- 커밋 하나 = 논리적 변경 하나. `style` 커밋은 기능 커밋과 섞지 않는다.
- 커밋 전: `./gradlew spotlessApply` + 테스트 통과.

## 브랜치와 머지
- 브랜치: `feat/`, `fix/`, `refactor/`, `chore/` + 영어 kebab-case (`fix/enroll-accept-race`).
- `main` 직접 커밋 금지. PR 없이 로컬 머지.
- 머지 전 빌드·테스트 통과 → `git switch main && git merge --no-ff {branch}` → 브랜치 삭제.

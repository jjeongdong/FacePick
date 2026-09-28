# 자체 로그인 · 사용자 · 앨범 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 이메일·비밀번호로 가입·로그인하고(JWT Access + Refresh), 로그인한 사용자가 앨범을 만들고 조회할 수 있는 facepick 백엔드의 첫 기능을 만든다.

**Architecture:** DDD 4계층 BC 세 개를 추가한다. `user`(프로필), `auth`(자격 증명·토큰), `album`(앨범·참여자). 보안 필터와 `@AuthUser`는 `global`에 두고, 토큰 검증은 `global`의 포트(`AccessTokenVerifier`)를 `auth`가 구현하는 방식으로 연결해 `global`이 BC를 모르게 한다. 가입 시 `auth`가 `user` 생성을 동기 호출하는 것만 경계 예외(`SYNC_COMMAND_ALLOWLIST`)로 둔다.

**Tech Stack:** Spring Boot 4.1.1, Java 21, Spring Security 7, jjwt 0.13.0, JPA(Hibernate 7), PostgreSQL 16(pgvector 이미지), Flyway, JUnit 5 + AssertJ + Mockito, Testcontainers 2, ArchUnit.

**Spec:**
- PRD: https://claude.ai/code/artifact/a24aaf7b-8b43-4e06-b845-3cf88dba2bc4 (4장 사용자 플로우, 5장 F1, 11장 결정 사항)
- 컨벤션: `backend/CLAUDE.md`, `backend/.claude/rules/*.md` (catchmate 백엔드 규칙을 옮긴 것)
- 이번 계획에서 정한 것 (2026-09-28 대화)
  - 자체 로그인: 이메일 + 비밀번호. 소셜 로그인 없음.
  - 토큰: Access(1시간) + Refresh(30일). React Native 앱이 쿠키를 다루기 불편해 토큰은 **응답 본문**으로 주고, refresh 토큰도 **요청 본문**으로 받는다 (catchmate 는 쿠키).
  - refresh 토큰 저장: Redis 대신 PostgreSQL `refresh_tokens` 테이블. 원문이 아닌 SHA-256 해시만 저장.
  - 앨범: 만든 사람이 자동으로 앨범장(OWNER). 30일 뒤 만료(PRD 결정). 참여자만 조회 가능.

**이번 범위 밖:** 이메일 인증·비밀번호 찾기, 초대 링크로 참여, refresh 토큰 회전(rotation), 만료 앨범 삭제 배치, CORS(웹 뷰어를 붙일 때), 관리자 기능.

## Global Constraints

- 루트 패키지 `com.back.facepick`, BC 는 최상위 패키지 하나씩: `user`, `auth`, `album` (+ 기존 빈 `photo`, `person`).
- 각 BC 는 `presentation / application / domain / infrastructure`. 의존: Presentation → Application → Domain ← Infrastructure.
- 타 BC 는 `{Bc}QueryApi`, `application.dto.api`, `domain.event` 만 사용. 타 BC 엔티티는 ID 로만 참조. 예외는 `BoundedContexts` allowlist 에 등록하고 사유 주석.
- 비즈니스 규칙은 엔티티에. 엔티티 생성은 `private` 생성자 + `static create(...)`. public setter 금지. 도메인에서 `LocalDateTime.now()` 금지(시각은 인자로).
- DTO·이벤트는 `record`, 흐름은 Request → Command → Result. 변환은 DTO 안(`toCommand()`, `from()`, `of()`).
- `@Transactional` 은 Application 계층 public 메서드에만, 메서드마다. 조회는 `readOnly = true`.
- 에러마다 전용 예외(`domain/exception/{상수 PascalCase}Exception extends BusinessException`) + BC 별 `{Bc}ErrorCode` enum. 도메인은 `HttpStatus` 를 모른다.
- Lombok: 빈은 `@RequiredArgsConstructor`/`@Slf4j`, 엔티티는 `@Getter`/`@NoArgsConstructor(access = PROTECTED)` 만.
- 금지: `var`, `TODO`, 와일드카드 import, `Optional.get()`, `System.out`, `Date`/`Calendar`(Infrastructure 의 jjwt 변환 제외).
- Controller 반환은 `ResponseEntity<T>`. 생성 201(본문 Result), 조회 200, 결과 없음 204. Swagger·검증 어노테이션(`@Valid`)은 `{Bc}ApiDocs` 인터페이스에만.
- 스키마는 Flyway(`src/main/resources/db/migration`)로만 바꾼다. `ddl-auto: validate`. 테이블 복수형 snake_case, PK 컬럼 `{테이블 단수}_id`, 시각 컬럼 `TIMESTAMP(6)`.
- 주석·로그·커밋 메시지는 한국어. 주석은 "왜"만. 로그에 토큰·이메일 금지.
- 테스트: 클래스 `{대상}Test`, 메서드명 영어 camelCase + `@DisplayName` 한국어, `// given // when // then`, AssertJ, Mockito BDD(`given`/`then`). 픽스처는 `src/test/java/com/back/facepick/{bc}/fixture/`. 시각은 고정값.
- 커밋: `type(scope): 한국어 요약`. 커밋 전 `./gradlew spotlessApply build` 통과. `main` 직접 커밋 금지(최초 커밋 제외), 브랜치에서 작업 후 `git merge --no-ff`.

## Review Focus

1. **대소문자만 다른 이메일로 재가입** (`me@example.com` 뒤에 `ME@example.com`) → 409 `AUTH_EMAIL_ALREADY_EXISTS`. 저장·조회 모두 소문자로 정규화한다. → Task 3 `CredentialTest`, `CredentialRepositoryImplQueryTest`
2. **같은 이메일로 동시에 가입** → 한쪽만 성공, 다른 쪽은 500 이 아니라 409. 사전 조회가 아니라 유니크 제약 위반을 예외로 바꿔 막는다. → Task 3 `CredentialRepositoryImplQueryTest`
3. **72바이트를 넘는 비밀번호**(한글 25자 = 75바이트) → 500 이 아니라 400 `AUTH_INVALID_PASSWORD`. BCrypt 가 72바이트 초과 입력에 예외를 던지기 때문. → Task 3 `CredentialTest`
4. **토큰 용도 바꿔 쓰기**: refresh 토큰으로 API 호출, access 토큰으로 재발급 요청 → 둘 다 401. → Task 3 `JwtTokenProviderTest`
5. **1초 안에 두 번 로그인** → 두 refresh 토큰이 서로 달라 `token_hash` 유니크 제약에 걸리지 않는다(jti 로 구분). → Task 3 `JwtTokenProviderTest`

---

## File Structure

```
facepick/
├── docs/superpowers/plans/2026-09-28-auth-user-album.md   (이 문서)
└── backend/
    ├── build.gradle                                          수정: security, jjwt
    ├── src/main/resources/
    │   ├── application.yml                                   수정: jwt 만료, album.retention-days 제거
    │   ├── application-local.yml                             수정: jwt 비밀키
    │   └── db/migration/
    │       ├── V1__create_users.sql
    │       ├── V2__create_auth_tables.sql
    │       └── V3__create_albums.sql
    ├── src/main/java/com/back/facepick/
    │   ├── global/
    │   │   ├── authorization/annotation/AuthUser.java
    │   │   ├── authorization/resolver/AuthUserArgumentResolver.java
    │   │   ├── config/security/  AccessTokenVerifier, AuthenticatedUser, JwtAuthenticationFilter,
    │   │   │                     JwtAuthenticationEntryPoint, JwtAccessDeniedHandler,
    │   │   │                     SecurityErrorResponses, SecurityConfig
    │   │   ├── config/web/  WebMvcConfig, SwaggerConfig
    │   │   └── error/  UnauthorizedException (추가), GlobalExceptionHandler (수정)
    │   ├── user/
    │   │   ├── domain/  User, UserRole, UserErrorCode, UserRepository, exception/*
    │   │   ├── infrastructure/  UserJpaRepository, UserRepositoryImpl
    │   │   ├── application/  UserCommandService, UserQueryService, UserQueryApi, dto/{command,result,api}
    │   │   └── presentation/  UserController, UserApiDocs
    │   ├── auth/
    │   │   ├── domain/  Credential, RefreshToken, PasswordEncryptor, AuthTokenProvider,
    │   │   │            CredentialRepository, RefreshTokenRepository, AuthErrorCode, exception/*
    │   │   ├── infrastructure/  BCryptPasswordEncryptor, JwtTokenProvider,
    │   │   │                    Credential{Jpa,}Repository{,Impl}, RefreshToken{Jpa,}Repository{,Impl}
    │   │   ├── application/  AuthCommandService, dto/{command,result}
    │   │   └── presentation/  AuthController, AuthApiDocs, dto/request
    │   └── album/
    │       ├── domain/  Album, AlbumMember, AlbumRole, AlbumErrorCode, AlbumRepository,
    │       │            AlbumMemberRepository, exception/*
    │       ├── infrastructure/  Album{Jpa,}Repository{,Impl}, AlbumMember{Jpa,}Repository{,Impl}
    │       ├── application/  AlbumCommandService, AlbumQueryService, dto/{command,result}
    │       └── presentation/  AlbumController, AlbumApiDocs, dto/request
    └── src/test/java/com/back/facepick/
        ├── architecture/BoundedContexts.java                 수정: SYNC_COMMAND_ALLOWLIST
        ├── global/…                                          필터·리졸버·핸들러 테스트
        ├── user/{fixture,domain,infrastructure,application,presentation}/
        ├── auth/{fixture,domain,infrastructure,application,presentation}/
        └── album/{fixture,domain,infrastructure,application,presentation}/
```

작업 순서와 의존: Task 0 → 1 → 2 → 3 → 4 → 5 → 6 → 7. Task 1 이후 Task 3 전까지는 `AccessTokenVerifier` 구현체가 없어 앱이 뜨지 않는다(단위 테스트는 통과). Task 3 부터 앱이 뜬다.

---

### Task 0: git 저장소와 작업 브랜치

**Files:** 없음 (저장소 초기화)

- [ ] **Step 1: 저장소를 만들고 현재 구조를 최초 커밋한다**

```bash
cd ~/facepick
git init -b main
git status --short   # infra/.env, backend/.idea, backend/build 가 목록에 없어야 한다 (.gitignore)
git add .
git commit -m "chore: 프로젝트 구조 초기화"
```

- [ ] **Step 2: 작업 브랜치를 만든다**

```bash
git switch -c feat/auth-user-album
```

---

### Task 1: global 보안 기반 (JWT 필터, `@AuthUser`, 401·403 응답)

**Files:**
- Modify: `backend/build.gradle`
- Create: `backend/src/main/java/com/back/facepick/global/error/UnauthorizedException.java`
- Modify: `backend/src/main/java/com/back/facepick/global/error/GlobalExceptionHandler.java`
- Create: `global/authorization/annotation/AuthUser.java`, `global/authorization/resolver/AuthUserArgumentResolver.java`
- Create: `global/config/security/{AccessTokenVerifier,AuthenticatedUser,JwtAuthenticationFilter,JwtAuthenticationEntryPoint,JwtAccessDeniedHandler,SecurityErrorResponses,SecurityConfig}.java`
- Create: `global/config/web/{WebMvcConfig,SwaggerConfig}.java`
- Test: `global/authorization/resolver/AuthUserArgumentResolverTest.java`, `global/config/security/{JwtAuthenticationFilterTest,JwtAuthenticationEntryPointTest}.java`
- Modify test: `global/error/GlobalExceptionHandlerTest.java`

(이하 경로의 `global/…` 는 `backend/src/main/java/com/back/facepick/` 또는 `backend/src/test/java/com/back/facepick/` 기준)

**Interfaces:**
- Produces:
  - `@AuthUser Long userId` — 컨트롤러 파라미터. 인증이 없으면 `UnauthorizedException`(401).
  - `interface AccessTokenVerifier { AuthenticatedUser verify(String token); }` — Task 3 의 `JwtTokenProvider` 가 구현.
  - `record AuthenticatedUser(Long userId, String role)`
  - 공개 경로: `/api/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`. 나머지는 인증 필요.

- [ ] **Step 1: 의존성을 추가한다**

`backend/build.gradle` 의 `dependencies` 에서 `spring-boot-starter-kafka` 줄 아래에 추가:

```groovy
    implementation 'org.springframework.boot:spring-boot-starter-security'
```

`runtimeOnly 'org.postgresql:postgresql'` 줄 아래에 추가:

```groovy

    // JWT
    implementation 'io.jsonwebtoken:jjwt-api:0.13.0'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.13.0'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.13.0'
```

- [ ] **Step 2: 실패하는 테스트를 쓴다 — `@AuthUser` 리졸버**

`backend/src/test/java/com/back/facepick/global/authorization/resolver/AuthUserArgumentResolverTest.java`:

```java
package com.back.facepick.global.authorization.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.global.error.UnauthorizedException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthUserArgumentResolverTest {

    private final AuthUserArgumentResolver resolver = new AuthUserArgumentResolver();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증 정보의 사용자 ID 를 Long 으로 넘긴다")
    void resolvesUserId() {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(7L, null, List.of()));

        // when
        Object userId = resolver.resolveArgument(null, null, null, null);

        // then
        assertThat(userId).isEqualTo(7L);
    }

    @Test
    @DisplayName("인증 정보가 없으면 UnauthorizedException 을 던진다")
    void throwsWhenNoAuthentication() {
        // when & then
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("익명 사용자면 UnauthorizedException 을 던진다")
    void throwsWhenAnonymous() {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        // when & then
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOf(UnauthorizedException.class);
    }
}
```

- [ ] **Step 3: 실패하는 테스트를 쓴다 — JWT 필터, EntryPoint**

`backend/src/test/java/com/back/facepick/global/config/security/JwtAuthenticationFilterTest.java`:

```java
package com.back.facepick.global.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    private static final String VALID_TOKEN = "valid";

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(token -> {
        if (VALID_TOKEN.equals(token)) {
            return new AuthenticatedUser(7L, "ROLE_USER");
        }
        throw new IllegalArgumentException("유효하지 않은 토큰");
    });

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("올바른 Bearer 토큰이면 사용자 ID 로 인증한다")
    void authenticatesValidToken() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        // then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isEqualTo(7L);
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("잘못된 토큰이면 인증 없이 다음 필터로 넘긴다")
    void skipsInvalidToken() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer broken");
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 인증 없이 다음 필터로 넘긴다")
    void skipsMissingHeader() throws Exception {
        // given
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }
}
```

`backend/src/test/java/com/back/facepick/global/config/security/JwtAuthenticationEntryPointTest.java`:

```java
package com.back.facepick.global.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.json.JsonMapper;

class JwtAuthenticationEntryPointTest {

    @Test
    @DisplayName("인증 없이 보호된 경로에 오면 401 과 UNAUTHORIZED 본문으로 응답한다")
    void writesUnauthorized() throws Exception {
        // given
        JwtAuthenticationEntryPoint entryPoint =
                new JwtAuthenticationEntryPoint(JsonMapper.builder().build());
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        entryPoint.commence(
                new MockHttpServletRequest(), response, new InsufficientAuthenticationException("인증 없음"));

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("{\"code\":\"UNAUTHORIZED\",\"message\":\"인증에 실패했습니다.\"}");
    }
}
```

- [ ] **Step 4: `GlobalExceptionHandlerTest` 에 권한 거부 케이스를 추가한다**

`backend/src/test/java/com/back/facepick/global/error/GlobalExceptionHandlerTest.java`:

import 목록에 추가:

```java
import org.springframework.security.access.AccessDeniedException;
```

`unsupportedMediaTypeIsInvalidInput` 테스트 아래에 추가:

```java
    @Test
    @DisplayName("권한 거부는 403 FORBIDDEN 으로 응답한다")
    void accessDeniedIsForbidden() throws Exception {
        assertError(get("/test/denied"), 403, "FORBIDDEN", "접근 권한이 없습니다.");
    }
```

`TestController` 의 `tooLarge()` 아래에 추가:

```java
        @GetMapping("/test/denied")
        void denied() {
            throw new AccessDeniedException("관리자 전용");
        }
```

- [ ] **Step 5: 테스트가 실패하는지 확인한다**

Run: `cd ~/facepick/backend && ./gradlew test --tests 'com.back.facepick.global.*'`
Expected: 컴파일 실패 — `AuthUserArgumentResolver`, `JwtAuthenticationFilter`, `AuthenticatedUser`, `JwtAuthenticationEntryPoint`, `UnauthorizedException` 를 찾을 수 없음.

- [ ] **Step 6: 예외와 `@AuthUser` 를 구현한다**

`global/error/UnauthorizedException.java`:

```java
package com.back.facepick.global.error;

// 특정 BC 에 속하지 않는 인증 실패(요청에 로그인 정보가 없거나 형식이 잘못됨)용.
public class UnauthorizedException extends BusinessException {
    public UnauthorizedException() {
        super(GlobalErrorCode.UNAUTHORIZED);
    }
}
```

`global/error/GlobalExceptionHandler.java` — import 추가 `import org.springframework.security.access.AccessDeniedException;`, 그리고 `handleMethodNotSupported` 아래에 추가:

```java
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return toResponse(GlobalErrorCode.FORBIDDEN, GlobalErrorCode.FORBIDDEN.message());
    }
```

`global/authorization/annotation/AuthUser.java`:

```java
package com.back.facepick.global.authorization.annotation;

import io.swagger.v3.oas.annotations.Parameter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Parameter(hidden = true)
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthUser {}
```

`global/authorization/resolver/AuthUserArgumentResolver.java`:

```java
package com.back.facepick.global.authorization.resolver;

import com.back.facepick.global.authorization.annotation.AuthUser;
import com.back.facepick.global.error.UnauthorizedException;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class AuthUserArgumentResolver implements HandlerMethodArgumentResolver {
    private static final String ANONYMOUS_USER = "anonymousUser";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthUser.class)
                && Long.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || authentication.getPrincipal() == null
                || ANONYMOUS_USER.equals(authentication.getPrincipal())) {
            throw new UnauthorizedException();
        }
        try {
            return Long.parseLong(authentication.getPrincipal().toString());
        } catch (NumberFormatException e) {
            throw new UnauthorizedException();
        }
    }
}
```

`global/config/web/WebMvcConfig.java`:

```java
package com.back.facepick.global.config.web;

import com.back.facepick.global.authorization.resolver.AuthUserArgumentResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    private final AuthUserArgumentResolver authUserArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authUserArgumentResolver);
    }
}
```

- [ ] **Step 7: 보안 필터와 응답 핸들러를 구현한다**

`global/config/security/AccessTokenVerifier.java`:

```java
package com.back.facepick.global.config.security;

// 보안 필터가 인증 BC 의 구현을 모르게 하려고 둔 포트. global 은 BC 에 의존하지 않는다.
public interface AccessTokenVerifier {

    // 서명·만료·용도 검증에 실패하면 예외를 던진다. 호출 측(필터)이 예외를 잡아 인증 없음으로 처리한다.
    AuthenticatedUser verify(String token);
}
```

`global/config/security/AuthenticatedUser.java`:

```java
package com.back.facepick.global.config.security;

public record AuthenticatedUser(Long userId, String role) {}
```

`global/config/security/JwtAuthenticationFilter.java`:

```java
package com.back.facepick.global.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenVerifier accessTokenVerifier;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        resolveToken(request).ifPresent(this::authenticate);
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            AuthenticatedUser user = accessTokenVerifier.verify(token);
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(
                            user.userId(), null, List.of(new SimpleGrantedAuthority(user.role()))));
        } catch (RuntimeException e) {
            // 여기서 응답하지 않는다. 인증이 필요한 경로면 EntryPoint 가 401 로 응답하고, 공개 경로는 그대로 통과한다.
            log.warn("유효하지 않은 액세스 토큰 reason={}", e.getClass().getSimpleName());
            SecurityContextHolder.clearContext();
        }
    }

    private Optional<String> resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return Optional.of(header.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }
}
```

`global/config/security/SecurityErrorResponses.java`:

```java
package com.back.facepick.global.config.security;

import com.back.facepick.global.error.ErrorCode;
import com.back.facepick.global.error.ErrorHttpStatus;
import com.back.facepick.global.error.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

// 보안 필터 단계의 실패는 GlobalExceptionHandler 에 닿지 않으므로, 같은 본문 형식을 여기서 직접 쓴다.
final class SecurityErrorResponses {

    private SecurityErrorResponses() {}

    static void write(HttpServletResponse response, JsonMapper jsonMapper, ErrorCode errorCode) throws IOException {
        response.setStatus(ErrorHttpStatus.of(errorCode.type()).value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(ErrorResponse.from(errorCode)));
    }
}
```

`global/config/security/JwtAuthenticationEntryPoint.java`:

```java
package com.back.facepick.global.config.security;

import com.back.facepick.global.error.GlobalErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final JsonMapper jsonMapper;

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        SecurityErrorResponses.write(response, jsonMapper, GlobalErrorCode.UNAUTHORIZED);
    }
}
```

`global/config/security/JwtAccessDeniedHandler.java`:

```java
package com.back.facepick.global.config.security;

import com.back.facepick.global.error.GlobalErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {
    private final JsonMapper jsonMapper;

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        SecurityErrorResponses.write(response, jsonMapper, GlobalErrorCode.FORBIDDEN);
    }
}
```

`global/config/security/SecurityConfig.java`:

```java
package com.back.facepick.global.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private static final String[] PUBLIC_PATHS = {"/api/auth/**", "/swagger-ui/**", "/v3/api-docs/**"};

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth.requestMatchers(PUBLIC_PATHS)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

`global/config/web/SwaggerConfig.java`:

```java
package com.back.facepick.global.config.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    private static final String JWT_SCHEME_NAME = "JWT";

    @Bean
    public OpenAPI openAPI() {
        SecurityScheme securityScheme = new SecurityScheme()
                .name(JWT_SCHEME_NAME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .info(new Info().title("Facepick API").description("Facepick 서비스 API 명세서").version("0.1.0"))
                .addSecurityItem(new SecurityRequirement().addList(JWT_SCHEME_NAME))
                .components(new Components().addSecuritySchemes(JWT_SCHEME_NAME, securityScheme));
    }
}
```

- [ ] **Step 8: 테스트가 통과하는지 확인한다**

Run: `./gradlew spotlessApply test --tests 'com.back.facepick.global.*' --tests 'com.back.facepick.architecture.*'`
Expected: PASS (리졸버 3, 필터 3, EntryPoint 1, GlobalExceptionHandler 13, 아키텍처 전부)

- [ ] **Step 9: 커밋한다**

```bash
cd ~/facepick
git add backend
git commit -m "feat(global): JWT 인증 필터와 @AuthUser 추가"
```

---

### Task 2: user BC (사용자 엔티티, 내 정보 조회, 타 BC 용 조회)

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__create_users.sql`
- Create: `user/domain/{User,UserRole,UserErrorCode,UserRepository}.java`, `user/domain/exception/{UserNotFoundException,UserInvalidNicknameException}.java`
- Create: `user/infrastructure/{UserJpaRepository,UserRepositoryImpl}.java`
- Create: `user/application/{UserCommandService,UserQueryService,UserQueryApi}.java`
- Create: `user/application/dto/command/UserCreateCommand.java`, `dto/result/{UserCreateResult,UserResult}.java`, `dto/api/UserInfo.java`
- Create: `user/presentation/{UserController,UserApiDocs}.java`
- Test: `user/fixture/UserFixture.java`, `user/domain/UserTest.java`, `user/infrastructure/UserRepositoryImplTest.java`, `user/application/{UserCommandServiceTest,UserQueryServiceTest,UserQueryApiTest}.java`, `user/presentation/UserControllerTest.java`

**Interfaces:**
- Consumes: `@AuthUser` (Task 1)
- Produces (Task 4·6 이 사용):
  - `UserCommandService.createUser(UserCreateCommand command) → UserCreateResult` — auth 만 호출 (경계 예외)
  - `record UserCreateCommand(String nickname)`
  - `record UserCreateResult(Long userId, String authority, LocalDateTime createdAt)`
  - `UserQueryApi.getInfo(Long userId) → UserInfo` (없으면 `UserNotFoundException` 404)
  - `UserQueryApi.getInfos(Collection<Long> userIds) → Map<Long, UserInfo>`
  - `record UserInfo(Long userId, String nickname, String authority)`
  - `GET /api/users/me → 200 {userId, nickname}`

- [ ] **Step 1: 마이그레이션을 쓴다**

`backend/src/main/resources/db/migration/V1__create_users.sql`:

```sql
CREATE TABLE users (
    user_id     BIGSERIAL    PRIMARY KEY,
    nickname    VARCHAR(20)  NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL,
    modified_at TIMESTAMP(6) NOT NULL
);
```

- [ ] **Step 2: 실패하는 도메인 테스트와 픽스처를 쓴다**

`backend/src/test/java/com/back/facepick/user/fixture/UserFixture.java`:

```java
package com.back.facepick.user.fixture;

import com.back.facepick.user.domain.User;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

public final class UserFixture {

    public static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 1, 12, 0);

    private UserFixture() {}

    public static User user(Long userId, String nickname) {
        User user = User.create(nickname);
        // 저장 없이 쓰는 단위 테스트용이라 id·생성 시각을 리플렉션으로 채운다.
        ReflectionTestUtils.setField(user, "id", userId);
        ReflectionTestUtils.setField(user, "createdAt", CREATED_AT);
        return user;
    }
}
```

`backend/src/test/java/com/back/facepick/user/domain/UserTest.java`:

```java
package com.back.facepick.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.user.domain.exception.UserInvalidNicknameException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserTest {

    @Nested
    @DisplayName("사용자 생성")
    class Create {

        @Test
        @DisplayName("앞뒤 공백을 지운 닉네임과 일반 사용자 권한으로 생성된다")
        void createsWithTrimmedNickname() {
            // when
            User user = User.create("  민수 ");

            // then
            assertThat(user.getNickname()).isEqualTo("민수");
            assertThat(user.getRole()).isEqualTo(UserRole.USER);
        }

        @Test
        @DisplayName("빈 닉네임은 허용하지 않는다")
        void throwsWhenBlank() {
            // when & then
            assertThatThrownBy(() -> User.create("   ")).isInstanceOf(UserInvalidNicknameException.class);
        }

        @Test
        @DisplayName("20자를 넘는 닉네임은 허용하지 않는다")
        void throwsWhenTooLong() {
            // when & then
            assertThatThrownBy(() -> User.create("가".repeat(21))).isInstanceOf(UserInvalidNicknameException.class);
        }
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인한다**

Run: `./gradlew test --tests 'com.back.facepick.user.*'`
Expected: 컴파일 실패 — `User`, `UserRole`, `UserInvalidNicknameException` 없음.

- [ ] **Step 4: 도메인을 구현한다**

`user/domain/UserRole.java`:

```java
package com.back.facepick.user.domain;

public enum UserRole {
    USER("ROLE_USER");

    private final String authority;

    UserRole(String authority) {
        this.authority = authority;
    }

    public String authority() {
        return authority;
    }
}
```

`user/domain/UserErrorCode.java`:

```java
package com.back.facepick.user.domain;

import com.back.facepick.global.error.ErrorCode;
import com.back.facepick.global.error.ErrorType;

public enum UserErrorCode implements ErrorCode {
    USER_NOT_FOUND(ErrorType.NOT_FOUND, "존재하지 않는 사용자입니다."),
    USER_INVALID_NICKNAME(ErrorType.INVALID, "닉네임은 1~20자여야 합니다.");

    private final ErrorType type;
    private final String message;

    UserErrorCode(ErrorType type, String message) {
        this.type = type;
        this.message = message;
    }

    @Override
    public ErrorType type() {
        return type;
    }

    @Override
    public String message() {
        return message;
    }
}
```

`user/domain/exception/UserNotFoundException.java`:

```java
package com.back.facepick.user.domain.exception;

import com.back.facepick.global.error.BusinessException;
import com.back.facepick.user.domain.UserErrorCode;

public class UserNotFoundException extends BusinessException {
    public UserNotFoundException() {
        super(UserErrorCode.USER_NOT_FOUND);
    }
}
```

`user/domain/exception/UserInvalidNicknameException.java`:

```java
package com.back.facepick.user.domain.exception;

import com.back.facepick.global.error.BusinessException;
import com.back.facepick.user.domain.UserErrorCode;

public class UserInvalidNicknameException extends BusinessException {
    public UserInvalidNicknameException() {
        super(UserErrorCode.USER_INVALID_NICKNAME);
    }
}
```

`user/domain/User.java`:

```java
package com.back.facepick.user.domain;

import com.back.facepick.global.persistence.BaseTimeEntity;
import com.back.facepick.user.domain.exception.UserInvalidNicknameException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User extends BaseTimeEntity {
    private static final int MAX_NICKNAME_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, length = MAX_NICKNAME_LENGTH)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    private User(String nickname, UserRole role) {
        this.nickname = nickname;
        this.role = role;
    }

    public static User create(String nickname) {
        if (nickname == null || nickname.isBlank() || nickname.strip().length() > MAX_NICKNAME_LENGTH) {
            throw new UserInvalidNicknameException();
        }
        return new User(nickname.strip(), UserRole.USER);
    }
}
```

`user/domain/UserRepository.java`:

```java
package com.back.facepick.user.domain;

import java.util.Collection;
import java.util.List;

public interface UserRepository {
    User save(User user);

    User getById(Long userId);

    List<User> findAllByIds(Collection<Long> userIds);
}
```

- [ ] **Step 5: 도메인 테스트가 통과하는지 확인한다**

Run: `./gradlew test --tests 'com.back.facepick.user.domain.*'`
Expected: PASS (3개)

- [ ] **Step 6: 실패하는 저장소·서비스·컨트롤러 테스트를 쓴다**

`backend/src/test/java/com/back/facepick/user/infrastructure/UserRepositoryImplTest.java`:

```java
package com.back.facepick.user.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.back.facepick.user.domain.exception.UserNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserRepositoryImplTest {

    @Mock
    private UserJpaRepository userJpaRepository;

    @InjectMocks
    private UserRepositoryImpl userRepository;

    @Test
    @DisplayName("사용자가 없으면 UserNotFoundException 을 던진다")
    void getByIdThrowsWhenMissing() {
        // given
        given(userJpaRepository.findById(99L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userRepository.getById(99L)).isInstanceOf(UserNotFoundException.class);
    }
}
```

`backend/src/test/java/com/back/facepick/user/application/UserCommandServiceTest.java`:

```java
package com.back.facepick.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.back.facepick.user.application.dto.command.UserCreateCommand;
import com.back.facepick.user.application.dto.result.UserCreateResult;
import com.back.facepick.user.domain.User;
import com.back.facepick.user.domain.UserRepository;
import com.back.facepick.user.fixture.UserFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserCommandServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserCommandService userCommandService;

    @Test
    @DisplayName("사용자를 만들고 ID·권한·생성 시각을 돌려준다")
    void createUser() {
        // given
        given(userRepository.save(any(User.class))).willReturn(UserFixture.user(1L, "민수"));

        // when
        UserCreateResult result = userCommandService.createUser(new UserCreateCommand("민수"));

        // then
        assertThat(result).isEqualTo(new UserCreateResult(1L, "ROLE_USER", UserFixture.CREATED_AT));
    }
}
```

`backend/src/test/java/com/back/facepick/user/application/UserQueryServiceTest.java`:

```java
package com.back.facepick.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.back.facepick.user.application.dto.result.UserResult;
import com.back.facepick.user.domain.UserRepository;
import com.back.facepick.user.fixture.UserFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserQueryService userQueryService;

    @Test
    @DisplayName("내 정보를 조회한다")
    void getMyUser() {
        // given
        given(userRepository.getById(1L)).willReturn(UserFixture.user(1L, "민수"));

        // when
        UserResult result = userQueryService.getMyUser(1L);

        // then
        assertThat(result).isEqualTo(new UserResult(1L, "민수"));
    }
}
```

`backend/src/test/java/com/back/facepick/user/application/UserQueryApiTest.java`:

```java
package com.back.facepick.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.back.facepick.user.application.dto.api.UserInfo;
import com.back.facepick.user.domain.UserRepository;
import com.back.facepick.user.fixture.UserFixture;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserQueryApiTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserQueryApi userQueryApi;

    @Test
    @DisplayName("사용자 한 명의 공개 정보를 조회한다")
    void getInfo() {
        // given
        given(userRepository.getById(1L)).willReturn(UserFixture.user(1L, "민수"));

        // when
        UserInfo info = userQueryApi.getInfo(1L);

        // then
        assertThat(info).isEqualTo(new UserInfo(1L, "민수", "ROLE_USER"));
    }

    @Test
    @DisplayName("여러 사용자 정보를 ID 로 찾을 수 있는 Map 으로 돌려준다")
    void getInfos() {
        // given
        given(userRepository.findAllByIds(List.of(1L, 2L)))
                .willReturn(List.of(UserFixture.user(1L, "민수"), UserFixture.user(2L, "지영")));

        // when
        Map<Long, UserInfo> infosById = userQueryApi.getInfos(List.of(1L, 2L));

        // then
        assertThat(infosById).containsOnlyKeys(1L, 2L);
        assertThat(infosById.get(2L).nickname()).isEqualTo("지영");
    }
}
```

`backend/src/test/java/com/back/facepick/user/presentation/UserControllerTest.java`:

```java
package com.back.facepick.user.presentation;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.facepick.global.authorization.resolver.AuthUserArgumentResolver;
import com.back.facepick.global.error.GlobalExceptionHandler;
import com.back.facepick.user.application.UserQueryService;
import com.back.facepick.user.application.dto.result.UserResult;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserQueryService userQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthUserArgumentResolver())
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/users/me 는 200 과 내 정보")
    void getMyUser() throws Exception {
        // given
        given(userQueryService.getMyUser(1L)).willReturn(new UserResult(1L, "민수"));

        // when & then
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.nickname").value("민수"));
    }
}
```

- [ ] **Step 7: 테스트가 실패하는지 확인한다**

Run: `./gradlew test --tests 'com.back.facepick.user.*'`
Expected: 컴파일 실패 — `UserJpaRepository`, `UserRepositoryImpl`, `UserCommandService` 등 없음.

- [ ] **Step 8: 저장소·서비스·컨트롤러를 구현한다**

`user/infrastructure/UserJpaRepository.java`:

```java
package com.back.facepick.user.infrastructure;

import com.back.facepick.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<User, Long> {}
```

`user/infrastructure/UserRepositoryImpl.java`:

```java
package com.back.facepick.user.infrastructure;

import com.back.facepick.user.domain.User;
import com.back.facepick.user.domain.UserRepository;
import com.back.facepick.user.domain.exception.UserNotFoundException;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {
    private final UserJpaRepository userJpaRepository;

    @Override
    public User save(User user) {
        return userJpaRepository.save(user);
    }

    @Override
    public User getById(Long userId) {
        return userJpaRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }

    @Override
    public List<User> findAllByIds(Collection<Long> userIds) {
        return userJpaRepository.findAllById(userIds);
    }
}
```

`user/application/dto/command/UserCreateCommand.java`:

```java
package com.back.facepick.user.application.dto.command;

public record UserCreateCommand(String nickname) {}
```

`user/application/dto/result/UserCreateResult.java`:

```java
package com.back.facepick.user.application.dto.result;

import com.back.facepick.user.domain.User;
import java.time.LocalDateTime;

public record UserCreateResult(Long userId, String authority, LocalDateTime createdAt) {
    public static UserCreateResult from(User user) {
        return new UserCreateResult(user.getId(), user.getRole().authority(), user.getCreatedAt());
    }
}
```

`user/application/dto/result/UserResult.java`:

```java
package com.back.facepick.user.application.dto.result;

import com.back.facepick.user.domain.User;

public record UserResult(Long userId, String nickname) {
    public static UserResult from(User user) {
        return new UserResult(user.getId(), user.getNickname());
    }
}
```

`user/application/dto/api/UserInfo.java`:

```java
package com.back.facepick.user.application.dto.api;

import com.back.facepick.user.domain.User;

public record UserInfo(Long userId, String nickname, String authority) {
    public static UserInfo from(User user) {
        return new UserInfo(user.getId(), user.getNickname(), user.getRole().authority());
    }
}
```

`user/application/UserCommandService.java`:

```java
package com.back.facepick.user.application;

import com.back.facepick.user.application.dto.command.UserCreateCommand;
import com.back.facepick.user.application.dto.result.UserCreateResult;
import com.back.facepick.user.domain.User;
import com.back.facepick.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserCommandService {
    private final UserRepository userRepository;

    @Transactional
    public UserCreateResult createUser(UserCreateCommand command) {
        User user = userRepository.save(User.create(command.nickname()));
        return UserCreateResult.from(user);
    }
}
```

`user/application/UserQueryService.java`:

```java
package com.back.facepick.user.application;

import com.back.facepick.user.application.dto.result.UserResult;
import com.back.facepick.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserQueryService {
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResult getMyUser(Long userId) {
        return UserResult.from(userRepository.getById(userId));
    }
}
```

`user/application/UserQueryApi.java`:

```java
package com.back.facepick.user.application;

import com.back.facepick.user.application.dto.api.UserInfo;
import com.back.facepick.user.domain.User;
import com.back.facepick.user.domain.UserRepository;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserQueryApi {
    private final UserRepository userRepository;

    /**
     * 사용자 한 명의 공개 정보를 조회한다.
     *
     * @param userId 사용자 ID
     * @return 사용자 ID·닉네임·권한
     * @throws com.back.facepick.user.domain.exception.UserNotFoundException 없는 사용자면 (404)
     */
    @Transactional(readOnly = true)
    public UserInfo getInfo(Long userId) {
        return UserInfo.from(userRepository.getById(userId));
    }

    /**
     * 여러 사용자의 공개 정보를 한 번에 조회한다. 없는 ID 는 결과에서 빠진다.
     *
     * @param userIds 사용자 ID 목록
     * @return 사용자 ID 를 키로 한 사용자 정보
     */
    @Transactional(readOnly = true)
    public Map<Long, UserInfo> getInfos(Collection<Long> userIds) {
        return userRepository.findAllByIds(userIds).stream().collect(Collectors.toMap(User::getId, UserInfo::from));
    }
}
```

`user/presentation/UserApiDocs.java`:

```java
package com.back.facepick.user.presentation;

import com.back.facepick.user.application.dto.result.UserResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "[사용자] 사용자 API")
public interface UserApiDocs {

    @Operation(summary = "내 정보 조회", description = "로그인한 사용자의 ID 와 닉네임을 조회합니다.")
    ResponseEntity<UserResult> getMyUser(@Parameter(hidden = true) Long userId);
}
```

`user/presentation/UserController.java`:

```java
package com.back.facepick.user.presentation;

import com.back.facepick.global.authorization.annotation.AuthUser;
import com.back.facepick.user.application.UserQueryService;
import com.back.facepick.user.application.dto.result.UserResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController implements UserApiDocs {
    private final UserQueryService userQueryService;

    @Override
    @GetMapping("/me")
    public ResponseEntity<UserResult> getMyUser(@AuthUser Long userId) {
        return ResponseEntity.ok(userQueryService.getMyUser(userId));
    }
}
```

- [ ] **Step 9: 테스트가 통과하는지 확인한다**

Run: `./gradlew spotlessApply test --tests 'com.back.facepick.user.*' --tests 'com.back.facepick.architecture.*'`
Expected: PASS

- [ ] **Step 10: 커밋한다**

```bash
git add backend
git commit -m "feat(user): 사용자 엔티티와 내 정보 조회 API 추가"
```

---

### Task 3: auth 도메인·인프라 (자격 증명, refresh 토큰, JWT, BCrypt)

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__create_auth_tables.sql`
- Modify: `backend/src/main/resources/application.yml`, `application-local.yml`
- Create: `auth/domain/{Credential,RefreshToken,PasswordEncryptor,AuthTokenProvider,CredentialRepository,RefreshTokenRepository,AuthErrorCode}.java`
- Create: `auth/domain/exception/{AuthInvalidTokenException,AuthInvalidRefreshTokenException,AuthInvalidCredentialsException,AuthEmailAlreadyExistsException,AuthInvalidPasswordException,AuthInvalidEmailException}.java`
- Create: `auth/infrastructure/{BCryptPasswordEncryptor,JwtTokenProvider,CredentialJpaRepository,CredentialRepositoryImpl,RefreshTokenJpaRepository,RefreshTokenRepositoryImpl}.java`
- Test: `auth/fixture/{FakePasswordEncryptor,CredentialFixture}.java`, `auth/domain/{CredentialTest,RefreshTokenTest}.java`, `auth/infrastructure/{JwtTokenProviderTest,BCryptPasswordEncryptorTest,CredentialRepositoryImplQueryTest,RefreshTokenRepositoryImplQueryTest}.java`

**Interfaces:**
- Consumes: `AccessTokenVerifier`, `AuthenticatedUser` (Task 1)
- Produces (Task 4 가 사용):
  - `Credential.create(Long userId, String email, String rawPassword, PasswordEncryptor encryptor) → Credential`
  - `Credential.normalizeEmail(String email) → String`, `credential.authenticate(String rawPassword, PasswordEncryptor encryptor)` (틀리면 `AuthInvalidCredentialsException`), `credential.getUserId()`
  - `RefreshToken.create(Long userId, String rawToken, LocalDateTime expiresAt) → RefreshToken`, `RefreshToken.hash(String rawToken) → String`
  - `interface PasswordEncryptor { String encrypt(String rawPassword); boolean matches(String rawPassword, String passwordHash); }`
  - `interface AuthTokenProvider { String createAccessToken(Long userId, String role); String createRefreshToken(Long userId); Long getUserIdFromRefreshToken(String refreshToken); long refreshTokenTtlMillis(); }`
  - `CredentialRepository { Credential save(Credential); Optional<Credential> findByEmail(String email); }` — 중복 이메일 저장 시 `AuthEmailAlreadyExistsException`
  - `RefreshTokenRepository { RefreshToken save(RefreshToken); boolean existsByTokenHash(String tokenHash); void deleteByTokenHash(String tokenHash); }`
  - 예외: `AuthInvalidTokenException`, `AuthInvalidRefreshTokenException`, `AuthInvalidCredentialsException`, `AuthEmailAlreadyExistsException`, `AuthInvalidPasswordException`, `AuthInvalidEmailException`

- [ ] **Step 1: 마이그레이션과 설정을 쓴다**

`backend/src/main/resources/db/migration/V2__create_auth_tables.sql`:

```sql
-- user_id 는 users 를 ID 로만 참조한다 (BC 간 FK 를 두지 않는다).
CREATE TABLE credentials (
    credential_id BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       NOT NULL UNIQUE,
    email         VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP(6) NOT NULL,
    modified_at   TIMESTAMP(6) NOT NULL
);

CREATE TABLE refresh_tokens (
    refresh_token_id BIGSERIAL    PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    token_hash       VARCHAR(64)  NOT NULL UNIQUE,
    expires_at       TIMESTAMP(6) NOT NULL,
    created_at       TIMESTAMP(6) NOT NULL,
    modified_at      TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
```

`backend/src/main/resources/application.yml` 끝에 추가:

```yaml
jwt:
  # 운영에서는 환경 변수로 32바이트 이상 값을 넣는다. 비어 있으면 앱이 뜨지 않는다.
  secret-key: ${JWT_SECRET_KEY:}
  access-expiration-millis: 3600000
  refresh-expiration-millis: 2592000000
```

`backend/src/main/resources/application-local.yml` 끝에 추가:

```yaml
jwt:
  secret-key: local-only-secret-key-change-me-0123456789
```

- [ ] **Step 2: 테스트 픽스처를 쓴다**

`backend/src/test/java/com/back/facepick/auth/fixture/FakePasswordEncryptor.java`:

```java
package com.back.facepick.auth.fixture;

import com.back.facepick.auth.domain.PasswordEncryptor;
import java.util.Objects;

// BCrypt 는 느리고 결과가 매번 달라 단위 테스트에서는 예측 가능한 가짜 해시를 쓴다.
public final class FakePasswordEncryptor implements PasswordEncryptor {
    private static final String PREFIX = "hashed:";

    @Override
    public String encrypt(String rawPassword) {
        return PREFIX + rawPassword;
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return Objects.equals(PREFIX + rawPassword, passwordHash);
    }
}
```

`backend/src/test/java/com/back/facepick/auth/fixture/CredentialFixture.java`:

```java
package com.back.facepick.auth.fixture;

import com.back.facepick.auth.domain.Credential;

public final class CredentialFixture {

    public static final String PASSWORD = "password123";

    private CredentialFixture() {}

    public static Credential credential(Long userId, String email) {
        return Credential.create(userId, email, PASSWORD, new FakePasswordEncryptor());
    }
}
```

- [ ] **Step 3: 실패하는 도메인 테스트를 쓴다**

`backend/src/test/java/com/back/facepick/auth/domain/CredentialTest.java`:

```java
package com.back.facepick.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.auth.domain.exception.AuthInvalidCredentialsException;
import com.back.facepick.auth.domain.exception.AuthInvalidEmailException;
import com.back.facepick.auth.domain.exception.AuthInvalidPasswordException;
import com.back.facepick.auth.fixture.FakePasswordEncryptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CredentialTest {

    private final PasswordEncryptor encryptor = new FakePasswordEncryptor();

    @Nested
    @DisplayName("자격 증명 생성")
    class Create {

        @Test
        @DisplayName("이메일은 공백을 지우고 소문자로, 비밀번호는 해시로 저장한다")
        void normalizesEmailAndHashesPassword() {
            // when
            Credential credential = Credential.create(1L, "  Me@Example.COM ", "password123", encryptor);

            // then
            assertThat(credential.getEmail()).isEqualTo("me@example.com");
            assertThat(credential.getPasswordHash()).isEqualTo("hashed:password123");
            assertThat(credential.getUserId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("빈 이메일은 허용하지 않는다")
        void throwsWhenEmailBlank() {
            // when & then
            assertThatThrownBy(() -> Credential.create(1L, "  ", "password123", encryptor))
                    .isInstanceOf(AuthInvalidEmailException.class);
        }

        @Test
        @DisplayName("8자보다 짧은 비밀번호는 허용하지 않는다")
        void throwsWhenPasswordTooShort() {
            // when & then
            assertThatThrownBy(() -> Credential.create(1L, "me@example.com", "short", encryptor))
                    .isInstanceOf(AuthInvalidPasswordException.class);
        }

        @Test
        @DisplayName("72바이트를 넘는 비밀번호는 글자 수가 적어도 허용하지 않는다")
        void throwsWhenPasswordOver72Bytes() {
            // given
            String korean25 = "가".repeat(25); // UTF-8 로 75바이트

            // when & then
            assertThatThrownBy(() -> Credential.create(1L, "me@example.com", korean25, encryptor))
                    .isInstanceOf(AuthInvalidPasswordException.class);
        }

        @Test
        @DisplayName("정확히 72바이트인 비밀번호는 허용한다")
        void acceptsPasswordOf72Bytes() {
            // when & then
            assertThatCode(() -> Credential.create(1L, "me@example.com", "a".repeat(72), encryptor))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("비밀번호 확인")
    class Authenticate {

        @Test
        @DisplayName("비밀번호가 맞으면 통과한다")
        void passesWhenMatches() {
            // given
            Credential credential = Credential.create(1L, "me@example.com", "password123", encryptor);

            // when & then
            assertThatCode(() -> credential.authenticate("password123", encryptor))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("비밀번호가 틀리면 AuthInvalidCredentialsException 을 던진다")
        void throwsWhenMismatch() {
            // given
            Credential credential = Credential.create(1L, "me@example.com", "password123", encryptor);

            // when & then
            assertThatThrownBy(() -> credential.authenticate("wrong-password", encryptor))
                    .isInstanceOf(AuthInvalidCredentialsException.class);
        }
    }
}
```

`backend/src/test/java/com/back/facepick/auth/domain/RefreshTokenTest.java`:

```java
package com.back.facepick.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 10, 1, 12, 0);

    @Test
    @DisplayName("원문 대신 64자리 SHA-256 해시를 저장한다")
    void storesHashInsteadOfRawToken() {
        // when
        RefreshToken refreshToken = RefreshToken.create(1L, "raw-token", EXPIRES_AT);

        // then
        assertThat(refreshToken.getTokenHash()).hasSize(64).isNotEqualTo("raw-token");
        assertThat(refreshToken.getTokenHash()).isEqualTo(RefreshToken.hash("raw-token"));
        assertThat(refreshToken.getExpiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    @DisplayName("다른 토큰은 다른 해시가 된다")
    void differentTokensHaveDifferentHashes() {
        // when & then
        assertThat(RefreshToken.hash("token-a")).isNotEqualTo(RefreshToken.hash("token-b"));
    }
}
```

- [ ] **Step 4: 테스트가 실패하는지 확인한다**

Run: `./gradlew test --tests 'com.back.facepick.auth.*'`
Expected: 컴파일 실패 — `Credential`, `RefreshToken`, `PasswordEncryptor`, 예외 클래스 없음.

- [ ] **Step 5: 도메인을 구현한다**

`auth/domain/AuthErrorCode.java`:

```java
package com.back.facepick.auth.domain;

import com.back.facepick.global.error.ErrorCode;
import com.back.facepick.global.error.ErrorType;

public enum AuthErrorCode implements ErrorCode {
    AUTH_INVALID_TOKEN(ErrorType.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    AUTH_INVALID_REFRESH_TOKEN(ErrorType.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."),
    AUTH_INVALID_CREDENTIALS(ErrorType.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTH_EMAIL_ALREADY_EXISTS(ErrorType.CONFLICT, "이미 가입된 이메일입니다."),
    AUTH_INVALID_PASSWORD(ErrorType.INVALID, "비밀번호는 8자 이상, 72바이트 이하여야 합니다."),
    AUTH_INVALID_EMAIL(ErrorType.INVALID, "이메일 형식이 올바르지 않습니다.");

    private final ErrorType type;
    private final String message;

    AuthErrorCode(ErrorType type, String message) {
        this.type = type;
        this.message = message;
    }

    @Override
    public ErrorType type() {
        return type;
    }

    @Override
    public String message() {
        return message;
    }
}
```

예외 6개 — 모두 같은 형태. `auth/domain/exception/AuthInvalidTokenException.java`:

```java
package com.back.facepick.auth.domain.exception;

import com.back.facepick.auth.domain.AuthErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AuthInvalidTokenException extends BusinessException {
    public AuthInvalidTokenException() {
        super(AuthErrorCode.AUTH_INVALID_TOKEN);
    }
}
```

`auth/domain/exception/AuthInvalidRefreshTokenException.java`:

```java
package com.back.facepick.auth.domain.exception;

import com.back.facepick.auth.domain.AuthErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AuthInvalidRefreshTokenException extends BusinessException {
    public AuthInvalidRefreshTokenException() {
        super(AuthErrorCode.AUTH_INVALID_REFRESH_TOKEN);
    }
}
```

`auth/domain/exception/AuthInvalidCredentialsException.java`:

```java
package com.back.facepick.auth.domain.exception;

import com.back.facepick.auth.domain.AuthErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AuthInvalidCredentialsException extends BusinessException {
    public AuthInvalidCredentialsException() {
        super(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
    }
}
```

`auth/domain/exception/AuthEmailAlreadyExistsException.java`:

```java
package com.back.facepick.auth.domain.exception;

import com.back.facepick.auth.domain.AuthErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AuthEmailAlreadyExistsException extends BusinessException {
    public AuthEmailAlreadyExistsException() {
        super(AuthErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
    }
}
```

`auth/domain/exception/AuthInvalidPasswordException.java`:

```java
package com.back.facepick.auth.domain.exception;

import com.back.facepick.auth.domain.AuthErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AuthInvalidPasswordException extends BusinessException {
    public AuthInvalidPasswordException() {
        super(AuthErrorCode.AUTH_INVALID_PASSWORD);
    }
}
```

`auth/domain/exception/AuthInvalidEmailException.java`:

```java
package com.back.facepick.auth.domain.exception;

import com.back.facepick.auth.domain.AuthErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AuthInvalidEmailException extends BusinessException {
    public AuthInvalidEmailException() {
        super(AuthErrorCode.AUTH_INVALID_EMAIL);
    }
}
```

`auth/domain/PasswordEncryptor.java`:

```java
package com.back.facepick.auth.domain;

// 도메인이 Spring Security 를 모르게 하려고 둔 포트. 구현은 infrastructure 의 BCryptPasswordEncryptor.
public interface PasswordEncryptor {
    String encrypt(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
```

`auth/domain/AuthTokenProvider.java`:

```java
package com.back.facepick.auth.domain;

public interface AuthTokenProvider {

    // "Bearer " 접두사를 붙여 돌려준다. 클라이언트는 그대로 Authorization 헤더에 넣는다.
    String createAccessToken(Long userId, String role);

    String createRefreshToken(Long userId);

    // refresh 토큰이 아니거나 서명·만료가 유효하지 않으면 AuthInvalidTokenException.
    Long getUserIdFromRefreshToken(String refreshToken);

    long refreshTokenTtlMillis();
}
```

`auth/domain/Credential.java`:

```java
package com.back.facepick.auth.domain;

import com.back.facepick.auth.domain.exception.AuthInvalidCredentialsException;
import com.back.facepick.auth.domain.exception.AuthInvalidEmailException;
import com.back.facepick.auth.domain.exception.AuthInvalidPasswordException;
import com.back.facepick.global.persistence.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "credentials")
public class Credential extends BaseTimeEntity {
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MIN_PASSWORD_LENGTH = 8;
    // BCrypt 는 72바이트를 넘는 입력을 받지 않는다(Spring Security 가 예외를 던진다).
    // 한글은 한 글자가 3바이트라 글자 수가 아니라 바이트로 막는다.
    private static final int MAX_PASSWORD_BYTES = 72;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "credential_id")
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false, unique = true, length = MAX_EMAIL_LENGTH)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    private Credential(Long userId, String email, String passwordHash) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public static Credential create(
            Long userId, String email, String rawPassword, PasswordEncryptor passwordEncryptor) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isEmpty() || normalizedEmail.length() > MAX_EMAIL_LENGTH) {
            throw new AuthInvalidEmailException();
        }
        if (rawPassword == null
                || rawPassword.length() < MIN_PASSWORD_LENGTH
                || rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new AuthInvalidPasswordException();
        }
        return new Credential(userId, normalizedEmail, passwordEncryptor.encrypt(rawPassword));
    }

    // 대소문자만 다른 이메일로 중복 가입하지 않도록 저장과 조회 모두 같은 형태로 맞춘다.
    public static String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public void authenticate(String rawPassword, PasswordEncryptor passwordEncryptor) {
        if (!passwordEncryptor.matches(rawPassword, passwordHash)) {
            throw new AuthInvalidCredentialsException();
        }
    }
}
```

`auth/domain/RefreshToken.java`:

```java
package com.back.facepick.auth.domain;

import com.back.facepick.global.persistence.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseTimeEntity {
    private static final String HASH_ALGORITHM = "SHA-256";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    private RefreshToken(Long userId, String tokenHash, LocalDateTime expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken create(Long userId, String rawToken, LocalDateTime expiresAt) {
        return new RefreshToken(userId, hash(rawToken), expiresAt);
    }

    // DB 가 유출돼도 저장된 값으로 토큰을 재발급받지 못하도록 원문 대신 해시만 저장한다.
    public static String hash(String rawToken) {
        try {
            byte[] digest =
                    MessageDigest.getInstance(HASH_ALGORITHM).digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 모든 JVM 이 반드시 제공하는 알고리즘이라 실제로는 올 수 없다.
            throw new IllegalStateException(e);
        }
    }
}
```

`auth/domain/CredentialRepository.java`:

```java
package com.back.facepick.auth.domain;

import java.util.Optional;

public interface CredentialRepository {

    // 같은 이메일이 이미 있으면 AuthEmailAlreadyExistsException.
    Credential save(Credential credential);

    Optional<Credential> findByEmail(String email);
}
```

`auth/domain/RefreshTokenRepository.java`:

```java
package com.back.facepick.auth.domain;

public interface RefreshTokenRepository {
    RefreshToken save(RefreshToken refreshToken);

    boolean existsByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);
}
```

- [ ] **Step 6: 도메인 테스트가 통과하는지 확인한다**

Run: `./gradlew test --tests 'com.back.facepick.auth.domain.*'`
Expected: PASS (Credential 7, RefreshToken 2)

- [ ] **Step 7: 실패하는 인프라 테스트를 쓴다**

`backend/src/test/java/com/back/facepick/auth/infrastructure/JwtTokenProviderTest.java`:

```java
package com.back.facepick.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.auth.domain.exception.AuthInvalidTokenException;
import com.back.facepick.global.config.security.AuthenticatedUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-for-jwt-must-be-32-bytes!!";
    private static final long ONE_MINUTE = 60_000L;

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, ONE_MINUTE, ONE_MINUTE);

    @Test
    @DisplayName("액세스 토큰은 Bearer 접두사가 붙고, 사용자 ID 와 권한으로 검증된다")
    void verifiesAccessToken() {
        // when
        String accessToken = provider.createAccessToken(7L, "ROLE_USER");

        // then
        assertThat(accessToken).startsWith("Bearer ");
        assertThat(provider.verify(accessToken)).isEqualTo(new AuthenticatedUser(7L, "ROLE_USER"));
    }

    @Test
    @DisplayName("리프레시 토큰에서 사용자 ID 를 꺼낸다")
    void readsUserIdFromRefreshToken() {
        // when
        String refreshToken = provider.createRefreshToken(7L);

        // then
        assertThat(provider.getUserIdFromRefreshToken(refreshToken)).isEqualTo(7L);
    }

    @Test
    @DisplayName("같은 사용자에게 연달아 발급한 리프레시 토큰은 서로 다르다")
    void refreshTokensAreUnique() {
        // when
        String first = provider.createRefreshToken(7L);
        String second = provider.createRefreshToken(7L);

        // then
        assertThat(first).isNotEqualTo(second);
    }

    @Nested
    @DisplayName("거부하는 토큰")
    class Rejects {

        @Test
        @DisplayName("리프레시 토큰으로는 API 인증을 할 수 없다")
        void refreshTokenCannotAuthenticate() {
            // given
            String refreshToken = provider.createRefreshToken(7L);

            // when & then
            assertThatThrownBy(() -> provider.verify(refreshToken)).isInstanceOf(AuthInvalidTokenException.class);
        }

        @Test
        @DisplayName("액세스 토큰으로는 재발급을 받을 수 없다")
        void accessTokenCannotReissue() {
            // given
            String accessToken = provider.createAccessToken(7L, "ROLE_USER");

            // when & then
            assertThatThrownBy(() -> provider.getUserIdFromRefreshToken(accessToken))
                    .isInstanceOf(AuthInvalidTokenException.class);
        }

        @Test
        @DisplayName("다른 키로 서명된 토큰은 거부한다")
        void rejectsForeignSignature() {
            // given
            JwtTokenProvider other =
                    new JwtTokenProvider("another-secret-key-for-jwt-32-bytes-long!!", ONE_MINUTE, ONE_MINUTE);
            String accessToken = other.createAccessToken(7L, "ROLE_USER");

            // when & then
            assertThatThrownBy(() -> provider.verify(accessToken)).isInstanceOf(AuthInvalidTokenException.class);
        }

        @Test
        @DisplayName("만료된 토큰은 거부한다")
        void rejectsExpiredToken() {
            // given
            JwtTokenProvider expired = new JwtTokenProvider(SECRET, -ONE_MINUTE, -ONE_MINUTE);
            String accessToken = expired.createAccessToken(7L, "ROLE_USER");

            // when & then
            assertThatThrownBy(() -> provider.verify(accessToken)).isInstanceOf(AuthInvalidTokenException.class);
        }

        @Test
        @DisplayName("비어 있는 토큰은 거부한다")
        void rejectsBlankToken() {
            // when & then
            assertThatThrownBy(() -> provider.getUserIdFromRefreshToken(""))
                    .isInstanceOf(AuthInvalidTokenException.class);
        }
    }
}
```

`backend/src/test/java/com/back/facepick/auth/infrastructure/BCryptPasswordEncryptorTest.java`:

```java
package com.back.facepick.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BCryptPasswordEncryptorTest {

    private final BCryptPasswordEncryptor encryptor = new BCryptPasswordEncryptor();

    @Test
    @DisplayName("원문과 다른 해시를 만들고, 같은 비밀번호만 일치로 판단한다")
    void encryptsAndMatches() {
        // when
        String hash = encryptor.encrypt("password123");

        // then
        assertThat(hash).isNotEqualTo("password123");
        assertThat(encryptor.matches("password123", hash)).isTrue();
        assertThat(encryptor.matches("wrong-password", hash)).isFalse();
    }
}
```

`backend/src/test/java/com/back/facepick/auth/infrastructure/CredentialRepositoryImplQueryTest.java`:

```java
package com.back.facepick.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.auth.domain.Credential;
import com.back.facepick.auth.domain.exception.AuthEmailAlreadyExistsException;
import com.back.facepick.auth.fixture.CredentialFixture;
import com.back.facepick.global.config.data.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// local 프로필의 개발 DB 대신 컨테이너 DB 에만 붙는다. 스키마는 Flyway 마이그레이션으로 만든다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, CredentialRepositoryImpl.class})
@Testcontainers(disabledWithoutDocker = true)
class CredentialRepositoryImplQueryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private CredentialRepositoryImpl credentialRepository;

    @Test
    @DisplayName("정규화된 이메일로 찾는다")
    void findsByNormalizedEmail() {
        // given
        credentialRepository.save(CredentialFixture.credential(1L, "Me@Example.com"));

        // when & then
        assertThat(credentialRepository.findByEmail("me@example.com"))
                .get()
                .extracting(Credential::getUserId)
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("대소문자만 다른 이메일을 다시 저장하면 유니크 제약이 AuthEmailAlreadyExistsException 으로 바뀐다")
    void duplicateEmailIsConflict() {
        // given
        credentialRepository.save(CredentialFixture.credential(1L, "me@example.com"));

        // when & then
        assertThatThrownBy(() -> credentialRepository.save(CredentialFixture.credential(2L, "ME@example.com")))
                .isInstanceOf(AuthEmailAlreadyExistsException.class);
    }
}
```

`backend/src/test/java/com/back/facepick/auth/infrastructure/RefreshTokenRepositoryImplQueryTest.java`:

```java
package com.back.facepick.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.facepick.auth.domain.RefreshToken;
import com.back.facepick.global.config.data.JpaAuditingConfig;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, RefreshTokenRepositoryImpl.class})
@Testcontainers(disabledWithoutDocker = true)
class RefreshTokenRepositoryImplQueryTest {

    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 10, 1, 12, 0);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private RefreshTokenRepositoryImpl refreshTokenRepository;

    @Test
    @DisplayName("저장한 토큰은 해시로 찾을 수 있고, 지우면 사라지며, 없는 토큰을 지워도 조용히 끝난다")
    void savesExistsAndDeletes() {
        // given
        refreshTokenRepository.save(RefreshToken.create(1L, "raw-token", EXPIRES_AT));
        String tokenHash = RefreshToken.hash("raw-token");

        // when & then
        assertThat(refreshTokenRepository.existsByTokenHash(tokenHash)).isTrue();
        refreshTokenRepository.deleteByTokenHash(tokenHash);
        refreshTokenRepository.deleteByTokenHash(tokenHash);
        assertThat(refreshTokenRepository.existsByTokenHash(tokenHash)).isFalse();
    }
}
```

- [ ] **Step 8: 테스트가 실패하는지 확인한다**

Run: `./gradlew test --tests 'com.back.facepick.auth.infrastructure.*'`
Expected: 컴파일 실패 — `JwtTokenProvider`, `BCryptPasswordEncryptor`, `CredentialRepositoryImpl`, `RefreshTokenRepositoryImpl` 없음.

- [ ] **Step 9: 인프라를 구현한다**

`auth/infrastructure/BCryptPasswordEncryptor.java`:

```java
package com.back.facepick.auth.infrastructure;

import com.back.facepick.auth.domain.PasswordEncryptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordEncryptor implements PasswordEncryptor {
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public String encrypt(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
    }
}
```

`auth/infrastructure/JwtTokenProvider.java`:

```java
package com.back.facepick.auth.infrastructure;

import com.back.facepick.auth.domain.AuthTokenProvider;
import com.back.facepick.auth.domain.exception.AuthInvalidTokenException;
import com.back.facepick.global.config.security.AccessTokenVerifier;
import com.back.facepick.global.config.security.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider implements AuthTokenProvider, AccessTokenVerifier {
    private static final String BEARER = "Bearer ";
    private static final String ACCESS_TOKEN_SUBJECT = "AccessToken";
    private static final String REFRESH_TOKEN_SUBJECT = "RefreshToken";
    private static final String ID_CLAIM = "id";
    private static final String ROLE_CLAIM = "role";

    private final SecretKey signingKey;
    private final long accessTokenExpirationMillis;
    private final long refreshTokenExpirationMillis;

    public JwtTokenProvider(
            @Value("${jwt.secret-key}") String secretKey,
            @Value("${jwt.access-expiration-millis}") long accessTokenExpirationMillis,
            @Value("${jwt.refresh-expiration-millis}") long refreshTokenExpirationMillis) {
        // 32바이트보다 짧은 키면 여기서 WeakKeyException 이 나 앱이 뜨지 않는다.
        this.signingKey = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMillis = accessTokenExpirationMillis;
        this.refreshTokenExpirationMillis = refreshTokenExpirationMillis;
    }

    @Override
    public String createAccessToken(Long userId, String role) {
        Date now = new Date();
        return BEARER
                + Jwts.builder()
                        .subject(ACCESS_TOKEN_SUBJECT)
                        .claim(ID_CLAIM, userId)
                        .claim(ROLE_CLAIM, role)
                        .issuedAt(now)
                        .expiration(new Date(now.getTime() + accessTokenExpirationMillis))
                        .signWith(signingKey)
                        .compact();
    }

    @Override
    public String createRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                // 발급 시각이 초 단위라 같은 초에 두 번 로그인하면 토큰이 같아져 token_hash 유니크 제약에 걸린다. jti 로 구분한다.
                .id(UUID.randomUUID().toString())
                .subject(REFRESH_TOKEN_SUBJECT)
                .claim(ID_CLAIM, userId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpirationMillis))
                .signWith(signingKey)
                .compact();
    }

    @Override
    public Long getUserIdFromRefreshToken(String refreshToken) {
        return parseClaims(refreshToken, REFRESH_TOKEN_SUBJECT).get(ID_CLAIM, Long.class);
    }

    @Override
    public AuthenticatedUser verify(String token) {
        Claims claims = parseClaims(token, ACCESS_TOKEN_SUBJECT);
        return new AuthenticatedUser(claims.get(ID_CLAIM, Long.class), claims.get(ROLE_CLAIM, String.class));
    }

    @Override
    public long refreshTokenTtlMillis() {
        return refreshTokenExpirationMillis;
    }

    // access·refresh 가 같은 키로 서명되므로 subject 로 용도를 구분한다.
    // refresh 토큰으로 API 를 호출하거나 access 토큰으로 재발급받는 것을 막는다.
    private Claims parseClaims(String token, String expectedSubject) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(removeBearer(token))
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthInvalidTokenException();
        }
        if (!expectedSubject.equals(claims.getSubject())) {
            throw new AuthInvalidTokenException();
        }
        return claims;
    }

    private String removeBearer(String token) {
        if (token != null && token.startsWith(BEARER)) {
            return token.substring(BEARER.length());
        }
        return token;
    }
}
```

`auth/infrastructure/CredentialJpaRepository.java`:

```java
package com.back.facepick.auth.infrastructure;

import com.back.facepick.auth.domain.Credential;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredentialJpaRepository extends JpaRepository<Credential, Long> {
    Optional<Credential> findByEmail(String email);
}
```

`auth/infrastructure/CredentialRepositoryImpl.java`:

```java
package com.back.facepick.auth.infrastructure;

import com.back.facepick.auth.domain.Credential;
import com.back.facepick.auth.domain.CredentialRepository;
import com.back.facepick.auth.domain.exception.AuthEmailAlreadyExistsException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CredentialRepositoryImpl implements CredentialRepository {
    private final CredentialJpaRepository credentialJpaRepository;

    // 이메일 중복은 미리 조회하지 않고 유니크 제약으로 막는다. 동시에 같은 이메일로 가입해도 한쪽만 성공한다.
    // saveAndFlush 로 INSERT 를 즉시 실행해 위반을 이 자리에서 잡는다.
    @Override
    public Credential save(Credential credential) {
        try {
            return credentialJpaRepository.saveAndFlush(credential);
        } catch (DataIntegrityViolationException e) {
            throw new AuthEmailAlreadyExistsException();
        }
    }

    @Override
    public Optional<Credential> findByEmail(String email) {
        return credentialJpaRepository.findByEmail(email);
    }
}
```

`auth/infrastructure/RefreshTokenJpaRepository.java`:

```java
package com.back.facepick.auth.infrastructure;

import com.back.facepick.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, Long> {
    boolean existsByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true)
    @Query("delete from RefreshToken r where r.tokenHash = :tokenHash")
    void deleteByTokenHash(@Param("tokenHash") String tokenHash);
}
```

`auth/infrastructure/RefreshTokenRepositoryImpl.java`:

```java
package com.back.facepick.auth.infrastructure;

import com.back.facepick.auth.domain.RefreshToken;
import com.back.facepick.auth.domain.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepositoryImpl implements RefreshTokenRepository {
    private final RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        return refreshTokenJpaRepository.save(refreshToken);
    }

    @Override
    public boolean existsByTokenHash(String tokenHash) {
        return refreshTokenJpaRepository.existsByTokenHash(tokenHash);
    }

    @Override
    public void deleteByTokenHash(String tokenHash) {
        refreshTokenJpaRepository.deleteByTokenHash(tokenHash);
    }
}
```

- [ ] **Step 10: 테스트가 통과하는지 확인한다** (Docker 가 떠 있어야 Query 테스트가 실행된다)

Run: `./gradlew spotlessApply test --tests 'com.back.facepick.auth.*' --tests 'com.back.facepick.architecture.*'`
Expected: PASS. `build/test-results/test/TEST-*QueryTest.xml` 에서 `skipped="0"` 인지 확인(Docker 없으면 건너뛰어진다).

- [ ] **Step 11: 앱이 뜨는지 확인한다** (이제 `AccessTokenVerifier` 구현체가 있다)

```bash
cd ~/facepick/infra && docker compose up -d
docker compose exec -T postgres psql -U facepick -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
cd ~/facepick/backend && ./gradlew bootRun
```

Expected: 로그에 `Successfully applied 2 migrations` 와 `Started FacepickApplication`. 확인 후 Ctrl+C.

- [ ] **Step 12: 커밋한다**

```bash
cd ~/facepick
git add backend
git commit -m "feat(auth): 자격 증명·리프레시 토큰 저장과 JWT 발급 기반 추가"
```

---

### Task 4: auth 유스케이스와 API (회원가입, 로그인, 재발급, 로그아웃)

**Files:**
- Modify: `backend/src/test/java/com/back/facepick/architecture/BoundedContexts.java`
- Create: `auth/application/AuthCommandService.java`
- Create: `auth/application/dto/command/{SignUpCommand,LoginCommand,TokenReissueCommand,LogoutCommand}.java`
- Create: `auth/application/dto/result/{SignUpResult,LoginResult,TokenReissueResult}.java`
- Create: `auth/presentation/{AuthController,AuthApiDocs}.java`
- Create: `auth/presentation/dto/request/{SignUpRequest,LoginRequest,TokenReissueRequest,LogoutRequest}.java`
- Test: `auth/application/AuthCommandServiceTest.java`, `auth/presentation/AuthControllerTest.java`

**Interfaces:**
- Consumes: Task 2 `UserCommandService.createUser`, `UserCreateCommand`, `UserCreateResult`, `UserQueryApi.getInfo`, `UserInfo`; Task 3 전부
- Produces (API):
  - `POST /api/auth/signup {email, password, nickname}` → 201 `{userId, accessToken, refreshToken, createdAt}`
  - `POST /api/auth/login {email, password}` → 200 `{userId, accessToken, refreshToken}`
  - `POST /api/auth/reissue {refreshToken}` → 200 `{accessToken}`
  - `POST /api/auth/logout {refreshToken}` → 204
  - `accessToken` 은 `"Bearer ..."` 형태. 클라이언트는 그대로 `Authorization` 헤더에 넣는다.

- [ ] **Step 1: 경계 예외를 등록한다**

`BoundedContexts.java` 의 `SYNC_COMMAND_ALLOWLIST` 를 바꾼다:

```java
    // 타 BC 명령을 이벤트가 아닌 동기 호출로만 처리할 수 있어 경계 규칙에서 뺀 클래스의 FQCN.
    // 추가할 때는 해당 클래스의 호출 자리에 사유를 주석으로 남긴다.
    // AuthCommandService: 가입 시 새 userId 로 곧바로 자격 증명과 토큰을 만들어야 해서 UserCommandService.createUser 를 동기 호출한다.
    static final Set<String> SYNC_COMMAND_ALLOWLIST = Set.of("com.back.facepick.auth.application.AuthCommandService");
```

- [ ] **Step 2: 실패하는 서비스 테스트를 쓴다**

`backend/src/test/java/com/back/facepick/auth/application/AuthCommandServiceTest.java`:

```java
package com.back.facepick.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.back.facepick.auth.application.dto.command.LoginCommand;
import com.back.facepick.auth.application.dto.command.LogoutCommand;
import com.back.facepick.auth.application.dto.command.SignUpCommand;
import com.back.facepick.auth.application.dto.command.TokenReissueCommand;
import com.back.facepick.auth.application.dto.result.LoginResult;
import com.back.facepick.auth.application.dto.result.SignUpResult;
import com.back.facepick.auth.application.dto.result.TokenReissueResult;
import com.back.facepick.auth.domain.AuthTokenProvider;
import com.back.facepick.auth.domain.Credential;
import com.back.facepick.auth.domain.CredentialRepository;
import com.back.facepick.auth.domain.RefreshToken;
import com.back.facepick.auth.domain.RefreshTokenRepository;
import com.back.facepick.auth.domain.exception.AuthInvalidCredentialsException;
import com.back.facepick.auth.domain.exception.AuthInvalidRefreshTokenException;
import com.back.facepick.auth.fixture.CredentialFixture;
import com.back.facepick.auth.fixture.FakePasswordEncryptor;
import com.back.facepick.user.application.UserCommandService;
import com.back.facepick.user.application.UserQueryApi;
import com.back.facepick.user.application.dto.api.UserInfo;
import com.back.facepick.user.application.dto.command.UserCreateCommand;
import com.back.facepick.user.application.dto.result.UserCreateResult;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthCommandServiceTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 1, 12, 0);
    private static final String ACCESS_TOKEN = "Bearer access";
    private static final String REFRESH_TOKEN = "refresh";

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Spy
    private FakePasswordEncryptor passwordEncryptor = new FakePasswordEncryptor();

    @Mock
    private AuthTokenProvider tokenProvider;

    @Mock
    private UserCommandService userCommandService;

    @Mock
    private UserQueryApi userQueryApi;

    @InjectMocks
    private AuthCommandService authCommandService;

    private void givenTokens(Long userId) {
        given(tokenProvider.createAccessToken(userId, "ROLE_USER")).willReturn(ACCESS_TOKEN);
        given(tokenProvider.createRefreshToken(userId)).willReturn(REFRESH_TOKEN);
        given(tokenProvider.refreshTokenTtlMillis()).willReturn(60_000L);
    }

    @Test
    @DisplayName("회원가입하면 사용자·자격 증명을 만들고 토큰을 발급하며 refresh 토큰은 해시로 저장한다")
    void signUp() {
        // given
        given(userCommandService.createUser(new UserCreateCommand("민수")))
                .willReturn(new UserCreateResult(1L, "ROLE_USER", CREATED_AT));
        givenTokens(1L);

        // when
        SignUpResult result =
                authCommandService.signUp(new SignUpCommand("me@example.com", "password123", "민수"));

        // then
        assertThat(result).isEqualTo(new SignUpResult(1L, ACCESS_TOKEN, REFRESH_TOKEN, CREATED_AT));
        then(credentialRepository).should().save(any(Credential.class));
        then(refreshTokenRepository)
                .should()
                .save(argThat(token -> token.getTokenHash().equals(RefreshToken.hash(REFRESH_TOKEN))));
    }

    @Nested
    @DisplayName("로그인")
    class Login {

        @Test
        @DisplayName("이메일 대소문자와 상관없이 로그인하고 토큰을 발급한다")
        void issuesTokens() {
            // given
            given(credentialRepository.findByEmail("me@example.com"))
                    .willReturn(Optional.of(CredentialFixture.credential(1L, "me@example.com")));
            given(userQueryApi.getInfo(1L)).willReturn(new UserInfo(1L, "민수", "ROLE_USER"));
            givenTokens(1L);

            // when
            LoginResult result =
                    authCommandService.login(new LoginCommand("ME@example.com", CredentialFixture.PASSWORD));

            // then
            assertThat(result).isEqualTo(new LoginResult(1L, ACCESS_TOKEN, REFRESH_TOKEN));
        }

        @Test
        @DisplayName("가입하지 않은 이메일이면 AuthInvalidCredentialsException 을 던진다")
        void throwsWhenEmailUnknown() {
            // given
            given(credentialRepository.findByEmail("nobody@example.com")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authCommandService.login(new LoginCommand("nobody@example.com", "password123")))
                    .isInstanceOf(AuthInvalidCredentialsException.class);
        }

        @Test
        @DisplayName("비밀번호가 틀리면 토큰을 발급하지 않고 AuthInvalidCredentialsException 을 던진다")
        void throwsWhenPasswordWrong() {
            // given
            given(credentialRepository.findByEmail("me@example.com"))
                    .willReturn(Optional.of(CredentialFixture.credential(1L, "me@example.com")));

            // when & then
            assertThatThrownBy(() -> authCommandService.login(new LoginCommand("me@example.com", "wrong-password")))
                    .isInstanceOf(AuthInvalidCredentialsException.class);
            then(tokenProvider).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("토큰 재발급")
    class Reissue {

        @Test
        @DisplayName("저장된 refresh 토큰이면 최신 권한으로 access 토큰을 다시 발급한다")
        void reissuesAccessToken() {
            // given
            given(tokenProvider.getUserIdFromRefreshToken(REFRESH_TOKEN)).willReturn(1L);
            given(refreshTokenRepository.existsByTokenHash(RefreshToken.hash(REFRESH_TOKEN)))
                    .willReturn(true);
            given(userQueryApi.getInfo(1L)).willReturn(new UserInfo(1L, "민수", "ROLE_USER"));
            given(tokenProvider.createAccessToken(1L, "ROLE_USER")).willReturn(ACCESS_TOKEN);

            // when
            TokenReissueResult result = authCommandService.reissueToken(new TokenReissueCommand(REFRESH_TOKEN));

            // then
            assertThat(result).isEqualTo(new TokenReissueResult(ACCESS_TOKEN));
        }

        @Test
        @DisplayName("로그아웃으로 지운 refresh 토큰이면 AuthInvalidRefreshTokenException 을 던진다")
        void throwsWhenTokenRemoved() {
            // given
            given(tokenProvider.getUserIdFromRefreshToken(REFRESH_TOKEN)).willReturn(1L);
            given(refreshTokenRepository.existsByTokenHash(RefreshToken.hash(REFRESH_TOKEN)))
                    .willReturn(false);

            // when & then
            assertThatThrownBy(() -> authCommandService.reissueToken(new TokenReissueCommand(REFRESH_TOKEN)))
                    .isInstanceOf(AuthInvalidRefreshTokenException.class);
        }
    }

    @Test
    @DisplayName("로그아웃하면 refresh 토큰을 해시로 찾아 지운다")
    void logout() {
        // when
        authCommandService.logout(new LogoutCommand(REFRESH_TOKEN));

        // then
        then(refreshTokenRepository).should().deleteByTokenHash(RefreshToken.hash(REFRESH_TOKEN));
    }
}
```

- [ ] **Step 3: 실패하는 컨트롤러 테스트를 쓴다**

`backend/src/test/java/com/back/facepick/auth/presentation/AuthControllerTest.java`:

```java
package com.back.facepick.auth.presentation;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.facepick.auth.application.AuthCommandService;
import com.back.facepick.auth.application.dto.command.LogoutCommand;
import com.back.facepick.auth.application.dto.command.SignUpCommand;
import com.back.facepick.auth.application.dto.result.SignUpResult;
import com.back.facepick.global.error.GlobalExceptionHandler;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthCommandService authCommandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authCommandService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/auth/signup 은 201 과 토큰")
    void signUp() throws Exception {
        // given
        given(authCommandService.signUp(new SignUpCommand("me@example.com", "password123", "민수")))
                .willReturn(new SignUpResult(1L, "Bearer access", "refresh", LocalDateTime.of(2026, 9, 1, 12, 0)));

        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"me@example.com\",\"password\":\"password123\",\"nickname\":\"민수\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.accessToken").value("Bearer access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"));
    }

    @Test
    @DisplayName("이메일 형식이 틀리면 400 INVALID_INPUT")
    void signUpRejectsInvalidEmail() throws Exception {
        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"password123\",\"nickname\":\"민수\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("이메일 형식이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("POST /api/auth/logout 은 204")
    void logout() throws Exception {
        // when & then
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh\"}"))
                .andExpect(status().isNoContent());
        then(authCommandService).should().logout(new LogoutCommand("refresh"));
    }
}
```

- [ ] **Step 4: 테스트가 실패하는지 확인한다**

Run: `./gradlew test --tests 'com.back.facepick.auth.application.*' --tests 'com.back.facepick.auth.presentation.*'`
Expected: 컴파일 실패 — `AuthCommandService`, Command·Result·Request, `AuthController` 없음.

- [ ] **Step 5: Command·Result 를 구현한다**

`auth/application/dto/command/SignUpCommand.java`:

```java
package com.back.facepick.auth.application.dto.command;

public record SignUpCommand(String email, String password, String nickname) {}
```

`auth/application/dto/command/LoginCommand.java`:

```java
package com.back.facepick.auth.application.dto.command;

public record LoginCommand(String email, String password) {}
```

`auth/application/dto/command/TokenReissueCommand.java`:

```java
package com.back.facepick.auth.application.dto.command;

public record TokenReissueCommand(String refreshToken) {}
```

`auth/application/dto/command/LogoutCommand.java`:

```java
package com.back.facepick.auth.application.dto.command;

public record LogoutCommand(String refreshToken) {}
```

`auth/application/dto/result/SignUpResult.java`:

```java
package com.back.facepick.auth.application.dto.result;

import java.time.LocalDateTime;

public record SignUpResult(Long userId, String accessToken, String refreshToken, LocalDateTime createdAt) {}
```

`auth/application/dto/result/LoginResult.java`:

```java
package com.back.facepick.auth.application.dto.result;

public record LoginResult(Long userId, String accessToken, String refreshToken) {}
```

`auth/application/dto/result/TokenReissueResult.java`:

```java
package com.back.facepick.auth.application.dto.result;

public record TokenReissueResult(String accessToken) {}
```

- [ ] **Step 6: 서비스를 구현한다**

`auth/application/AuthCommandService.java`:

```java
package com.back.facepick.auth.application;

import com.back.facepick.auth.application.dto.command.LoginCommand;
import com.back.facepick.auth.application.dto.command.LogoutCommand;
import com.back.facepick.auth.application.dto.command.SignUpCommand;
import com.back.facepick.auth.application.dto.command.TokenReissueCommand;
import com.back.facepick.auth.application.dto.result.LoginResult;
import com.back.facepick.auth.application.dto.result.SignUpResult;
import com.back.facepick.auth.application.dto.result.TokenReissueResult;
import com.back.facepick.auth.domain.AuthTokenProvider;
import com.back.facepick.auth.domain.Credential;
import com.back.facepick.auth.domain.CredentialRepository;
import com.back.facepick.auth.domain.PasswordEncryptor;
import com.back.facepick.auth.domain.RefreshToken;
import com.back.facepick.auth.domain.RefreshTokenRepository;
import com.back.facepick.auth.domain.exception.AuthInvalidCredentialsException;
import com.back.facepick.auth.domain.exception.AuthInvalidRefreshTokenException;
import com.back.facepick.user.application.UserCommandService;
import com.back.facepick.user.application.UserQueryApi;
import com.back.facepick.user.application.dto.api.UserInfo;
import com.back.facepick.user.application.dto.command.UserCreateCommand;
import com.back.facepick.user.application.dto.result.UserCreateResult;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthCommandService {
    private final CredentialRepository credentialRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncryptor passwordEncryptor;
    private final AuthTokenProvider tokenProvider;

    private final UserCommandService userCommandService;
    private final UserQueryApi userQueryApi;

    // 이메일 중복은 credentials.email 유니크 제약으로 막는다(CredentialRepositoryImpl.save).
    // 제약에 걸리면 같은 트랜잭션에서 먼저 만든 사용자도 함께 롤백된다.
    @Transactional
    public SignUpResult signUp(SignUpCommand command) {
        // 새 userId 로 곧바로 자격 증명과 토큰을 만들어야 해서 user 생성만은 이벤트가 아닌 동기 호출이다.
        // 아키텍처 테스트의 BoundedContexts.SYNC_COMMAND_ALLOWLIST 에 이 클래스가 등록돼 있다.
        UserCreateResult user = userCommandService.createUser(new UserCreateCommand(command.nickname()));
        credentialRepository.save(
                Credential.create(user.userId(), command.email(), command.password(), passwordEncryptor));
        Tokens tokens = issueTokens(user.userId(), user.authority(), LocalDateTime.now());
        return new SignUpResult(user.userId(), tokens.accessToken(), tokens.refreshToken(), user.createdAt());
    }

    @Transactional
    public LoginResult login(LoginCommand command) {
        // 이메일이 없을 때와 비밀번호가 틀릴 때 같은 예외를 던져 가입 여부를 알려 주지 않는다.
        Credential credential = credentialRepository
                .findByEmail(Credential.normalizeEmail(command.email()))
                .orElseThrow(AuthInvalidCredentialsException::new);
        credential.authenticate(command.password(), passwordEncryptor);
        UserInfo user = userQueryApi.getInfo(credential.getUserId());
        Tokens tokens = issueTokens(user.userId(), user.authority(), LocalDateTime.now());
        return new LoginResult(user.userId(), tokens.accessToken(), tokens.refreshToken());
    }

    @Transactional(readOnly = true)
    public TokenReissueResult reissueToken(TokenReissueCommand command) {
        Long userId = tokenProvider.getUserIdFromRefreshToken(command.refreshToken());
        // 서명이 유효해도 로그아웃으로 지운 토큰은 거부한다.
        if (!refreshTokenRepository.existsByTokenHash(RefreshToken.hash(command.refreshToken()))) {
            throw new AuthInvalidRefreshTokenException();
        }
        // 토큰에 담긴 권한을 믿지 않고 최신 권한을 다시 조회한다.
        UserInfo user = userQueryApi.getInfo(userId);
        return new TokenReissueResult(tokenProvider.createAccessToken(user.userId(), user.authority()));
    }

    // 이미 지웠거나 없는 토큰이어도 로그아웃은 성공으로 끝낸다.
    @Transactional
    public void logout(LogoutCommand command) {
        refreshTokenRepository.deleteByTokenHash(RefreshToken.hash(command.refreshToken()));
    }

    private Tokens issueTokens(Long userId, String role, LocalDateTime now) {
        String accessToken = tokenProvider.createAccessToken(userId, role);
        String refreshToken = tokenProvider.createRefreshToken(userId);
        LocalDateTime expiresAt = now.plus(Duration.ofMillis(tokenProvider.refreshTokenTtlMillis()));
        refreshTokenRepository.save(RefreshToken.create(userId, refreshToken, expiresAt));
        return new Tokens(accessToken, refreshToken);
    }

    private record Tokens(String accessToken, String refreshToken) {}
}
```

- [ ] **Step 7: Request·ApiDocs·Controller 를 구현한다**

`auth/presentation/dto/request/SignUpRequest.java`:

```java
package com.back.facepick.auth.presentation.dto.request;

import com.back.facepick.auth.application.dto.command.SignUpCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @NotBlank(message = "이메일을 입력해주세요.")
                @Email(message = "이메일 형식이 올바르지 않습니다.")
                @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
                String email,
        @NotBlank(message = "비밀번호를 입력해주세요.") @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
                String password,
        @NotBlank(message = "닉네임을 입력해주세요.") @Size(max = 20, message = "닉네임은 20자 이하여야 합니다.")
                String nickname) {
    public SignUpCommand toCommand() {
        return new SignUpCommand(email, password, nickname);
    }
}
```

`auth/presentation/dto/request/LoginRequest.java`:

```java
package com.back.facepick.auth.presentation.dto.request;

import com.back.facepick.auth.application.dto.command.LoginCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "이메일을 입력해주세요.") @Email(message = "이메일 형식이 올바르지 않습니다.") String email,
        @NotBlank(message = "비밀번호를 입력해주세요.") String password) {
    public LoginCommand toCommand() {
        return new LoginCommand(email, password);
    }
}
```

`auth/presentation/dto/request/TokenReissueRequest.java`:

```java
package com.back.facepick.auth.presentation.dto.request;

import com.back.facepick.auth.application.dto.command.TokenReissueCommand;
import jakarta.validation.constraints.NotBlank;

public record TokenReissueRequest(@NotBlank(message = "리프레시 토큰을 입력해주세요.") String refreshToken) {
    public TokenReissueCommand toCommand() {
        return new TokenReissueCommand(refreshToken);
    }
}
```

`auth/presentation/dto/request/LogoutRequest.java`:

```java
package com.back.facepick.auth.presentation.dto.request;

import com.back.facepick.auth.application.dto.command.LogoutCommand;
import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(@NotBlank(message = "리프레시 토큰을 입력해주세요.") String refreshToken) {
    public LogoutCommand toCommand() {
        return new LogoutCommand(refreshToken);
    }
}
```

`auth/presentation/AuthApiDocs.java`:

```java
package com.back.facepick.auth.presentation;

import com.back.facepick.auth.application.dto.result.LoginResult;
import com.back.facepick.auth.application.dto.result.SignUpResult;
import com.back.facepick.auth.application.dto.result.TokenReissueResult;
import com.back.facepick.auth.presentation.dto.request.LoginRequest;
import com.back.facepick.auth.presentation.dto.request.LogoutRequest;
import com.back.facepick.auth.presentation.dto.request.SignUpRequest;
import com.back.facepick.auth.presentation.dto.request.TokenReissueRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

// 검증 어노테이션은 여기에만 둔다 (구현 메서드에 두면 HV000151).
@Tag(name = "[인증] 회원가입·로그인 API")
public interface AuthApiDocs {

    @Operation(summary = "회원가입", description = "이메일·비밀번호·닉네임으로 가입하고 토큰을 발급합니다. (201)")
    ResponseEntity<SignUpResult> signUp(@Valid SignUpRequest request);

    @Operation(summary = "로그인", description = "이메일·비밀번호로 로그인하고 토큰을 발급합니다.")
    ResponseEntity<LoginResult> login(@Valid LoginRequest request);

    @Operation(summary = "액세스 토큰 재발급", description = "리프레시 토큰으로 새 액세스 토큰을 발급합니다.")
    ResponseEntity<TokenReissueResult> reissueToken(@Valid TokenReissueRequest request);

    @Operation(summary = "로그아웃", description = "리프레시 토큰을 폐기합니다. 이미 폐기된 토큰이어도 204 입니다.")
    ResponseEntity<Void> logout(@Valid LogoutRequest request);
}
```

`auth/presentation/AuthController.java`:

```java
package com.back.facepick.auth.presentation;

import com.back.facepick.auth.application.AuthCommandService;
import com.back.facepick.auth.application.dto.result.LoginResult;
import com.back.facepick.auth.application.dto.result.SignUpResult;
import com.back.facepick.auth.application.dto.result.TokenReissueResult;
import com.back.facepick.auth.presentation.dto.request.LoginRequest;
import com.back.facepick.auth.presentation.dto.request.LogoutRequest;
import com.back.facepick.auth.presentation.dto.request.SignUpRequest;
import com.back.facepick.auth.presentation.dto.request.TokenReissueRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApiDocs {
    private final AuthCommandService authCommandService;

    @Override
    @PostMapping("/signup")
    public ResponseEntity<SignUpResult> signUp(@RequestBody SignUpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authCommandService.signUp(request.toCommand()));
    }

    @Override
    @PostMapping("/login")
    public ResponseEntity<LoginResult> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authCommandService.login(request.toCommand()));
    }

    @Override
    @PostMapping("/reissue")
    public ResponseEntity<TokenReissueResult> reissueToken(@RequestBody TokenReissueRequest request) {
        return ResponseEntity.ok(authCommandService.reissueToken(request.toCommand()));
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody LogoutRequest request) {
        authCommandService.logout(request.toCommand());
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 8: 테스트가 통과하는지 확인한다**

Run: `./gradlew spotlessApply test --tests 'com.back.facepick.auth.*' --tests 'com.back.facepick.architecture.*'`
Expected: PASS. 아키텍처 테스트가 실패하면 `SYNC_COMMAND_ALLOWLIST` 의 FQCN 오타를 먼저 확인한다(규칙을 완화하지 않는다).

- [ ] **Step 9: 커밋한다**

```bash
git add backend
git commit -m "feat(auth): 이메일 회원가입·로그인·토큰 재발급·로그아웃 API 추가"
```

---

### Task 5: album 도메인·인프라 (앨범, 참여자)

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__create_albums.sql`
- Modify: `backend/src/main/resources/application.yml` (`album.retention-days` 제거)
- Delete: `album/domain/.gitkeep`, `album/infrastructure/.gitkeep`
- Create: `album/domain/{Album,AlbumMember,AlbumRole,AlbumErrorCode,AlbumRepository,AlbumMemberRepository}.java`, `album/domain/exception/{AlbumNotFoundException,AlbumInvalidTitleException,AlbumNotMemberException}.java`
- Create: `album/infrastructure/{AlbumJpaRepository,AlbumRepositoryImpl,AlbumMemberJpaRepository,AlbumMemberRepositoryImpl}.java`
- Test: `album/fixture/AlbumFixture.java`, `album/domain/{AlbumTest,AlbumMemberTest}.java`, `album/infrastructure/{AlbumRepositoryImplTest,AlbumMemberRepositoryImplQueryTest}.java`

**Interfaces:**
- Produces (Task 6 이 사용):
  - `Album.create(Long ownerId, String title, LocalDateTime now) → Album` (만료 = now + 30일), `getId()`, `getOwnerId()`, `getTitle()`, `getExpiresAt()`, `getCreatedAt()`
  - `AlbumMember.create(Album album, Long userId, AlbumRole role) → AlbumMember`, `getAlbum()`, `getUserId()`, `getRole()`, `getCreatedAt()`
  - `enum AlbumRole { OWNER, MEMBER }`
  - `AlbumRepository { Album save(Album); Album getById(Long albumId); }` — 없으면 `AlbumNotFoundException`(404)
  - `AlbumMemberRepository { AlbumMember save(AlbumMember); AlbumMember getByAlbumIdAndUserId(Long albumId, Long userId); List<AlbumMember> findAllByUserIdWithAlbum(Long userId); }` — 참여자가 아니면 `AlbumNotMemberException`(403)

- [ ] **Step 1: 마이그레이션을 쓰고 쓰지 않는 설정을 지운다**

`backend/src/main/resources/db/migration/V3__create_albums.sql`:

```sql
-- owner_id, user_id 는 users 를 ID 로만 참조한다 (BC 간 FK 를 두지 않는다).
CREATE TABLE albums (
    album_id    BIGSERIAL    PRIMARY KEY,
    owner_id    BIGINT       NOT NULL,
    title       VARCHAR(50)  NOT NULL,
    expires_at  TIMESTAMP(6) NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL,
    modified_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE album_members (
    album_member_id BIGSERIAL    PRIMARY KEY,
    album_id        BIGINT       NOT NULL REFERENCES albums (album_id) ON DELETE CASCADE,
    user_id         BIGINT       NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL,
    modified_at     TIMESTAMP(6) NOT NULL,
    UNIQUE (album_id, user_id)
);

CREATE INDEX idx_album_members_user ON album_members (user_id, created_at DESC);
```

`backend/src/main/resources/application.yml` 에서 아래 두 줄을 지운다 (보관 기간은 `Album.RETENTION_DAYS` 상수로 옮긴다):

```yaml
album:
  retention-days: 30
```

```bash
rm ~/facepick/backend/src/main/java/com/back/facepick/album/domain/.gitkeep \
   ~/facepick/backend/src/main/java/com/back/facepick/album/infrastructure/.gitkeep
```

- [ ] **Step 2: 실패하는 도메인 테스트와 픽스처를 쓴다**

`backend/src/test/java/com/back/facepick/album/fixture/AlbumFixture.java`:

```java
package com.back.facepick.album.fixture;

import com.back.facepick.album.domain.Album;
import com.back.facepick.album.domain.AlbumMember;
import com.back.facepick.album.domain.AlbumRole;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

public final class AlbumFixture {

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0);

    private AlbumFixture() {}

    public static Album album(Long albumId, Long ownerId) {
        Album album = Album.create(ownerId, "제주 여행", NOW);
        // 저장 없이 쓰는 단위 테스트용이라 id·생성 시각을 리플렉션으로 채운다.
        ReflectionTestUtils.setField(album, "id", albumId);
        ReflectionTestUtils.setField(album, "createdAt", NOW);
        return album;
    }

    public static AlbumMember member(Album album, Long userId, AlbumRole role) {
        AlbumMember member = AlbumMember.create(album, userId, role);
        ReflectionTestUtils.setField(member, "createdAt", NOW);
        return member;
    }
}
```

`backend/src/test/java/com/back/facepick/album/domain/AlbumTest.java`:

```java
package com.back.facepick.album.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.album.domain.exception.AlbumInvalidTitleException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AlbumTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0);

    @Nested
    @DisplayName("앨범 생성")
    class Create {

        @Test
        @DisplayName("앞뒤 공백을 지운 제목으로 만들고 30일 뒤에 만료된다")
        void createsWithExpiry() {
            // when
            Album album = Album.create(1L, "  제주 여행 ", NOW);

            // then
            assertThat(album.getTitle()).isEqualTo("제주 여행");
            assertThat(album.getOwnerId()).isEqualTo(1L);
            assertThat(album.getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 12, 0));
        }

        @Test
        @DisplayName("빈 제목은 허용하지 않는다")
        void throwsWhenBlank() {
            // when & then
            assertThatThrownBy(() -> Album.create(1L, " ", NOW)).isInstanceOf(AlbumInvalidTitleException.class);
        }

        @Test
        @DisplayName("50자를 넘는 제목은 허용하지 않는다")
        void throwsWhenTooLong() {
            // when & then
            assertThatThrownBy(() -> Album.create(1L, "가".repeat(51), NOW))
                    .isInstanceOf(AlbumInvalidTitleException.class);
        }
    }
}
```

`backend/src/test/java/com/back/facepick/album/domain/AlbumMemberTest.java`:

```java
package com.back.facepick.album.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AlbumMemberTest {

    @Test
    @DisplayName("앨범·사용자·역할로 참여자를 만든다")
    void createsMember() {
        // given
        Album album = Album.create(1L, "제주 여행", LocalDateTime.of(2026, 9, 1, 12, 0));

        // when
        AlbumMember member = AlbumMember.create(album, 2L, AlbumRole.MEMBER);

        // then
        assertThat(member.getAlbum()).isSameAs(album);
        assertThat(member.getUserId()).isEqualTo(2L);
        assertThat(member.getRole()).isEqualTo(AlbumRole.MEMBER);
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인한다**

Run: `./gradlew test --tests 'com.back.facepick.album.*'`
Expected: 컴파일 실패 — `Album`, `AlbumMember`, `AlbumRole`, `AlbumInvalidTitleException` 없음.

- [ ] **Step 4: 도메인을 구현한다**

`album/domain/AlbumRole.java`:

```java
package com.back.facepick.album.domain;

public enum AlbumRole {
    OWNER,
    MEMBER
}
```

`album/domain/AlbumErrorCode.java`:

```java
package com.back.facepick.album.domain;

import com.back.facepick.global.error.ErrorCode;
import com.back.facepick.global.error.ErrorType;

public enum AlbumErrorCode implements ErrorCode {
    ALBUM_NOT_FOUND(ErrorType.NOT_FOUND, "존재하지 않는 앨범입니다."),
    ALBUM_INVALID_TITLE(ErrorType.INVALID, "앨범 제목은 1~50자여야 합니다."),
    ALBUM_NOT_MEMBER(ErrorType.FORBIDDEN, "앨범 참여자만 볼 수 있습니다.");

    private final ErrorType type;
    private final String message;

    AlbumErrorCode(ErrorType type, String message) {
        this.type = type;
        this.message = message;
    }

    @Override
    public ErrorType type() {
        return type;
    }

    @Override
    public String message() {
        return message;
    }
}
```

`album/domain/exception/AlbumNotFoundException.java`:

```java
package com.back.facepick.album.domain.exception;

import com.back.facepick.album.domain.AlbumErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AlbumNotFoundException extends BusinessException {
    public AlbumNotFoundException() {
        super(AlbumErrorCode.ALBUM_NOT_FOUND);
    }
}
```

`album/domain/exception/AlbumInvalidTitleException.java`:

```java
package com.back.facepick.album.domain.exception;

import com.back.facepick.album.domain.AlbumErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AlbumInvalidTitleException extends BusinessException {
    public AlbumInvalidTitleException() {
        super(AlbumErrorCode.ALBUM_INVALID_TITLE);
    }
}
```

`album/domain/exception/AlbumNotMemberException.java`:

```java
package com.back.facepick.album.domain.exception;

import com.back.facepick.album.domain.AlbumErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AlbumNotMemberException extends BusinessException {
    public AlbumNotMemberException() {
        super(AlbumErrorCode.ALBUM_NOT_MEMBER);
    }
}
```

`album/domain/Album.java`:

```java
package com.back.facepick.album.domain;

import com.back.facepick.album.domain.exception.AlbumInvalidTitleException;
import com.back.facepick.global.persistence.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "albums")
public class Album extends BaseTimeEntity {
    // PRD 결정: 저장 비용과 얼굴 데이터 보관을 줄이려고 앨범은 30일 뒤 삭제한다.
    public static final int RETENTION_DAYS = 30;
    private static final int MAX_TITLE_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "album_id")
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    private Album(Long ownerId, String title, LocalDateTime expiresAt) {
        this.ownerId = ownerId;
        this.title = title;
        this.expiresAt = expiresAt;
    }

    public static Album create(Long ownerId, String title, LocalDateTime now) {
        if (title == null || title.isBlank() || title.strip().length() > MAX_TITLE_LENGTH) {
            throw new AlbumInvalidTitleException();
        }
        return new Album(ownerId, title.strip(), now.plusDays(RETENTION_DAYS));
    }
}
```

`album/domain/AlbumMember.java`:

```java
package com.back.facepick.album.domain;

import com.back.facepick.global.persistence.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "album_members")
public class AlbumMember extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "album_member_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "album_id", nullable = false)
    private Album album;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlbumRole role;

    private AlbumMember(Album album, Long userId, AlbumRole role) {
        this.album = album;
        this.userId = userId;
        this.role = role;
    }

    public static AlbumMember create(Album album, Long userId, AlbumRole role) {
        return new AlbumMember(album, userId, role);
    }
}
```

`album/domain/AlbumRepository.java`:

```java
package com.back.facepick.album.domain;

public interface AlbumRepository {
    Album save(Album album);

    Album getById(Long albumId);
}
```

`album/domain/AlbumMemberRepository.java`:

```java
package com.back.facepick.album.domain;

import java.util.List;

public interface AlbumMemberRepository {
    AlbumMember save(AlbumMember albumMember);

    // 참여자가 아니면 AlbumNotMemberException.
    AlbumMember getByAlbumIdAndUserId(Long albumId, Long userId);

    /** 최근 참여순 (같으면 id 내림차순). 앨범을 함께 불러온다. */
    List<AlbumMember> findAllByUserIdWithAlbum(Long userId);
}
```

- [ ] **Step 5: 도메인 테스트가 통과하는지 확인한다**

Run: `./gradlew test --tests 'com.back.facepick.album.domain.*'`
Expected: PASS (Album 3, AlbumMember 1)

- [ ] **Step 6: 실패하는 저장소 테스트를 쓴다**

`backend/src/test/java/com/back/facepick/album/infrastructure/AlbumRepositoryImplTest.java`:

```java
package com.back.facepick.album.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.back.facepick.album.domain.exception.AlbumNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlbumRepositoryImplTest {

    @Mock
    private AlbumJpaRepository albumJpaRepository;

    @InjectMocks
    private AlbumRepositoryImpl albumRepository;

    @Test
    @DisplayName("앨범이 없으면 AlbumNotFoundException 을 던진다")
    void getByIdThrowsWhenMissing() {
        // given
        given(albumJpaRepository.findById(99L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> albumRepository.getById(99L)).isInstanceOf(AlbumNotFoundException.class);
    }
}
```

`backend/src/test/java/com/back/facepick/album/infrastructure/AlbumMemberRepositoryImplQueryTest.java`:

```java
package com.back.facepick.album.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.album.domain.Album;
import com.back.facepick.album.domain.AlbumMember;
import com.back.facepick.album.domain.AlbumRole;
import com.back.facepick.album.domain.exception.AlbumNotMemberException;
import com.back.facepick.global.config.data.JpaAuditingConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, AlbumMemberRepositoryImpl.class})
@Testcontainers(disabledWithoutDocker = true)
class AlbumMemberRepositoryImplQueryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private AlbumMemberRepositoryImpl albumMemberRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("내 앨범 목록 - 내가 참여한 앨범만 최근 참여순으로, 앨범을 함께 불러온다")
    void findsMyAlbumsLatestFirstWithAlbum() {
        // given
        Album jeju = entityManager.persist(Album.create(1L, "제주", NOW));
        Album busan = entityManager.persist(Album.create(2L, "부산", NOW));
        albumMemberRepository.save(AlbumMember.create(jeju, 1L, AlbumRole.OWNER));
        albumMemberRepository.save(AlbumMember.create(busan, 2L, AlbumRole.OWNER));
        albumMemberRepository.save(AlbumMember.create(busan, 1L, AlbumRole.MEMBER));
        entityManager.flush();
        entityManager.clear();

        // when
        List<AlbumMember> members = albumMemberRepository.findAllByUserIdWithAlbum(1L);

        // then
        assertThat(members).extracting(member -> member.getAlbum().getTitle()).containsExactly("부산", "제주");
        assertThat(members).allSatisfy(member -> assertThat(Hibernate.isInitialized(member.getAlbum()))
                .isTrue());
    }

    @Test
    @DisplayName("참여자 조회 - 참여하지 않은 앨범이면 AlbumNotMemberException")
    void throwsWhenNotMember() {
        // given
        Album jeju = entityManager.persist(Album.create(1L, "제주", NOW));
        albumMemberRepository.save(AlbumMember.create(jeju, 1L, AlbumRole.OWNER));

        // when & then
        assertThatThrownBy(() -> albumMemberRepository.getByAlbumIdAndUserId(jeju.getId(), 2L))
                .isInstanceOf(AlbumNotMemberException.class);
    }
}
```

- [ ] **Step 7: 테스트가 실패하는지 확인한다**

Run: `./gradlew test --tests 'com.back.facepick.album.infrastructure.*'`
Expected: 컴파일 실패 — `AlbumJpaRepository`, `AlbumRepositoryImpl`, `AlbumMemberRepositoryImpl` 없음.

- [ ] **Step 8: 저장소를 구현한다**

`album/infrastructure/AlbumJpaRepository.java`:

```java
package com.back.facepick.album.infrastructure;

import com.back.facepick.album.domain.Album;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlbumJpaRepository extends JpaRepository<Album, Long> {}
```

`album/infrastructure/AlbumRepositoryImpl.java`:

```java
package com.back.facepick.album.infrastructure;

import com.back.facepick.album.domain.Album;
import com.back.facepick.album.domain.AlbumRepository;
import com.back.facepick.album.domain.exception.AlbumNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AlbumRepositoryImpl implements AlbumRepository {
    private final AlbumJpaRepository albumJpaRepository;

    @Override
    public Album save(Album album) {
        return albumJpaRepository.save(album);
    }

    @Override
    public Album getById(Long albumId) {
        return albumJpaRepository.findById(albumId).orElseThrow(AlbumNotFoundException::new);
    }
}
```

`album/infrastructure/AlbumMemberJpaRepository.java`:

```java
package com.back.facepick.album.infrastructure;

import com.back.facepick.album.domain.AlbumMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlbumMemberJpaRepository extends JpaRepository<AlbumMember, Long> {

    @Query("select m from AlbumMember m where m.album.id = :albumId and m.userId = :userId")
    Optional<AlbumMember> findByAlbumIdAndUserId(@Param("albumId") Long albumId, @Param("userId") Long userId);

    // 목록에서 앨범 제목·만료일을 함께 쓰므로 fetch join 으로 N+1 을 막는다.
    @Query("select m from AlbumMember m join fetch m.album where m.userId = :userId"
            + " order by m.createdAt desc, m.id desc")
    List<AlbumMember> findAllByUserIdWithAlbum(@Param("userId") Long userId);
}
```

`album/infrastructure/AlbumMemberRepositoryImpl.java`:

```java
package com.back.facepick.album.infrastructure;

import com.back.facepick.album.domain.AlbumMember;
import com.back.facepick.album.domain.AlbumMemberRepository;
import com.back.facepick.album.domain.exception.AlbumNotMemberException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AlbumMemberRepositoryImpl implements AlbumMemberRepository {
    private final AlbumMemberJpaRepository albumMemberJpaRepository;

    @Override
    public AlbumMember save(AlbumMember albumMember) {
        return albumMemberJpaRepository.save(albumMember);
    }

    @Override
    public AlbumMember getByAlbumIdAndUserId(Long albumId, Long userId) {
        return albumMemberJpaRepository
                .findByAlbumIdAndUserId(albumId, userId)
                .orElseThrow(AlbumNotMemberException::new);
    }

    @Override
    public List<AlbumMember> findAllByUserIdWithAlbum(Long userId) {
        return albumMemberJpaRepository.findAllByUserIdWithAlbum(userId);
    }
}
```

- [ ] **Step 9: 테스트가 통과하는지 확인한다**

Run: `./gradlew spotlessApply test --tests 'com.back.facepick.album.*' --tests 'com.back.facepick.architecture.*'`
Expected: PASS (Query 테스트 `skipped="0"` 확인)

- [ ] **Step 10: 커밋한다**

```bash
git add backend
git commit -m "feat(album): 앨범·참여자 엔티티와 저장소 추가"
```

---

### Task 6: album 유스케이스와 API (생성, 조회, 내 앨범 목록)

**Files:**
- Delete: `album/application/.gitkeep`, `album/presentation/.gitkeep`
- Create: `album/application/{AlbumCommandService,AlbumQueryService}.java`
- Create: `album/application/dto/command/AlbumCreateCommand.java`, `dto/result/{AlbumCreateResult,AlbumDetailResult,AlbumResult}.java`
- Create: `album/presentation/{AlbumController,AlbumApiDocs}.java`, `presentation/dto/request/AlbumCreateRequest.java`
- Test: `album/application/{AlbumCommandServiceTest,AlbumQueryServiceTest}.java`, `album/presentation/AlbumControllerTest.java`

**Interfaces:**
- Consumes: Task 5 전부, Task 2 `UserQueryApi.getInfo`, `UserInfo`, Task 1 `@AuthUser`
- Produces (API):
  - `POST /api/albums {title}` → 201 `{albumId, title, expiresAt, createdAt}` (만든 사람이 OWNER)
  - `GET /api/albums/me` → 200 `[{albumId, title, role, expiresAt, joinedAt}]` (최근 참여순)
  - `GET /api/albums/{albumId}` → 200 `{albumId, title, ownerId, ownerNickname, expiresAt, createdAt}` / 없으면 404 `ALBUM_NOT_FOUND` / 참여자가 아니면 403 `ALBUM_NOT_MEMBER`

- [ ] **Step 1: 실패하는 서비스 테스트를 쓴다**

```bash
rm ~/facepick/backend/src/main/java/com/back/facepick/album/application/.gitkeep \
   ~/facepick/backend/src/main/java/com/back/facepick/album/presentation/.gitkeep
```

`backend/src/test/java/com/back/facepick/album/application/AlbumCommandServiceTest.java`:

```java
package com.back.facepick.album.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.back.facepick.album.application.dto.command.AlbumCreateCommand;
import com.back.facepick.album.application.dto.result.AlbumCreateResult;
import com.back.facepick.album.domain.Album;
import com.back.facepick.album.domain.AlbumMemberRepository;
import com.back.facepick.album.domain.AlbumRepository;
import com.back.facepick.album.domain.AlbumRole;
import com.back.facepick.album.fixture.AlbumFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlbumCommandServiceTest {

    @Mock
    private AlbumRepository albumRepository;

    @Mock
    private AlbumMemberRepository albumMemberRepository;

    @InjectMocks
    private AlbumCommandService albumCommandService;

    @Test
    @DisplayName("앨범을 만들면 만든 사람을 앨범장으로 등록한다")
    void createAlbumRegistersOwner() {
        // given
        Album album = AlbumFixture.album(10L, 1L);
        given(albumRepository.save(any(Album.class))).willReturn(album);

        // when
        AlbumCreateResult result = albumCommandService.createAlbum(1L, new AlbumCreateCommand("제주 여행"));

        // then
        assertThat(result).isEqualTo(new AlbumCreateResult(10L, "제주 여행", album.getExpiresAt(), AlbumFixture.NOW));
        then(albumMemberRepository)
                .should()
                .save(argThat(member -> member.getAlbum() == album
                        && member.getUserId().equals(1L)
                        && member.getRole() == AlbumRole.OWNER));
    }
}
```

`backend/src/test/java/com/back/facepick/album/application/AlbumQueryServiceTest.java`:

```java
package com.back.facepick.album.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.back.facepick.album.application.dto.result.AlbumDetailResult;
import com.back.facepick.album.application.dto.result.AlbumResult;
import com.back.facepick.album.domain.Album;
import com.back.facepick.album.domain.AlbumMemberRepository;
import com.back.facepick.album.domain.AlbumRepository;
import com.back.facepick.album.domain.AlbumRole;
import com.back.facepick.album.domain.exception.AlbumNotMemberException;
import com.back.facepick.album.fixture.AlbumFixture;
import com.back.facepick.user.application.UserQueryApi;
import com.back.facepick.user.application.dto.api.UserInfo;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlbumQueryServiceTest {

    @Mock
    private AlbumRepository albumRepository;

    @Mock
    private AlbumMemberRepository albumMemberRepository;

    @Mock
    private UserQueryApi userQueryApi;

    @InjectMocks
    private AlbumQueryService albumQueryService;

    @Nested
    @DisplayName("앨범 조회")
    class GetAlbum {

        @Test
        @DisplayName("참여자는 앨범장 닉네임과 함께 앨범을 조회한다")
        void returnsAlbumWithOwnerNickname() {
            // given
            Album album = AlbumFixture.album(10L, 1L);
            given(albumRepository.getById(10L)).willReturn(album);
            given(albumMemberRepository.getByAlbumIdAndUserId(10L, 2L))
                    .willReturn(AlbumFixture.member(album, 2L, AlbumRole.MEMBER));
            given(userQueryApi.getInfo(1L)).willReturn(new UserInfo(1L, "민수", "ROLE_USER"));

            // when
            AlbumDetailResult result = albumQueryService.getAlbum(2L, 10L);

            // then
            assertThat(result)
                    .isEqualTo(new AlbumDetailResult(
                            10L, "제주 여행", 1L, "민수", album.getExpiresAt(), AlbumFixture.NOW));
        }

        @Test
        @DisplayName("참여자가 아니면 AlbumNotMemberException 을 던지고 사용자 정보는 조회하지 않는다")
        void throwsWhenNotMember() {
            // given
            given(albumRepository.getById(10L)).willReturn(AlbumFixture.album(10L, 1L));
            given(albumMemberRepository.getByAlbumIdAndUserId(10L, 3L)).willThrow(new AlbumNotMemberException());

            // when & then
            assertThatThrownBy(() -> albumQueryService.getAlbum(3L, 10L))
                    .isInstanceOf(AlbumNotMemberException.class);
            then(userQueryApi).shouldHaveNoInteractions();
        }
    }

    @Test
    @DisplayName("내 앨범 목록은 참여 정보와 앨범을 합쳐 돌려준다")
    void getMyAlbums() {
        // given
        Album album = AlbumFixture.album(10L, 1L);
        given(albumMemberRepository.findAllByUserIdWithAlbum(1L))
                .willReturn(List.of(AlbumFixture.member(album, 1L, AlbumRole.OWNER)));

        // when
        List<AlbumResult> results = albumQueryService.getMyAlbums(1L);

        // then
        assertThat(results)
                .containsExactly(
                        new AlbumResult(10L, "제주 여행", AlbumRole.OWNER, album.getExpiresAt(), AlbumFixture.NOW));
    }
}
```

- [ ] **Step 2: 실패하는 컨트롤러 테스트를 쓴다**

`backend/src/test/java/com/back/facepick/album/presentation/AlbumControllerTest.java`:

```java
package com.back.facepick.album.presentation;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.facepick.album.application.AlbumCommandService;
import com.back.facepick.album.application.AlbumQueryService;
import com.back.facepick.album.application.dto.command.AlbumCreateCommand;
import com.back.facepick.album.application.dto.result.AlbumCreateResult;
import com.back.facepick.album.application.dto.result.AlbumResult;
import com.back.facepick.album.domain.AlbumRole;
import com.back.facepick.global.authorization.resolver.AuthUserArgumentResolver;
import com.back.facepick.global.error.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AlbumControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0);

    @Mock
    private AlbumCommandService albumCommandService;

    @Mock
    private AlbumQueryService albumQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AlbumController(albumCommandService, albumQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthUserArgumentResolver())
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/albums 는 201 과 만든 앨범")
    void createAlbum() throws Exception {
        // given
        given(albumCommandService.createAlbum(1L, new AlbumCreateCommand("제주 여행")))
                .willReturn(new AlbumCreateResult(10L, "제주 여행", NOW.plusDays(30), NOW));

        // when & then
        mockMvc.perform(post("/api/albums")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제주 여행\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.albumId").value(10))
                .andExpect(jsonPath("$.title").value("제주 여행"));
    }

    @Test
    @DisplayName("빈 제목이면 400 INVALID_INPUT")
    void createAlbumRejectsBlankTitle() throws Exception {
        // when & then
        mockMvc.perform(post("/api/albums")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("앨범 제목을 입력해주세요."));
    }

    @Test
    @DisplayName("GET /api/albums/me 는 200 과 내 앨범 목록")
    void getMyAlbums() throws Exception {
        // given
        given(albumQueryService.getMyAlbums(1L))
                .willReturn(List.of(new AlbumResult(10L, "제주 여행", AlbumRole.OWNER, NOW.plusDays(30), NOW)));

        // when & then
        mockMvc.perform(get("/api/albums/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].albumId").value(10))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인한다**

Run: `./gradlew test --tests 'com.back.facepick.album.application.*' --tests 'com.back.facepick.album.presentation.*'`
Expected: 컴파일 실패 — `AlbumCommandService`, `AlbumQueryService`, DTO, `AlbumController` 없음.

- [ ] **Step 4: Command·Result 를 구현한다**

`album/application/dto/command/AlbumCreateCommand.java`:

```java
package com.back.facepick.album.application.dto.command;

public record AlbumCreateCommand(String title) {}
```

`album/application/dto/result/AlbumCreateResult.java`:

```java
package com.back.facepick.album.application.dto.result;

import com.back.facepick.album.domain.Album;
import java.time.LocalDateTime;

public record AlbumCreateResult(Long albumId, String title, LocalDateTime expiresAt, LocalDateTime createdAt) {
    public static AlbumCreateResult from(Album album) {
        return new AlbumCreateResult(album.getId(), album.getTitle(), album.getExpiresAt(), album.getCreatedAt());
    }
}
```

`album/application/dto/result/AlbumDetailResult.java`:

```java
package com.back.facepick.album.application.dto.result;

import com.back.facepick.album.domain.Album;
import java.time.LocalDateTime;

public record AlbumDetailResult(
        Long albumId,
        String title,
        Long ownerId,
        String ownerNickname,
        LocalDateTime expiresAt,
        LocalDateTime createdAt) {

    public static AlbumDetailResult of(Album album, String ownerNickname) {
        return new AlbumDetailResult(
                album.getId(),
                album.getTitle(),
                album.getOwnerId(),
                ownerNickname,
                album.getExpiresAt(),
                album.getCreatedAt());
    }
}
```

`album/application/dto/result/AlbumResult.java`:

```java
package com.back.facepick.album.application.dto.result;

import com.back.facepick.album.domain.AlbumMember;
import com.back.facepick.album.domain.AlbumRole;
import java.time.LocalDateTime;

public record AlbumResult(
        Long albumId, String title, AlbumRole role, LocalDateTime expiresAt, LocalDateTime joinedAt) {

    public static AlbumResult from(AlbumMember member) {
        return new AlbumResult(
                member.getAlbum().getId(),
                member.getAlbum().getTitle(),
                member.getRole(),
                member.getAlbum().getExpiresAt(),
                member.getCreatedAt());
    }
}
```

- [ ] **Step 5: 서비스를 구현한다**

`album/application/AlbumCommandService.java`:

```java
package com.back.facepick.album.application;

import com.back.facepick.album.application.dto.command.AlbumCreateCommand;
import com.back.facepick.album.application.dto.result.AlbumCreateResult;
import com.back.facepick.album.domain.Album;
import com.back.facepick.album.domain.AlbumMember;
import com.back.facepick.album.domain.AlbumMemberRepository;
import com.back.facepick.album.domain.AlbumRepository;
import com.back.facepick.album.domain.AlbumRole;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlbumCommandService {
    private final AlbumRepository albumRepository;
    private final AlbumMemberRepository albumMemberRepository;

    @Transactional
    public AlbumCreateResult createAlbum(Long userId, AlbumCreateCommand command) {
        Album album = albumRepository.save(Album.create(userId, command.title(), LocalDateTime.now()));
        albumMemberRepository.save(AlbumMember.create(album, userId, AlbumRole.OWNER));
        return AlbumCreateResult.from(album);
    }
}
```

`album/application/AlbumQueryService.java`:

```java
package com.back.facepick.album.application;

import com.back.facepick.album.application.dto.result.AlbumDetailResult;
import com.back.facepick.album.application.dto.result.AlbumResult;
import com.back.facepick.album.domain.Album;
import com.back.facepick.album.domain.AlbumMemberRepository;
import com.back.facepick.album.domain.AlbumRepository;
import com.back.facepick.user.application.UserQueryApi;
import com.back.facepick.user.application.dto.api.UserInfo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlbumQueryService {
    private final AlbumRepository albumRepository;
    private final AlbumMemberRepository albumMemberRepository;
    private final UserQueryApi userQueryApi;

    @Transactional(readOnly = true)
    public AlbumDetailResult getAlbum(Long userId, Long albumId) {
        Album album = albumRepository.getById(albumId);
        // 참여자가 아니면 여기서 AlbumNotMemberException(403)이 난다. 없는 앨범은 위에서 404 가 먼저 난다.
        albumMemberRepository.getByAlbumIdAndUserId(albumId, userId);
        UserInfo owner = userQueryApi.getInfo(album.getOwnerId());
        return AlbumDetailResult.of(album, owner.nickname());
    }

    @Transactional(readOnly = true)
    public List<AlbumResult> getMyAlbums(Long userId) {
        return albumMemberRepository.findAllByUserIdWithAlbum(userId).stream()
                .map(AlbumResult::from)
                .toList();
    }
}
```

- [ ] **Step 6: Request·ApiDocs·Controller 를 구현한다**

`album/presentation/dto/request/AlbumCreateRequest.java`:

```java
package com.back.facepick.album.presentation.dto.request;

import com.back.facepick.album.application.dto.command.AlbumCreateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlbumCreateRequest(
        @NotBlank(message = "앨범 제목을 입력해주세요.") @Size(max = 50, message = "앨범 제목은 50자 이하여야 합니다.")
                String title) {
    public AlbumCreateCommand toCommand() {
        return new AlbumCreateCommand(title);
    }
}
```

`album/presentation/AlbumApiDocs.java`:

```java
package com.back.facepick.album.presentation;

import com.back.facepick.album.application.dto.result.AlbumCreateResult;
import com.back.facepick.album.application.dto.result.AlbumDetailResult;
import com.back.facepick.album.application.dto.result.AlbumResult;
import com.back.facepick.album.presentation.dto.request.AlbumCreateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;

// 검증 어노테이션은 여기에만 둔다 (구현 메서드에 두면 HV000151).
@Tag(name = "[앨범] 앨범 API")
public interface AlbumApiDocs {

    @Operation(summary = "앨범 생성", description = "앨범을 만들고 만든 사람을 앨범장으로 등록합니다. 30일 뒤 만료됩니다. (201)")
    ResponseEntity<AlbumCreateResult> createAlbum(@Parameter(hidden = true) Long userId, @Valid AlbumCreateRequest request);

    @Operation(summary = "내 앨범 목록", description = "내가 참여한 앨범을 최근 참여순으로 조회합니다.")
    ResponseEntity<List<AlbumResult>> getMyAlbums(@Parameter(hidden = true) Long userId);

    @Operation(summary = "앨범 조회", description = "앨범 정보를 조회합니다. 참여자만 조회할 수 있습니다.")
    ResponseEntity<AlbumDetailResult> getAlbum(@Parameter(hidden = true) Long userId, Long albumId);
}
```

`album/presentation/AlbumController.java`:

```java
package com.back.facepick.album.presentation;

import com.back.facepick.album.application.AlbumCommandService;
import com.back.facepick.album.application.AlbumQueryService;
import com.back.facepick.album.application.dto.result.AlbumCreateResult;
import com.back.facepick.album.application.dto.result.AlbumDetailResult;
import com.back.facepick.album.application.dto.result.AlbumResult;
import com.back.facepick.album.presentation.dto.request.AlbumCreateRequest;
import com.back.facepick.global.authorization.annotation.AuthUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/albums")
@RequiredArgsConstructor
public class AlbumController implements AlbumApiDocs {
    private final AlbumCommandService albumCommandService;
    private final AlbumQueryService albumQueryService;

    @Override
    @PostMapping
    public ResponseEntity<AlbumCreateResult> createAlbum(
            @AuthUser Long userId, @RequestBody AlbumCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(albumCommandService.createAlbum(userId, request.toCommand()));
    }

    @Override
    @GetMapping("/me")
    public ResponseEntity<List<AlbumResult>> getMyAlbums(@AuthUser Long userId) {
        return ResponseEntity.ok(albumQueryService.getMyAlbums(userId));
    }

    @Override
    @GetMapping("/{albumId}")
    public ResponseEntity<AlbumDetailResult> getAlbum(@AuthUser Long userId, @PathVariable Long albumId) {
        return ResponseEntity.ok(albumQueryService.getAlbum(userId, albumId));
    }
}
```

- [ ] **Step 7: 전체 테스트가 통과하는지 확인한다**

Run: `./gradlew spotlessApply build`
Expected: BUILD SUCCESSFUL (spotlessCheck + 전체 테스트 + 아키텍처 테스트)

- [ ] **Step 8: 커밋한다**

```bash
git add backend
git commit -m "feat(album): 앨범 생성·조회·내 앨범 목록 API 추가"
```

---

### Task 7: 실제 서버로 전체 흐름 확인 후 머지

**Files:** 없음 (검증)

- [ ] **Step 1: DB 를 비우고 서버를 띄운다**

```bash
cd ~/facepick/infra && docker compose up -d
docker compose exec -T postgres psql -U facepick -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
cd ~/facepick/backend && ./gradlew bootRun
```

Expected: `Successfully applied 3 migrations`, `Started FacepickApplication`. 아래 단계는 다른 터미널에서.

- [ ] **Step 2: 회원가입·로그인·내 정보를 확인한다**

```bash
BASE=http://localhost:8080
json() { python3 -c "import sys,json;print(json.load(sys.stdin)['$1'])"; }

A=$(curl -s -X POST $BASE/api/auth/signup -H 'Content-Type: application/json' \
  -d '{"email":"minsu@example.com","password":"password123","nickname":"민수"}')
echo "$A"                                  # 201 본문: userId, accessToken("Bearer ..."), refreshToken
TOKEN_A=$(echo "$A" | json accessToken); REFRESH_A=$(echo "$A" | json refreshToken)

curl -s $BASE/api/users/me -H "Authorization: $TOKEN_A"; echo              # {"userId":1,"nickname":"민수"}
curl -s -o /dev/null -w '%{http_code}\n' $BASE/api/users/me                 # 401
curl -s -X POST $BASE/api/auth/signup -H 'Content-Type: application/json' \
  -d '{"email":"MINSU@example.com","password":"password123","nickname":"민수2"}'; echo   # 409 AUTH_EMAIL_ALREADY_EXISTS
curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"minsu@example.com","password":"wrong-pass"}'; echo          # 401 AUTH_INVALID_CREDENTIALS
```

- [ ] **Step 3: 앨범 생성·조회·권한을 확인한다**

```bash
ALBUM=$(curl -s -X POST $BASE/api/albums -H "Authorization: $TOKEN_A" -H 'Content-Type: application/json' \
  -d '{"title":"제주 여행"}')
echo "$ALBUM"                              # 201: albumId, expiresAt = 오늘 + 30일
ALBUM_ID=$(echo "$ALBUM" | json albumId)

curl -s $BASE/api/albums/me -H "Authorization: $TOKEN_A"; echo               # [{... "role":"OWNER" ...}]
curl -s $BASE/api/albums/$ALBUM_ID -H "Authorization: $TOKEN_A"; echo        # ownerNickname: 민수

B=$(curl -s -X POST $BASE/api/auth/signup -H 'Content-Type: application/json' \
  -d '{"email":"jiyoung@example.com","password":"password123","nickname":"지영"}')
TOKEN_B=$(echo "$B" | json accessToken)
curl -s $BASE/api/albums/$ALBUM_ID -H "Authorization: $TOKEN_B"; echo        # 403 ALBUM_NOT_MEMBER
curl -s $BASE/api/albums/9999 -H "Authorization: $TOKEN_B"; echo             # 404 ALBUM_NOT_FOUND
```

- [ ] **Step 4: 토큰 재발급·로그아웃을 확인한다**

```bash
curl -s -X POST $BASE/api/auth/reissue -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH_A\"}"; echo                               # 200 {"accessToken":"Bearer ..."}
curl -s -X POST $BASE/api/auth/reissue -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"${TOKEN_A#Bearer }\"}"; echo                       # 401 AUTH_INVALID_TOKEN (access 토큰으로 재발급 불가)
curl -s -o /dev/null -w '%{http_code}\n' -X POST $BASE/api/auth/logout -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH_A\"}"                                      # 204
curl -s -X POST $BASE/api/auth/reissue -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH_A\"}"; echo                               # 401 AUTH_INVALID_REFRESH_TOKEN
```

기대와 다른 응답이 하나라도 있으면 머지하지 않고 원인을 고친 뒤(해당 Task 의 테스트부터 추가) Step 1 부터 다시 한다.

- [ ] **Step 5: 서버를 끄고 머지한다**

bootRun 터미널에서 Ctrl+C 후:

```bash
cd ~/facepick/backend && ./gradlew spotlessApply build
cd ~/facepick
git status --short          # 비어 있어야 한다
git switch main
git merge --no-ff feat/auth-user-album -m "Merge branch 'feat/auth-user-album'"
git branch -d feat/auth-user-album
```

---

## 다음 계획 후보

- 초대 링크로 앨범 참여 (`album` BC: 초대 토큰 발급, `POST /api/albums/join`, `AlbumQueryApi.isMember` — photo BC 가 쓸 것)
- 사진 업로드 파이프라인 (`photo` BC: 업로드 URL 발급, 업로드 완료 → Kafka `photo.uploaded`)

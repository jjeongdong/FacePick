package com.back.facepick.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// 아키텍처·소스 규칙 검사 대상 BC 와, 경계 규칙의 허용 예외 목록.
final class BoundedContexts {

    static final Path MAIN_ROOT = Path.of("src/main/java/com/back/facepick");

    private static final String GLOBAL = "global";

    // 최상위 패키지 중 global 을 뺀 전부가 BC 다. 손으로 관리하는 목록은 새 BC 를 빠뜨려
    // 검사에서 조용히 빠지게 만들므로 디렉터리에서 찾는다.
    static final Set<String> NAMES = discover();

    // 성능 문제가 측정으로 확인돼 타 BC 테이블 JOIN 을 허용한 클래스의 FQCN.
    // 추가할 때는 해당 클래스에 사유와 측정 근거를 주석으로 남긴다.
    static final Set<String> CROSS_CONTEXT_ALLOWLIST = Set.of();

    // 타 BC 명령을 이벤트가 아닌 동기 호출로만 처리할 수 있어 경계 규칙에서 뺀 클래스의 FQCN.
    // 추가할 때는 해당 클래스의 호출 자리에 사유를 주석으로 남긴다.
    static final Set<String> SYNC_COMMAND_ALLOWLIST = Set.of();

    private BoundedContexts() {}

    private static Set<String> discover() {
        try (Stream<Path> children = Files.list(MAIN_ROOT)) {
            return children.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !GLOBAL.equals(name))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

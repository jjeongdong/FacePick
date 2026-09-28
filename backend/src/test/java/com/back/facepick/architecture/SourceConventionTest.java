package com.back.facepick.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SourceConventionTest {

    @Test
    @DisplayName("BC 의 소스는 텍스트 규칙을 지킨다")
    void contextSourcesFollowConventions() throws IOException {
        // given
        List<String> report = new ArrayList<>();

        // when
        for (String context : BoundedContexts.NAMES) {
            try (Stream<Path> paths = Files.walk(BoundedContexts.MAIN_ROOT.resolve(context))) {
                List<Path> javaFiles =
                        paths.filter(path -> path.toString().endsWith(".java")).toList();
                for (Path file : javaFiles) {
                    for (SourceConventionRules.Violation violation :
                            SourceConventionRules.check(Files.readString(file))) {
                        report.add(file + ":" + violation.lineNumber() + " [" + violation.rule() + "] "
                                + violation.line());
                    }
                }
            }
        }

        // then
        assertThat(report).isEmpty();
    }

    @Test
    @DisplayName("global 을 뺀 최상위 패키지를 모두 BC 로 찾는다 — 경로가 틀려 검사 대상이 비는 채로 통과하지 않게")
    void discoversBoundedContexts() {
        assertThat(BoundedContexts.NAMES).contains("album", "photo", "person").doesNotContain("global");
    }
}

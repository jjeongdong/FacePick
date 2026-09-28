package com.back.facepick.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SourceConventionRulesTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "import lombok.Data;",
                "import lombok.Setter;",
                "import lombok.Builder;",
                "import lombok.AllArgsConstructor;",
                "import lombok.Value;",
                "import lombok.SneakyThrows;",
                "import lombok.*;",
                "@lombok.Setter private String name;",
                "var boards = new ArrayList<Board>();",
                "for (var board : boards) {",
                "// TODO 나중에 정리",
                "import java.util.*;",
                "import static org.assertj.core.api.Assertions.*;"
            })
    @DisplayName("금지된 패턴이 있는 줄을 위반으로 보고한다")
    void reportsForbiddenPattern(String line) {
        // when
        List<SourceConventionRules.Violation> violations = SourceConventionRules.check(line);

        // then
        assertThat(violations).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "import lombok.Getter;",
                "import lombok.NoArgsConstructor;",
                "import lombok.RequiredArgsConstructor;",
                "import lombok.extern.slf4j.Slf4j;",
                "import org.springframework.beans.factory.annotation.Value;",
                "String variable = \"x\";",
                "int var2 = 1;",
                "Object var = null;",
                "import java.util.List;",
                "import static org.assertj.core.api.Assertions.assertThat;"
            })
    @DisplayName("허용된 코드는 위반으로 보고하지 않는다")
    void acceptsAllowedCode(String line) {
        // when
        List<SourceConventionRules.Violation> violations = SourceConventionRules.check(line);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("위반 줄 번호와 규칙 이름을 함께 보고한다")
    void reportsLineNumberAndRule() {
        // given
        String source = "package a;\n\nclass A {\n    // TODO 삭제\n}\n";

        // when
        List<SourceConventionRules.Violation> violations = SourceConventionRules.check(source);

        // then
        assertThat(violations).containsExactly(new SourceConventionRules.Violation("TODO 금지", 4, "// TODO 삭제"));
    }
}

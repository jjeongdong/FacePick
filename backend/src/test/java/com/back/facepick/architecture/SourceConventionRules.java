package com.back.facepick.architecture;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

// 바이트코드에 남지 않아 ArchUnit 으로 볼 수 없는 규칙(Lombok 은 SOURCE 보존, var·주석은 컴파일 후 사라짐)을 소스 텍스트로 검사한다.
final class SourceConventionRules {

    record Violation(String rule, int lineNumber, String line) {}

    private record Rule(String name, Pattern pattern) {}

    private static final List<Rule> RULES = List.of(
            new Rule(
                    "금지된 Lombok",
                    Pattern.compile(
                            "^\\s*import\\s+lombok\\.(Data|Setter|Builder|AllArgsConstructor|Value|SneakyThrows|\\*)\\s*;")),
            new Rule(
                    "금지된 Lombok",
                    Pattern.compile("@lombok\\.(Data|Setter|Builder|AllArgsConstructor|Value|SneakyThrows)\\b")),
            new Rule("var 금지", Pattern.compile("(^|[\\s(;])var\\s+\\w+\\s*[=:]")),
            new Rule("TODO 금지", Pattern.compile("\\bTODO\\b")),
            new Rule("와일드카드 import 금지", Pattern.compile("^\\s*import\\s+(static\\s+)?[\\w.]+\\.\\*\\s*;")));

    private SourceConventionRules() {}

    static List<Violation> check(String source) {
        List<Violation> violations = new ArrayList<>();
        String[] lines = source.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            for (Rule rule : RULES) {
                if (rule.pattern().matcher(lines[index]).find()) {
                    violations.add(new Violation(rule.name(), index + 1, lines[index].strip()));
                }
            }
        }
        return violations;
    }
}

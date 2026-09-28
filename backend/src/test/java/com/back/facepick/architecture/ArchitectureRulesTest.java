package com.back.facepick.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ArchitectureRulesTest {

    private static final String GOOD_ROOT = "com.back.archfixture.good";
    private static final String BAD_ROOT = "com.back.archfixture.bad";

    @Test
    @DisplayName("규칙을 지킨 픽스처는 위반이 없다")
    void goodFixturePasses() {
        // when
        String report = report(GOOD_ROOT, Set.of("alpha", "beta"), Set.of());

        // then
        assertThat(report).isEmpty();
    }

    @Test
    @DisplayName("BC 가 없으면 규칙도 없다")
    void noContextsProduceNoRules() {
        assertThat(ArchitectureRules.all(GOOD_ROOT, Set.of(), Set.of())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "DomainUsesHttp",
                "DomainCallsNow",
                "DomainHasSetter",
                "DomainTransactionalMethod",
                "MisplacedCommandService",
                "ApplicationUsesInfra",
                "MisplacedException",
                "ClassLevelTransactional",
                "FieldInjection",
                "UsesSystemOut",
                "UsesOptionalGet",
                "UsesDate",
                "UsesOtherDomain",
                "InfraUsesApplication",
                "PresentationUsesEntity",
                "GlobalUsesMigrated",
                "ChainingQueryApi"
            })
    @DisplayName("위반 픽스처는 모두 보고된다")
    void badFixtureViolationIsReported(String className) {
        // when
        String report = report(BAD_ROOT, Set.of("alpha"), Set.of());

        // then
        assertThat(report).contains(className);
    }

    @Test
    @DisplayName("허용 목록에 등록한 클래스와 그 내부 클래스는 타 BC 를 참조해도 통과한다")
    void allowlistedCrossContextClassPasses() {
        // when
        String report = report(BAD_ROOT, Set.of("alpha"), Set.of(BAD_ROOT + ".alpha.application.UsesOtherDomain"));

        // then
        assertThat(report).doesNotContain("UsesOtherDomain");
    }

    private static String report(String root, Set<String> contexts, Set<String> allowlist) {
        JavaClasses classes = new ClassFileImporter().importPackages(root);
        return ArchitectureRules.all(root, contexts, allowlist).stream()
                .map(rule -> rule.evaluate(classes))
                .filter(EvaluationResult::hasViolation)
                .map(result -> result.getFailureReport().toString())
                .collect(Collectors.joining("\n"));
    }
}

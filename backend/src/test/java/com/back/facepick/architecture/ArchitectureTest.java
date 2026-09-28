package com.back.facepick.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private static final String ROOT = "com.back.facepick";

    @Test
    @DisplayName("BC 는 아키텍처 규칙을 지킨다")
    void contextsFollowArchitectureRules() {
        // given
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);

        // when & then
        Set<String> boundaryExceptions = new HashSet<>(BoundedContexts.CROSS_CONTEXT_ALLOWLIST);
        boundaryExceptions.addAll(BoundedContexts.SYNC_COMMAND_ALLOWLIST);
        for (ArchRule rule : ArchitectureRules.all(ROOT, BoundedContexts.NAMES, boundaryExceptions)) {
            rule.check(classes);
        }
    }
}

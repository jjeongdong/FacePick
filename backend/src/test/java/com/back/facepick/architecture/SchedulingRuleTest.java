package com.back.facepick.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.back.facepick.global.config.scheduling.SchedulerNames;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

// scheduler 를 빠뜨린 @Scheduled 는 Spring 이 로컬 단일 스레드 스케줄러로 조용히 돌려 벌크헤드를 벗어난다.
class SchedulingRuleTest {

    @Test
    @DisplayName("모든 @Scheduled 는 정해진 스케줄러 풀(core·external·storage) 중 하나를 지정한다")
    void everyScheduledMethodNamesKnownScheduler() {
        // given
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.back.facepick");

        // when & then
        methods()
                .that()
                .areAnnotatedWith(Scheduled.class)
                .should(new ArchCondition<JavaMethod>("정해진 스케줄러 풀을 지정한다") {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        String scheduler =
                                method.getAnnotationOfType(Scheduled.class).scheduler();
                        if (!SchedulerNames.ALL.contains(scheduler)) {
                            events.add(SimpleConditionEvent.violated(
                                    method, method.getFullName() + " 의 scheduler='" + scheduler + "'"));
                        }
                    }
                })
                .check(classes);
    }
}

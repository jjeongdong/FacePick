package com.back.facepick.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// 기계로 검사할 수 있는 컨벤션 규칙. 루트 패키지와 BC 목록을 인자로 받아 픽스처로도 검증할 수 있게 한다.
final class ArchitectureRules {

    private static final String SPRING_TRANSACTIONAL = "org.springframework.transaction.annotation.Transactional";
    private static final String JAKARTA_TRANSACTIONAL = "jakarta.transaction.Transactional";
    private static final String AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";

    private ArchitectureRules() {}

    static List<ArchRule> all(String root, Set<String> contexts, Set<String> crossContextAllowlist) {
        List<ArchRule> rules = new ArrayList<>();
        for (String context : contexts) {
            String base = root + "." + context;
            rules.addAll(layerRules(root, base));
            rules.add(boundaryRule(root, context, crossContextAllowlist));
            rules.add(queryApiRule(root, context));
            rules.add(globalRule(root, base));
            rules.addAll(locationRules(base));
            rules.addAll(codingRules(base));
        }
        // BC 에 특정 종류의 클래스가 없을 수 있으므로 빈 대상은 통과시킨다.
        return rules.stream().map(rule -> rule.allowEmptyShould(true)).toList();
    }

    private static List<ArchRule> layerRules(String root, String base) {
        DescribedPredicate<JavaClass> domainNonEnum = bcLayer(root, "domain")
                .and(DescribedPredicate.describe("enum 이 아님", (JavaClass target) -> !target.isEnum()));
        return List.of(
                noClasses()
                        .that()
                        .resideInAPackage(base + ".domain..")
                        .should()
                        .dependOnClassesThat(bcLayer(root, "presentation")
                                .or(bcLayer(root, "application"))
                                .or(bcLayer(root, "infrastructure"))
                                .or(resideInAnyPackage(
                                        "org.springframework.web..",
                                        "org.springframework.http..",
                                        "org.springframework.data..")))
                        .as("Domain 은 다른 계층과 Spring Web·Data 에 의존하지 않는다"),
                noClasses()
                        .that()
                        .resideInAPackage(base + ".application..")
                        .should()
                        .dependOnClassesThat(bcLayer(root, "presentation").or(bcLayer(root, "infrastructure")))
                        .as("Application 은 Presentation·Infrastructure 에 의존하지 않는다"),
                noClasses()
                        .that()
                        .resideInAPackage(base + ".presentation..")
                        .should()
                        .dependOnClassesThat(bcLayer(root, "infrastructure").or(domainNonEnum))
                        .as("Presentation 은 Infrastructure 와 Domain(enum 제외)에 의존하지 않는다"),
                noClasses()
                        .that()
                        .resideInAPackage(base + ".infrastructure..")
                        .should()
                        .dependOnClassesThat(bcLayer(root, "presentation").or(bcLayer(root, "application")))
                        .as("Infrastructure 는 Presentation·Application 에 의존하지 않는다"));
    }

    // "*" 패턴은 global 에도 매칭되므로, 여러 BC 가 공유하는 global/infrastructure 등은 계층 규칙 대상에서 뺀다.
    private static DescribedPredicate<JavaClass> bcLayer(String root, String layer) {
        return resideInAnyPackage(root + ".*." + layer + "..").and(resideOutsideOfPackage(root + ".global.."));
    }

    private static ArchRule boundaryRule(String root, String context, Set<String> allowlist) {
        DescribedPredicate<JavaClass> notAllowlisted =
                DescribedPredicate.describe("경계 예외 목록에 없음", (JavaClass origin) -> allowlist.stream()
                        .noneMatch(name -> origin.getName().equals(name)
                                || origin.getName().startsWith(name + "$")));
        DescribedPredicate<JavaClass> otherContextInternal = DescribedPredicate.describe(
                "다른 BC 의 비공개 클래스", (JavaClass target) -> isOtherContextInternal(root, context, target));
        return noClasses()
                .that()
                .resideInAPackage(root + "." + context + "..")
                .and(notAllowlisted)
                .should()
                .dependOnClassesThat(otherContextInternal)
                .as(context + " 는 다른 BC 의 QueryApi, application.dto.api, domain.event 만 사용한다");
    }

    // QueryApi 끼리 서로 주입하면 조회가 연쇄되고 빈 순환이 생긴다. 조회 양방향을 허용하는 대신 QueryApi 는 타 BC 를 모르게 한다.
    private static ArchRule queryApiRule(String root, String context) {
        DescribedPredicate<JavaClass> otherContextNonApiDto = DescribedPredicate.describe(
                "다른 BC 의 application.dto.api 가 아닌 클래스",
                (JavaClass target) -> isOtherContextNonApiDto(root, context, target));
        return noClasses()
                .that()
                .resideInAPackage(root + "." + context + ".application")
                .and()
                .haveSimpleNameEndingWith("QueryApi")
                .should()
                .dependOnClassesThat(otherContextNonApiDto)
                .as(context + " 의 QueryApi 는 다른 BC 의 application.dto.api 만 사용한다");
    }

    private static boolean isOtherContextNonApiDto(String root, String context, JavaClass target) {
        String targetPackage = target.getPackageName();
        if (!targetPackage.startsWith(root + ".")) {
            return false;
        }
        String targetContext = contextOf(root, targetPackage);
        if (targetContext.equals(context) || targetContext.equals("global")) {
            return false;
        }
        return !isInPackage(targetPackage, root + "." + targetContext + ".application.dto.api");
    }

    private static String contextOf(String root, String targetPackage) {
        String rest = targetPackage.substring(root.length() + 1);
        return rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : rest;
    }

    private static boolean isOtherContextInternal(String root, String context, JavaClass target) {
        String targetPackage = target.getPackageName();
        if (!targetPackage.startsWith(root + ".")) {
            return false;
        }
        String targetContext = contextOf(root, targetPackage);
        if (targetContext.equals(context) || targetContext.equals("global")) {
            return false;
        }
        String targetBase = root + "." + targetContext;
        boolean isQueryApi = targetPackage.equals(targetBase + ".application")
                && target.getSimpleName().endsWith("QueryApi");
        boolean isApiDto = isInPackage(targetPackage, targetBase + ".application.dto.api");
        boolean isEvent = isInPackage(targetPackage, targetBase + ".domain.event");
        return !(isQueryApi || isApiDto || isEvent);
    }

    private static boolean isInPackage(String targetPackage, String packageName) {
        return targetPackage.equals(packageName) || targetPackage.startsWith(packageName + ".");
    }

    private static ArchRule globalRule(String root, String base) {
        return noClasses()
                .that()
                .resideInAPackage(root + ".global..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(base + "..")
                .as("global 은 BC 에 의존하지 않는다");
    }

    private static List<ArchRule> locationRules(String base) {
        return List.of(
                located(base, "CommandService", ".application"),
                located(base, "QueryService", ".application"),
                located(base, "QueryApi", ".application"),
                located(base, "Controller", ".presentation"),
                located(base, "ApiDocs", ".presentation"),
                located(base, "Request", ".presentation.dto.request"),
                located(base, "Command", ".application.dto.command"),
                located(base, "Result", ".application.dto.result"),
                located(base, "Info", ".application.dto.api"),
                located(base, "Listener", ".application.event"),
                located(base, "Event", ".domain.event"),
                located(base, "Exception", ".domain.exception"),
                located(base, "ErrorCode", ".domain"),
                located(base, "RepositoryImpl", ".infrastructure"),
                located(base, "JpaRepository", ".infrastructure"));
    }

    private static ArchRule located(String base, String suffix, String packageSuffix) {
        return classes()
                .that()
                .resideInAPackage(base + "..")
                .and()
                .areTopLevelClasses()
                .and()
                .haveSimpleNameEndingWith(suffix)
                .should()
                .resideInAPackage(base + packageSuffix)
                .as("*" + suffix + " 는 " + base + packageSuffix + " 에 둔다");
    }

    private static List<ArchRule> codingRules(String base) {
        return List.of(
                noClasses()
                        .that()
                        .resideInAPackage(base + "..")
                        .should()
                        .beAnnotatedWith(SPRING_TRANSACTIONAL)
                        .orShould()
                        .beAnnotatedWith(JAKARTA_TRANSACTIONAL)
                        .as("클래스 레벨 @Transactional 금지"),
                noMethods()
                        .that()
                        .areDeclaredInClassesThat()
                        .resideInAPackage(base + "..")
                        .and()
                        .areDeclaredInClassesThat()
                        .resideOutsideOfPackage(base + ".application..")
                        .should()
                        .beAnnotatedWith(SPRING_TRANSACTIONAL)
                        .orShould()
                        .beAnnotatedWith(JAKARTA_TRANSACTIONAL)
                        .as("@Transactional 은 Application 계층에만"),
                noFields()
                        .that()
                        .areDeclaredInClassesThat()
                        .resideInAPackage(base + "..")
                        .should()
                        .beAnnotatedWith(AUTOWIRED)
                        .as("필드 주입 금지"),
                noClasses()
                        .that()
                        .resideInAPackage(base + "..")
                        .should(GeneralCodingRules.ACCESS_STANDARD_STREAMS)
                        .as("System.out/err 금지"),
                noClasses()
                        .that()
                        .resideInAPackage(base + "..")
                        .should()
                        .callMethod(Optional.class, "get")
                        .as("Optional.get() 금지"),
                noClasses()
                        .that()
                        .resideInAPackage(base + "..")
                        .and()
                        // 외부 라이브러리(jjwt 등)가 Date 를 요구하므로 변환은 Infrastructure 안에서만 허용한다.
                        .resideOutsideOfPackage(base + ".infrastructure..")
                        .should()
                        .dependOnClassesThat(assignableTo(Date.class).or(assignableTo(Calendar.class)))
                        .as("Date·Calendar 금지 (Infrastructure 의 외부 라이브러리 변환 제외)"),
                noClasses()
                        .that()
                        .resideInAPackage(base + ".domain..")
                        .should()
                        .callMethod(LocalDateTime.class, "now")
                        .orShould()
                        .callMethod(LocalDate.class, "now")
                        .as("Domain 에서 now() 금지 — 시각은 인자로"),
                noMethods()
                        .that()
                        .areDeclaredInClassesThat()
                        .resideInAPackage(base + ".domain..")
                        .and()
                        .arePublic()
                        .should()
                        .haveNameMatching("set[A-Z].*")
                        .as("Domain public setter 금지"));
    }
}

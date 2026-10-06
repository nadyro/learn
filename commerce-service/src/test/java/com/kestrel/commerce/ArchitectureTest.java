package com.kestrel.commerce;

import static com.tngtech.archunit.base.DescribedPredicate.and;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;

/**
 * Architecture rules, checked on every build.
 *
 * <p>The service is a modular monolith: one deployable, split into business modules (catalog, inventory, customer,
 * order, payment) plus technical {@code shared} code. Each module has three layers:
 *
 * <ul>
 *   <li>{@code api}: REST controllers and their DTOs. Nothing depends on another module's api.
 *   <li>{@code application}: use cases (services). The public entry point of a module for other modules.
 *   <li>{@code domain}: entities, repositories and business rules. Depends on nothing but {@code shared}.
 * </ul>
 *
 * If a rule fails, do not just change the rule: discuss it with the team, and write an ADR if the rule should change.
 */
@AnalyzeClasses(packages = "com.kestrel.commerce", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "com.kestrel.commerce";
    private static final List<String> MODULES = List.of("catalog", "inventory", "customer", "order", "payment");

    @ArchTest
    static final ArchRule modules_have_no_cyclic_dependencies =
            slices().matching(ROOT + ".(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule shared_code_does_not_depend_on_business_modules = noClasses()
            .that()
            .resideInAPackage(ROOT + ".shared..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(MODULES.stream().map(m -> ROOT + "." + m + "..").toArray(String[]::new));

    @ArchTest
    static final ArchRule domain_does_not_depend_on_application_or_api = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..application..", "..api..");

    @ArchTest
    static final ArchRule application_does_not_depend_on_api = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..api..");

    @ArchTest
    static final ArchRule modules_do_not_use_each_others_rest_layer = CompositeArchRule.of(MODULES.stream()
            .map(module -> noClasses()
                    .that()
                    .resideOutsideOfPackage(ROOT + "." + module + "..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage(ROOT + "." + module + ".api..")
                    .as("no other module should depend on " + module + ".api"))
            .map(ArchRule.class::cast)
            .toList());

    @ArchTest
    static final ArchRule repositories_are_only_used_inside_their_own_module = CompositeArchRule.of(MODULES.stream()
            .map(module -> noClasses()
                    .that()
                    .resideOutsideOfPackage(ROOT + "." + module + "..")
                    .should()
                    .dependOnClassesThat(
                            and(resideInAPackage(ROOT + "." + module + ".."), simpleNameEndingWith("Repository")))
                    .as("repositories of " + module + " should only be used by " + module
                            + " (other modules go through its application services)"))
            .map(ArchRule.class::cast)
            .toList());

    @ArchTest
    static final ArchRule controllers_only_call_application_services = noClasses()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Repository");

    @ArchTest
    static final ArchRule controllers_live_in_api_packages =
            classes().that().areAnnotatedWith(RestController.class).should().resideInAPackage("..api..");

    @ArchTest
    static final ArchRule no_field_injection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    static final ArchRule no_system_out = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule no_java_util_logging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
}

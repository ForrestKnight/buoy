package io.github.forrestknight.buoy;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Layering rules as build failures, not review comments (spec 0001).
 */
@AnalyzeClasses(packages = "io.github.forrestknight.buoy",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** Controllers speak to services; repositories are an implementation detail. */
    @ArchTest
    static final ArchRule apiNeverTouchesPersistence = noClasses()
            .that().resideInAPackage("..buoy.api..")
            .should().dependOnClassesThat().resideInAPackage("..buoy.persistence..");

    /** The domain model knows nothing about the application built around it. */
    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that().resideInAPackage("..buoy.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "tools.jackson..",
                    "..buoy.api..", "..buoy.service..", "..buoy.persistence..", "..buoy.config..");

    /**
     * The evaluation engine is pure logic: JDK, the domain's own types, the hash
     * function, wire-format annotations, and nullness annotations — nothing else.
     * This is what keeps it exhaustively testable and extractable into a client SDK.
     */
    @ArchTest
    static final ArchRule engineIsPure = classes()
            .that().haveNameMatching(".*\\.domain\\.(Evaluator|Bucketing|SemVer|EvaluationContext"
                    + "|EvaluationResult|EvaluationReason|Clause|TargetingRule|Operator|Rollout(\\$.+)?)")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "java..", "io.github.forrestknight.buoy.domain",
                    "org.apache.commons.codec..", "com.fasterxml.jackson.annotation..", "org.jspecify..");
}

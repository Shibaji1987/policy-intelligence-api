package com.shibajide.policyintelligence;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packagesOf = PolicyIntelligenceApplication.class,
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    @ArchTest
    static final ArchRule advisorDoesNotReachIntoInfrastructure =
            noClasses().that().resideInAPackage("..advisor..")
                    .should().accessClassesThat()
                    .resideInAnyPackage("..document.infrastructure..", "..retrieval.infrastructure..")
                    .because("advisor workflows must communicate with other modules through application services");
}

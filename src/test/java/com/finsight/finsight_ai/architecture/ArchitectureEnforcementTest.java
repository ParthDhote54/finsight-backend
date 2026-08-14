package com.finsight.finsight_ai.architecture;

// 1. Make sure JavaClasses is imported from com.tngtech.archunit.core.domain
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureEnforcementTest {

    private final JavaClasses importedClasses = new ClassFileImporter()
            .importPackages("com.finsight.finsight_ai");

    @Test
    @DisplayName("Rule 1: AI package must not depend on TransactionService")
    void rule1_aiPackageMustNotDependOnTransactionService() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..ai..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("TransactionService");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("Rule 2: Event consumer package must not access SecurityContextHolder")
    void rule2_eventConsumerMustNotUseSecurityContextHolder() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..event.consumer..")
                .should().dependOnClassesThat().haveSimpleName("SecurityContextHolder");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("Rule 3: Only AI adapter packages may import Vertex AI SDK classes")
    void rule3_onlyAiGatewayPackageMayImportVertexAiSDK() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackages("..ai..adapter..", "..ai..adapters..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.ai.vertexai..",
                        "com.google.cloud.vertexai.."
                );

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("Rule 4: No JPA EntityManager inside AI core (domain/application) or ports")
    void rule4_noJpaEntityManagerInsideAiPackage() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..ai..")
                .and().resideOutsideOfPackage("..ai..persistence..")
                .should().dependOnClassesThat().haveSimpleName("EntityManager");

        rule.check(importedClasses);
    }
}

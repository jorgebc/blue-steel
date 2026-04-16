package com.bluesteel.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces Hexagonal Architecture layer boundaries on every build (D-037).
 *
 * <p>Rules pass vacuously when the target packages are empty — this is expected until production
 * code is added in subsequent phases. A failing rule always means a layer violation in production
 * code, never a test to relax.
 */
@AnalyzeClasses(packages = "com.bluesteel")
class ArchitectureTest {

  // Rule 1: Domain core has zero org.springframework.* imports (ARCH-01)
  @ArchTest
  static final ArchRule domainHasNoSpringImports =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.springframework..");

  // Rule 2: Domain core has zero jakarta.persistence.* imports (ARCH-01)
  // JPA entities live in adapters.out.persistence only — never in domain.
  @ArchTest
  static final ArchRule domainHasNoPersistenceImports =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("jakarta.persistence..");

  // Rule 3: Adapters are never imported by domain or application layers (ARCH-02)
  // Domain and application code must depend only on ports (interfaces), never on adapter classes.
  @ArchTest
  static final ArchRule domainAndApplicationDoNotDependOnAdapters =
      noClasses()
          .that()
          .resideInAnyPackage("..domain..", "..application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..adapters..");

  // Rule 4: Config classes in the adapters tree are co-located with their adapter (ARCH-03, D-039)
  // WebConfig → adapters.in.web, PersistenceConfig → adapters.out.persistence, etc.
  // The top-level config/ package is reserved for cross-cutting beans only.
  @ArchTest
  static final ArchRule adapterConfigClassesAreCoLocated =
      classes()
          .that()
          .haveSimpleNameEndingWith("Config")
          .and()
          .resideInAPackage("..adapters..")
          .should()
          .resideInAPackage("..adapters.in..*")
          .orShould()
          .resideInAPackage("..adapters.out..*");
}

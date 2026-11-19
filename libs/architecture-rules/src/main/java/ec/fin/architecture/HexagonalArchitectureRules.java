package ec.fin.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Shared hexagonal rules. Each service includes them with
 * {@code @ArchTest static final ArchTests hexagonal = ArchTests.in(HexagonalArchitectureRules.class);}.
 */
public final class HexagonalArchitectureRules {

  private static final String DOMAIN = "..domain..";
  private static final String APPLICATION = "..application..";
  private static final String INFRASTRUCTURE = "..infrastructure..";

  @ArchTest
  static final ArchRule layersRespectTheDependencyRule = layeredArchitecture()
      .consideringOnlyDependenciesInLayers()
      .layer("Domain").definedBy(DOMAIN)
      .layer("Application").definedBy(APPLICATION)
      .layer("Infrastructure").definedBy(INFRASTRUCTURE)
      .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
      .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
      .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure");

  @ArchTest
  static final ArchRule coreIsFrameworkAgnostic = noClasses()
      .that().resideInAnyPackage(DOMAIN, APPLICATION)
      .should().dependOnClassesThat().resideInAnyPackage(
          "jakarta..", "javax..", "io.quarkus..", "io.smallrye..", "io.vertx..",
          "org.eclipse.microprofile..", "org.hibernate..", "com.fasterxml..", "org.apache.kafka..")
      .because("domain and use cases must not know about frameworks or infrastructure");

  @ArchTest
  static final ArchRule domainDoesNotLog = noClasses()
      .that().resideInAPackage(DOMAIN)
      .should().dependOnClassesThat().resideInAnyPackage("org.slf4j..", "org.jboss.logging..")
      .because("the domain reports what happened through return values and events, not logs");

  private HexagonalArchitectureRules() {
  }
}

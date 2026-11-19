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
  private static final String APPLICATION_SERVICES = "..application.service..";
  private static final String ADAPTERS = "..infrastructure.adapter..";
  private static final String DRIVING_ADAPTERS = "..infrastructure.adapter.in..";
  private static final String DRIVEN_ADAPTERS = "..infrastructure.adapter.out..";

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

  // A service may have no adapters of one kind (fraud-mock has no driven adapters), so these rules accept it.
  @ArchTest
  static final ArchRule adaptersReachUseCasesThroughPorts = noClasses()
      .that().resideInAPackage(ADAPTERS)
      .should().dependOnClassesThat().resideInAPackage(APPLICATION_SERVICES)
      .because("adapters call port/in interfaces; only the composition root knows the implementations")
      .allowEmptyShould(true);

  @ArchTest
  static final ArchRule drivingAdaptersDoNotUseDrivenAdapters = noClasses()
      .that().resideInAPackage(DRIVING_ADAPTERS)
      .should().dependOnClassesThat().resideInAPackage(DRIVEN_ADAPTERS)
      .because("a driving adapter reaches infrastructure only through a use case and its ports")
      .allowEmptyShould(true);

  @ArchTest
  static final ArchRule drivenAdaptersDoNotUseDrivingAdapters = noClasses()
      .that().resideInAPackage(DRIVEN_ADAPTERS)
      .should().dependOnClassesThat().resideInAPackage(DRIVING_ADAPTERS)
      .because("driven adapters are called by the core and must not know how it is triggered")
      .allowEmptyShould(true);

  private HexagonalArchitectureRules() {
  }
}

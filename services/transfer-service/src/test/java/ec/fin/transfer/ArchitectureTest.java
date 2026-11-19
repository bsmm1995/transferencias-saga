package ec.fin.transfer;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import ec.fin.architecture.HexagonalArchitectureRules;

@AnalyzeClasses(packages = "ec.fin.transfer", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchTests hexagonal = ArchTests.in(HexagonalArchitectureRules.class);
}

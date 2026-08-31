package com.minipaintdex.cli;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.minipaintdex.cli", importOptions = ImportOption.DoNotIncludeTests.class)
class CliArchitectureTest {
    @ArchTest
    static final ArchRule cli_uses_application_ports = noClasses()
            .that().resideInAPackage("com.minipaintdex.cli..")
            .should().dependOnClassesThat().resideInAPackage("com.minipaintdex.application");

    @ArchTest
    static final ArchRule cli_does_not_access_file_repositories = noClasses()
            .that().resideInAPackage("com.minipaintdex.cli..")
            .should().dependOnClassesThat().resideInAPackage("com.minipaintdex.adapter.file..");
}

package com.minipaintdex.server;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.minipaintdex.domain.event.DomainEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "com.minipaintdex", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest
    static final ArchRule onnx_is_confined_to_its_adapter = noClasses()
            .that().resideOutsideOfPackage("com.minipaintdex.adapter.onnx..")
            .should().dependOnClassesThat().resideInAPackage("ai.onnxruntime..");

    @ArchTest
    static final ArchRule market_never_depends_on_workshop = noClasses()
            .that().resideInAnyPackage("..market..")
            .should().dependOnClassesThat().resideInAnyPackage("..workshop..");

    @ArchTest
    static final ArchRule market_application_does_not_bypass_the_boundary = noClasses()
            .that().resideInAPackage("com.minipaintdex.application..")
            .and().haveSimpleNameStartingWith("Market")
            .should().dependOnClassesThat().resideInAnyPackage("..workshop..");

    @ArchTest
    static final ArchRule market_catalog_does_not_use_the_cross_context_kernel = noClasses()
            .that().haveSimpleName("MarketCatalogApplicationService")
            .should().dependOnClassesThat().haveSimpleName("WorkshopCommandService");

    @ArchTest
    static final ArchRule market_application_does_not_use_the_global_snapshot_repository = noClasses()
            .that().resideInAPackage("com.minipaintdex.application..")
            .and().haveSimpleNameStartingWith("Market")
            .should().dependOnClassesThat().haveSimpleName("SnapshotRepository");

    @ArchTest
    static final ArchRule market_application_does_not_use_the_cross_context_snapshot = noClasses()
            .that().resideInAPackage("com.minipaintdex.application..")
            .and().haveSimpleNameStartingWith("Market")
            .should().dependOnClassesThat().haveSimpleName("DataSnapshot");

    @ArchTest
    static final ArchRule workshop_only_consumes_market_interfaces = com.tngtech.archunit.lang.syntax.ArchRuleDefinition
            .classes().that().resideInAnyPackage("..workshop..")
            .should(new ArchCondition<>("depend on MARKET only through interfaces") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    item.getDirectDependenciesFromSelf().stream()
                            .filter(dependency -> dependency.getTargetClass().getPackageName().contains(".market."))
                            .filter(dependency -> !dependency.getTargetClass().isInterface())
                            .forEach(dependency -> events.add(SimpleConditionEvent.violated(
                                    item, dependency.getDescription())));
                }
            });

    @ArchTest
    static final ArchRule lucene_is_confined_to_its_adapter = noClasses()
            .that().resideOutsideOfPackage("com.minipaintdex.adapter.lucene..")
            .should().dependOnClassesThat().resideInAPackage("org.apache.lucene..");

    @ArchTest
    static final ArchRule lucene_does_not_read_workshop_or_storage = noClasses()
            .that().resideInAPackage("com.minipaintdex.adapter.lucene..")
            .should().dependOnClassesThat().resideInAnyPackage("..workshop..", "com.minipaintdex.adapter.file..")
            .orShould().dependOnClassesThat().haveSimpleName("SnapshotRepository");

    @ArchTest
    static final ArchRule domain_is_framework_independent = noClasses()
            .that().resideInAPackage("com.minipaintdex.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "tools.jackson..", "com.fasterxml.jackson..", "picocli..",
                    "com.minipaintdex.application..", "com.minipaintdex.adapter..", "com.minipaintdex.server..");

    @ArchTest
    static final ArchRule application_only_depends_inward = noClasses()
            .that().resideInAPackage("com.minipaintdex.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "tools.jackson..", "com.fasterxml.jackson..", "picocli..",
                    "com.minipaintdex.adapter..", "com.minipaintdex.bootstrap..", "com.minipaintdex.server..");

    @ArchTest
    static final ArchRule rest_does_not_depend_on_file_adapter = noClasses()
            .that().resideInAPackage("com.minipaintdex.server..")
            .should().dependOnClassesThat().resideInAPackage("com.minipaintdex.adapter.file..");

    @ArchTest
    static final ArchRule input_adapters_do_not_depend_on_service_implementation = noClasses()
            .that().resideInAnyPackage("com.minipaintdex.server.api..", "com.minipaintdex.cli..")
            .should().dependOnClassesThat().resideInAPackage("com.minipaintdex.application");

    @ArchTest
    static final ArchRule application_boundaries_are_typed = noClasses()
            .that().resideInAnyPackage(
                    "com.minipaintdex.application.usecase..",
                    "com.minipaintdex.application.command..",
                    "com.minipaintdex.application.result..",
                    "com.minipaintdex.application.view..",
                    "com.minipaintdex.application.document..",
                    "com.minipaintdex.application.port..")
            .should().dependOnClassesThat().haveFullyQualifiedName("java.util.Map");

    @ArchTest
    static final ArchRule concrete_domain_events_are_immutable_data_records = classes()
            .that().areAssignableTo(DomainEvent.class)
            .and().areNotInterfaces()
            .should(new ArchCondition<>("be Java records") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    if (!item.isRecord()) {
                        events.add(SimpleConditionEvent.violated(
                                item, item.getName() + " is a domain event but is not a record"));
                    }
                }
            });

    @ArchTest
    static final ArchRule site_and_market_services_do_not_use_workshop_command_coordination = noClasses()
            .that().haveSimpleName("SiteApplicationService")
            .or().haveSimpleName("MarketCatalogApplicationService")
            .should().dependOnClassesThat().haveSimpleName("WorkshopCommandService");

    @ArchTest
    static final ArchRule workshop_queries_do_not_depend_on_mutation_ports = noClasses()
            .that().haveSimpleName("WorkshopQueryService")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Writer")
            .orShould().dependOnClassesThat().haveSimpleName("EventBus");
}

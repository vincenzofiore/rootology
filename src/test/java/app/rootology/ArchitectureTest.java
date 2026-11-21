package app.rootology;

import app.rootology.resilience.CircuitBreaker;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Random;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

/**
 * The project's conventions as tests (#150): a change that breaks one fails the build, whoever wrote it.
 * Module boundaries and cycles between modules are {@link ModularityTest}'s. Violations that existed when a rule
 * came in are frozen in {@code src/test/resources/archunit-store}: only new ones fail, and the store shrinks by
 * itself as they are fixed. Never add to the store by hand to make a build pass: fix the code.
 */
class ArchitectureTest {

    static final JavaClasses APP = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("app.rootology");

    static final Set<String> TIME_TYPES = Set.of("java.time.Instant", "java.time.LocalDate",
            "java.time.LocalDateTime", "java.time.LocalTime", "java.time.ZonedDateTime", "java.time.OffsetDateTime",
            "java.time.OffsetTime", "java.time.YearMonth", "java.time.Year", "java.time.MonthDay");

    static final DescribedPredicate<JavaCall<?>> READS_THE_REAL_TIME = DescribedPredicate.describe(
            "reads the machine's time", call -> {
                String owner = call.getTargetOwner().getName();
                String name = call.getName();
                return (TIME_TYPES.contains(owner) && name.equals("now"))
                        || (owner.equals(Clock.class.getName()) && name.startsWith("system"))
                        || (owner.equals(System.class.getName()) && name.equals("currentTimeMillis"));
            });

    @Test
    void nowComesFromTheClockBean() {
        FreezingArchRule.freeze(noClasses().that().doNotHaveFullyQualifiedName(RootologyApplication.class.getName())
                .should().callMethodWhere(READS_THE_REAL_TIME)
                .because("\"now\" comes from the Clock bean (LocalDate.now(clock), clock.instant()), "
                        + "which tests replace with @TestBean")).check(APP);
    }

    @Test
    void theTimeZoneComesFromTheClock() {
        FreezingArchRule.freeze(noClasses().that().doNotHaveFullyQualifiedName(RootologyApplication.class.getName())
                .should().callMethod(ZoneId.class, "of", String.class)
                .because("the app's time zone is the Clock bean's: use clock.getZone()")).check(APP);
    }

    @Test
    void stylesLiveInTheStylesheet() {
        noClasses().that().resideInAPackage("app.rootology.ui..")
                .should().callMethodWhere(DescribedPredicate.describe("getStyle()",
                        call -> call.getName().equals("getStyle")))
                .because("styles live only in styles/rootology.css: add a class name instead")
                .check(APP);
    }

    @Test
    void theUiGoesThroughServices() {
        noClasses().that().resideInAPackage("app.rootology.ui..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.simple.JdbcClient")
                .orShould().dependOnClassesThat().resideInAnyPackage("org.neo4j..", "org.springframework.data.neo4j..")
                .because("a view calls a module's service, which checks access and owns the transaction")
                .check(APP);
    }

    @Test
    void theQualitySuiteStaysIsolated() {
        noClasses().that().resideInAnyPackage("app.rootology.ontology..", "app.rootology.inference..")
                .should().dependOnClassesThat().resideInAnyPackage("app.rootology.facts..", "app.rootology.pipeline..",
                        "app.rootology.garden..", "app.rootology.enrichment..", "app.rootology.ui..")
                .because("the suite's fixture and arbiter are ground truth and never see the real knowledge base")
                .check(APP);
    }

    /** Guarded by the class that calls them: MET Norway by WeatherService, the sign-in's email lookup by its timeouts. */
    static final Set<String> GUARDED_ELSEWHERE = Set.of("app.rootology.weather.MetNorwayClient",
            "app.rootology.ui.GitHubEmails", "app.rootology.decision.DecisionLayerConfiguration");

    @Test
    void externalCallsGoThroughACircuitBreaker() {
        classes().that(DescribedPredicate.describe("call an external service over HTTP",
                        (JavaClass c) -> c.getDirectDependenciesFromSelf().stream().anyMatch(d ->
                                d.getTargetClass().getName().equals("org.springframework.web.client.RestClient")
                                        || d.getTargetClass().getName().equals("java.net.http.HttpClient"))))
                .should(new ArchCondition<JavaClass>("use a resilience.CircuitBreaker") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        boolean guarded = GUARDED_ELSEWHERE.contains(item.getName())
                                || item.getDirectDependenciesFromSelf().stream()
                                .anyMatch(d -> d.getTargetClass().isEquivalentTo(CircuitBreaker.class));
                        events.add(new SimpleConditionEvent(item, guarded,
                                item.getName() + " calls an external service without a CircuitBreaker"));
                    }
                })
                .because("the app survives a failing external service: every call has a timeout and a breaker")
                .check(APP);
    }

    @Test
    void settingsComeFromSpring() {
        noClasses().should().callMethod(System.class, "getenv", String.class)
                .orShould().callMethod(System.class, "getenv")
                .because("settings are ROOTOLOGY_* variables bound by Spring (@Value, @ConfigurationProperties)")
                .check(APP);
    }

    @Test
    void hygiene() {
        NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.because("log with SLF4J").check(APP);
        NO_CLASSES_SHOULD_USE_FIELD_INJECTION.because("inject through the constructor").check(APP);
        noClasses().should().callMethod(Throwable.class, "printStackTrace")
                .because("log with SLF4J, the exception type only when its message may carry personal data")
                .check(APP);
    }

    @Test
    void randomnessOnlyWhereItIsTheExperiment() {
        ArchRule rule = noClasses().that().doNotHaveSimpleName("NoisyFacts")
                .should().dependOnClassesThat().haveFullyQualifiedName(Random.class.getName())
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.util.concurrent.ThreadLocalRandom")
                .because("codes and tokens use SecureRandom; seeded noise belongs to NoisyFacts only");
        rule.check(APP);
    }
}

package org.kevinkib.cardgames.architecture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Control test: runs every rule of {@link ArchitectureRules} against the deliberately wrong classes
 * of {@code architecture.fixture} and checks that the expected offender is reported. Without it, a
 * rule with a typo in a package pattern would pass forever on an empty match.
 */
class ArchitectureRulesControlTest {

    private static final String FIXTURE_ROOT = "org.kevinkib.cardgames.architecture.fixture";
    private static final ArchitectureRules RULES = new ArchitectureRules(FIXTURE_ROOT);
    private static JavaClasses fixtures;

    @BeforeAll
    static void importFixtures() {
        fixtures = new ClassFileImporter().importPackages(FIXTURE_ROOT);
    }

    private static void assertViolation(ArchRule rule, String offender) {
        AssertionError error = assertThrows(AssertionError.class, () -> rule.check(fixtures));
        assertTrue(error.getMessage().contains(offender),
                "expected a violation naming " + offender + " but got: " + error.getMessage());
    }

    @Test
    void sessionmanagementDependingOnAGameIsDetected() {
        assertViolation(RULES.sessionAndKernelKnowNoGame(), "SessionUsesGame");
    }

    @Test
    void kernelDependingOnPresentationIsDetected() {
        assertViolation(RULES.sessionAndKernelKnowNoGame(), "KernelUsesPresentation");
    }

    @Test
    void domainDependingOnSpringIsDetected() {
        assertViolation(RULES.gameDomainIsPure("bullshit"), "DomainUsesSpring");
    }

    @Test
    void domainDependingOnSessionmanagementIsDetected() {
        assertViolation(RULES.gameDomainIsPure("bullshit"), "DomainUsesSession");
    }

    @Test
    void domainDependingOnItsPresentationIsDetected() {
        assertViolation(RULES.gameDomainIsPure("bullshit"), "DomainUsesPresentation");
    }

    @Test
    void gameDependingOnAnotherGameIsDetected() {
        assertViolation(RULES.gameDoesNotKnowOtherGames("kobo"), "PresentationUsesOtherGame");
    }

    @Test
    void sharedPresentationDependingOnAGameIsDetected() {
        assertViolation(RULES.sharedPresentationKnowsNoGame(), "SharedUsesGame");
    }

    @Test
    void cycleBetweenTopLevelPackagesIsDetected() {
        assertViolation(RULES.noCyclesBetweenTopLevelPackages(), "CycleA");
    }
}

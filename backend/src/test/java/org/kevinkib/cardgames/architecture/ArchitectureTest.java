package org.kevinkib.cardgames.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Locks the dependency rules of {@code docs/architecture/context-map.md} on the main code (test
 * classes are excluded). {@link ArchitectureRulesControlTest} proves each rule can fail.
 */
class ArchitectureTest {

    private static final ArchitectureRules RULES = new ArchitectureRules("org.kevinkib.cardgames");
    private static JavaClasses mainClasses;

    @BeforeAll
    static void importMainClasses() {
        mainClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.kevinkib.cardgames");
    }

    @Test
    void sessionmanagementAndKernelKnowNoConcreteGame() {
        RULES.sessionAndKernelKnowNoGame().check(mainClasses);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bataillecorse", "bullshit", "kobo", "threemenmorris", "president", "mangola", "draughts", "ninetynine"})
    void gameDomainIsFreeOfSpringSessionsAndTransport(String game) {
        RULES.gameDomainIsPure(game).check(mainClasses);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bataillecorse", "bullshit", "kobo", "threemenmorris", "president", "mangola", "draughts", "ninetynine"})
    void gameDependsOnNoOtherGame(String game) {
        RULES.gameDoesNotKnowOtherGames(game).check(mainClasses);
    }

    @Test
    void sharedPresentationKnowsNoConcreteGame() {
        RULES.sharedPresentationKnowsNoGame().check(mainClasses);
    }

    @Test
    void noCyclesBetweenTopLevelPackages() {
        RULES.noCyclesBetweenTopLevelPackages().check(mainClasses);
    }
}

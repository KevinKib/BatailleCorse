package org.kevinkib.cardgames.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The mangola context is not yet in {@link ArchitectureRules#GAMES} (the table gets its entry with the
 * wiring PR, so that the shared architecture tests are touched once): until then the same two rules are
 * applied to it here, so that its domain is checked from the first commit.
 */
class MangolaDomainPurityTest {

    private static final ArchitectureRules RULES = new ArchitectureRules("org.kevinkib.cardgames");
    private static JavaClasses mainClasses;

    @BeforeAll
    static void importMainClasses() {
        mainClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.kevinkib.cardgames");
    }

    @Test
    void mangolaDomainIsFreeOfSpringSessionsAndTransport() {
        RULES.gameDomainIsPure("mangola").check(mainClasses);
    }

    @Test
    void mangolaDependsOnNoOtherGame() {
        RULES.gameDoesNotKnowOtherGames("mangola").check(mainClasses);
    }
}

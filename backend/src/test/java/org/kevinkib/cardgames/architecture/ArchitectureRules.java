package org.kevinkib.cardgames.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.lang.ArchRule;
import java.util.ArrayList;
import java.util.List;

/**
 * The dependency rules of {@code docs/architecture/context-map.md}, parameterised by the root
 * package so that {@link ArchitectureRulesControlTest} can run them against deliberately wrong
 * fixture classes and prove that each rule really detects a violation.
 */
final class ArchitectureRules {

    static final List<String> GAMES = List.of("bataillecorse", "bullshit", "kobo", "threemenmorris", "president",
            "mangola", "draughts", "ninetynine");

    private final String root;

    ArchitectureRules(String root) {
        this.root = root;
    }

    private List<String> gamePackages() {
        return GAMES.stream().map(game -> root + "." + game + "..").toList();
    }

    private String[] gamePackagesExcept(String game) {
        return GAMES.stream().filter(other -> !other.equals(game))
                .map(other -> root + "." + other + "..").toArray(String[]::new);
    }

    /** {@code sessionmanagement} and the shared kernel {@code game} know no game and no transport. */
    ArchRule sessionAndKernelKnowNoGame() {
        List<String> forbidden = new ArrayList<>(gamePackages());
        forbidden.add(root + ".presentation..");
        forbidden.add(root + ".config..");
        return noClasses().that().resideInAnyPackage(root + ".sessionmanagement..", root + ".game..")
                .should().dependOnClassesThat().resideInAnyPackage(forbidden.toArray(String[]::new))
                .as("sessionmanagement and game depend on no concrete game, presentation or config");
    }

    /** A game's domain depends on neither Spring, sessions, transport nor configuration. */
    ArchRule gameDomainIsPure(String game) {
        return noClasses().that().resideInAPackage(root + "." + game + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        root + ".sessionmanagement..", root + ".presentation..", root + ".config..",
                        root + "." + game + ".presentation..", "org.springframework..", "io.micrometer..")
                .as(game + ".domain depends on no Spring, metrics, sessionmanagement, presentation or config");
    }

    /** No game (domain or presentation) depends on another game. */
    ArchRule gameDoesNotKnowOtherGames(String game) {
        return noClasses().that().resideInAPackage(root + "." + game + "..")
                .should().dependOnClassesThat().resideInAnyPackage(gamePackagesExcept(game))
                .as(game + " depends on no other game");
    }

    /** The shared presentation package (not a game's own) knows no concrete game. */
    ArchRule sharedPresentationKnowsNoGame() {
        return noClasses().that().resideInAPackage(root + ".presentation..")
                .should().dependOnClassesThat().resideInAnyPackage(gamePackages().toArray(String[]::new))
                .as("the shared presentation depends on no concrete game");
    }

    /** No cycle between the top-level packages (games, sessionmanagement, game, presentation, config). */
    ArchRule noCyclesBetweenTopLevelPackages() {
        return slices().matching(root + ".(*)..").should().beFreeOfCycles();
    }
}

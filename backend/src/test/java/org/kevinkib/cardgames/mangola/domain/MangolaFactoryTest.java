package org.kevinkib.cardgames.mangola.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.InvalidOptionException;
import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.OptionType;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MangolaFactoryTest {

    private static final PlayerId HOST = new PlayerId(0);
    private static final PlayerId GUEST = new PlayerId(1);

    private final MangolaFactory factory = new MangolaFactory(MangolaLayoutsTest.scripted(0));

    private Mangola create(MangolaFactory factory, Map<String, String> options) {
        return (Mangola) factory.create(GameId.generate(), 2, GameOptions.of(options));
    }

    @Test
    void theGameTypeAndBoundsAreMangolaForExactlyTwoPlayers() {
        assertEquals("mangola", MangolaFactory.GAME_TYPE);
        assertEquals("mangola", factory.gameType());
        assertEquals(2, factory.minPlayers());
        assertEquals(2, factory.maxPlayers());
        assertTrue(factory.supportsBots());
        assertThrows(IllegalArgumentException.class, () -> factory.create(GameId.generate(), 3));
        assertThrows(IllegalArgumentException.class, () -> factory.create(GameId.generate(), 1));
    }

    @Test
    void theOnlyGameOptionIsWhoStartsWithItsDefault() {
        assertEquals(List.of(
                new OptionDescriptor("firstPlayer", OptionType.CHOICE, List.of("host", "guest", "random"),
                        "random", "options.mangola.firstPlayer")), factory.optionDescriptors());
    }

    @Test
    void theOnlySeatOptionIsTheLevelWithGreedyAsTheDefault() {
        assertEquals(List.of(new OptionDescriptor("level", OptionType.CHOICE, List.of("random", "greedy", "expert"),
                "greedy", "options.mangola.level")), factory.seatOptionDescriptors());
        factory.validateSeatOption("level", "expert");
        assertThrows(InvalidOptionException.class, () -> factory.validateSeatOption("level", "grandmaster"));
        assertThrows(InvalidOptionException.class, () -> factory.validateSeatOption("tier", "3"));
    }

    @Test
    void validateCompletesTheDefaultsAndRefusesUnknownKeysAndValues() {
        assertEquals(GameOptions.of(Map.of("firstPlayer", "random")), factory.validate(GameOptions.none()));
        assertEquals(GameOptions.of(Map.of("firstPlayer", "guest")),
                factory.validate(GameOptions.of(Map.of("firstPlayer", "guest"))));
        // the set-up is always free placement (owner decision 2026-10-15): an old client's "setup" is an unknown option
        assertThrows(InvalidOptionException.class, () -> factory.validate(GameOptions.of(Map.of("setup", "free"))));
        assertThrows(InvalidOptionException.class,
                () -> factory.validate(GameOptions.of(Map.of("setup", "checkerboard"))));
        assertThrows(InvalidOptionException.class,
                () -> factory.validate(GameOptions.of(Map.of("firstPlayer", "nobody"))));
        assertThrows(InvalidOptionException.class, () -> factory.validate(GameOptions.of(Map.of("level", "x"))));
    }

    @Test
    void noOptionsBuildsAFreeGameInPlacement() {
        Mangola game = (Mangola) factory.create(GameId.generate(), 2);
        assertEquals(Phase.PLACEMENT, game.phase());
        assertEquals(MangolaRules.OWNER, game.rules());
    }

    @Test
    void theFirstPlayerOptionMapsToTheSeatThatBecomesSideA() {
        assertEquals(HOST, create(factory, Map.of("firstPlayer", "host")).seatA());
        assertEquals(GUEST, create(factory, Map.of("firstPlayer", "guest")).seatA());
    }

    @Test
    void randomFirstPlayerIsDrawnOnceFromTheGenerator() {
        assertEquals(HOST, create(new MangolaFactory(MangolaLayoutsTest.scripted(0)),
                Map.of("firstPlayer", "random")).seatA());
        assertEquals(GUEST, create(new MangolaFactory(MangolaLayoutsTest.scripted(1)),
                Map.of("firstPlayer", "random")).seatA());
    }

    @Test
    void theDefaultFirstPlayerIsRandom() {
        assertEquals(GUEST, create(new MangolaFactory(MangolaLayoutsTest.scripted(1)), Map.of()).seatA());
    }

    @Test
    void aFixedFirstPlayerDoesNotConsumeTheGenerator() {
        MangolaFactory strict = new MangolaFactory(MangolaLayoutsTest.scripted(99)); // any draw would throw
        assertEquals(HOST, create(strict, Map.of("firstPlayer", "host")).seatA());
    }

    @Test
    void everyGameStartsInPlacementWhateverTheFirstPlayerOption() {
        for (String first : List.of("host", "guest", "random")) {
            assertEquals(Phase.PLACEMENT, create(factory, Map.of("firstPlayer", first)).phase());
        }
    }

    @Test
    void anUnknownValueReachingCreateIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> create(factory, Map.of("firstPlayer", "nobody")));
    }
}

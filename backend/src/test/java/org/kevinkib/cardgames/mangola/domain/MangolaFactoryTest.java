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

class MangolaFactoryTest {

    private static final PlayerId HOST = new PlayerId(0);
    private static final PlayerId GUEST = new PlayerId(1);

    private final MangolaFactory factory = new MangolaFactory(MangolaSetupTest.scripted(0));

    private Mangola create(MangolaFactory factory, Map<String, String> options) {
        return (Mangola) factory.create(GameId.generate(), 2, GameOptions.of(options));
    }

    @Test
    void theGameTypeAndBoundsAreMangolaForExactlyTwoPlayers() {
        assertEquals("mangola", MangolaFactory.GAME_TYPE);
        assertEquals("mangola", factory.gameType());
        assertEquals(2, factory.minPlayers());
        assertEquals(2, factory.maxPlayers());
        assertFalse(factory.supportsBots(), "bots arrive with their own PR");
        assertThrows(IllegalArgumentException.class, () -> factory.create(GameId.generate(), 3));
        assertThrows(IllegalArgumentException.class, () -> factory.create(GameId.generate(), 1));
    }

    @Test
    void theOptionsAreTheSetupThenWhoStartsWithTheirDefaults() {
        assertEquals(List.of(
                new OptionDescriptor("setup", OptionType.CHOICE, List.of("free", "checkerboard", "random"),
                        "free", "options.mangola.setup"),
                new OptionDescriptor("firstPlayer", OptionType.CHOICE, List.of("host", "guest", "random"),
                        "random", "options.mangola.firstPlayer")), factory.optionDescriptors());
        assertEquals(List.of(), factory.seatOptionDescriptors());
    }

    @Test
    void validateCompletesTheDefaultsAndRefusesUnknownKeysAndValues() {
        assertEquals(GameOptions.of(Map.of("setup", "free", "firstPlayer", "random")),
                factory.validate(GameOptions.none()));
        assertEquals(GameOptions.of(Map.of("setup", "random", "firstPlayer", "random")),
                factory.validate(GameOptions.of(Map.of("setup", "random"))));
        assertThrows(InvalidOptionException.class, () -> factory.validate(GameOptions.of(Map.of("setup", "two"))));
        assertThrows(InvalidOptionException.class,
                () -> factory.validate(GameOptions.of(Map.of("firstPlayer", "nobody"))));
        assertThrows(InvalidOptionException.class, () -> factory.validate(GameOptions.of(Map.of("level", "x"))));
    }

    @Test
    void noOptionsBuildsAFreeGameInPlacement() {
        Mangola game = (Mangola) factory.create(GameId.generate(), 2);
        assertEquals(Phase.PLACEMENT, game.phase());
        assertEquals(MangolaSetup.FREE, game.rules().setup());
        assertEquals(MangolaRules.OWNER, game.rules());
    }

    @Test
    void theFirstPlayerOptionMapsToTheSeatThatBecomesSideA() {
        assertEquals(HOST, create(factory, Map.of("firstPlayer", "host")).seatA());
        assertEquals(GUEST, create(factory, Map.of("firstPlayer", "guest")).seatA());
    }

    @Test
    void randomFirstPlayerIsDrawnOnceFromTheGenerator() {
        assertEquals(HOST, create(new MangolaFactory(MangolaSetupTest.scripted(0)),
                Map.of("firstPlayer", "random")).seatA());
        assertEquals(GUEST, create(new MangolaFactory(MangolaSetupTest.scripted(1)),
                Map.of("firstPlayer", "random")).seatA());
    }

    @Test
    void theDefaultFirstPlayerIsRandom() {
        assertEquals(GUEST, create(new MangolaFactory(MangolaSetupTest.scripted(1)), Map.of()).seatA());
    }

    @Test
    void aFixedFirstPlayerDoesNotConsumeTheGenerator() {
        MangolaFactory strict = new MangolaFactory(MangolaSetupTest.scripted(99)); // any draw would throw
        assertEquals(HOST, create(strict, Map.of("firstPlayer", "host")).seatA());
    }

    @Test
    void theCheckerboardSetupSkipsPlacementAndStartsInPlay() {
        Mangola game = create(factory, Map.of("setup", "checkerboard", "firstPlayer", "guest"));
        assertEquals(Phase.PLAY, game.phase());
        assertEquals(MangolaSetup.checkerboard(), game.board());
        assertEquals(GUEST, game.currentSeat());
        assertEquals(MangolaSetup.CHECKERBOARD, game.rules().setup());
        assertEquals(1, game.repetitions());
    }

    @Test
    void theRandomSetupSkipsPlacementAndDrawsBothSidesAfterTheFirstSeat() {
        // firstPlayer random: nextInt(2) -> 1 (guest); then 32 drops on loop index 1 for A and 32 on index 2 for B
        int[] script = new int[1 + 64];
        script[0] = 1;
        for (int i = 1; i <= 32; i++) {
            script[i] = 1;
        }
        for (int i = 33; i <= 64; i++) {
            script[i] = 2;
        }
        Mangola game = create(new MangolaFactory(new ScriptedOnce(script)),
                Map.of("setup", "random", "firstPlayer", "random"));
        assertEquals(Phase.PLAY, game.phase());
        assertEquals(GUEST, game.seatA());
        assertEquals(32, game.board().seeds(MangolaGeometry.loop(Side.A).get(1)));
        assertEquals(32, game.board().seeds(MangolaGeometry.loop(Side.B).get(2)));
        assertEquals(64, game.board().total());
    }

    @Test
    void anUnknownValueReachingCreateIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> create(factory, Map.of("setup", "two")));
        assertThrows(IllegalArgumentException.class, () -> create(factory, Map.of("firstPlayer", "nobody")));
    }

    /** Plays a script of {@code nextInt} answers once, in order, whatever the bound. */
    private static final class ScriptedOnce implements java.util.random.RandomGenerator {
        private final int[] values;
        private int next;

        ScriptedOnce(int[] values) {
            this.values = values;
        }

        @Override
        public long nextLong() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int nextInt(int bound) {
            return values[next++];
        }
    }
}

package org.kevinkib.cardgames.kilodemerde.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.InvalidOptionException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KiloDeMerdeOptionsTest {

    @Test
    void defaultIsTenRounds() {
        KiloDeMerdeOptions defaults = KiloDeMerdeOptions.defaults();
        assertEquals(KiloDeMerdeOptions.Rounds.TEN, defaults.rounds());
        assertEquals(10, defaults.rounds().count());
    }

    @Test
    void roundsAreFiveTenFifteenTwenty() {
        assertEquals(5, KiloDeMerdeOptions.Rounds.FIVE.count());
        assertEquals(15, KiloDeMerdeOptions.Rounds.FIFTEEN.count());
        assertEquals(20, KiloDeMerdeOptions.Rounds.TWENTY.count());
    }

    @Test
    void strictParseReadsRounds() {
        assertEquals(KiloDeMerdeOptions.Rounds.FIFTEEN,
                KiloDeMerdeOptions.parseStrict(GameOptions.of(Map.of("rounds", "15"))).rounds());
    }

    @Test
    void strictParseTakesTheDefaultForAMissingKey() {
        assertEquals(KiloDeMerdeOptions.defaults(), KiloDeMerdeOptions.parseStrict(GameOptions.none()));
    }

    @Test
    void strictParseRefusesAnUnsupportedValue() {
        assertThrows(InvalidOptionException.class,
                () -> KiloDeMerdeOptions.parseStrict(GameOptions.of(Map.of("rounds", "7"))));
    }

    @Test
    void strictParseRefusesAnUnknownKey() {
        assertThrows(InvalidOptionException.class,
                () -> KiloDeMerdeOptions.parseStrict(GameOptions.of(Map.of("lives", "3"))));
    }

    @Test
    void lenientParseFallsBackToTheDefault() {
        assertEquals(KiloDeMerdeOptions.Rounds.TEN,
                KiloDeMerdeOptions.from(GameOptions.of(Map.of("rounds", "7"))).rounds());
        assertEquals(KiloDeMerdeOptions.Rounds.TWENTY,
                KiloDeMerdeOptions.from(GameOptions.of(Map.of("rounds", "20"))).rounds());
    }

    @Test
    void toGameOptionsRoundTrips() {
        KiloDeMerdeOptions options = new KiloDeMerdeOptions(KiloDeMerdeOptions.Rounds.FIVE);
        assertEquals(options, KiloDeMerdeOptions.parseStrict(options.toGameOptions()));
        assertEquals(Map.of("rounds", "5"), options.toGameOptions().values());
    }
}

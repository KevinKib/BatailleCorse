package org.kevinkib.cardgames.ninetynine.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.InvalidOptionException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NinetyNineOptionsTest {

    @Test
    void defaultsAreThreeLivesAndQuickModeOff() {
        NinetyNineOptions defaults = NinetyNineOptions.defaults();
        assertEquals(NinetyNineOptions.Lives.THREE, defaults.lives());
        assertEquals(NinetyNineOptions.QuickMode.OFF, defaults.quick());
        assertEquals(3, defaults.lives().count());
    }

    @Test
    void strictParseReadsBothKeys() {
        NinetyNineOptions parsed = NinetyNineOptions.parseStrict(GameOptions.of(Map.of("lives", "2", "quick", "on")));
        assertEquals(NinetyNineOptions.Lives.TWO, parsed.lives());
        assertEquals(NinetyNineOptions.QuickMode.ON, parsed.quick());
    }

    @Test
    void strictParseTakesDefaultsForMissingKeys() {
        assertEquals(NinetyNineOptions.defaults(), NinetyNineOptions.parseStrict(GameOptions.none()));
    }

    @Test
    void strictParseRefusesAnUnsupportedLivesValue() {
        assertThrows(InvalidOptionException.class,
                () -> NinetyNineOptions.parseStrict(GameOptions.of(Map.of("lives", "5"))));
    }

    @Test
    void strictParseRefusesAnUnknownQuickValue() {
        assertThrows(InvalidOptionException.class,
                () -> NinetyNineOptions.parseStrict(GameOptions.of(Map.of("quick", "maybe"))));
    }

    @Test
    void strictParseRefusesAnUnknownKey() {
        assertThrows(InvalidOptionException.class,
                () -> NinetyNineOptions.parseStrict(GameOptions.of(Map.of("decks", "2"))));
    }

    @Test
    void lenientParseFallsBackToDefaults() {
        NinetyNineOptions parsed = NinetyNineOptions.from(GameOptions.of(Map.of("lives", "5", "quick", "on")));
        assertEquals(NinetyNineOptions.Lives.THREE, parsed.lives());
        assertEquals(NinetyNineOptions.QuickMode.ON, parsed.quick());
    }

    @Test
    void toGameOptionsRoundTrips() {
        NinetyNineOptions options = new NinetyNineOptions(NinetyNineOptions.Lives.FOUR, NinetyNineOptions.QuickMode.ON);
        assertEquals(options, NinetyNineOptions.parseStrict(options.toGameOptions()));
        assertEquals(Map.of("lives", "4", "quick", "on"), options.toGameOptions().values());
    }
}

package org.kevinkib.cardgames.blackjack.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.InvalidOptionException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BlackjackOptionsTest {

    @Test
    void defaultsAreTenHandsAndMaxBetFifty() {
        BlackjackOptions defaults = BlackjackOptions.defaults();
        assertEquals(BlackjackOptions.Hands.TEN, defaults.hands());
        assertEquals(BlackjackOptions.MaxBet.FIFTY, defaults.maxBet());
        assertEquals(10, defaults.hands().count());
        assertEquals(50, defaults.maxBet().amount());
    }

    @Test
    void fixedConstants() {
        assertEquals(100, BlackjackOptions.STARTING_STACK);
        assertEquals(1, BlackjackOptions.MIN_BET);
    }

    @Test
    void strictParseReadsBothKeys() {
        BlackjackOptions parsed = BlackjackOptions.parseStrict(GameOptions.of(Map.of("hands", "15", "maxBet", "100")));
        assertEquals(BlackjackOptions.Hands.FIFTEEN, parsed.hands());
        assertEquals(BlackjackOptions.MaxBet.HUNDRED, parsed.maxBet());
    }

    @Test
    void strictParseAcceptsEveryListedValue() {
        for (String hands : new String[] {"10", "5", "15", "20"}) {
            assertEquals(hands, BlackjackOptions.parseStrict(GameOptions.of(Map.of("hands", hands))).hands().key());
        }
        for (String maxBet : new String[] {"50", "25", "100"}) {
            assertEquals(maxBet, BlackjackOptions.parseStrict(GameOptions.of(Map.of("maxBet", maxBet))).maxBet().key());
        }
    }

    @Test
    void strictParseTakesDefaultsForMissingKeys() {
        assertEquals(BlackjackOptions.defaults(), BlackjackOptions.parseStrict(GameOptions.none()));
    }

    @Test
    void strictParseRefusesAnUnsupportedHandsValue() {
        assertThrows(InvalidOptionException.class,
                () -> BlackjackOptions.parseStrict(GameOptions.of(Map.of("hands", "7"))));
    }

    @Test
    void strictParseRefusesAnUnsupportedMaxBetValue() {
        assertThrows(InvalidOptionException.class,
                () -> BlackjackOptions.parseStrict(GameOptions.of(Map.of("maxBet", "30"))));
    }

    @Test
    void strictParseRefusesAnUnknownKey() {
        assertThrows(InvalidOptionException.class,
                () -> BlackjackOptions.parseStrict(GameOptions.of(Map.of("decks", "2"))));
    }

    @Test
    void lenientParseFallsBackToDefaults() {
        BlackjackOptions parsed = BlackjackOptions.from(GameOptions.of(Map.of("hands", "7", "maxBet", "25")));
        assertEquals(BlackjackOptions.Hands.TEN, parsed.hands());
        assertEquals(BlackjackOptions.MaxBet.TWENTY_FIVE, parsed.maxBet());
    }

    @Test
    void toGameOptionsRoundTrips() {
        BlackjackOptions options = new BlackjackOptions(BlackjackOptions.Hands.TWENTY, BlackjackOptions.MaxBet.TWENTY_FIVE);
        assertEquals(options, BlackjackOptions.parseStrict(options.toGameOptions()));
        assertEquals(Map.of("hands", "20", "maxBet", "25"), options.toGameOptions().values());
    }
}

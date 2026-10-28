package org.kevinkib.cardgames.blackjack.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackCards.cards;

class DealerRuleTest {

    @ParameterizedTest
    @CsvSource({"10S 2D", "10S 6D", "2S 3D", "AS 5D"})
    void theDealerDrawsBelowSeventeen(String codes) {
        assertTrue(DealerRule.mustDraw(HandValue.of(cards(codes), false)));
    }

    @Test
    void theDealerStandsOnAHardSeventeen() {
        assertFalse(DealerRule.mustDraw(HandValue.of(cards("10S 7D"), false)));
    }

    @Test
    void theDealerStandsOnASoftSeventeen() {
        HandValue softSeventeen = HandValue.of(cards("6S AD"), false);
        assertTrue(softSeventeen.soft());
        assertFalse(DealerRule.mustDraw(softSeventeen));
    }

    @Test
    void theDealerStandsOnEverythingAboveSeventeen() {
        assertFalse(DealerRule.mustDraw(HandValue.of(cards("10S 8D"), false)));
        assertFalse(DealerRule.mustDraw(HandValue.of(cards("AS 7D"), false)));
        assertFalse(DealerRule.mustDraw(HandValue.of(cards("10S 6D 5C"), false)));
        assertFalse(DealerRule.mustDraw(HandValue.of(cards("10S 6D 8C"), false)));
    }

    @Test
    void aSoftHandBelowSeventeenDraws() {
        assertTrue(DealerRule.mustDraw(HandValue.of(cards("AS 5D"), false)));
    }
}

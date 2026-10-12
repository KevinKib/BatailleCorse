package org.kevinkib.cardgames.presentation.dto;

import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CardLabelsTest {

    @Test
    void givenEveryPublishedRankLabel_whenRank_thenReadsItBack() {
        for (FrenchRank rank : FrenchRank.values()) {
            assertThat(CardLabels.rank(rank.toString()), is(rank));
        }
    }

    @Test
    void givenEveryPublishedSuitLabel_whenSuit_thenReadsItBack() {
        for (FrenchSuit suit : FrenchSuit.values()) {
            assertThat(CardLabels.suit(suit.toString()), is(suit));
        }
    }

    @Test
    void givenDtoOfACard_whenCard_thenRoundTrips() {
        Card original = new Card(FrenchRank.QUEEN, FrenchSuit.DIAMOND);

        assertThat(CardLabels.card(CardDto.from(original)), is(original));
    }

    @Test
    void givenUnknownRank_whenRank_thenThrowsWithTheLabel() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> CardLabels.rank("NOPE"));

        assertThat(e.getMessage(), is("Unknown rank: NOPE"));
    }

    @Test
    void givenNullRank_whenRank_thenThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> CardLabels.rank(null));

        assertThat(e.getMessage(), is("Unknown rank: null"));
    }

    @Test
    void givenUnknownSuit_whenSuit_thenThrowsWithTheLabel() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> CardLabels.suit("NOPE"));

        assertThat(e.getMessage(), is("Unknown suit: NOPE"));
    }

    @Test
    void givenUnknownLabels_whenCard_thenThrowsWithBoth() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CardLabels.card(new CardDto("NOPE", "NOPE", "NOPE_NOPE")));

        assertThat(e.getMessage(), is("Unknown card: rank=NOPE suit=NOPE"));
    }
}

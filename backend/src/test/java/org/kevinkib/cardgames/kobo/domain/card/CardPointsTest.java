package org.kevinkib.cardgames.kobo.domain.card;

import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.card;

class CardPointsTest {

    @Test
    void givenNumberedCards_thenPointsAreFaceValueAndAceIsOne() {
        assertThat(CardPoints.of(card("AS")), is(1));
        assertThat(CardPoints.of(card("2H")), is(2));
        assertThat(CardPoints.of(card("10D")), is(10));
    }

    @Test
    void givenFaceCards_thenJackIs11AndQueenIs12() {
        assertThat(CardPoints.of(card("JH")), is(11));
        assertThat(CardPoints.of(card("QC")), is(12));
    }

    @Test
    void givenKings_thenBlackIs13AndRedIs0() {
        assertThat(CardPoints.of(card("KS")), is(13));
        assertThat(CardPoints.of(card("KC")), is(13));
        assertThat(CardPoints.of(card("KH")), is(0));
        assertThat(CardPoints.of(card("KD")), is(0));
    }

    @Test
    void givenJoker_thenRejected() {
        Card joker = new Card(FrenchRank.JOKER, FrenchSuit.RED_JOKER);
        assertThrows(IllegalArgumentException.class, () -> CardPoints.of(joker));
    }
}

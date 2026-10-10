package org.kevinkib.cardgames.kobo.domain.power;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.card;

class PowerTest {

    @Test
    void givenAceToSix_thenNoPower() {
        for (String c : new String[]{"AH", "2H", "3S", "4D", "5C", "6H"}) {
            assertThat(c, Power.of(card(c)), is(Power.NONE));
        }
    }

    @Test
    void givenSevenAndEight_thenPeekOwn() {
        assertThat(Power.of(card("7H")), is(Power.PEEK_OWN));
        assertThat(Power.of(card("8S")), is(Power.PEEK_OWN));
    }

    @Test
    void givenNineAndTen_thenPeekOther() {
        assertThat(Power.of(card("9H")), is(Power.PEEK_OTHER));
        assertThat(Power.of(card("10S")), is(Power.PEEK_OTHER));
    }

    @Test
    void givenJackAndQueen_thenBlindSwap() {
        assertThat(Power.of(card("JH")), is(Power.BLIND_SWAP));
        assertThat(Power.of(card("QS")), is(Power.BLIND_SWAP));
    }

    @Test
    void givenAnyKing_thenKingPower() {
        assertThat(Power.of(card("KH")), is(Power.KING));
        assertThat(Power.of(card("KS")), is(Power.KING));
    }
}

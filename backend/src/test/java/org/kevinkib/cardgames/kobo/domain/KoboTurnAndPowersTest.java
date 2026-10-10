package org.kevinkib.cardgames.kobo.domain;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.aKobo;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.ref;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.seat;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.card;

class KoboTurnAndPowersTest {

    private Kobo deciding(String drawn) {
        return aKobo().withTableau("AH", "2H", "3H", "4H").withTableau("5S", "6S", "9D", "KS")
                .withTableau("AC", "2C", "3C", "10S")
                .inPhase(Phase.DECISION).withDrawn(drawn).build();
    }

    // ---- swap / discard / end turn

    @Test
    void whenSwapDrawn_thenOldCardOnDiscardAndDrawnCardInSlotAtNewRevision() throws Exception {
        Kobo kobo = deciding("7S");

        kobo.swapDrawn(seat(0), 1);

        assertThat(kobo.discardTop().get(), is(card("2H")));
        assertThat(kobo.slots(seat(0)).get(1).card(), is(card("7S")));
        assertThat(kobo.slots(seat(0)).get(1).revision(), is(1));
        assertThat(kobo.phase(), is(Phase.TURN_END));
        assertThat(kobo.hasDrawn(), is(false));
        assertThat(kobo.pendingPower(), is(Power.NONE));
    }

    @Test
    void givenUnknownSlot_whenSwapDrawn_thenRejected() {
        Kobo kobo = deciding("7S");

        assertThrows(InvalidSlotException.class, () -> kobo.swapDrawn(seat(0), 9));

        assertThat(kobo.hasDrawn(), is(true));
        assertThat(kobo.phase(), is(Phase.DECISION));
        assertThat(kobo.version(), is(0));
    }

    @Test
    void whenDiscardDrawnWithoutPower_thenTurnEnd() throws Exception {
        Kobo kobo = deciding("5H");

        kobo.discardDrawn(seat(0));

        assertThat(kobo.discardTop().get(), is(card("5H")));
        assertThat(kobo.phase(), is(Phase.TURN_END));
    }

    @Test
    void whenDiscardDrawnWithPower_thenPowerPhaseWithMatchingPower() throws Exception {
        for (String[] c : new String[][]{{"7H", "PEEK_OWN"}, {"9H", "PEEK_OTHER"}, {"JH", "BLIND_SWAP"}, {"KH", "KING"}}) {
            Kobo kobo = deciding(c[0]);
            kobo.discardDrawn(seat(0));
            assertThat(kobo.phase(), is(Phase.POWER));
            assertThat(kobo.pendingPower(), is(Power.valueOf(c[1])));
        }
    }

    @Test
    void whenSwapASeven_thenNoPower() throws Exception {
        Kobo kobo = deciding("7H");

        kobo.swapDrawn(seat(0), 0);

        assertThat(kobo.phase(), is(Phase.TURN_END));
        assertThat(kobo.pendingPower(), is(Power.NONE));
    }

    @Test
    void endTurnPassesToNextSeatAndWraps() throws Exception {
        Kobo kobo = aKobo().withPlayers(3).withDrawPile("2D", "3D", "4D", "5D", "6D", "7D")
                .inPhase(Phase.DRAW).build();

        for (int expected : new int[]{0, 1, 2, 0}) {
            assertThat(kobo.currentSeat(), is(seat(expected)));
            kobo.draw(seat(expected));
            kobo.discardDrawn(seat(expected));
            if (kobo.phase() == Phase.POWER) {
                kobo.skipPower(seat(expected));
            }
            kobo.endTurn(seat(expected));
            assertThat(kobo.phase(), is(Phase.DRAW));
        }
    }

    @Test
    void endTurnByWrongSeatOrPhaseIsRejected() {
        Kobo kobo = aKobo().withPlayers(2).inPhase(Phase.TURN_END).build();

        assertThrows(NotPlayersTurnException.class, () -> kobo.endTurn(seat(1)));
        Kobo drawing = aKobo().withPlayers(2).build();
        assertThrows(WrongPhaseException.class, () -> drawing.endTurn(seat(0)));
    }

    // ---- peek

    @Test
    void peekOwn_revealsToTheActorOnly() throws Exception {
        Kobo kobo = deciding("8S");
        kobo.discardDrawn(seat(0));

        KoboEvent event = kobo.peek(seat(0), ref(0, 3));

        assertThat(kobo.revealFor(seat(0)).get().card(), is(card("4H")));
        assertThat(kobo.revealFor(seat(1)).isPresent(), is(false));
        assertThat(kobo.phase(), is(Phase.TURN_END));
        assertThat(event.toString().contains("4H"), is(false));
        assertThat(event.toString().contains("FOUR"), is(false));
    }

    @Test
    void peekOwnWithAnotherPlayersCard_isRejected() throws Exception {
        Kobo kobo = deciding("7S");
        kobo.discardDrawn(seat(0));

        assertThrows(InvalidSlotException.class, () -> kobo.peek(seat(0), ref(1, 0)));
    }

    @Test
    void peekOther_needsAnotherPlayersCard() throws Exception {
        Kobo kobo = deciding("10S");
        kobo.discardDrawn(seat(0));

        assertThrows(InvalidSlotException.class, () -> kobo.peek(seat(0), ref(0, 0)));
        kobo.peek(seat(0), ref(1, 3));

        assertThat(kobo.revealFor(seat(0)).get().card(), is(card("KS")));
        assertThat(kobo.revealFor(seat(0)).get().ref(), is(ref(1, 3)));
    }

    @Test
    void peekOfAnEmptySlotOrWithoutAPeekPower_isRejected() throws Exception {
        Kobo kobo = deciding("9S");
        kobo.discardDrawn(seat(0));
        kobo.matchDiscard(seat(1), ref(1, 2), null, card("9S").getRank());
        assertThrows(EmptySlotException.class, () -> kobo.peek(seat(0), ref(1, 2)));

        Kobo jack = deciding("JS");
        jack.discardDrawn(seat(0));
        assertThrows(WrongPhaseException.class, () -> jack.peek(seat(0), ref(1, 0)));
    }

    @Test
    void skipPower_endsThePowerAndRevealIsClearedOnTheNextCommand() throws Exception {
        Kobo kobo = deciding("9S");
        kobo.discardDrawn(seat(0));

        kobo.skipPower(seat(0));

        assertThat(kobo.phase(), is(Phase.TURN_END));
        assertThat(kobo.revealFor(seat(0)).isPresent(), is(false));

        Kobo peeked = deciding("7S");
        peeked.discardDrawn(seat(0));
        peeked.peek(seat(0), ref(0, 0));
        peeked.endTurn(seat(0));
        assertThat(peeked.revealFor(seat(0)).isPresent(), is(false));
    }

    // ---- blind swap

    @Test
    void blindSwap_exchangesTwoCardsAndRevealsNothing() throws Exception {
        Kobo kobo = deciding("QS");
        kobo.discardDrawn(seat(0));

        KoboEvent event = kobo.blindSwap(seat(0), 0, ref(1, 3));

        assertThat(kobo.slots(seat(0)).get(0).card(), is(card("KS")));
        assertThat(kobo.slots(seat(1)).get(3).card(), is(card("AH")));
        assertThat(kobo.slots(seat(0)).get(0).revision(), is(1));
        assertThat(kobo.slots(seat(1)).get(3).revision(), is(1));
        assertThat(kobo.phase(), is(Phase.TURN_END));
        assertThat(kobo.revealFor(seat(0)).isPresent(), is(false));
        assertThat(kobo.revealFor(seat(1)).isPresent(), is(false));
        assertThat(event instanceof KoboEvent.BlindSwapped, is(true));
    }

    @Test
    void blindSwap_rejectsSelfEmptySlotsAndWrongPower() throws Exception {
        Kobo kobo = deciding("JS");
        kobo.discardDrawn(seat(0));

        assertThrows(InvalidSlotException.class, () -> kobo.blindSwap(seat(0), 0, ref(0, 1)));
        assertThrows(InvalidSlotException.class, () -> kobo.blindSwap(seat(0), 9, ref(1, 1)));
        assertThrows(InvalidSlotException.class, () -> kobo.blindSwap(seat(0), 0, ref(7, 1)));
        assertThat(kobo.version(), is(1));

        Kobo seven = deciding("7S");
        seven.discardDrawn(seat(0));
        assertThrows(WrongPhaseException.class, () -> seven.blindSwap(seat(0), 0, ref(1, 1)));
    }

    @Test
    void blindSwapWithNoOwnCard_canOnlySkip() throws Exception {
        Kobo kobo = aKobo().withTableau().withTableau("5S", "6S", "9D", "KS")
                .inPhase(Phase.DECISION).withDrawn("JS").build();
        kobo.discardDrawn(seat(0));

        assertThrows(InvalidSlotException.class, () -> kobo.blindSwap(seat(0), 0, ref(1, 0)));
        kobo.skipPower(seat(0));
        assertThat(kobo.phase(), is(Phase.TURN_END));
    }

    // ---- king

    @Test
    void king_peekThenSwap() throws Exception {
        Kobo kobo = deciding("KH");
        kobo.discardDrawn(seat(0));

        kobo.peek(seat(0), ref(2, 3));
        assertThat(kobo.phase(), is(Phase.KING_DECISION));
        assertThat(kobo.revealFor(seat(0)).get().card(), is(card("10S")));

        kobo.kingSwap(seat(0), 0);

        assertThat(kobo.slots(seat(0)).get(0).card(), is(card("10S")));
        assertThat(kobo.slots(seat(2)).get(3).card(), is(card("AH")));
        assertThat(kobo.phase(), is(Phase.TURN_END));
        assertThat(kobo.revealFor(seat(0)).isPresent(), is(false));
    }

    @Test
    void king_blackKingAlsoHasThePowerAndSkipKeepsEverything() throws Exception {
        Kobo kobo = deciding("KS");
        kobo.discardDrawn(seat(0));
        kobo.peek(seat(0), ref(1, 0));

        kobo.skipPower(seat(0));

        assertThat(kobo.slots(seat(1)).get(0).card(), is(card("5S")));
        assertThat(kobo.phase(), is(Phase.TURN_END));
    }

    @Test
    void king_peekOfOwnCardIsRejected() throws Exception {
        Kobo kobo = deciding("KS");
        kobo.discardDrawn(seat(0));

        assertThrows(InvalidSlotException.class, () -> kobo.peek(seat(0), ref(0, 0)));
    }

    @Test
    void king_swapRejectedWhenThePeekedCardVanished_butSkipStillWorks() throws Exception {
        Kobo kobo = deciding("KS");
        kobo.discardDrawn(seat(0));
        kobo.peek(seat(0), ref(1, 3));

        kobo.matchDiscard(seat(1), ref(1, 3), null, card("KS").getRank());

        assertThrows(EmptySlotException.class, () -> kobo.kingSwap(seat(0), 0));
        kobo.skipPower(seat(0));
        assertThat(kobo.phase(), is(Phase.TURN_END));
    }

    @Test
    void king_swapRejectedAsStaleWhenThePeekedCardWasReplaced() throws Exception {
        Kobo kobo = deciding("KS");
        kobo.discardDrawn(seat(0));
        kobo.peek(seat(0), ref(1, 3));

        kobo.matchDiscard(seat(2), ref(1, 3), 0, card("KS").getRank());

        assertThrows(StaleSlotException.class, () -> kobo.kingSwap(seat(0), 0));
        kobo.skipPower(seat(0));
        assertThat(kobo.phase(), is(Phase.TURN_END));
    }
}

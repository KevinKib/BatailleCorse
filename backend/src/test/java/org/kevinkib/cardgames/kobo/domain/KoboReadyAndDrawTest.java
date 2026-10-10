package org.kevinkib.cardgames.kobo.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.aKobo;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.seat;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.card;

class KoboReadyAndDrawTest {

    // ---- memorisation barrier

    @Test
    void givenMemorising_whenEveryoneReady_thenFirstSeatMustDraw() throws Exception {
        Kobo kobo = new Kobo(GameId.generate(), 3, 1L);

        kobo.ready(seat(0));
        kobo.ready(seat(2));
        assertThat(kobo.phase(), is(Phase.MEMORISING));
        assertThat(kobo.readySeats().size(), is(2));
        kobo.ready(seat(1));

        assertThat(kobo.phase(), is(Phase.DRAW));
        assertThat(kobo.currentSeat(), is(seat(0)));
        assertThat(kobo.readySeats().size(), is(0));
        assertThat(kobo.version(), is(3));
    }

    @Test
    void whenReadyTwice_thenRejectedWithoutVersionBump() throws Exception {
        Kobo kobo = new Kobo(GameId.generate(), 2, 1L);
        kobo.ready(seat(0));
        int version = kobo.version();

        assertThrows(AlreadyReadyException.class, () -> kobo.ready(seat(0)));

        assertThat(kobo.version(), is(version));
    }

    @Test
    void givenWrongPhaseOrUnknownSeat_whenReady_thenRejected() throws Exception {
        Kobo drawing = aKobo().withPlayers(2).inPhase(Phase.DRAW).build();
        Kobo memorising = new Kobo(GameId.generate(), 2, 1L);

        assertThrows(WrongPhaseException.class, () -> drawing.ready(seat(0)));
        assertThrows(IllegalArgumentException.class, () -> memorising.ready(seat(5)));
    }

    @Test
    void givenMemorising_thenStartingCardsAreSlotsTwoAndThreeUntilTheFirstDraw() throws Exception {
        Kobo kobo = new Kobo(GameId.generate(), 2, 1L);
        PlayerId p0 = seat(0);

        assertThat(kobo.startingCards(p0).keySet(), contains(2, 3));
        assertThat(kobo.startingCards(p0).get(2), is(kobo.slots(p0).get(2).card()));

        kobo.ready(seat(0));
        kobo.ready(seat(1));
        assertThat(kobo.startingCards(p0).keySet(), contains(2, 3));

        kobo.draw(seat(0));
        assertThat(kobo.startingCards(p0).isEmpty(), is(true));
    }

    // ---- drawing

    @Test
    void whenCurrentPlayerDraws_thenCardHeldPrivatelyAndPhaseIsDecision() throws Exception {
        Kobo kobo = aKobo().withPlayers(2).withDrawPile("9H", "2C").build();

        kobo.draw(seat(0));

        assertThat(kobo.phase(), is(Phase.DECISION));
        assertThat(kobo.drawnCard(seat(0)).get(), is(card("9H")));
        assertThat(kobo.drawnCard(seat(1)).isPresent(), is(false));
        assertThat(kobo.hasDrawn(), is(true));
        assertThat(kobo.drawPileSize(), is(1));
        assertThat(kobo.version(), is(1));
    }

    @Test
    void givenWrongSeatOrPhase_whenDraw_thenRejectedAndUnchanged() {
        Kobo kobo = aKobo().withPlayers(2).build();

        assertThrows(NotPlayersTurnException.class, () -> kobo.draw(seat(1)));
        assertThat(kobo.version(), is(0));

        Kobo deciding = aKobo().withPlayers(2).inPhase(Phase.DECISION).withDrawn("9H").build();
        assertThrows(WrongPhaseException.class, () -> deciding.draw(seat(0)));
        assertThat(deciding.version(), is(0));
    }

    @Test
    void givenEmptyDrawPile_whenDraw_thenDiscardBelowTheTopIsReshuffled() throws Exception {
        Kobo kobo = aKobo().withPlayers(2).withDrawPile().withDiscard("KS", "2H", "3H", "4H", "5H").build();

        kobo.draw(seat(0));

        assertThat(kobo.discardTop().get(), is(card("KS")));
        assertThat(kobo.discardSize(), is(1));
        assertThat(kobo.drawPileSize(), is(3));
        assertThat(kobo.phase(), is(Phase.DECISION));
        assertThat(kobo.cardsInPlay(), is(8 + 5));
    }

    @Test
    void givenNothingToDraw_whenDraw_thenSkippedAndTurnGoesToItsEnd() throws Exception {
        Kobo kobo = aKobo().withPlayers(2).withDrawPile().withDiscard("KS").build();

        KoboEvent event = kobo.draw(seat(0));

        assertThat(event instanceof KoboEvent.DrawSkipped, is(true));
        assertThat(kobo.phase(), is(Phase.TURN_END));
        assertThat(kobo.hasDrawn(), is(false));
        assertThat(kobo.startingCards(seat(0)).isEmpty(), is(true));
        assertThat(kobo.revealFor(seat(0)), is(java.util.Optional.empty()));
        assertThat(kobo.pendingFinalTurns(), is(empty()));
    }
}

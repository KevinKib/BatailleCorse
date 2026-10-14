package org.kevinkib.cardgames.kobo.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.domain.power.Power;
import org.kevinkib.cardgames.kobo.domain.turn.Action;
import org.kevinkib.cardgames.kobo.domain.turn.Phase;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.aKobo;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.ref;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.seat;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.card;

class KoboBotViewTest {

    private static boolean noCardInAnySlot(KoboBotView view) {
        return view.tableaux().values().stream().flatMap(List::stream).allMatch(s -> s.card() == null);
    }

    @Test
    void inMemorising_thenOnlyOwnStartingCardsAreVisible() {
        Kobo kobo = aKobo().withTableau("AH", "2H", "3H", "4H").withTableau("5S", "6S", "9D", "KS")
                .inPhase(Phase.MEMORISING).build();

        KoboBotView view = KoboBotView.forSeat(kobo, seat(1));

        assertThat(noCardInAnySlot(view), is(true));
        assertThat(view.startingCards(), is(Map.of(2, card("9D"), 3, card("KS"))));
        assertThat(view.drawn().isPresent(), is(false));
        assertThat(view.reveal().isPresent(), is(false));
        assertThat(view.can(Action.READY), is(true));
    }

    @Test
    void afterTheFirstDraw_thenStartingCardsAreGoneForEverySeat() throws Exception {
        Kobo kobo = new Kobo(GameId.generate(), 2, 7L);
        kobo.ready(seat(0));
        kobo.ready(seat(1));
        assertThat(KoboBotView.forSeat(kobo, seat(0)).startingCards().size(), is(2));

        kobo.draw(seat(0));

        assertThat(KoboBotView.forSeat(kobo, seat(0)).startingCards().isEmpty(), is(true));
        assertThat(KoboBotView.forSeat(kobo, seat(1)).startingCards().isEmpty(), is(true));
    }

    @Test
    void inDecision_thenDrawnCardIsVisibleToTheCurrentSeatOnly() {
        Kobo kobo = aKobo().withPlayers(3).inPhase(Phase.DECISION).currentSeat(1).withDrawn("QS").build();

        KoboBotView current = KoboBotView.forSeat(kobo, seat(1));
        KoboBotView other = KoboBotView.forSeat(kobo, seat(0));

        assertThat(current.drawn().get(), is(card("QS")));
        assertThat(other.drawn().isPresent(), is(false));
        assertThat(other.hasDrawn(), is(true));
        assertThat(noCardInAnySlot(current), is(true));
        assertThat(current.can(Action.SWAP_DRAWN), is(true));
        assertThat(other.can(Action.SWAP_DRAWN), is(false));
    }

    @Test
    void afterAPeek_thenTheRevealIsVisibleToThePeekerOnlyAndNoSlotShowsACard() throws Exception {
        Kobo kobo = aKobo().withTableau("AH", "2H", "3H", "4H").withTableau("5S", "6S", "9D", "KS")
                .inPhase(Phase.POWER).withPendingPower(Power.PEEK_OTHER).build();

        kobo.peek(seat(0), ref(1, 2));

        KoboBotView peeker = KoboBotView.forSeat(kobo, seat(0));
        assertThat(peeker.reveal().get().card(), is(card("9D")));
        assertThat(peeker.reveal().get().ref(), is(ref(1, 2)));
        assertThat(KoboBotView.forSeat(kobo, seat(1)).reveal().isPresent(), is(false));
        assertThat(noCardInAnySlot(peeker), is(true));
    }

    @Test
    void inKingDecision_thenThePeekedCardIsTheRevealAndTheKingSwapIsOffered() throws Exception {
        Kobo kobo = aKobo().withTableau("AH", "2H", "3H", "4H").withTableau("5S", "6S", "9D", "KS")
                .inPhase(Phase.POWER).withPendingPower(Power.KING).build();

        kobo.peek(seat(0), ref(1, 0));

        KoboBotView view = KoboBotView.forSeat(kobo, seat(0));
        assertThat(view.phase(), is(Phase.KING_DECISION));
        assertThat(view.reveal().get().card(), is(card("5S")));
        assertThat(view.can(Action.KING_SWAP), is(true));
        assertThat(KoboBotView.forSeat(kobo, seat(1)).reveal().isPresent(), is(false));
    }

    @Test
    void inTurnEnd_thenNoCardIsVisibleAndTheKoboCanBeAnnounced() {
        Kobo kobo = aKobo().withPlayers(3).inPhase(Phase.TURN_END).build();

        KoboBotView view = KoboBotView.forSeat(kobo, seat(0));

        assertThat(noCardInAnySlot(view), is(true));
        assertThat(view.can(Action.ANNOUNCE_KOBO), is(true));
        assertThat(view.can(Action.END_TURN), is(true));
        assertThat(view.opponents(), contains(seat(1), seat(2)));
    }

    @Test
    void slotsCarryOccupancyAndRevisionWithoutTheCard() throws Exception {
        Kobo kobo = aKobo().withPlayers(2).inPhase(Phase.DECISION).withDrawn("5H").build();
        kobo.swapDrawn(seat(0), 1);

        KoboBotView view = KoboBotView.forSeat(kobo, seat(1));

        KoboBotView.SlotView swapped = view.slots(seat(0)).get(1);
        assertThat(swapped.occupied(), is(true));
        assertThat(swapped.revision(), is(1));
        assertThat(swapped.card() == null, is(true));
        assertThat(view.slots(seat(0)).get(0).revision(), is(0));
        assertThat(view.discardTop().get(), is(card("2H")));
    }

    @Test
    void afterTheRoundIsRevealed_thenEveryCardIsVisibleAndTheResultIsKnown() throws Exception {
        Kobo kobo = aKobo().withTableau("AH", "2H", "3H", "4H").withTableau("5S", "6S", "9D", "KS")
                .inPhase(Phase.TURN_END).withDrawPile("2C", "3C").build();
        kobo.announceKobo(seat(0));
        kobo.draw(seat(1));
        kobo.discardDrawn(seat(1));
        kobo.endTurn(seat(1));

        KoboBotView view = KoboBotView.forSeat(kobo, seat(1));

        assertThat(view.revealed(), is(true));
        assertThat(view.slots(seat(0)).get(0).card(), is(card("AH")));
        assertThat(view.slots(seat(1)).get(3).card(), is(card("KS")));
        assertThat(view.announcer().get(), is(seat(0)));
        assertThat(view.lastRound().isPresent(), is(true));
        assertThat(view.phase(), is(Phase.GIFT));
        assertThat(view.totals().get(seat(1)), is(0));
    }

    @Test
    void viewIsASnapshotOfPublicState() {
        Kobo kobo = aKobo().withPlayers(2).withDiscard("9S").inPhase(Phase.DRAW).build();

        KoboBotView view = KoboBotView.forSeat(kobo, seat(0));

        assertThat(view.seat(), is(seat(0)));
        assertThat(view.currentSeat(), is(seat(0)));
        assertThat(view.discardTop().get(), is(card("9S")));
        assertThat(view.drawPileSize(), is(5));
        assertThat(view.actions(), hasItem(Action.DRAW));
        assertThat(view.actions(), not(hasItem(Action.READY)));
        assertThat(view.pendingFinalTurns(), is(empty()));
        assertThat(view.handSize(seat(1)), is(4));
        assertThat(view.version(), is(kobo.version()));
    }
}

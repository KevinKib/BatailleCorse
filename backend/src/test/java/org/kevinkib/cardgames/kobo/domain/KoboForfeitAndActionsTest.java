package org.kevinkib.cardgames.kobo.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.aKobo;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.seat;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.card;

class KoboForfeitAndActionsTest {

    private KoboBuilder threePlayers() {
        return aKobo()
                .withTableau("AH", "KH", "KD", "AD")
                .withTableau("5S", "6S", "9D", "KS")
                .withTableau("2C", "3C", "4C", "KC")
                .withDiscard("2S")
                .withDrawPile("2D", "3D", "4D", "5D", "6D", "7D");
    }

    // ---- forfeit

    @Test
    void nonCurrentPlayerForfeits_cardsGoUnderTheDrawPileAndTheTurnStays() {
        Kobo kobo = threePlayers().inPhase(Phase.DRAW).build();
        int cards = kobo.cardsInPlay();

        kobo.forfeit(seat(2));

        assertThat(kobo.getPlayerIds(), contains(seat(0), seat(1)));
        assertThat(kobo.currentSeat(), is(seat(0)));
        assertThat(kobo.phase(), is(Phase.DRAW));
        assertThat(kobo.drawPileSize(), is(10));
        assertThat(kobo.cardsInPlay(), is(cards));
        assertThat(kobo.version(), is(1));
        assertThat(kobo.isFinished(), is(false));
    }

    @Test
    void currentPlayerForfeitsAfterDrawing_heldCardGoesUnderThePileAndTheNextSeatMustDraw() throws Exception {
        Kobo kobo = threePlayers().inPhase(Phase.DECISION).withDrawn("9S").currentSeat(1).build();
        int cards = kobo.cardsInPlay();

        kobo.forfeit(seat(1));

        assertThat(kobo.currentSeat(), is(seat(2)));
        assertThat(kobo.phase(), is(Phase.DRAW));
        assertThat(kobo.hasDrawn(), is(false));
        assertThat(kobo.cardsInPlay(), is(cards));
    }

    @Test
    void currentPlayerForfeitsInAPower_theTurnPassesOn() {
        Kobo kobo = threePlayers().inPhase(Phase.POWER).withPendingPower(Power.PEEK_OWN).currentSeat(2).build();

        kobo.forfeit(seat(2));

        assertThat(kobo.currentSeat(), is(seat(0)));
        assertThat(kobo.phase(), is(Phase.DRAW));
        assertThat(kobo.pendingPower(), is(Power.NONE));
    }

    @Test
    void forfeitWhileMemorising_reevaluatesTheBarrier() throws Exception {
        Kobo kobo = new Kobo(GameId.generate(), 3, 1L);
        kobo.ready(seat(0));
        kobo.ready(seat(1));

        kobo.forfeit(seat(2));

        assertThat(kobo.phase(), is(Phase.DRAW));
        assertThat(kobo.currentSeat(), is(seat(0)));
    }

    @Test
    void forfeitDuringFinalTurns_leavesTheQueue_andScoresWhenItEmpties() throws Exception {
        Kobo kobo = threePlayers().inPhase(Phase.TURN_END).build();
        kobo.announceKobo(seat(0));
        kobo.draw(seat(1));
        kobo.discardDrawn(seat(1));
        kobo.endTurn(seat(1));

        kobo.forfeit(seat(2));

        assertThat(kobo.phase(), is(Phase.GIFT));
        assertThat(kobo.getPlayerIds(), hasSize(2));
    }

    @Test
    void announcerForfeits_koboIsVoidAndTheRoundIsScoredWithHandValuesOnly() throws Exception {
        Kobo kobo = threePlayers().inPhase(Phase.TURN_END).currentSeat(1).build();
        kobo.announceKobo(seat(1));
        kobo.forfeit(seat(1));
        kobo.draw(seat(2));
        kobo.discardDrawn(seat(2));
        kobo.endTurn(seat(2));
        kobo.draw(seat(0));
        kobo.discardDrawn(seat(0));
        kobo.endTurn(seat(0));

        assertThat(kobo.phase(), is(Phase.ROUND_OVER));
        assertThat(kobo.totals().get(seat(0)), is(2));
        assertThat(kobo.totals().get(seat(2)), is(22));
        assertThat(kobo.lastRoundResult().get().announcer(), is((org.kevinkib.cardgames.game.PlayerId) null));
    }

    @Test
    void announcerForfeitsWhileOwingTheGift_roundIsFinalisedWithoutGift() throws Exception {
        Kobo kobo = threePlayers().inPhase(Phase.TURN_END).build();
        kobo.announceKobo(seat(0));
        for (int s : new int[]{1, 2}) {
            kobo.draw(seat(s));
            kobo.discardDrawn(seat(s));
            kobo.endTurn(seat(s));
        }
        assertThat(kobo.phase(), is(Phase.GIFT));

        kobo.forfeit(seat(0));

        assertThat(kobo.phase(), is(Phase.ROUND_OVER));
        assertThat(kobo.lastRoundResult().get().giftTo(), is((org.kevinkib.cardgames.game.PlayerId) null));
    }

    @Test
    void lastTwoPlayers_oneForfeits_theOtherWins() {
        Kobo kobo = aKobo().withPlayers(2).build();

        kobo.forfeit(seat(0));

        assertThat(kobo.isFinished(), is(true));
        assertThat(kobo.result().winners(), contains(seat(1)));
        assertThat(kobo.result().reason(), is(KoboResult.Reason.FORFEIT));
    }

    @Test
    void forfeitOfUnknownSeatOrOnFinishedGame_isANoOp() {
        Kobo kobo = aKobo().withPlayers(2).build();
        kobo.forfeit(seat(9));
        assertThat(kobo.version(), is(0));

        kobo.forfeit(seat(0));
        int version = kobo.version();
        kobo.forfeit(seat(1));
        assertThat(kobo.version(), is(version));
    }

    // ---- available actions

    @Test
    void actionsPerPhase() throws Exception {
        Kobo memorising = new Kobo(GameId.generate(), 2, 1L);
        assertThat(memorising.availableActions(seat(0)), contains(Action.READY));
        memorising.ready(seat(0));
        assertThat(memorising.availableActions(seat(0)), is(empty()));

        Kobo draw = threePlayers().inPhase(Phase.DRAW).build();
        assertThat(draw.availableActions(seat(0)), contains(Action.DRAW, Action.MATCH_DISCARD));
        assertThat(draw.availableActions(seat(1)), contains(Action.MATCH_DISCARD));

        Kobo decision = threePlayers().inPhase(Phase.DECISION).withDrawn("9S").build();
        assertThat(decision.availableActions(seat(0)),
                contains(Action.SWAP_DRAWN, Action.DISCARD_DRAWN, Action.MATCH_DISCARD));
        assertThat(decision.drawnCard(seat(0)).get(), is(card("9S")));
        assertThat(decision.drawnCard(seat(1)).isPresent(), is(false));

        Kobo peek = threePlayers().inPhase(Phase.POWER).withPendingPower(Power.PEEK_OTHER).build();
        assertThat(peek.availableActions(seat(0)), contains(Action.PEEK, Action.SKIP_POWER, Action.MATCH_DISCARD));
        Kobo swap = threePlayers().inPhase(Phase.POWER).withPendingPower(Power.BLIND_SWAP).build();
        assertThat(swap.availableActions(seat(0)), contains(Action.BLIND_SWAP, Action.SKIP_POWER, Action.MATCH_DISCARD));

        Kobo king = threePlayers().inPhase(Phase.KING_DECISION).build();
        assertThat(king.availableActions(seat(0)), contains(Action.KING_SWAP, Action.SKIP_POWER, Action.MATCH_DISCARD));

        Kobo end = threePlayers().inPhase(Phase.TURN_END).build();
        assertThat(end.availableActions(seat(0)),
                contains(Action.END_TURN, Action.ANNOUNCE_KOBO, Action.MATCH_DISCARD));
        end.announceKobo(seat(0));
        end.draw(seat(1));
        end.discardDrawn(seat(1));
        assertThat(end.availableActions(seat(1)), contains(Action.END_TURN, Action.MATCH_DISCARD));
    }

    @Test
    void noMatchWithoutADiscardTop_andNothingWhenFinishedOrUnknown() {
        Kobo kobo = threePlayers().withDiscard().inPhase(Phase.DRAW).build();
        assertThat(kobo.availableActions(seat(0)), contains(Action.DRAW));

        Kobo finished = aKobo().withPlayers(2).build();
        finished.forfeit(seat(0));
        assertThat(finished.availableActions(seat(1)), is(empty()));
        assertThat(kobo.availableActions(seat(8)), is(empty()));
    }

    @Test
    void giftAndRoundOverActions() throws Exception {
        Kobo kobo = threePlayers().inPhase(Phase.TURN_END).build();
        kobo.announceKobo(seat(0));
        for (int s : new int[]{1, 2}) {
            kobo.draw(seat(s));
            kobo.discardDrawn(seat(s));
            kobo.endTurn(seat(s));
        }
        assertThat(kobo.availableActions(seat(0)), contains(Action.GIVE_TEN));
        assertThat(kobo.availableActions(seat(1)), is(empty()));
        kobo.giveTen(seat(0), seat(1));
        List<Action> expected = List.of(Action.READY);
        assertThat(kobo.availableActions(seat(2)), is(expected));
        assertThat(kobo.getPlayerIds(), containsInAnyOrder(seat(0), seat(1), seat(2)));
    }
}

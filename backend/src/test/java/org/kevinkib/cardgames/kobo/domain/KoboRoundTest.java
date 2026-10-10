package org.kevinkib.cardgames.kobo.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.aKobo;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.ref;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.seat;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.card;

class KoboRoundTest {

    /** Seat 0 holds 2 points, seat 1 holds 33, seat 2 holds 22; seat 0 has just played its turn. */
    private KoboBuilder atTurnEnd() {
        return aKobo()
                .withTableau("AH", "KH", "KD", "AD")
                .withTableau("5S", "6S", "9D", "KS")
                .withTableau("2C", "3C", "4C", "KC")
                .withDiscard("2S")
                .withDrawPile("2D", "3D", "4D", "5D", "6D", "7D", "8D", "9D")
                .inPhase(Phase.TURN_END).currentSeat(0);
    }

    private void playQuickTurn(Kobo kobo, int seat) throws Exception {
        kobo.draw(seat(seat));
        kobo.discardDrawn(seat(seat));
        if (kobo.phase() == Phase.POWER) {
            kobo.skipPower(seat(seat));
        }
        kobo.endTurn(seat(seat));
    }

    // ---- announce

    @Test
    void announce_queuesTheOthersInSeatOrderAndPassesTheTurn() throws Exception {
        Kobo kobo = atTurnEnd().currentSeat(1).build();

        kobo.announceKobo(seat(1));

        assertThat(kobo.announcer().get(), is(seat(1)));
        assertThat(kobo.pendingFinalTurns(), contains(seat(2), seat(0)));
        assertThat(kobo.currentSeat(), is(seat(2)));
        assertThat(kobo.phase(), is(Phase.DRAW));
        assertThat(kobo.version(), is(1));
    }

    @Test
    void announce_isRefusedWhileADecisionOrPowerIsPending_outOfTurnTwiceAndInFinalTurns() throws Exception {
        for (Phase phase : new Phase[]{Phase.DRAW, Phase.DECISION, Phase.POWER, Phase.KING_DECISION}) {
            Kobo kobo = atTurnEnd().inPhase(phase).withDrawn("2S").build();
            assertThrows(WrongPhaseException.class, () -> kobo.announceKobo(seat(0)));
            assertThat(kobo.version(), is(0));
        }
        Kobo kobo = atTurnEnd().build();
        assertThrows(NotPlayersTurnException.class, () -> kobo.announceKobo(seat(1)));

        kobo.announceKobo(seat(0));
        playQuickTurn(kobo, 1);
        kobo.draw(seat(2));
        kobo.discardDrawn(seat(2));
        assertThrows(WrongPhaseException.class, () -> kobo.announceKobo(seat(2)));
    }

    @Test
    void aPlayerWithNoCardCanAnnounce() throws Exception {
        Kobo kobo = aKobo().withTableau().withTableau("5S", "6S", "9D", "KS").inPhase(Phase.TURN_END).build();

        kobo.announceKobo(seat(0));

        assertThat(kobo.announcer().get(), is(seat(0)));
    }

    @Test
    void finalTurns_everyoneElsePlaysOnceAndMatchingStaysPossible() throws Exception {
        Kobo kobo = atTurnEnd().build();
        kobo.announceKobo(seat(0));

        playQuickTurn(kobo, 1);
        assertThat(kobo.phase(), is(Phase.DRAW));
        assertThat(kobo.currentSeat(), is(seat(2)));
        // the top is now seat 1 discarded 2D: seat 2 matches it with its own 2C
        kobo.matchDiscard(seat(2), ref(2, 0), null, card("2D").getRank());
        playQuickTurn(kobo, 2);

        assertThat(kobo.phase(), is(Phase.GIFT));
        assertThat(kobo.currentSeat(), is(seat(0)));
    }

    @Test
    void twoPlayers_announcerThenExactlyOneMoreTurn() throws Exception {
        Kobo kobo = aKobo().withTableau("AH", "KH", "KD", "AD").withTableau("5S", "6S", "9D", "KS")
                .withDiscard("2S").inPhase(Phase.TURN_END).build();
        kobo.announceKobo(seat(0));

        assertThat(kobo.pendingFinalTurns(), contains(seat(1)));
        playQuickTurn(kobo, 1);

        assertThat(kobo.phase(), is(Phase.GIFT));
    }

    // ---- resolution

    @Test
    void announcerWithFewestPoints_givesTenAndScoresZero() throws Exception {
        Kobo kobo = atTurnEnd().build();
        kobo.announceKobo(seat(0));
        playQuickTurn(kobo, 1);
        playQuickTurn(kobo, 2);

        assertThat(kobo.isRevealed(), is(true));
        assertThrows(NotPlayersTurnException.class, () -> kobo.giveTen(seat(1), seat(2)));
        assertThrows(InvalidGiftTargetException.class, () -> kobo.giveTen(seat(0), seat(0)));
        assertThrows(InvalidGiftTargetException.class, () -> kobo.giveTen(seat(0), seat(9)));

        kobo.giveTen(seat(0), seat(2));

        assertThat(kobo.phase(), is(Phase.ROUND_OVER));
        assertThat(kobo.totals().get(seat(0)), is(0));
        assertThat(kobo.totals().get(seat(1)), is(33));
        assertThat(kobo.totals().get(seat(2)), is(32));
        assertThat(kobo.lastRoundResult().get().giftTo(), is(seat(2)));
    }

    @Test
    void announcerNotLowest_isPenalisedAtOnceWithoutAGiftPhase() throws Exception {
        Kobo kobo = atTurnEnd().currentSeat(1).build();
        kobo.announceKobo(seat(1));
        playQuickTurn(kobo, 2);
        playQuickTurn(kobo, 0);

        assertThat(kobo.phase(), is(Phase.ROUND_OVER));
        assertThat(kobo.totals().get(seat(1)), is(33 + 20));
        assertThat(kobo.totals().get(seat(0)), is(2));
        assertThat(kobo.totals().get(seat(2)), is(22));
    }

    @Test
    void totalsExactly75Or100DropTo50_andOnly101EndsTheGame() throws Exception {
        Kobo reset = atTurnEnd().withTotals(0, 42, 100 - 22).build();
        reset.announceKobo(seat(0));
        playQuickTurn(reset, 1);
        playQuickTurn(reset, 2);
        reset.giveTen(seat(0), seat(1));

        // seat 1: 42 + 33 + 10 = 85; seat 2: 78 + 22 = 100 -> 50
        assertThat(reset.totals().get(seat(1)), is(85));
        assertThat(reset.totals().get(seat(2)), is(50));
        assertThat(reset.phase(), is(Phase.ROUND_OVER));

        Kobo over = atTurnEnd().withTotals(0, 69, 0).build();
        over.announceKobo(seat(0));
        playQuickTurn(over, 1);
        playQuickTurn(over, 2);
        over.giveTen(seat(0), seat(2));
        // seat 1: 69 + 33 = 102
        assertThat(over.phase(), is(Phase.FINISHED));
        assertThat(over.isFinished(), is(true));
        assertThat(over.result().winners(), contains(seat(0)));
        assertThat(over.result().reason(), is(KoboResult.Reason.SCORE));
    }

    @Test
    void gameEnd_tiedLowestTotalsAllWin() throws Exception {
        Kobo kobo = aKobo().withTableau("AH", "AD", "KD", "KH").withTableau("KH", "KD")
                .withTableau("QS", "QC", "KS", "KC")
                .withDiscard("2S").withDrawPile("3D", "4D", "5D", "6D")
                .withTotals(96, 98, 70).inPhase(Phase.TURN_END).currentSeat(2).build();
        kobo.announceKobo(seat(2));
        playQuickTurn(kobo, 0);
        playQuickTurn(kobo, 1);

        // seat 2 announced with 50 points against 2 and 0: it scores 70, then 50 + 20
        assertThat(kobo.phase(), is(Phase.FINISHED));
        assertThat(kobo.result().winners(), contains(seat(0), seat(1)));
        assertThat(kobo.totals().get(seat(2)), is(140));
    }

    @Test
    void roundOver_everyoneReady_startsTheNextRoundWithTheTotalsKept() throws Exception {
        Kobo kobo = atTurnEnd().currentSeat(1).build();
        kobo.announceKobo(seat(1));
        playQuickTurn(kobo, 2);
        playQuickTurn(kobo, 0);
        assertThat(kobo.phase(), is(Phase.ROUND_OVER));
        assertThat(kobo.availableActions(seat(0)), contains(Action.READY));

        kobo.ready(seat(0));
        kobo.ready(seat(1));
        assertThat(kobo.phase(), is(Phase.ROUND_OVER));
        kobo.ready(seat(2));

        assertThat(kobo.phase(), is(Phase.MEMORISING));
        assertThat(kobo.round(), is(2));
        assertThat(kobo.currentSeat(), is(seat(1)));
        assertThat(kobo.discardSize(), is(0));
        assertThat(kobo.drawPileSize(), is(52 - 12));
        assertThat(kobo.cardsInPlay(), is(52));
        assertThat(kobo.totals().get(seat(1)), is(53));
        assertThat(kobo.isRevealed(), is(false));
        assertThat(kobo.announcer().isPresent(), is(false));
        assertThat(kobo.startingCards(seat(0)).keySet(), hasSize(2));
        for (PlayerId p : kobo.getPlayerIds()) {
            assertThat(kobo.handSize(p), is(4));
        }
    }

    @Test
    void aWholeScriptedMatch_endsWhenSomeoneGoesAbove100() throws Exception {
        Kobo kobo = new Kobo(org.kevinkib.cardgames.game.GameId.generate(), 3, 99L);
        int guard = 0;
        while (!kobo.isFinished() && guard++ < 5000) {
            switch (kobo.phase()) {
                case MEMORISING, ROUND_OVER -> {
                    for (PlayerId p : kobo.getPlayerIds()) {
                        if (kobo.availableActions(p).contains(Action.READY)) {
                            kobo.ready(p);
                        }
                    }
                }
                case DRAW -> kobo.draw(kobo.currentSeat());
                case DECISION -> kobo.swapDrawn(kobo.currentSeat(), kobo.slots(kobo.currentSeat()).stream()
                        .filter(Tableau.Slot::occupied).findFirst().get().index());
                case TURN_END -> {
                    // announce every other turn once somebody has played a few
                    if (kobo.availableActions(kobo.currentSeat()).contains(Action.ANNOUNCE_KOBO) && guard % 5 == 0) {
                        kobo.announceKobo(kobo.currentSeat());
                    } else {
                        kobo.endTurn(kobo.currentSeat());
                    }
                }
                case GIFT -> kobo.giveTen(kobo.currentSeat(), kobo.getPlayerIds().stream()
                        .filter(p -> !p.equals(kobo.currentSeat())).findFirst().get());
                default -> throw new IllegalStateException(kobo.phase().name());
            }
            if (!kobo.isFinished()) {
                assertThat(kobo.cardsInPlay(), is(52));
            }
        }
        assertThat(kobo.isFinished(), is(true));
        assertThat(kobo.totals().values().stream().anyMatch(t -> t > 100), is(true));
        assertThat(kobo.result().winners().isEmpty(), is(false));
    }
}

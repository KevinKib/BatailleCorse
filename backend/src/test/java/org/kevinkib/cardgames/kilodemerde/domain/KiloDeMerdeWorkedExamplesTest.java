package org.kevinkib.cardgames.kilodemerde.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kilodemerde.domain.bot.KiloDeMerdeBotView;
import org.kevinkib.cardgames.kilodemerde.domain.event.Flushed;
import org.kevinkib.cardgames.kilodemerde.domain.event.KiloDeMerdeEvent;
import org.kevinkib.cardgames.kilodemerde.domain.event.KiloDrawn;
import org.kevinkib.cardgames.kilodemerde.domain.event.RaceOpened;
import org.kevinkib.cardgames.kilodemerde.domain.event.RoundOver;
import org.kevinkib.cardgames.kilodemerde.domain.event.StepResolved;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloCards.c;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloCards.cards;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeBuilder.A;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeBuilder.B;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeBuilder.C;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeBuilder.D;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeBuilder.E;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeBuilder.game;

/** The worked examples of spec section 8.3 (1 to 10, and the version rule 14), one test each. */
class KiloDeMerdeWorkedExamplesTest {

    // ---- 1 and 2: the pass step ----

    @Test
    void example1_aStepResolvesAtomicallyWhenTheLastSeatChooses() throws Exception {
        KiloDeMerde game = game(4)
                .hand(A, "QC JS JH 2S").hand(B, "2D KS KH QS").hand(C, "JH JD QD 2H").hand(D, "KC 2C JC QH")
                .build();

        game.choosePass(A, c("QC"));
        game.choosePass(B, c("2D"));
        game.choosePass(C, c("JH"));
        assertEquals(1, game.step());
        assertTrue(game.hasChosen(A));
        List<KiloDeMerdeEvent> events = game.choosePass(D, c("KC"));

        assertEquals(2, game.step());
        assertEquals(Phase.PASSING, game.phase());
        assertEquals(cards("KC JS JH 2S"), game.hand(A));
        assertEquals(cards("QC KS KH QS"), game.hand(B));
        assertEquals(cards("2D JD QD 2H"), game.hand(C));
        assertEquals(cards("JH 2C JC QH"), game.hand(D));
        assertEquals(Optional.of(c("KC")), game.lastReceived(A));
        assertEquals(Optional.of(c("QC")), game.lastGiven(A));
        assertEquals(Optional.of(c("JH")), game.lastReceived(D));
        for (PlayerId seat : game.activeSeats()) {
            assertEquals(4, game.hand(seat).size());
            assertFalse(game.hasChosen(seat));
        }
        assertTrue(events.stream().anyMatch(e -> e instanceof StepResolved s && s.step() == 2));
        assertEquals(List.of(new KiloDeMerdeBotView.Pass(1, c("QC"), c("KC"))), game.passHistory(A));
    }

    @Test
    void example2_aChoiceIsFinalAndTheCardMustBeInTheHand() throws Exception {
        KiloDeMerde game = game(4).hand(A, "QC KS JH 2S").build();

        game.choosePass(A, c("QC"));

        assertThrows(AlreadyChoseException.class, () -> game.choosePass(A, c("KS")));
        assertEquals(Optional.of(c("QC")), game.chosen(A));
        assertThrows(CardNotInHandException.class, () -> game.choosePass(B, c("AH")));
        assertFalse(game.hasChosen(B));
    }

    // ---- 3 to 6: the slap ----

    @Test
    void example3_aValidRaceMakesTheLastSlapperDrawTheTopKilo() throws Exception {
        KiloDeMerde game = game(4).hand(A, "KS KH KD KC").pile("AH 9C 8C").build();

        List<KiloDeMerdeEvent> opened = game.slap(A);

        assertEquals(Phase.RACE, game.phase());
        assertEquals(List.of(A), game.slapOrder());
        assertTrue(opened.stream().anyMatch(e -> e instanceof RaceOpened r && r.seat().equals(A)));
        assertThrows(WrongPhaseException.class, () -> game.choosePass(B, c("JS")));

        game.slap(C);
        assertEquals(Phase.RACE, game.phase());
        List<KiloDeMerdeEvent> last = game.slap(B);

        assertEquals(Phase.ROUND_OVER, game.phase());
        assertEquals(100, game.kilos(D));
        assertEquals(cards("AH"), game.kiloCards(D));
        assertEquals(2, game.pileSize());
        assertTrue(last.stream().anyMatch(e -> e instanceof KiloDrawn k && k.seat().equals(D)));
        assertTrue(last.stream().anyMatch(e -> e instanceof RoundOver));
        RoundOutcome outcome = game.lastOutcome().orElseThrow();
        assertEquals(RoundOutcome.Kind.RACE, outcome.kind());
        assertEquals(A, outcome.firstSlapper());
        assertEquals(List.of(A, C, B), outcome.slapOrder());
        assertEquals(List.of(A), outcome.holders());
        assertEquals(cards("KS KH KD KC"), outcome.hands().get(A));
        assertEquals(4, outcome.hands().size());
        assertEquals(1, outcome.draws().size());
        assertEquals(new KiloDraw(D, c("AH"), false, 100), outcome.draws().get(0));
        assertEquals(1, game.roundsPlayed());
    }

    @Test
    void example4_anySeatMayOpenTheRaceWhenAnyHandIsFourOfAKind() throws Exception {
        KiloDeMerde game = game(4).hand(A, "QS QH QD QC").build();

        game.slap(C);
        assertEquals(Phase.RACE, game.phase());
        assertEquals(List.of(C), game.slapOrder());
        game.slap(A);
        game.slap(D);

        assertEquals(Phase.ROUND_OVER, game.phase());
        assertEquals(List.of(C, A, D), game.lastOutcome().orElseThrow().slapOrder());
        assertEquals(100, game.kilos(B));
        assertEquals(0, game.kilos(A));
        assertEquals(List.of(A), game.lastOutcome().orElseThrow().holders());
    }

    @Test
    void example5_aFalseSlapMakesTheSlapperDrawAloneAndEndsTheRound() throws Exception {
        KiloDeMerde game = game(4).pile("9C 8C").build();

        List<KiloDeMerdeEvent> events = game.slap(B);

        assertEquals(Phase.ROUND_OVER, game.phase());
        assertEquals(9, game.kilos(B));
        assertEquals(1, game.pileSize());
        RoundOutcome outcome = game.lastOutcome().orElseThrow();
        assertEquals(RoundOutcome.Kind.FALSE_SLAP, outcome.kind());
        assertEquals(B, outcome.firstSlapper());
        assertTrue(outcome.holders().isEmpty());
        assertEquals(List.of(new KiloDraw(B, c("9C"), false, 9)), outcome.draws());
        assertTrue(events.stream().anyMatch(e -> e instanceof org.kevinkib.cardgames.kilodemerde.domain.event.FalseSlap));
        assertEquals(1, game.roundsPlayed());
    }

    @Test
    void example6_aSecondSlapBySameSeatIsRefusedWithoutPenalty() throws Exception {
        KiloDeMerde game = game(4).hand(A, "KS KH KD KC").build();
        game.slap(A);
        game.slap(C);
        KiloSnapshot before = KiloSnapshot.of(game);

        assertThrows(AlreadySlappedException.class, () -> game.slap(C));

        assertEquals(before, KiloSnapshot.of(game));
        assertEquals(List.of(A, C), game.slapOrder());
    }

    // ---- 7: the flush ----

    @Test
    void example7_aDrawnSevenReturnsTheDrawersKilosAndTheSevenToTheShuffledPile() throws Exception {
        RecordingDeckSource source = new RecordingDeckSource();
        KiloDeMerde game = game(4).hand(A, "KS KH KD KC").kiloCards(D, "AH KS").pile("7D 2C 3C")
                .deckSource(source).build();
        assertEquals(113, game.kilos(D));

        game.slap(A);
        game.slap(C);
        List<KiloDeMerdeEvent> events = game.slap(B);

        assertEquals(0, game.kilos(D));
        assertTrue(game.kiloCards(D).isEmpty());
        assertEquals(2 + 3, game.pileSize());
        assertEquals(1, source.pileShuffles.size());
        assertEquals(new HashSet<>(cards("2C 3C AH KS 7D")), new HashSet<>(source.pileShuffles.get(0)));
        assertEquals(5, source.pileShuffles.get(0).size());
        Flushed flushed = events.stream().filter(e -> e instanceof Flushed).map(e -> (Flushed) e)
                .findFirst().orElseThrow();
        assertEquals(new Flushed(D, 3), flushed);
        assertEquals(new KiloDraw(D, c("7D"), true, 0), game.lastOutcome().orElseThrow().draws().get(0));
    }

    // ---- 8: the race timeout ----

    @Test
    void example8_theRaceTimeoutMakesEveryNonSlapperDrawFromTheFirstSlappersLeft() throws Exception {
        KiloDeMerde game = game(5).hand(A, "KS KH KD KC").pile("5C 6C 8C 9C").build();
        game.slap(A);
        game.slap(B);
        game.slap(C);
        int race = game.raceId();

        assertThrows(WrongPhaseException.class, () -> game.closeRace(race + 1));
        assertEquals(Phase.RACE, game.phase());
        List<KiloDeMerdeEvent> events = game.closeRace(race);

        assertEquals(Phase.ROUND_OVER, game.phase());
        assertEquals(5, game.kilos(D));
        assertEquals(6, game.kilos(E));
        assertEquals(0, game.kilos(A));
        List<KiloDraw> draws = game.lastOutcome().orElseThrow().draws();
        assertEquals(List.of(D, E), draws.stream().map(KiloDraw::seat).toList());
        assertEquals(List.of(A, B, C), game.lastOutcome().orElseThrow().slapOrder());
        assertEquals(2, events.stream().filter(e -> e instanceof KiloDrawn).count());
        assertThrows(WrongPhaseException.class, () -> game.closeRace(race));
    }

    // ---- 9: the pass timer ----

    @Test
    void example9_thePassTimerChoosesTheDefaultPassForSeatsThatDidNot() throws Exception {
        KiloDeMerde game = game(4)
                .hand(A, "QC KS KH 2S").hand(B, "2D KS KH QS").hand(C, "JS JH QD 2C").hand(D, "KC 2H JC QH")
                .build();
        game.choosePass(A, c("QC"));
        game.choosePass(B, c("2D"));

        assertThrows(WrongPhaseException.class, () -> game.expirePassTimer(2));
        game.expirePassTimer(1);

        assertEquals(2, game.step());
        assertEquals(Optional.of(c("2C")), game.lastGiven(C));
        assertEquals(Optional.of(KiloDeMerdeRules.defaultPass(cards("KC 2H JC QH"))), game.lastGiven(D));
        assertEquals(Optional.of(c("2C")), game.lastReceived(D));
        assertThrows(WrongPhaseException.class, () -> game.expirePassTimer(1));
    }

    // ---- 10: the stall ----

    @Test
    void example10_aRoundStallsAfterFortyStepsWithoutASlap() throws Exception {
        KiloDeMerde game = game(4).build();

        for (int i = 0; i < KiloDeMerdeRules.STALL_LIMIT - 1; i++) {
            passFirstCards(game);
        }
        assertEquals(Phase.PASSING, game.phase());
        assertEquals(KiloDeMerdeRules.STALL_LIMIT, game.step());
        passFirstCards(game);

        assertEquals(Phase.ROUND_OVER, game.phase());
        RoundOutcome outcome = game.lastOutcome().orElseThrow();
        assertEquals(RoundOutcome.Kind.STALLED, outcome.kind());
        assertTrue(outcome.draws().isEmpty());
        assertNull(outcome.firstSlapper());
        assertEquals(1, game.roundsPlayed());
        assertEquals(5, game.pileSize());
    }

    private static void passFirstCards(KiloDeMerde game) throws KiloDeMerdeException {
        for (PlayerId seat : game.activeSeats()) {
            game.choosePass(seat, game.hand(seat).get(0));
        }
    }

    // ---- 14: version and refusals ----

    @Test
    void example14_everyAcceptedCommandBumpsTheVersionAndEveryRefusedOneChangesNothing() throws Exception {
        KiloDeMerde game = game(4).hand(A, "QC KS KH KD").hand(D, "KC JS QS 2S").build();
        int version = game.version();

        game.choosePass(A, c("QC"));
        assertEquals(version + 1, game.version());
        game.choosePass(B, c("JS"));
        game.choosePass(C, c("JS"));
        game.choosePass(D, c("KC"));
        assertEquals(version + 4, game.version());
        game.slap(A);
        assertEquals(version + 5, game.version());
        game.slap(B);
        game.slap(C);
        assertEquals(version + 7, game.version());
        game.nextRound();
        assertEquals(version + 8, game.version());
        game.expirePassTimer(1);
        assertEquals(version + 9, game.version());
    }

    @Test
    void refusedCommandsNeverMutate() throws Exception {
        KiloDeMerde game = game(4).hand(A, "KS KH KD KC").build();
        game.choosePass(B, c("JS"));
        KiloSnapshot passing = KiloSnapshot.of(game);

        assertThrows(AlreadyChoseException.class, () -> game.choosePass(B, c("QS")));
        assertThrows(CardNotInHandException.class, () -> game.choosePass(C, c("AH")));
        assertThrows(NotActiveSeatException.class, () -> game.choosePass(new PlayerId(9), c("JS")));
        assertThrows(NotActiveSeatException.class, () -> game.slap(new PlayerId(9)));
        assertThrows(WrongPhaseException.class, () -> game.closeRace(1));
        assertThrows(WrongPhaseException.class, () -> game.expirePassTimer(7));
        assertThrows(WrongPhaseException.class, game::nextRound);
        assertEquals(passing, KiloSnapshot.of(game));

        game.slap(A);
        KiloSnapshot race = KiloSnapshot.of(game);
        assertThrows(WrongPhaseException.class, () -> game.choosePass(C, c("JS")));
        assertThrows(WrongPhaseException.class, () -> game.expirePassTimer(1));
        assertThrows(AlreadySlappedException.class, () -> game.slap(A));
        assertThrows(WrongPhaseException.class, game::nextRound);
        assertEquals(race, KiloSnapshot.of(game));

        game.closeRace(game.raceId());
        KiloSnapshot over = KiloSnapshot.of(game);
        assertThrows(WrongPhaseException.class, () -> game.slap(B));
        assertThrows(WrongPhaseException.class, () -> game.choosePass(B, c("JS")));
        assertThrows(WrongPhaseException.class, () -> game.closeRace(game.raceId()));
        assertEquals(over, KiloSnapshot.of(game));
    }

    @Test
    void availableActionsFollowThePhaseAndTheChoice() throws Exception {
        KiloDeMerde game = game(4).hand(A, "KS KH KD KC").build();

        assertEquals(List.of(Action.PASS, Action.SLAP), game.availableActions(A));
        game.choosePass(A, c("KS"));
        assertEquals(List.of(Action.SLAP), game.availableActions(A));
        game.slap(B);
        assertEquals(List.of(Action.SLAP), game.availableActions(C));
        assertTrue(game.availableActions(B).isEmpty());
        game.slap(C);
        game.slap(D);
        assertTrue(game.availableActions(A).isEmpty());
    }

    @Test
    void neighboursFollowTheSeatOrderWithWrapAround() {
        KiloDeMerde game = game(4).build();

        assertEquals(B, game.leftOf(A));
        assertEquals(A, game.leftOf(D));
        assertEquals(D, game.rightOf(A));
        assertEquals(A, game.rightOf(B));
    }

    @Test
    void aSkippedDrawIsRecordedWithoutACard() throws Exception {
        KiloDeMerde game = game(4).hand(A, "KS KH KD KC").pile("2C").build();
        game.slap(A);
        game.slap(B);
        game.closeRace(game.raceId());

        List<KiloDraw> draws = game.lastOutcome().orElseThrow().draws();
        assertEquals(2, draws.size());
        assertEquals(new KiloDraw(C, c("2C"), false, 2), draws.get(0));
        assertEquals(new KiloDraw(D, null, false, 0), draws.get(1));
        assertEquals(0, game.pileSize());
    }
}

package org.kevinkib.cardgames.kilodemerde.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.kilodemerde.domain.event.KiloDeMerdeEvent;
import org.kevinkib.cardgames.kilodemerde.domain.event.ReadyMarked;
import org.kevinkib.cardgames.kilodemerde.domain.event.RoundDealt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeBuilder.A;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeBuilder.B;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeBuilder.C;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeBuilder.D;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeBuilder.game;

/** The ready barrier: every seat confirms before the first round and between two rounds. */
class KiloDeMerdeReadyTest {

    private static KiloDeMerde newGame(int players) {
        return new KiloDeMerde(GameId.generate(), players, new RandomDeckSource(), KiloDeMerdeOptions.defaults());
    }

    @Test
    void aNewGameWaitsForEverySeatBeforeTheFirstRound() throws Exception {
        KiloDeMerde game = newGame(3);
        assertEquals(Phase.AWAITING_START, game.phase());
        assertEquals(List.of(Action.READY), game.availableActions(A));
        assertThrows(WrongPhaseException.class, () -> game.choosePass(A, game.hand(A).get(0)));
        assertThrows(WrongPhaseException.class, () -> game.slap(A));

        game.ready(A);
        game.ready(B);
        assertEquals(Phase.AWAITING_START, game.phase());
        assertTrue(game.isReady(A));
        assertFalse(game.isReady(C));
        assertTrue(game.availableActions(A).isEmpty());

        List<KiloDeMerdeEvent> events = game.ready(C);

        assertEquals(Phase.PASSING, game.phase());
        assertEquals(List.of(new ReadyMarked(C), new RoundDealt(1)), events);
        assertFalse(game.isReady(A));
        assertEquals(List.of(Action.PASS, Action.SLAP), game.availableActions(A));
    }

    @Test
    void confirmingTwiceChangesNothing() throws Exception {
        KiloDeMerde game = newGame(3);
        game.ready(A);
        int version = game.version();

        assertTrue(game.ready(A).isEmpty());

        assertEquals(version, game.version());
        assertEquals(Phase.AWAITING_START, game.phase());
    }

    @Test
    void theNextRoundIsDealtOnlyWhenEverySeatIsReady() throws Exception {
        KiloDeMerde game = game(3).hand(A, "KS KH KD KC").pile("9C 8C 6C 5C").build();
        game.slap(A);
        game.slap(B);
        assertEquals(Phase.ROUND_OVER, game.phase());

        game.ready(A);
        game.ready(B);
        assertEquals(Phase.ROUND_OVER, game.phase());
        assertEquals(1, game.round());
        List<KiloDeMerdeEvent> events = game.ready(C);

        assertEquals(Phase.PASSING, game.phase());
        assertEquals(2, game.round());
        assertEquals(List.of(new ReadyMarked(C), new RoundDealt(2)), events);
        assertFalse(game.isReady(A));
    }

    @Test
    void theLastReadyAfterTheLastRoundEndsTheGame() throws Exception {
        KiloDeMerde game = game(3).hand(A, "KS KH KD KC").pile("9C 8C 6C 5C").roundsPlayed(9).build();
        game.slap(A);
        game.slap(B);

        game.ready(A);
        game.ready(B);
        game.ready(C);

        assertTrue(game.isFinished());
    }

    @Test
    void theTimeoutCountsTheMissingSeatsReady() throws Exception {
        KiloDeMerde game = game(3).hand(A, "KS KH KD KC").build();
        game.slap(A);
        game.slap(B);
        game.ready(B);

        List<KiloDeMerdeEvent> events = game.expireReady();

        assertEquals(List.of(new ReadyMarked(A), new ReadyMarked(C), new RoundDealt(2)), events);
        assertEquals(Phase.PASSING, game.phase());
    }

    @Test
    void readyAndTheTimeoutAreRefusedWhilePlaying() {
        KiloDeMerde game = game(3).build();

        assertThrows(WrongPhaseException.class, () -> game.ready(A));
        assertThrows(WrongPhaseException.class, game::expireReady);
    }

    @Test
    void aForfeitOfTheLastMissingSeatMovesTheTableOn() throws Exception {
        KiloDeMerde game = game(4).hand(A, "KS KH KD KC").build();
        game.slap(A);
        game.slap(B);
        game.closeRace(game.raceId());
        game.ready(A);
        game.ready(B);
        game.ready(C);
        assertEquals(Phase.ROUND_OVER, game.phase());

        game.forfeit(D);

        assertEquals(Phase.PASSING, game.phase());
        assertEquals(2, game.round());
        assertEquals(3, game.activeSeats().size());
    }

    @Test
    void aForfeitBeforeTheFirstRoundReDealsAndKeepsWaiting() throws Exception {
        KiloDeMerde game = newGame(4);
        game.ready(A);

        game.forfeit(D);

        assertEquals(Phase.AWAITING_START, game.phase());
        assertEquals(3, game.activeSeats().size());
        assertTrue(game.isReady(A));
        game.ready(B);
        game.ready(C);
        assertEquals(Phase.PASSING, game.phase());
        assertEquals(4, game.hand(A).size());
    }

    @Test
    void aSeatThatLeftCannotConfirm() throws Exception {
        KiloDeMerde game = newGame(4);
        game.forfeit(D);

        assertThrows(NotActiveSeatException.class, () -> game.ready(D));
    }
}

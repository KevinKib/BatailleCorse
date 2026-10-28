package org.kevinkib.cardgames.blackjack.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.blackjack.domain.event.BlackjackEvent;
import org.kevinkib.cardgames.blackjack.domain.event.BlackjackResult;
import org.kevinkib.cardgames.blackjack.domain.event.Forfeited;
import org.kevinkib.cardgames.blackjack.domain.event.GameFinished;
import org.kevinkib.cardgames.blackjack.domain.event.HandDealt;
import org.kevinkib.cardgames.blackjack.domain.event.HandSettled;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.A;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.B;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.C;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.bets;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.event;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.game;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.has;

class BlackjackForfeitTest {

    // A: 10S 8D (18); B: 10H 6H (16); C: 9D 8H (17); up 6C; hole 4C (10): the dealer draws 5H and 3D: 18.
    private static final String SHOE = "10S 10H 9D 6C 8D 6H 8H 4C 5H 3D";

    // Worked example 14, first half
    @Test
    void aForfeitOnItsTurnEliminatesTheSeatLosesTheBetAndMovesTheTurnOn() throws Exception {
        Blackjack game = game(3).shoe(SHOE).build();
        bets(game, 10, 10, 10);
        game.stand(A);

        game.forfeit(B);

        List<BlackjackEvent> events = game.lastForfeitEvents();
        assertEquals(new Forfeited(B), event(events, Forfeited.class));
        assertTrue(game.seat(B).forfeited());
        assertTrue(game.seat(B).eliminated());
        assertEquals(90, game.seat(B).stack());
        assertTrue(game.seat(B).hands().isEmpty());
        assertEquals(C, game.currentSeat());
        assertEquals(Phase.PLAYING, game.phase());

        List<BlackjackEvent> settle = game.stand(C);
        HandSettled settled = event(settle, HandSettled.class);
        assertEquals(Map.of(A, 0, C, -10), settled.nets());
        assertEquals(100, game.seat(A).stack());
        assertEquals(90, game.seat(C).stack());
        assertEquals(2, game.handNumber());
        assertEquals(C, game.button());
    }

    // Worked example 14, second half
    @Test
    void aForfeitDuringBettingBeforeBettingMeansTheDealNoLongerWaitsForTheSeat() throws Exception {
        Blackjack game = game(3).shoe("10S 10D 10H 6S 7D 8H").build();
        game.bet(B, 10);
        game.bet(C, 10);
        assertEquals(Phase.BETTING, game.phase());

        game.forfeit(A);

        assertEquals(Phase.PLAYING, game.phase());
        assertTrue(has(game.lastForfeitEvents(), HandDealt.class));
        assertEquals(B, game.button());
        assertEquals(B, game.currentSeat());
        assertEquals(100, game.seat(A).stack());
        assertTrue(game.seat(A).forfeited());
    }

    @Test
    void aSeatThatForfeitsAfterBettingLosesItsBet() throws Exception {
        Blackjack game = game(3).build();
        game.bet(B, 10);

        game.forfeit(B);

        assertEquals(90, game.seat(B).stack());
        assertEquals(Phase.BETTING, game.phase());
        assertFalse(game.isFinished());
    }

    @Test
    void ifTheForfeiterWasTheLastActorTheHandIsSettledForTheOthers() throws Exception {
        Blackjack game = game(3).shoe(SHOE).build();
        bets(game, 10, 10, 10);
        game.stand(A);
        game.stand(B);

        game.forfeit(C);

        HandSettled settled = event(game.lastForfeitEvents(), HandSettled.class);
        assertEquals(Map.of(A, 0, B, -10), settled.nets());
        assertEquals(2, game.handNumber());
        assertEquals(Phase.BETTING, game.phase());
    }

    @Test
    void aForfeitThatLeavesOneLivePlayerEndsTheGameAndGivesItsBetBack() throws Exception {
        Blackjack game = game(2).shoe("10S 10D 10H 6S 7D 8H").build();
        bets(game, 10, 10);

        game.forfeit(A);

        assertTrue(game.isFinished());
        BlackjackResult result = event(game.lastForfeitEvents(), GameFinished.class).result();
        assertEquals(BlackjackResult.Reason.FORFEIT, result.reason());
        assertEquals(Map.of(B, 1, A, 2), result.places());
        assertEquals(Map.of(A, 90, B, 100), result.stacks());
    }

    @Test
    void forfeiteringASoloGameEndsItWithoutPlaces() {
        Blackjack game = game(1).build();

        game.forfeit(A);

        assertTrue(game.isFinished());
        BlackjackResult result = game.result().orElseThrow();
        assertEquals(BlackjackResult.Reason.FORFEIT, result.reason());
        assertEquals(Map.of(), result.places());
    }

    @Test
    void forfeitersRankBelowEveryEliminatedPlayerAndLivePlayer() throws Exception {
        // Hand 1: A (16) loses its last 10 and is out; B pushes, C wins. Hand 2: B forfeits during betting.
        Blackjack game = game(3).stacks(10, 100, 100).shoe("10S 10D 10C 10H 6S 8D 9C 8H").build();
        bets(game, 10, 10, 10);
        game.stand(A);
        game.stand(B);
        game.stand(C);
        assertTrue(game.seat(A).eliminated());

        game.forfeit(B);

        BlackjackResult result = game.result().orElseThrow();
        assertEquals(BlackjackResult.Reason.FORFEIT, result.reason());
        assertEquals(Map.of(C, 1, A, 2, B, 3), result.places());
    }

    @Test
    void forfeitingAnEliminatedOrFinishedSeatChangesNothing() throws Exception {
        Blackjack game = game(3).stacks(100, 0, 100).build();
        int version = game.version();

        game.forfeit(B);

        assertEquals(version, game.version());
        assertEquals(List.of(), game.lastForfeitEvents());
        assertFalse(game.seat(B).forfeited());

        Blackjack solo = game(1).build();
        solo.forfeit(A);
        int soloVersion = solo.version();
        solo.forfeit(A);
        assertEquals(soloVersion, solo.version());
        assertEquals(List.of(), solo.lastForfeitEvents());
    }

    @Test
    void anAcceptedForfeitBumpsTheVersion() {
        Blackjack game = game(3).build();
        int version = game.version();
        game.forfeit(C);
        assertEquals(version + 1, game.version());
    }
}

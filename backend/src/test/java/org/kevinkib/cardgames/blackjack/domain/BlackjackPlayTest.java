package org.kevinkib.cardgames.blackjack.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kevinkib.cardgames.blackjack.domain.event.BlackjackEvent;
import org.kevinkib.cardgames.blackjack.domain.event.Busted;
import org.kevinkib.cardgames.blackjack.domain.event.CardDrawn;
import org.kevinkib.cardgames.blackjack.domain.event.DealerPlayed;
import org.kevinkib.cardgames.blackjack.domain.event.HandSettled;
import org.kevinkib.cardgames.blackjack.domain.event.Stood;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.A;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.B;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.C;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.bets;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.event;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.game;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackBuilder.has;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackCards.c;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackCards.cards;

class BlackjackPlayTest {

    // A: 5S 6D (11); B: 10H 6H (16); C: 9D 8H (17); up 6C; hole 4C (10). Then A hits 10S, B hits 10D, the dealer
    // draws 5H and 3D.
    private static final String SHOE = "5S 10H 9D 6C 6D 6H 8H 4C 10S 10D 5H 3D";

    // Worked example 8
    @Test
    void aHandReachingTwentyOneStandsByItselfAndTheTurnMovesOn() throws Exception {
        Blackjack game = game(3).shoe(SHOE).build();
        bets(game, 10, 10, 10);

        List<BlackjackEvent> events = game.hit(A);

        assertEquals(new CardDrawn(A, 0, c("10S"), 21), event(events, CardDrawn.class));
        assertEquals(new Stood(A, 0), event(events, Stood.class));
        assertEquals(PlayerHand.Status.STOOD, game.seat(A).hands().get(0).status());
        assertEquals(B, game.currentSeat());
    }

    @Test
    void aBustedHandEndsTheTurnAndLosesItsBetAtTheSettlement() throws Exception {
        Blackjack game = game(3).shoe(SHOE).build();
        bets(game, 10, 10, 10);
        game.hit(A);

        List<BlackjackEvent> events = game.hit(B);

        assertEquals(new CardDrawn(B, 0, c("10D"), 26), event(events, CardDrawn.class));
        assertEquals(new Busted(B, 0), event(events, Busted.class));
        assertEquals(PlayerHand.Status.BUST, game.seat(B).hands().get(0).status());
        assertEquals(C, game.currentSeat());
    }

    @Test
    void afterTheLastSeatTheDealerDrawsToSeventeenAndTheHandIsSettled() throws Exception {
        Blackjack game = game(3).shoe(SHOE).build();
        bets(game, 10, 10, 10);
        game.hit(A);
        game.hit(B);

        List<BlackjackEvent> events = game.stand(C);

        DealerPlayed played = event(events, DealerPlayed.class);
        assertEquals(c("4C"), played.holeCard());
        assertEquals(cards("5H 3D"), played.drawn());
        assertEquals(18, played.value());
        HandSettled settled = event(events, HandSettled.class);
        assertEquals(10, settled.nets().get(A));
        assertEquals(-10, settled.nets().get(B));
        assertEquals(-10, settled.nets().get(C));
        assertEquals(List.of(A), settled.bestSeats());
        assertEquals(110, settled.stacks().get(A));
        assertEquals(90, settled.stacks().get(B));
        assertEquals(Phase.BETTING, game.phase());
        assertEquals(cards("6C 4C 5H 3D"), game.lastHand().dealerCards());
    }

    @Test
    void theDealerDrawsNothingWhenEveryHandIsBustedOrABlackjack() throws Exception {
        // A: AS KS (blackjack); B: 10H 6C; C: 10D 5C; up 6D; hole 5D (11). B and C hit a 10 and bust.
        Blackjack game = game(3).shoe("AS 10H 10D 6D KS 6C 5C 5D 10S 10C").build();
        bets(game, 10, 10, 10);
        assertEquals(B, game.currentSeat());
        game.hit(B);
        List<BlackjackEvent> events = game.hit(C);

        DealerPlayed played = event(events, DealerPlayed.class);
        assertEquals(List.of(), played.drawn());
        assertEquals(11, played.value());
        assertEquals(cards("6D 5D"), game.lastHand().dealerCards());
        assertEquals(15, game.lastHand().nets().get(A));
        assertEquals(-10, game.lastHand().nets().get(B));
        assertEquals(-10, game.lastHand().nets().get(C));
    }

    // Worked example 9
    @ParameterizedTest
    @CsvSource({"6H AS,'',17,10", "6H 10C,5D,21,-10"})
    void theDealerStandsOnASoftSeventeenAndDrawsOnSixteen(String upAndHole, String drawn, int total, int net)
            throws Exception {
        String[] cards = upAndHole.split(" ");
        // Solo: the seat holds 10S 9D (19), so a dealer standing on a soft 17 loses and one reaching 21 wins.
        Blackjack game = game(1).shoe("10S " + cards[0] + " 9D " + cards[1] + " " + drawn).build();
        bets(game, 10);

        List<BlackjackEvent> events = game.stand(A);

        assertEquals(total, event(events, DealerPlayed.class).value());
        assertEquals(drawn.isBlank() ? 0 : 1, event(events, DealerPlayed.class).drawn().size());
        assertEquals(net, event(events, HandSettled.class).nets().get(A));
    }

    @Test
    void aSeatCannotActOutOfTurn() throws Exception {
        Blackjack game = game(3).shoe(SHOE).build();
        bets(game, 10, 10, 10);
        int version = game.version();

        assertThrows(NotPlayersTurnException.class, () -> game.hit(B));
        assertThrows(NotPlayersTurnException.class, () -> game.stand(C));

        assertEquals(version, game.version());
        assertEquals(A, game.currentSeat());
        assertEquals(2, game.seat(B).hands().get(0).cards().size());
    }

    @Test
    void playCommandsAreRefusedWhileBetting() {
        Blackjack game = game(2).build();
        assertThrows(WrongPhaseException.class, () -> game.hit(A));
        assertThrows(WrongPhaseException.class, () -> game.stand(A));
        assertThrows(WrongPhaseException.class, () -> game.doubleDown(A));
        assertThrows(WrongPhaseException.class, () -> game.split(A));
    }

    @Test
    void anEliminatedSeatCannotPlay() throws Exception {
        Blackjack game = game(3).stacks(100, 0, 100).shoe(SHOE).build();
        bets(game, 10);
        game.bet(C, 10);
        assertThrows(EliminatedSeatException.class, () -> game.hit(B));
    }

    @Test
    void everyAcceptedActionBumpsTheVersion() throws Exception {
        Blackjack game = game(3).shoe(SHOE).build();
        bets(game, 10, 10, 10);
        int version = game.version();
        game.hit(A);
        assertEquals(version + 1, game.version());
        game.stand(B);
        assertEquals(version + 2, game.version());
    }

    @Test
    void availableActionsListHitAndStandOnTheTurnOfTheSeatOnly() throws Exception {
        Blackjack game = game(3).shoe(SHOE).build();
        assertEquals(List.of(Action.BET), game.availableActions(A));
        bets(game, 10, 10, 10);

        assertEquals(List.of(Action.HIT, Action.STAND, Action.DOUBLE), game.availableActions(A));
        assertEquals(List.of(), game.availableActions(B));
    }

    @Test
    void aSeatThatAlreadyBetHasNoActionWhileOthersAreBetting() throws Exception {
        Blackjack game = game(2).build();
        game.bet(A, 10);
        assertEquals(List.of(), game.availableActions(A));
        assertEquals(List.of(Action.BET), game.availableActions(B));
    }

    @Test
    void aHitOnAThreeCardHandKeepsTheSeatOnTurn() throws Exception {
        // Solo A: 2S 3D, up 6H, hole 10C; hits 4H (9) and stays on turn.
        Blackjack game = game(1).shoe("2S 6H 3D 10C 4H").build();
        bets(game, 10);

        List<BlackjackEvent> events = game.hit(A);

        assertEquals(9, event(events, CardDrawn.class).value());
        assertFalse(has(events, Stood.class));
        assertEquals(A, game.currentSeat());
        assertEquals(List.of(Action.HIT, Action.STAND), game.availableActions(A));
    }
}

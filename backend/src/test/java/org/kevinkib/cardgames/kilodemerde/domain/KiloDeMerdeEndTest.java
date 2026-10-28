package org.kevinkib.cardgames.kilodemerde.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kilodemerde.domain.event.GameFinished;
import org.kevinkib.cardgames.kilodemerde.domain.event.KiloDeMerdeEvent;
import org.kevinkib.cardgames.kilodemerde.domain.event.RoundDealt;
import org.kevinkib.cards.domain.Card;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloCards.c;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloCards.cards;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeBuilder.A;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeBuilder.B;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeBuilder.C;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeBuilder.D;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeBuilder.game;

class KiloDeMerdeEndTest {

    @Test
    void aNewGameDealsFourCardsFromTheHandDeckAndKeepsTheRestAsTheKiloPile() {
        for (int players = 3; players <= 10; players++) {
            KiloDeMerde game = new KiloDeMerde(GameId.generate(), players, new RandomDeckSource(),
                    KiloDeMerdeOptions.defaults());

            assertEquals(Phase.PASSING, game.phase());
            assertEquals(1, game.round());
            assertEquals(1, game.step());
            assertEquals(10, game.roundsTotal());
            assertEquals(KiloDeck.kiloPile(players).size(), game.pileSize());
            Set<Card> dealt = new HashSet<>();
            for (PlayerId seat : game.activeSeats()) {
                assertEquals(4, game.hand(seat).size());
                dealt.addAll(game.hand(seat));
                assertEquals(0, game.kilos(seat));
            }
            assertEquals(new HashSet<>(KiloDeck.handDeck(players)), dealt);
        }
    }

    @Test
    void theHandDeckIsShuffledThroughTheSeamAndDealtFourByFour() {
        RecordingDeckSource source = new RecordingDeckSource();

        KiloDeMerde game = new KiloDeMerde(GameId.generate(), 3, source, KiloDeMerdeOptions.defaults());

        assertEquals(List.of(KiloDeck.handDeck(3)), source.handDeckShuffles);
        assertEquals(List.of(KiloDeck.kiloPile(3)), source.pileShuffles);
        assertEquals(KiloDeck.handDeck(3).subList(0, 4), game.hand(A));
        assertEquals(KiloDeck.handDeck(3).subList(8, 12), game.hand(C));
    }

    @Test
    void nextRoundDealsAFreshHandDeckKeepingThePileAndTheKilos() throws Exception {
        KiloDeMerde game = game(3).hand(A, "KS KH KD KC").pile("9C 8C 6C 5C").build();
        game.slap(A);
        game.slap(B);
        assertEquals(Phase.ROUND_OVER, game.phase());
        assertEquals(9, game.kilos(C));

        List<KiloDeMerdeEvent> events = game.nextRound();

        assertEquals(Phase.PASSING, game.phase());
        assertEquals(2, game.round());
        assertEquals(1, game.step());
        assertEquals(3, game.pileSize());
        assertEquals(9, game.kilos(C));
        assertTrue(game.lastOutcome().isEmpty());
        assertEquals(List.of(new RoundDealt(2)), events);
        for (PlayerId seat : game.activeSeats()) {
            assertEquals(4, game.hand(seat).size());
            assertFalse(game.hasChosen(seat));
            assertTrue(game.lastReceived(seat).isEmpty());
        }
    }

    @Test
    void theGameEndsAfterTheChosenNumberOfRoundsAndSharesPlaces() throws Exception {
        KiloDeMerde game = game(4).rounds(KiloDeMerdeOptions.Rounds.FIVE).roundsPlayed(4).step(40)
                .kiloCards(B, "KS JC").kiloCards(D, "AH KS").build();
        for (PlayerId seat : game.activeSeats()) {
            game.choosePass(seat, game.hand(seat).get(0));
        }
        assertEquals(Phase.ROUND_OVER, game.phase());
        assertEquals(5, game.roundsPlayed());

        List<KiloDeMerdeEvent> events = game.nextRound();

        assertEquals(Phase.FINISHED, game.phase());
        assertTrue(game.isFinished());
        KiloDeMerdeResult result = game.result().orElseThrow();
        assertEquals(KiloDeMerdeResult.Reason.ROUNDS_PLAYED, result.reason());
        assertEquals(Map.of(A, 1, B, 3, C, 1, D, 4), result.places());
        assertEquals(Map.of(A, 0, B, 24, C, 0, D, 113), result.kilos());
        assertEquals(List.of(new GameFinished(result)), events);
        assertThrows(FinishedGameException.class, game::nextRound);
        assertThrows(FinishedGameException.class, () -> game.slap(A));
        assertThrows(FinishedGameException.class, () -> game.choosePass(A, c("JS")));
    }

    @Test
    void theGameEndsWhenTheKiloPileIsEmptyAtTheEndOfARound() throws Exception {
        KiloDeMerde game = game(4).hand(A, "KS KH KD KC").pile("2C").build();
        game.slap(A);
        game.slap(B);
        game.slap(D);
        assertEquals(2, game.kilos(C));
        assertEquals(0, game.pileSize());

        game.nextRound();

        assertEquals(Phase.FINISHED, game.phase());
        KiloDeMerdeResult result = game.result().orElseThrow();
        assertEquals(KiloDeMerdeResult.Reason.PILE_EMPTY, result.reason());
        assertEquals(Map.of(A, 1, B, 1, D, 1, C, 4), result.places());
    }

    @Test
    void theRoundsPlayedAndTheRoundNumberTrackTheStalledAndRacedRounds() throws Exception {
        KiloDeMerde game = game(3).rounds(KiloDeMerdeOptions.Rounds.FIVE).hand(A, "KS KH KD KC").build();

        game.slap(B);
        game.slap(A);
        assertEquals(1, game.roundsPlayed());
        assertEquals(1, game.round());
        game.nextRound();
        assertEquals(2, game.round());
        assertEquals(1, game.roundsPlayed());
    }

    @Test
    void aFalseSlapOnAnEmptyPileDrawsNothingAndEndsTheGame() throws Exception {
        KiloDeMerde game = game(3).pile("").build();

        game.slap(A);

        assertEquals(List.of(new KiloDraw(A, null, false, 0)), game.lastOutcome().orElseThrow().draws());
        game.nextRound();
        assertEquals(KiloDeMerdeResult.Reason.PILE_EMPTY, game.result().orElseThrow().reason());
        assertEquals(cards(""), game.kiloCards(A));
    }
}

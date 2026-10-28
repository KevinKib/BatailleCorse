package org.kevinkib.cardgames.kilodemerde.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kilodemerde.domain.event.Forfeited;
import org.kevinkib.cardgames.kilodemerde.domain.event.GameFinished;
import org.kevinkib.cardgames.kilodemerde.domain.event.RoundDealt;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloCards.c;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeBuilder.A;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeBuilder.B;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeBuilder.C;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeBuilder.D;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeBuilder.game;

/** Spec example 13 and A14: a forfeit voids the round and re-forms the table for one player fewer. */
class KiloDeMerdeForfeitTest {

    @Test
    void example13_theTableReformsForOnePlayerFewerThenEndsBelowThreePlayers() throws Exception {
        RecordingDeckSource source = new RecordingDeckSource();
        KiloDeMerde game = game(4).kiloCards(B, "KS JC").kiloCards(D, "AH").deckSource(source).build();
        game.choosePass(A, c("JS"));
        int pileBefore = game.pileSize();
        int version = game.version();

        game.forfeit(B);

        assertEquals(List.of(A, C, D), game.activeSeats());
        assertEquals(Phase.PASSING, game.phase());
        assertEquals(1, game.round());
        assertEquals(0, game.roundsPlayed());
        assertEquals(1, game.step());
        assertTrue(game.version() > version);
        assertEquals(0, game.kilos(B));
        assertTrue(game.hand(B).isEmpty());
        // the 2s (no longer dealt) and B's two kilo cards joined the pile, which was shuffled through the seam
        assertEquals(pileBefore + 4 + 2, game.pileSize());
        Set<Card> shuffled = new HashSet<>(source.pileShuffles.get(source.pileShuffles.size() - 1));
        assertTrue(shuffled.containsAll(List.of(c("2C"), c("2D"), c("2H"), c("2S"), c("KS"), c("JC"))));
        // a new round was dealt: J, Q, K to A, C and D, and nothing chosen
        Set<Card> dealt = new HashSet<>();
        for (PlayerId seat : game.activeSeats()) {
            assertEquals(4, game.hand(seat).size());
            assertFalse(game.hasChosen(seat));
            game.hand(seat).forEach(card -> assertFalse(card.getRank() == FrenchRank.TWO));
            dealt.addAll(game.hand(seat));
        }
        assertEquals(new HashSet<>(KiloDeck.handDeck(3)), dealt);
        assertEquals(List.of(new Forfeited(B), new RoundDealt(1)), game.lastForfeitEvents());

        game.forfeit(C);

        assertEquals(Phase.FINISHED, game.phase());
        KiloDeMerdeResult result = game.result().orElseThrow();
        assertEquals(KiloDeMerdeResult.Reason.FORFEIT, result.reason());
        assertEquals(Map.of(A, 1, D, 2, C, 3, B, 4), result.places());
        assertEquals(100, result.kilos().get(D));
        assertEquals(24, result.kilos().get(B));
        assertEquals(List.of(new Forfeited(C), new GameFinished(result)), game.lastForfeitEvents());
    }

    @Test
    void aForfeitDuringARaceVoidsTheRaceToo() throws Exception {
        KiloDeMerde game = game(4).hand(A, "KS KH KD KC").build();
        game.slap(A);

        game.forfeit(D);

        assertEquals(Phase.PASSING, game.phase());
        assertTrue(game.slapOrder().isEmpty());
        assertEquals(List.of(A, B, C), game.activeSeats());
        assertEquals(0, game.roundsPlayed());
    }

    @Test
    void aForfeitBetweenRoundsKeepsTheRevealAndRedealsOnNextRoundForTheSmallerTable() throws Exception {
        KiloDeMerde game = game(4).hand(A, "KS KH KD KC").build();
        game.slap(A);
        game.slap(B);
        game.slap(C);
        assertEquals(Phase.ROUND_OVER, game.phase());

        game.forfeit(D);

        assertEquals(Phase.ROUND_OVER, game.phase());
        assertEquals(1, game.roundsPlayed());
        assertTrue(game.lastOutcome().isPresent());
        game.nextRound();
        assertEquals(Phase.PASSING, game.phase());
        assertEquals(3, game.activeSeats().size());
        Set<Card> dealt = new HashSet<>();
        game.activeSeats().forEach(seat -> dealt.addAll(game.hand(seat)));
        assertEquals(new HashSet<>(KiloDeck.handDeck(3)), dealt);
    }

    @Test
    void aSeatThatIsAlreadyOutOrAFinishedGameChangeNothing() {
        KiloDeMerde game = game(4).build();
        game.forfeit(B);
        KiloSnapshot before = KiloSnapshot.of(game);

        game.forfeit(B);
        game.forfeit(new PlayerId(9));

        assertEquals(before, KiloSnapshot.of(game));
        assertTrue(game.lastForfeitEvents().isEmpty());
    }
}

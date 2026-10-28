package org.kevinkib.cardgames.ninetynine.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.ninetynine.domain.event.NinetyNineEvent;
import org.kevinkib.cardgames.ninetynine.domain.event.RoundDealt;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.A;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.B;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.C;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.c;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.cards;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.game;

class NinetyNineRoundTest {

    private static NinetyNine newGame(int seats, ScriptedDeckSource source, GameId id) {
        return new NinetyNine(id, seats, source, NinetyNineOptions.defaults());
    }

    @Test
    void roundOneDealsThreeCardsToEverySeatFromTheScriptedDeck() {
        ScriptedDeckSource source = new ScriptedDeckSource(new RandomDeckSource());
        GameId id = GameId.generate();
        source.fix(id, 1, cards("2H 3H 4H 5H 6H 7H 8H 9H 10H"));
        source.fixFirstSeat(id, 2);

        NinetyNine game = newGame(3, source, id);

        assertEquals(cards("2H 5H 8H"), game.hand(A));
        assertEquals(cards("3H 6H 9H"), game.hand(B));
        assertEquals(cards("4H 7H 10H"), game.hand(C));
        assertEquals(43, game.stockSize());
        assertEquals(0, game.total());
        assertEquals(1, game.round());
        assertEquals(Phase.PLAYING, game.phase());
        assertEquals(Direction.CLOCKWISE, game.direction());
        assertEquals(List.of(), game.pileSinceReshuffle());
        assertTrue(game.pileTop().isEmpty());
    }

    @Test
    void roundOneStartsWithTheSeatDrawnByTheSource() {
        ScriptedDeckSource source = new ScriptedDeckSource(new RandomDeckSource());
        GameId id = GameId.generate();
        source.fixFirstSeat(id, 1);

        assertEquals(B, newGame(3, source, id).currentSeat());
    }

    @Test
    void everySeatStartsWithTheConfiguredLives() {
        ScriptedDeckSource source = new ScriptedDeckSource(new RandomDeckSource());
        NinetyNine game = new NinetyNine(GameId.generate(), 4, source,
                new NinetyNineOptions(NinetyNineOptions.Lives.FOUR, NinetyNineOptions.QuickMode.OFF));

        assertEquals(4, game.lives().size());
        game.lives().values().forEach(lives -> assertEquals(4, lives));
        assertEquals(4, game.getPlayerIds().size());
    }

    @Test
    void aTableOfTwelveDealsThirtySixCardsAndKeepsSixteenInTheStock() {
        NinetyNine game = newGame(12, new ScriptedDeckSource(new RandomDeckSource()), GameId.generate());

        assertEquals(16, game.stockSize());
        game.handSizes().values().forEach(size -> assertEquals(3, size));
    }

    @Test
    void theNumberOfPlayersIsBetweenTwoAndTwelve() {
        ScriptedDeckSource source = new ScriptedDeckSource(new RandomDeckSource());
        assertThrows(IllegalArgumentException.class, () -> newGame(1, source, GameId.generate()));
        assertThrows(IllegalArgumentException.class, () -> newGame(13, source, GameId.generate()));
    }

    @Test
    void theLoserStartsTheNextRoundWithADirectionResetAndAFreshDeal() throws NinetyNineException {
        NinetyNine game = game(3).total(95).direction(Direction.COUNTER_CLOCKWISE).current(C)
                .hand(C, "9H 2H 3H").hand(B, "9S 2S 3S").hand(A, "7H 8H JH").build();
        // counter-clockwise from C is B: B plays a 9, then A is next and stuck
        game.play(C, c("9H"), null);
        game.play(B, c("9S"), null);
        game.play(A, c("7H"), null);
        assertEquals(Phase.ROUND_OVER, game.phase());

        List<NinetyNineEvent> events = game.nextRound();

        assertEquals(List.of(new RoundDealt(2, A)), events);
        assertEquals(A, game.currentSeat());
        assertEquals(Phase.PLAYING, game.phase());
        assertEquals(2, game.round());
        assertEquals(0, game.total());
        assertEquals(Direction.CLOCKWISE, game.direction());
        assertEquals(43, game.stockSize());
        game.handSizes().values().forEach(size -> assertEquals(3, size));
        assertTrue(game.pileTop().isEmpty());
        assertTrue(game.lastBust().isEmpty());
    }

    @Test
    void nextRoundDealsOnlyToLiveSeatsAndStartsWithTheNextLiveSeatAfterAnElimination() throws NinetyNineException {
        NinetyNine game = game(3).total(95).current(B).lives(C, 1).hand(B, "9H 2H 3H").hand(C, "7H 8H JH").build();
        game.play(B, c("9H"), null);
        game.play(C, c("7H"), null);
        assertEquals(Phase.ROUND_OVER, game.phase());

        game.nextRound();

        assertEquals(A, game.currentSeat());
        assertEquals(0, game.handSizes().get(C));
        assertEquals(3, game.handSizes().get(A));
        assertEquals(3, game.handSizes().get(B));
        assertEquals(46, game.stockSize());
    }

    @Test
    void nextRoundIsRefusedOutsideRoundOver() {
        NinetyNine game = game(3).build();
        int version = game.version();

        assertThrows(WrongPhaseException.class, game::nextRound);
        assertEquals(version, game.version());
    }

    @Test
    void nextRoundBumpsTheVersion() throws NinetyNineException {
        NinetyNine game = game(3).total(95).current(C).hand(C, "9H 2H 3H").hand(A, "7H 8H JH").build();
        game.play(C, c("9H"), null);
        game.play(A, c("7H"), null);
        int version = game.version();

        game.nextRound();

        assertEquals(version + 1, game.version());
    }

    @Test
    void nextRoundUsesTheSourceForTheDeckOfTheNewRound() throws NinetyNineException {
        ScriptedDeckSource source = new ScriptedDeckSource(new RandomDeckSource());
        NinetyNine game = game(3).deckSource(source).total(95).current(C).hand(C, "9H 2H 3H")
                .hand(A, "7H 8H JH").build();
        source.fix(game.getId(), 2, cards("KH QH JH 10H 9H 8H 7H 6H 5H"));
        game.play(C, c("9H"), null);
        game.play(A, c("7H"), null);

        game.nextRound();

        assertEquals(cards("KH 10H 7H"), game.hand(A));
        assertEquals(cards("QH 9H 6H"), game.hand(B));
        assertEquals(cards("JH 8H 5H"), game.hand(C));
    }

    @Test
    void aWholeRandomRoundNeverLeavesTheRulesWhateverIsPlayed() throws NinetyNineException {
        NinetyNine game = new NinetyNine(GameId.generate(), 5, new RandomDeckSource(), NinetyNineOptions.defaults());
        int guard = 0;
        while (!game.isFinished() && guard++ < 5000) {
            if (game.phase() == Phase.ROUND_OVER) {
                game.nextRound();
                continue;
            }
            PlayerId seat = game.currentSeat();
            boolean stuck = game.isStuck();
            var card = stuck ? game.hand(seat).get(0) : game.safeCards(seat).get(0);
            Integer ace = card.getRank() == FrenchRank.ACE ? 1 : null;
            game.play(seat, card, ace);
            // only a stuck seat takes the total over 99, and that ends the round
            assertTrue(game.total() >= 0 && (stuck || game.total() <= 99));
            assertTrue(!stuck || game.phase() != Phase.PLAYING);
        }
        assertTrue(game.isFinished());
    }

    @Test
    void aDealThatFailsLeavesTheRoundOverUntouchedSoItCanBeRetried() throws NinetyNineException {
        boolean[] failing = {true};
        DeckSource flaky = new ScriptedDeckSource(new RandomDeckSource()) {
            @Override
            public List<org.kevinkib.cards.domain.Card> shuffledDeck(org.kevinkib.cardgames.game.GameId id, int round) {
                if (failing[0]) {
                    throw new IllegalStateException("deck unavailable");
                }
                return super.shuffledDeck(id, round);
            }
        };
        failing[0] = false;
        NinetyNine game = game(3).deckSource(flaky).total(95).current(C).hand(C, "9H 2H 3H").hand(A, "7H 8H JH")
                .build();
        game.play(C, c("9H"), null);
        game.play(A, c("7H"), null);
        int round = game.round();
        int version = game.version();
        failing[0] = true;

        assertThrows(IllegalStateException.class, game::nextRound);

        assertEquals(Phase.ROUND_OVER, game.phase());
        assertEquals(round, game.round());
        assertEquals(version, game.version());
        failing[0] = false;
        game.nextRound();
        assertEquals(round + 1, game.round());
        assertEquals(Phase.PLAYING, game.phase());
    }
}

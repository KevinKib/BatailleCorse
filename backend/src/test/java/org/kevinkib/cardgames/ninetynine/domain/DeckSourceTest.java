package org.kevinkib.cardgames.ninetynine.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cards.domain.Card;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineBuilder.cards;

class DeckSourceTest {

    private final GameId id = GameId.generate();

    @Test
    void randomDeckHoldsFiftyTwoDistinctCards() {
        List<Card> deck = new RandomDeckSource().shuffledDeck(id, 1);

        assertEquals(52, deck.size());
        assertEquals(52, new HashSet<>(deck).size());
    }

    @Test
    void randomReshuffleKeepsTheSameCards() {
        List<Card> pile = cards("2H 3H 4H 5H 6H 7H");

        List<Card> reshuffled = new RandomDeckSource().reshuffle(id, pile);

        assertEquals(new HashSet<>(pile), new HashSet<>(reshuffled));
        assertEquals(6, reshuffled.size());
    }

    @Test
    void randomFirstSeatIsInRange() {
        RandomDeckSource source = new RandomDeckSource();
        for (int i = 0; i < 200; i++) {
            int seat = source.firstSeat(id, 5);
            assertTrue(seat >= 0 && seat < 5);
        }
    }

    @Test
    void scriptedDeckPutsTheFixedCardsFirstThenTheRestOnce() {
        ScriptedDeckSource source = new ScriptedDeckSource(new RandomDeckSource());
        source.fix(id, 2, cards("KH AS"));

        List<Card> deck = source.shuffledDeck(id, 2);

        assertEquals(cards("KH AS"), deck.subList(0, 2));
        assertEquals(52, new HashSet<>(deck).size());
        assertEquals(52, source.shuffledDeck(id, 1).size());
    }

    @Test
    void scriptedDeckRefusesARepeatedCardOrARoundBelowOne() {
        ScriptedDeckSource source = new ScriptedDeckSource(new RandomDeckSource());

        assertThrows(IllegalArgumentException.class, () -> source.fix(id, 1, cards("KH KH")));
        assertThrows(IllegalArgumentException.class, () -> source.fix(id, 0, cards("KH")));
    }

    @Test
    void scriptedFirstSeatAndReshuffleAreUsedOnceThenTheDelegateTakesOver() {
        ScriptedDeckSource source = new ScriptedDeckSource(new RandomDeckSource());
        source.fixFirstSeat(id, 3);
        source.fixReshuffle(id, cards("7H 2H"));

        assertEquals(3, source.firstSeat(id, 4));
        assertEquals(cards("7H 2H 5H 6H").subList(0, 2), source.reshuffle(id, cards("2H 5H 6H 7H")).subList(0, 2));
        assertEquals(4, source.reshuffle(id, cards("2H 5H 6H 7H")).size());
    }

    @Test
    void clearForgetsEverythingFixedForAGame() {
        ScriptedDeckSource source = new ScriptedDeckSource(new RandomDeckSource());
        source.fix(id, 1, cards("KH"));
        source.fixFirstSeat(id, 1);

        source.clear(id);

        assertEquals(52, source.shuffledDeck(id, 1).size());
    }
}

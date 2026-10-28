package org.kevinkib.cardgames.kilodemerde.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cards.domain.Card;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloCards.cards;

class DeckSourceTest {

    @Test
    void theRandomSourceKeepsTheSameCardsInAnotherOrder() {
        RandomDeckSource source = new RandomDeckSource();
        List<Card> deck = KiloDeck.handDeck(10);

        List<Card> shuffled = source.shuffleHandDeck(deck);
        List<Card> pile = source.shufflePile(KiloDeck.kiloPile(10));

        assertEquals(new HashSet<>(deck), new HashSet<>(shuffled));
        assertEquals(deck.size(), shuffled.size());
        assertNotEquals(deck, shuffled);
        assertEquals(new HashSet<>(KiloDeck.kiloPile(10)), new HashSet<>(pile));
    }

    @Test
    void aFixedDealIsUsedOnceForOneGameThenTheDelegateTakesOver() {
        ScriptedDeckSource source = new ScriptedDeckSource(new RecordingDeckSource());
        GameId id = GameId.generate();
        List<Card> handDeck = KiloDeck.handDeck(3);
        source.fixDeal(id, List.of(cards("KS KH KD QC"), cards("JS JH QD QH"), cards("KC JD JC QS")),
                cards("AS"));

        List<Card> hands = source.shuffleHandDeck(id, handDeck);
        List<Card> pile = source.shufflePile(id, KiloDeck.kiloPile(3));

        assertEquals(cards("KS KH KD QC JS JH QD QH KC JD JC QS"), hands);
        assertEquals(cards("AS"), pile);
        assertEquals(handDeck, source.shuffleHandDeck(id, handDeck));
        assertEquals(KiloDeck.kiloPile(3), source.shufflePile(id, KiloDeck.kiloPile(3)));
    }

    @Test
    void aFixedDealOfOneGameDoesNotTouchAnother() {
        ScriptedDeckSource source = new ScriptedDeckSource(new RecordingDeckSource());
        GameId scripted = GameId.generate();
        source.fixDeal(scripted, List.of(cards("KS KH KD QC"), cards("JS JH QD QH"), cards("KC JD JC QS")),
                cards("AS"));

        assertEquals(KiloDeck.handDeck(3), source.shuffleHandDeck(GameId.generate(), KiloDeck.handDeck(3)));
        assertEquals(KiloDeck.handDeck(3), source.shuffleHandDeck(KiloDeck.handDeck(3)));
    }

    @Test
    void handsMayBeShortAndTheRestOfTheHandDeckFollows() {
        ScriptedDeckSource source = new ScriptedDeckSource(new RecordingDeckSource());
        GameId id = GameId.generate();
        source.fixDeal(id, List.of(cards("KS KH KD QC")), List.of());

        List<Card> hands = source.shuffleHandDeck(id, KiloDeck.handDeck(3));

        assertEquals(cards("KS KH KD QC"), hands.subList(0, 4));
        assertEquals(12, hands.size());
        assertEquals(new HashSet<>(KiloDeck.handDeck(3)), new HashSet<>(hands));
        assertEquals(KiloDeck.kiloPile(3).size(), source.shufflePile(id, KiloDeck.kiloPile(3)).size());
    }

    @Test
    void aFixedCardOutsideTheDeckIsRefused() {
        ScriptedDeckSource source = new ScriptedDeckSource(new RecordingDeckSource());
        GameId id = GameId.generate();
        source.fixDeal(id, List.of(cards("AS 2S 3S 4S")), cards("KS"));

        assertThrows(IllegalArgumentException.class, () -> source.shuffleHandDeck(id, KiloDeck.handDeck(3)));
    }

    @Test
    void aRepeatedFixedCardIsRefused() {
        ScriptedDeckSource source = new ScriptedDeckSource(new RecordingDeckSource());

        assertThrows(IllegalArgumentException.class,
                () -> source.fixDeal(GameId.generate(), List.of(cards("KS KS KD QC")), cards("AS")));
        assertThrows(IllegalArgumentException.class,
                () -> source.fixDeal(GameId.generate(), List.of(cards("KS KH KD QC")), cards("KS")));
    }

    @Test
    void clearForgetsTheFixedDeal() {
        ScriptedDeckSource source = new ScriptedDeckSource(new RecordingDeckSource());
        GameId id = GameId.generate();
        source.fixDeal(id, List.of(cards("KS KH KD QC")), cards("AS"));

        source.clear(id);

        assertEquals(KiloDeck.handDeck(3), source.shuffleHandDeck(id, KiloDeck.handDeck(3)));
    }
}

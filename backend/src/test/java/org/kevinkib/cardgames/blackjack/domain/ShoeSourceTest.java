package org.kevinkib.cardgames.blackjack.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cards.domain.Card;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackCards.c;
import static org.kevinkib.cardgames.blackjack.domain.BlackjackCards.cards;

class ShoeSourceTest {

    private static void assertSixDecks(List<Card> shoe) {
        assertEquals(312, shoe.size());
        Map<Card, Long> counts = shoe.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertEquals(52, counts.size());
        counts.values().forEach(count -> assertEquals(6L, count));
    }

    @Test
    void theOrderedShoeIsSixFrenchDecksWithoutJokers() {
        assertSixDecks(Shoe.sixDecks());
    }

    @Test
    void theRandomSourceShufflesSixDecksAFreshWayEveryTime() {
        RandomShoeSource source = new RandomShoeSource();
        List<Card> first = source.shuffledShoe(GameId.generate(), 1);
        List<Card> second = source.shuffledShoe(GameId.generate(), 1);
        assertSixDecks(first);
        assertSixDecks(second);
        assertNotEquals(first, second);
    }

    @Test
    void theScriptedSourceDealsTheScriptedPrefixFirstThenTheRestOfTheSixDecks() {
        ScriptedShoeSource source = new ScriptedShoeSource(new RandomShoeSource());
        GameId id = GameId.generate();
        source.fix(id, 2, cards("AS KC QD"));

        List<Card> shoe = source.shuffledShoe(id, 2);

        assertSixDecks(shoe);
        assertEquals(cards("AS KC QD"), shoe.subList(0, 3));
    }

    @Test
    void theRestOfAScriptedShoeFollowsAFixedOrder() {
        ScriptedShoeSource source = new ScriptedShoeSource(new RandomShoeSource());
        GameId id = GameId.generate();
        source.fix(id, 1, cards("AS"));
        assertEquals(source.shuffledShoe(id, 1), source.shuffledShoe(id, 1));
    }

    @Test
    void aPrefixMayRepeatACardUpToSixTimes() {
        ScriptedShoeSource source = new ScriptedShoeSource(new RandomShoeSource());
        GameId id = GameId.generate();
        source.fix(id, 1, cards("AS AS AS AS AS AS"));
        assertSixDecks(source.shuffledShoe(id, 1));

        source.fix(id, 2, cards("AS AS AS AS AS AS AS"));
        assertThrows(IllegalArgumentException.class, () -> source.shuffledShoe(id, 2));
    }

    @Test
    void anUnscriptedHandOrAClearedGameUsesTheDelegate() {
        ScriptedShoeSource source = new ScriptedShoeSource((id, hand) -> Shoe.sixDecks());
        GameId id = GameId.generate();
        source.fix(id, 1, cards("AS"));

        assertEquals(Shoe.sixDecks(), source.shuffledShoe(id, 2));
        source.clear(id);
        assertEquals(Shoe.sixDecks(), source.shuffledShoe(id, 1));
        assertEquals(c("2C"), Shoe.sixDecks().get(0));
    }
}

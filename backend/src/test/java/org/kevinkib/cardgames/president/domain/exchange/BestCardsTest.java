package org.kevinkib.cardgames.president.domain.exchange;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kevinkib.cardgames.president.domain.card.RankOrder;
import org.kevinkib.cards.domain.Card;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.code;

class BestCardsTest {

    private static String ranks(List<Card> picked) {
        return String.join(" ", picked.stream().map(c -> {
            String code = code(c);
            return code.substring(0, code.length() - 1);
        }).toList());
    }

    /** Hands to the k best cards, weakest first; the suit of tied ranks is irrelevant, so only ranks are compared. */
    @ParameterizedTest(name = "best {1} of [{0}]")
    @CsvSource(delimiter = '|', value = {
            "3C 9D 2S AH KC | 2 | A 2",
            "3C 4C 5C       | 1 | 5",
            "KC KD 2C 2D    | 2 | 2 2",
            "AC AD KC       | 2 | A A",
            "2C KC KD       | 2 | K 2",
            "3C 4C          | 5 | 3 4",
            "3C 4C 5C       | 0 |",
    })
    void bestCards(String hand, int count, String expected) {
        List<Card> picked = BestCards.best(cards(hand), count, RankOrder.standard());

        assertThat(ranks(picked), is(expected == null ? "" : expected));
    }

    @ParameterizedTest(name = "lowest {1} of [{0}]")
    @CsvSource(delimiter = '|', value = {
            "3C 9D 2S AH KC | 2 | 3 9",
            "KC KD 2C 2D    | 1 | K",
            "3C 3D 4C       | 2 | 3 3",
            "3C 4C          | 5 | 3 4",
    })
    void lowestCards(String hand, int count, String expected) {
        List<Card> picked = BestCards.lowest(cards(hand), count, RankOrder.standard());

        assertThat(ranks(picked), is(expected));
    }

    @Test
    void theHandIsNotModified() {
        List<Card> hand = cards("5C 3C 9D");

        BestCards.best(hand, 2, RankOrder.standard());

        assertThat(hand, is(cards("5C 3C 9D")));
    }
}

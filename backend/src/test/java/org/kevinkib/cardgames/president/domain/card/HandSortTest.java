package org.kevinkib.cardgames.president.domain.card;

import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.Card;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.code;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.codes;

class HandSortTest {

    @Test
    void sortsFromWeakestToStrongestWithSuitsGroupedInsideARank() {
        List<Card> sorted = HandSort.sorted(cards("2S AH KC 3D 3C 10H 3H"), RankOrder.standard());

        assertThat(codes(sorted), is("3C 3D 3H 10H KC AH 2S"));
    }

    @Test
    void doesNotChangeTheInputList() {
        List<Card> input = cards("2S 3C");

        HandSort.sorted(input, RankOrder.standard());

        assertThat(code(input.get(0)), is("2S"));
    }
}

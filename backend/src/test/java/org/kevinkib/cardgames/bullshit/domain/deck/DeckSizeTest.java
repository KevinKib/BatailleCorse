package org.kevinkib.cardgames.bullshit.domain.deck;

import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class DeckSizeTest {

    @Test
    void givenKeys_whenFromKey_thenResolvedAndDefaultsTo52() {
        assertThat(DeckSize.fromKey("32"), is(DeckSize.SHORT));
        assertThat(DeckSize.fromKey("52"), is(DeckSize.FULL));
        assertThat(DeckSize.fromKey(null), is(DeckSize.FULL));
        assertThat(DeckSize.fromKey("bogus"), is(DeckSize.FULL));
    }

    @Test
    void givenShortDeck_whenCards_thenSevenToAceOnly() {
        assertThat(DeckSize.SHORT.cards(), hasSize(32));
        assertThat(DeckSize.SHORT.cards().stream().map(c -> c.getRank()).toList(), not(hasItem(FrenchRank.SIX)));
        assertThat(DeckSize.FULL.cards(), hasSize(52));
    }

    @Test
    void givenShortDeck_whenRanks_thenSevenToAce() {
        assertThat(DeckSize.SHORT.ranks(), contains(FrenchRank.SEVEN, FrenchRank.EIGHT, FrenchRank.NINE,
                FrenchRank.TEN, FrenchRank.JACK, FrenchRank.QUEEN, FrenchRank.KING, FrenchRank.ACE));
    }

    @Test
    void givenFullDeck_whenRanks_thenAceThenTwoToKing() {
        assertThat(DeckSize.FULL.ranks().get(0), is(FrenchRank.ACE));
        assertThat(DeckSize.FULL.ranks(), hasSize(13));
    }

    @Test
    void givenShortDeck_whenDeal_thenAllThirtyTwoCardsDistributed() {
        int total = DeckSize.SHORT.deal(3).stream().mapToInt(h -> h.getCards().size()).sum();
        assertThat(total, is(32));
    }
}

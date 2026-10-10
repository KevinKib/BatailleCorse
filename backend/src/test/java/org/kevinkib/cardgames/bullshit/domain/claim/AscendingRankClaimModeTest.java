package org.kevinkib.cardgames.bullshit.domain.claim;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.deck.DeckSize;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cards.testhelpers.CardBuilder.aCard;

class AscendingRankClaimModeTest {

    private final AscendingRankClaimMode mode = new AscendingRankClaimMode();

    @Test
    void givenNewGame_whenInitial_thenAce() {
        assertThat(mode.initial(), is(new RankTarget(FrenchRank.ACE)));
    }

    @Test
    void givenAce_whenNext_thenTwo() {
        assertThat(mode.next(new RankTarget(FrenchRank.ACE)), is(new RankTarget(FrenchRank.TWO)));
    }

    @Test
    void givenKing_whenNext_thenWrapsToAce() {
        assertThat(mode.next(new RankTarget(FrenchRank.KING)), is(new RankTarget(FrenchRank.ACE)));
    }

    @Test
    void givenShortDeck_whenInitial_thenSeven() {
        assertThat(new AscendingRankClaimMode(DeckSize.SHORT).initial(), is(new RankTarget(FrenchRank.SEVEN)));
    }

    @Test
    void givenShortDeck_whenNext_thenAbsentRanksAreSkipped() {
        AscendingRankClaimMode shortMode = new AscendingRankClaimMode(DeckSize.SHORT);
        ClaimTarget target = shortMode.initial();
        List<FrenchRank> seen = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            seen.add(((RankTarget) target).rank());
            target = shortMode.next(target);
        }
        assertThat(seen, contains(FrenchRank.SEVEN, FrenchRank.EIGHT, FrenchRank.NINE, FrenchRank.TEN,
                FrenchRank.JACK, FrenchRank.QUEEN, FrenchRank.KING, FrenchRank.ACE, FrenchRank.SEVEN));
    }

    @Test
    void givenAllCardsMatchTarget_whenMatches_thenTrue() {
        List<Card> cards = List.of(
                aCard().withRank(FrenchRank.SEVEN).withSuit(FrenchSuit.HEART).build(),
                aCard().withRank(FrenchRank.SEVEN).withSuit(FrenchSuit.CLUB).build());
        assertThat(mode.matches(cards, new RankTarget(FrenchRank.SEVEN)), is(true));
    }

    @Test
    void givenAnyCardOffTarget_whenMatches_thenFalse() {
        List<Card> cards = List.of(
                aCard().withRank(FrenchRank.SEVEN).withSuit(FrenchSuit.HEART).build(),
                aCard().withRank(FrenchRank.EIGHT).withSuit(FrenchSuit.CLUB).build());
        assertThat(mode.matches(cards, new RankTarget(FrenchRank.SEVEN)), is(false));
    }
}

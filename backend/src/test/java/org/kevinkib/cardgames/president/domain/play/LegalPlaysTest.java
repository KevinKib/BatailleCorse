package org.kevinkib.cardgames.president.domain.play;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.president.domain.card.RankOrder;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.codes;

class LegalPlaysTest {

    private static final String HAND = "3C 3H 5D 5S 5H KC";

    private static List<String> legal(String hand, String top) throws Exception {
        Play topPlay = top == null ? null : PlayParser.parse(cards(top));
        return LegalPlays.of(cards(hand), topPlay, RankOrder.standard()).stream()
                .map(p -> codes(p.cards())).toList();
    }

    @Test
    void leadingListsEverySetOfTheHandFromLowToHigh() throws Exception {
        assertThat(legal(HAND, null), contains(
                "3C", "3C 3H",
                "5D", "5D 5S", "5D 5S 5H",
                "KC"));
    }

    @Test
    void followingASingleListsOnlyHigherSingles() throws Exception {
        assertThat(legal(HAND, "4H"), contains("5D", "KC"));
    }

    @Test
    void followingAPairListsOnlyHigherPairs() throws Exception {
        assertThat(legal(HAND, "3D 3S"), contains("5D 5S"));
    }

    @Test
    void followingATripleListsOnlyHigherTriples() throws Exception {
        assertThat(legal(HAND, "4D 4S 4H"), contains("5D 5S 5H"));
    }

    @Test
    void nothingBeatsTheTopOfTheRanking() throws Exception {
        assertThat(legal(HAND, "2H"), empty());
    }

    @Test
    void aQuadNeedsFourCardsOfARank() throws Exception {
        assertThat(legal(HAND, "4D 4S 4H 4C"), empty());
        assertThat(legal("6C 6D 6H 6S", "4D 4S 4H 4C"), contains("6C 6D 6H 6S"));
    }

    private static List<String> legal(String hand, String top, Constraint constraint, boolean equalPlay)
            throws Exception {
        return LegalPlays.of(cards(hand), PlayParser.parse(cards(top)), RankOrder.standard(), constraint, equalPlay)
                .stream().map(p -> codes(p.cards())).toList();
    }

    @Test
    void withEqualPlayAnEqualSetJoinsTheHigherOnes() throws Exception {
        assertThat(legal("5D 5S 5H KC", "5C", Constraint.BEAT, true), contains("5D", "KC"));
        assertThat(legal("5D 5S 5H KC", "5C 5H", Constraint.BEAT, true), contains("5D 5S"));
    }

    @Test
    void withoutEqualPlayTheEqualSetIsNotListed() throws Exception {
        assertThat(legal("5D 5S 5H KC", "5C", Constraint.BEAT, false), contains("KC"));
    }

    @Test
    void underTheBindingOnlyTheSameRankAndCountIsListed() throws Exception {
        assertThat(legal("5D 5S 5H KC", "5C", Constraint.MATCH_OR_PASS, true), contains("5D"));
        assertThat(legal("5D 5S 5H KC", "5C 5H", Constraint.MATCH_OR_PASS, true), contains("5D 5S"));
        assertThat(legal("5D 5S 5H KC", "5C 5H 5D", Constraint.MATCH_OR_PASS, true), empty());
        assertThat(legal("KC KD", "5C", Constraint.MATCH_OR_PASS, true), empty());
        assertThat(legal("5D 2S", "5C", Constraint.MATCH_OR_PASS, true), contains("5D"));
    }

    @Test
    void anEmptyHandHasNoPlay() throws Exception {
        assertThat(legal("", null), empty());
    }
}

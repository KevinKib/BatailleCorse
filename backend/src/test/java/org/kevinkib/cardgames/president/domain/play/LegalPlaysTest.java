package org.kevinkib.cardgames.president.domain.play;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kevinkib.cardgames.president.domain.card.RankOrder;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
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
        assertThat(legal(HAND, "3D 3S"), contains("3C 3H", "5D 5S"));
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

    @Test
    void anEqualSetJoinsTheHigherOnes() throws Exception {
        assertThat(legal("5D 5S 5H KC", "5C", Constraint.BEAT), contains("5D", "KC"));
        assertThat(legal("5D 5S 5H KC", "5C 5H", Constraint.BEAT), contains("5D 5S"));
    }

    @Test
    void underTheBindingOnlyTheSameRankAndCountIsListed() throws Exception {
        assertThat(legal("5D 5S 5H KC", "5C", Constraint.MATCH_OR_PASS), contains("5D"));
        assertThat(legal("5D 5S 5H KC", "5C 5H", Constraint.MATCH_OR_PASS), contains("5D 5S"));
        assertThat(legal("5D 5S 5H KC", "5C 5H 5D 5S", Constraint.MATCH_OR_PASS), empty());
        assertThat(legal("KC KD", "5C", Constraint.MATCH_OR_PASS), empty());
        assertThat(legal("5D 2S", "5C", Constraint.MATCH_OR_PASS), contains("5D"));
    }

    private static List<String> legal(String hand, String top, Constraint constraint) throws Exception {
        return LegalPlays.of(cards(hand), PlayParser.parse(cards(top)), RankOrder.standard(), constraint)
                .stream().map(p -> codes(p.cards())).toList();
    }

    @ParameterizedTest(name = "{1}: {0} holding {2} -> {3}")
    @CsvSource(delimiter = '|', value = {
            // n twos answer n cards; a lone 2 no longer answers a pair or a triple
            "7C 7D 7H    | BEAT          | 2D 2H 2S 9C 9D | 2D 2H 2S",
            "7C 7D       | BEAT          | 2D 2H 9C 9D    | 9C 9D, 2D 2H",
            "7C          | BEAT          | 2D 2H 9C 9D    | 9C, 2D",
            // the same count holds against a play of 2s
            "2C 2S       | BEAT          | 2D 2H 9C 9D    | 2D 2H",
            "2C          | BEAT          | 2D 2H 9C 9D    | 2D",
            // the binding asks for the same rank and count, and a 2 is not that rank
            "7C          | MATCH_OR_PASS | 2D 7D          | 7D",
            "7C 7D       | MATCH_OR_PASS | 2D 2H 7H 7S    | 7H 7S",
    })
    void twosAnswerOnlyTheSameNumberOfCardsAndNeverTheBinding(String top, String constraint, String hand,
                                                              String expected) throws Exception {
        List<String> wanted = java.util.Arrays.stream(expected.split(",")).map(String::trim).toList();

        assertThat(legal(hand.trim(), top.trim(), Constraint.valueOf(constraint.trim())), is(wanted));
    }

    @Test
    void aLoneTwoOffersNothingOnAPairOrATripleAndNothingUnderTheBinding() throws Exception {
        assertThat(legal("2D 9C", "7C 7D", Constraint.BEAT), empty());
        assertThat(legal("2D", "7C 7D", Constraint.BEAT), empty());
        assertThat(legal("2D 2H", "7C 7D 7H", Constraint.BEAT), empty());
        assertThat(legal("2D 9C", "7C 7D", Constraint.MATCH_OR_PASS), empty());
        assertThat(legal("2D", "9C", Constraint.MATCH_OR_PASS), empty());
    }

    private static List<String> legalReduced(String hand, String top) throws Exception {
        return LegalPlays.of(cards(hand), PlayParser.parse(cards(top)), RankOrder.standard(), Constraint.BEAT, true)
                .stream().map(p -> codes(p.cards())).toList();
    }

    @Test
    void reducedCutOffersOneTwoLessOnAPairAndTwoTwosOnATriple() throws Exception {
        assertThat(legalReduced("2D 2H 9C", "7C 7D"), contains("2D", "2D 2H"));
        assertThat(legalReduced("2D 2H 9C", "7C 7D 7H"), contains("2D 2H"));
        assertThat(legalReduced("2D 9C", "7C 7D 7H"), empty());
        assertThat(legalReduced("2D 2H 2S", "7C 7D 7H 7S"), empty());
    }

    @Test
    void anEmptyHandHasNoPlay() throws Exception {
        assertThat(legal("", null), empty());
    }
}

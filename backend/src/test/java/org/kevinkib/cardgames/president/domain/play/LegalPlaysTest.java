package org.kevinkib.cardgames.president.domain.play;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kevinkib.cardgames.president.domain.card.RankOrder;
import org.kevinkib.cardgames.president.domain.rules.TwoCuts;

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
        assertThat(legal("5D 5S 5H KC", "5C 5H 5D 5S", Constraint.MATCH_OR_PASS, true), empty());
        assertThat(legal("KC KD", "5C", Constraint.MATCH_OR_PASS, true), empty());
        assertThat(legal("5D 2S", "5C", Constraint.MATCH_OR_PASS, true), contains("5D"));
    }

    private static List<String> legal(String hand, String top, Constraint constraint, TwoCuts twoCuts)
            throws Exception {
        return LegalPlays.of(cards(hand), PlayParser.parse(cards(top)), RankOrder.standard(), constraint, true,
                twoCuts).stream().map(p -> codes(p.cards())).toList();
    }

    @ParameterizedTest(name = "cuts={0}: {2} on {1} holding {3} -> {4}")
    @CsvSource(delimiter = '|', value = {
            // a single 2 answers any count; the other sets must still beat by count and rank
            "single | 7C 7D 7H | BEAT          | 2D 2H 9C 9D   | 2D",
            "single | 7C 7D    | BEAT          | 2D 2H 9C 9D   | 9C 9D, 2D, 2D 2H",
            "single | 7C       | BEAT          | 2D 2H 9C 9D   | 9C, 2D",
            "single | 7C 7D 7H 7S | BEAT        | 2D 2H 9C 9D   | 2D",
            // ladder: k twos on k+1 cards
            "ladder | 7C 7D 7H | BEAT          | 2D 2H 9C 9D   | 2D 2H",
            "ladder | 7C 7D    | BEAT          | 2D 2H 9C 9D   | 9C 9D, 2D, 2D 2H",
            "ladder | 7C 7D 7H 7S | BEAT        | 2D 2H 2S 9C   | 2D 2H 2S",
            "ladder | 7C 7D 7H 7S | BEAT        | 2D 2H 9C 9D   | ",
            // no cut
            "off    | 7C 7D 7H | BEAT          | 2D 2H 9C 9D   | ",
            "off    | 7C 7D    | BEAT          | 2D 2H 9C 9D   | 9C 9D, 2D 2H",
            // a cut never answers the binding
            "single | 7C       | MATCH_OR_PASS | 2D 7D         | 7D",
            "ladder | 7C 7D    | MATCH_OR_PASS | 2D 7H 7S      | 7H 7S",
            "single | 7C 7D    | MATCH_OR_PASS | 2D 9C         | ",
    })
    void cutsJoinTheLegalPlaysUnlessTheBindingHolds(String cuts, String top, String constraint, String hand,
                                                    String expected) throws Exception {
        TwoCuts twoCuts = TwoCuts.valueOf(cuts.trim().toUpperCase());
        List<String> wanted = expected == null || expected.isBlank() ? List.of()
                : java.util.Arrays.stream(expected.split(",")).map(String::trim).toList();

        assertThat(legal(hand.trim(), top.trim(), Constraint.valueOf(constraint.trim()), twoCuts), is(wanted));
    }

    @Test
    void anEmptyHandHasNoPlay() throws Exception {
        assertThat(legal("", null), empty());
    }
}

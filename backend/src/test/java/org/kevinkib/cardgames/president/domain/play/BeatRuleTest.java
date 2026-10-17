package org.kevinkib.cardgames.president.domain.play;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kevinkib.cardgames.president.domain.card.RankOrder;
import org.kevinkib.cardgames.president.domain.rules.TwoCuts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

class BeatRuleTest {

    private static Play play(String codes) throws Exception {
        return PlayParser.parse(cards(codes));
    }

    /** Same count and a strictly higher rank beat; everything else does not (base game, no variants). */
    @ParameterizedTest(name = "{1} on {0}: {2}")
    @CsvSource(delimiter = '|', value = {
            // singles
            "3C | 4C | true",
            "KC | AC | true",
            "AC | 2C | true",
            "3C | 2C | true",
            "2C | AC | false",
            "9C | 9D | false",
            "10C | 9D | false",
            // pairs
            "9C 9D | 10C 10D | true",
            "QC QD | KC KD | true",
            "AC AD | 2C 2D | true",
            "2C 2D | AC AD | false",
            "5C 5D | 5H 5S | false",
            // triples
            "4C 4D 4H | 5C 5D 5H | true",
            "KC KD KH | 3C 3D 3H | false",
            // quads
            "6C 6D 6H 6S | 7C 7D 7H 7S | true",
            "7C 7D 7H 7S | 6C 6D 6H 6S | false",
            // different counts never beat, whatever the rank
            "3C | 3D 3H | false",
            "3C | 2C 2D | false",
            "3C 3D | 2C | false",
            "5C 5D | 6C 6D 6H | false",
            "5C 5D 5H | 6C 6D | false",
            "5C | 6C 6D 6H 6S | false",
    })
    void beatMatrix(String top, String candidate, boolean beats) throws Exception {
        assertThat(BeatRule.beats(play(candidate), play(top), RankOrder.standard()), is(beats));
    }

    @Test
    void checkAcceptsABeatingPlay() throws Exception {
        assertDoesNotThrow(() -> BeatRule.check(play("KC"), play("9C"), RankOrder.standard()));
    }

    @Test
    void checkRefusesAPlayThatDoesNotBeat() throws Exception {
        Play top = play("KC");
        Play candidate = play("9C");

        assertThrows(PlayDoesNotBeatException.class, () -> BeatRule.check(candidate, top, RankOrder.standard()));
    }

    /**
     * The constraint decides what answers the top: BEAT takes a higher play of the same count and, with equal play
     * on, an equal one; MATCH_OR_PASS takes only the same rank and count (nothing higher, no cut); equal play off
     * leaves the base game. Rows: constraint, equalPlay, top, candidate, accepted.
     */
    @ParameterizedTest(name = "{0} equalPlay={1}: {3} on {2} -> {4}")
    @CsvSource(delimiter = '|', value = {
            "BEAT          | true  | 9C       | 9D       | true",
            "BEAT          | true  | 9C 9D    | 9H 9S    | true",
            "BEAT          | true  | 9C       | 10C      | true",
            "BEAT          | true  | 9C       | 9D 9H    | false",
            "BEAT          | true  | 9C       | 8D       | false",
            "BEAT          | false | 9C       | 9D       | false",
            "BEAT          | false | 9C       | 10D      | true",
            "MATCH_OR_PASS | true  | 9C       | 9D       | true",
            "MATCH_OR_PASS | true  | 9C 9D    | 9H 9S    | true",
            "MATCH_OR_PASS | true  | 9C       | 10D      | false",
            "MATCH_OR_PASS | true  | 9C       | 2D       | false",
            "MATCH_OR_PASS | true  | 9C       | 9D 9H    | false",
            "MATCH_OR_PASS | true  | 9C 9D    | 10C 10D  | false",
            "MATCH_OR_PASS | true  | 9C       | 8D       | false",
    })
    void theConstraintDecidesWhatAnswersTheTop(String constraint, boolean equalPlay, String top, String candidate,
                                               boolean accepted) throws Exception {
        Constraint c = Constraint.valueOf(constraint.trim());
        Play candidatePlay = play(candidate);
        Play topPlay = play(top);
        if (accepted) {
            assertDoesNotThrow(() -> BeatRule.check(candidatePlay, topPlay, RankOrder.standard(), c, equalPlay));
        } else {
            assertThrows(PlayDoesNotBeatException.class,
                    () -> BeatRule.check(candidatePlay, topPlay, RankOrder.standard(), c, equalPlay));
        }
    }

    /**
     * The 2 cuts (spec catalogue V4): under {@code single} one 2 answers any play whatever its count, under
     * {@code ladder} k twos answer a set of k+1 cards of a lower rank, and a cut never answers the equal-play
     * binding. A pair of 2s on a pair, or a 2 on a single, is an ordinary higher play whatever the option.
     * Rows: twoCuts, constraint, equalPlay, top, candidate, accepted.
     */
    @ParameterizedTest(name = "cuts={0} {1} equalPlay={2}: {4} on {3} -> {5}")
    @CsvSource(delimiter = '|', value = {
            "single | BEAT          | true  | 9C          | 2D       | true",
            "single | BEAT          | true  | 9C 9D       | 2D       | true",
            "single | BEAT          | true  | 9C 9D 9H    | 2D       | true",
            "single | BEAT          | true  | 9C 9D 9H 9S | 2D       | true",
            "single | BEAT          | true  | 9C          | 2D 2H    | false",
            "single | BEAT          | true  | 9C 9D 9H    | 2D 2H    | false",
            "single | BEAT          | true  | 9C 9D       | 2D 2H    | true",
            "single | BEAT          | true  | 2C          | 2D       | true",
            "single | BEAT          | false | 2C          | 2D       | false",
            "single | MATCH_OR_PASS | true  | 9C          | 2D       | false",
            "single | MATCH_OR_PASS | true  | 9C 9D       | 2D       | false",
            "single | MATCH_OR_PASS | true  | 2C          | 2D       | true",
            "ladder | BEAT          | true  | 9C 9D       | 2D       | true",
            "ladder | BEAT          | true  | 9C 9D 9H    | 2D 2H    | true",
            "ladder | BEAT          | true  | 9C 9D 9H 9S | 2D 2H 2S | true",
            "ladder | BEAT          | true  | 9C 9D 9H    | 2D       | false",
            "ladder | BEAT          | true  | 9C 9D 9H 9S | 2D 2H    | false",
            "ladder | BEAT          | true  | 9C 9D 9H 9S | 2D       | false",
            "ladder | BEAT          | true  | 9C          | 2D       | true",
            "ladder | BEAT          | true  | 9C 9D       | 2D 2H    | true",
            "ladder | BEAT          | true  | 9C 9D 9H    | 2D 2H 2S | true",
            "ladder | BEAT          | true  | 2C 2D       | 2H       | false",
            "ladder | BEAT          | true  | 2C 2D 2H    | 2S       | false",
            "ladder | MATCH_OR_PASS | true  | 9C 9D       | 2D       | false",
            "ladder | MATCH_OR_PASS | true  | 9C 9D 9H    | 2D 2H    | false",
            "off    | BEAT          | true  | 9C 9D       | 2D       | false",
            "off    | BEAT          | true  | 9C 9D 9H    | 2D 2H    | false",
            "off    | BEAT          | true  | 9C          | 2D       | true",
    })
    void aCutAnswersAnyPlayOfItsKindButNeverTheBinding(String cuts, String constraint, boolean equalPlay, String top,
                                                       String candidate, boolean accepted) throws Exception {
        TwoCuts twoCuts = TwoCuts.valueOf(cuts.trim().toUpperCase());
        Constraint c = Constraint.valueOf(constraint.trim());
        Play candidatePlay = play(candidate);
        Play topPlay = play(top);
        assertThat(BeatRule.answers(candidatePlay, topPlay, RankOrder.standard(), c, equalPlay, twoCuts),
                is(accepted));
        if (accepted) {
            assertDoesNotThrow(() -> BeatRule.check(candidatePlay, topPlay, RankOrder.standard(), c, equalPlay,
                    twoCuts));
        } else {
            assertThrows(PlayDoesNotBeatException.class,
                    () -> BeatRule.check(candidatePlay, topPlay, RankOrder.standard(), c, equalPlay, twoCuts));
        }
    }

    /** Which plays are cuts (they end the trick), as opposed to ordinary higher plays. Rows: twoCuts, top, candidate. */
    @ParameterizedTest(name = "cuts={0}: {2} on {1} -> {3}")
    @CsvSource(delimiter = '|', value = {
            "single | 9C          | 2D       | true",
            "single | 9C 9D       | 2D       | true",
            "single | 9C 9D 9H 9S | 2D       | true",
            "single | 2C          | 2D       | false",
            "single | 9C          | 2D 2H    | false",
            "single | 9C 9D       | 2D 2H    | false",
            "single | 9C          | 10D      | false",
            "ladder | 9C          | 2D       | false",
            "ladder | 9C 9D       | 2D       | true",
            "ladder | 9C 9D 9H    | 2D 2H    | true",
            "ladder | 9C 9D 9H 9S | 2D 2H 2S | true",
            "ladder | 9C 9D       | 2D 2H    | false",
            "ladder | 2C 2D       | 2H       | false",
            "ladder | 9C 9D 9H    | 2D       | false",
            "off    | 9C 9D       | 2D       | false",
            "off    | 9C          | 2D       | false",
    })
    void whichPlaysAreCuts(String cuts, String top, String candidate, boolean cut) throws Exception {
        TwoCuts twoCuts = TwoCuts.valueOf(cuts.trim().toUpperCase());

        assertThat(BeatRule.isCut(play(candidate), play(top), RankOrder.standard(), twoCuts), is(cut));
    }

    @ParameterizedTest(name = "{0} equals {1}: {2}")
    @CsvSource(delimiter = '|', value = {
            "9C       | 9D       | true",
            "9C 9D    | 9H 9S    | true",
            "9C       | 10D      | false",
            "9C       | 9D 9H    | false",
            "9C 9D    | 10H 10S  | false",
    })
    void twoPlaysAreEqualWhenTheyHoldTheSameRankAndCount(String top, String candidate, boolean equal)
            throws Exception {
        assertThat(BeatRule.isEqual(play(candidate), play(top)), is(equal));
    }
}

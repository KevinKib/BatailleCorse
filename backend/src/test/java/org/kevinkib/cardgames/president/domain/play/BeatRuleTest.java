package org.kevinkib.cardgames.president.domain.play;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kevinkib.cardgames.president.domain.card.RankOrder;

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
     * The constraint decides what answers the top: BEAT takes a higher play of the same count and an equal one (equal
     * play is a fixed rule); MATCH_OR_PASS takes only the same rank and count (nothing higher, a 2 included unless 2
     * is the bound rank). Rows: constraint, top, candidate, accepted.
     */
    @ParameterizedTest(name = "{0}: {2} on {1} -> {3}")
    @CsvSource(delimiter = '|', value = {
            "BEAT          | 9C       | 9D       | true",
            "BEAT          | 9C 9D    | 9H 9S    | true",
            "BEAT          | 9C       | 10C      | true",
            "BEAT          | 9C       | 9D 9H    | false",
            "BEAT          | 9C       | 8D       | false",
            "MATCH_OR_PASS | 9C       | 9D       | true",
            "MATCH_OR_PASS | 9C 9D    | 9H 9S    | true",
            "MATCH_OR_PASS | 9C       | 10D      | false",
            "MATCH_OR_PASS | 9C       | 2D       | false",
            "MATCH_OR_PASS | 9C       | 9D 9H    | false",
            "MATCH_OR_PASS | 9C 9D    | 10C 10D  | false",
            "MATCH_OR_PASS | 9C       | 8D       | false",
    })
    void theConstraintDecidesWhatAnswersTheTop(String constraint, String top, String candidate,
                                               boolean accepted) throws Exception {
        Constraint c = Constraint.valueOf(constraint.trim());
        Play candidatePlay = play(candidate);
        Play topPlay = play(top);
        if (accepted) {
            assertDoesNotThrow(() -> BeatRule.check(candidatePlay, topPlay, RankOrder.standard(), c));
        } else {
            assertThrows(PlayDoesNotBeatException.class,
                    () -> BeatRule.check(candidatePlay, topPlay, RankOrder.standard(), c));
        }
    }

    /**
     * The 2 cuts with the same number of cards as the play it answers (a fixed rule, owner feedback of 2026-10-18):
     * n twos on n cards beat it for any n, a lone 2 can no longer cut a pair or a triple, and the equal-play binding
     * accepts a 2 only when 2 is the bound rank. Rows: constraint, top, candidate, accepted.
     */
    @ParameterizedTest(name = "{0}: {2} on {1} -> {3}")
    @CsvSource(delimiter = '|', value = {
            "BEAT          | 9C          | 2D       | true",
            "BEAT          | 9C 9D       | 2D       | false",
            "BEAT          | 9C 9D 9H    | 2D       | false",
            "BEAT          | 9C 9D 9H 9S | 2D       | false",
            "BEAT          | 9C          | 2D 2H    | false",
            "BEAT          | 9C 9D       | 2D 2H    | true",
            "BEAT          | 9C 9D 9H    | 2D 2H    | false",
            "BEAT          | 9C 9D 9H    | 2D 2H 2S | true",
            "BEAT          | 2C          | 2D       | true",
            "BEAT          | 2C 2D       | 2H       | false",
            "BEAT          | 2C 2D       | 2H 2S    | true",
            "MATCH_OR_PASS | 9C          | 2D       | false",
            "MATCH_OR_PASS | 9C 9D       | 2D       | false",
            "MATCH_OR_PASS | 9C 9D       | 2D 2H    | false",
            "MATCH_OR_PASS | 9C 9D       | 10D 10H  | false",
            "MATCH_OR_PASS | 2C          | 2D       | true",
    })
    void aTwoCutsOnlyWithTheSameNumberOfCards(String constraint, String top, String candidate,
                                              boolean accepted) throws Exception {
        Constraint c = Constraint.valueOf(constraint.trim());
        Play candidatePlay = play(candidate);
        Play topPlay = play(top);
        assertThat(BeatRule.answers(candidatePlay, topPlay, RankOrder.standard(), c), is(accepted));
        if (accepted) {
            assertDoesNotThrow(() -> BeatRule.check(candidatePlay, topPlay, RankOrder.standard(), c));
        } else {
            assertThrows(PlayDoesNotBeatException.class,
                    () -> BeatRule.check(candidatePlay, topPlay, RankOrder.standard(), c));
        }
    }

    /**
     * With {@code reducedCut} on, n-1 twos also cut a group of n for n = 2 and 3. A single 2 never cuts a triple.
     * Rows: top, candidate, accepted with the option on.
     */
    @ParameterizedTest(name = "reducedCut: {1} on {0} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "9C 9D       | 2D       | true",
            "9C 9D 9H    | 2D 2H    | true",
            "9C 9D 9H    | 2D       | false",
            "9C          | 2D       | true",
            "9C 9D 9H 9S | 2D 2H 2S | false",
            "9C 9D 9H 9S | 2D       | false",
            "9C 9D       | 2D 2H    | true",
            "9C 9D 9H    | 2D 2H 2S | true",
    })
    void reducedCutAddsOneTwoLessForPairsAndTriples(String top, String candidate, boolean accepted)
            throws Exception {
        assertThat(BeatRule.answers(play(candidate), play(top), RankOrder.standard(), Constraint.BEAT, true),
                is(accepted));
        // the binding still takes a 2 only when 2 is the bound rank, whatever the option
        assertThat(BeatRule.answers(play(candidate), play(top), RankOrder.standard(),
                Constraint.MATCH_OR_PASS, true), is(false));
    }

    /** Which plays are cuts (they end the trick): n twos on n cards, nothing else. Rows: top, candidate. */
    @ParameterizedTest(name = "{1} on {0} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "9C          | 2D       | true",
            "9C 9D       | 2D       | false",
            "9C 9D 9H 9S | 2D       | false",
            "2C          | 2D       | true",
            "2C 2D       | 2H       | false",
            "9C          | 2D 2H    | false",
            "9C 9D       | 2D 2H    | true",
            "9C 9D 9H    | 2D 2H 2S | true",
            "9C          | 10D      | false",
            "9C 9D       | 9H 9S    | false",
    })
    void whichPlaysAreCuts(String top, String candidate, boolean cut) throws Exception {
        assertThat(BeatRule.isCut(play(candidate), play(top), RankOrder.standard()), is(cut));
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

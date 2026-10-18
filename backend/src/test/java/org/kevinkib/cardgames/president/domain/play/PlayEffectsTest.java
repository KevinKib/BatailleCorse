package org.kevinkib.cardgames.president.domain.play;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kevinkib.cardgames.president.domain.card.RankOrder;
import org.kevinkib.cardgames.president.domain.event.TrickEndReason;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

/**
 * The effects of a play on the trick (spec catalogue V4 and V5): which plays close it at once, and why. Both rules are
 * fixed (owner decision): one 2 closes the trick, and so does a quad played at once. The matrix is the play x the top;
 * {@code -} as a top is a lead. The four cards of a rank completed in succession belong to equal play and are not
 * decided here.
 */
class PlayEffectsTest {

    private static Play play(String codes) throws Exception {
        return PlayParser.parse(cards(codes));
    }

    @ParameterizedTest(name = "{1} on {0} -> {2}")
    @CsvSource(delimiter = '|', value = {
            // a quad played at once
            "-           | 9C 9D 9H 9S | QUAD",
            "5C 5D 5H 5S | 9C 9D 9H 9S | QUAD",
            "-           | 2C 2D 2H 2S | QUAD",
            // a quad is not a quad with fewer cards
            "-           | 9C 9D 9H    | NONE",
            "-           | 9C          | NONE",
            // one 2, on anything, even as a lead
            "9C          | 2D          | CUT",
            "-           | 2D          | CUT",
            // n twos on n cards cut; a lone 2 does not cut a group
            "9C 9D       | 2D 2H       | CUT",
            "9C 9D 9H    | 2D 2H 2S    | CUT",
            "9C 9D       | 2D          | NONE",
            "9C 9D 9H    | 2D          | NONE",
            "9C 9D 9H 9S | 2D          | NONE",
            "9C 9D 9H    | 2D 2H       | NONE",
            // 2s led close the trick at once; anything else goes on
            "-           | 2D 2H       | CUT",
            "-           | 2D 2H 2S    | CUT",
            "9C          | 10D         | NONE",
            "9C          | 2D 2H       | NONE",
    })
    void theEffectOfAPlayOnTheTrick(String top, String candidate, String expected) throws Exception {
        Play topPlay = top.trim().equals("-") ? null : play(top);

        Optional<TrickEndReason> reason = PlayEffects.clearReason(play(candidate), topPlay, RankOrder.standard());

        assertThat(reason.map(Enum::name).orElse("NONE"), is(expected.trim()));
    }

    /** With reducedCut on, n-1 twos close a pair or a triple; a lone 2 still never closes a triple. */
    @ParameterizedTest(name = "reducedCut: {1} on {0} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "9C 9D    | 2D       | CUT",
            "9C 9D 9H | 2D 2H    | CUT",
            "9C 9D 9H | 2D       | NONE",
            "9C 9D    | 2D 2H    | CUT",
            "-        | 2D 2H    | CUT",
    })
    void theReducedCutClosesWithOneTwoLess(String top, String candidate, String expected) throws Exception {
        Play topPlay = top.trim().equals("-") ? null : play(top);

        Optional<TrickEndReason> reason = PlayEffects.clearReason(play(candidate), topPlay, RankOrder.standard(),
                true);

        assertThat(reason.map(Enum::name).orElse("NONE"), is(expected.trim()));
    }
}

package org.kevinkib.cardgames.president.domain.play;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kevinkib.cardgames.president.domain.card.RankOrder;
import org.kevinkib.cardgames.president.domain.event.TrickEndReason;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cardgames.president.domain.rules.TwoCuts;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

/**
 * The effects of a play on the trick (spec catalogue V4 and V5): which plays close it at once, and why. The matrix is
 * the option value (twoCuts, quadClears) x the play x the top. {@code -} as a top is a lead. The four cards of a
 * rank completed in succession belong to equal play and are not decided here.
 */
class PlayEffectsTest {

    private static Play play(String codes) throws Exception {
        return PlayParser.parse(cards(codes));
    }

    @ParameterizedTest(name = "cuts={0} quadClears={1}: {3} on {2} -> {4}")
    @CsvSource(delimiter = '|', value = {
            // a quad played at once (quadClears)
            "off    | true  | -           | 9C 9D 9H 9S | QUAD",
            "off    | false | -           | 9C 9D 9H 9S | NONE",
            "off    | true  | 5C 5D 5H 5S | 9C 9D 9H 9S | QUAD",
            "off    | false | 5C 5D 5H 5S | 9C 9D 9H 9S | NONE",
            "off    | true  | -           | 2C 2D 2H 2S | QUAD",
            "single | true  | -           | 2C 2D 2H 2S | QUAD",
            "ladder | true  | 9C 9D 9H 9S | 2C 2D 2H     | CUT",
            // a quad is not a quad with fewer cards
            "off    | true  | -           | 9C 9D 9H    | NONE",
            "off    | true  | -           | 9C          | NONE",
            // single cut: one 2, on anything, even as a lead
            "single | true  | 9C          | 2D          | CUT",
            "single | true  | 9C 9D       | 2D          | CUT",
            "single | true  | 9C 9D 9H    | 2D          | CUT",
            "single | true  | 9C 9D 9H 9S | 2D          | CUT",
            "single | false | 9C 9D       | 2D          | CUT",
            "single | true  | -           | 2D          | CUT",
            "single | true  | 2C          | 2D          | NONE",
            "single | true  | -           | 2D 2H       | NONE",
            "single | true  | 9C          | 10D         | NONE",
            "single | true  | 9C 9D       | 2D 2H       | NONE",
            // ladder: k twos on k+1 cards
            "ladder | true  | 9C 9D       | 2D          | CUT",
            "ladder | true  | 9C 9D 9H    | 2D 2H       | CUT",
            "ladder | true  | 9C 9D 9H 9S | 2D 2H 2S    | CUT",
            "ladder | true  | 9C          | 2D          | NONE",
            "ladder | true  | -           | 2D          | NONE",
            "ladder | true  | 9C 9D       | 2D 2H       | NONE",
            "ladder | true  | 2C 2D       | 2H          | NONE",
            // no cut
            "off    | true  | 9C 9D       | 2D          | NONE",
            "off    | true  | -           | 2D          | NONE",
            "off    | true  | 9C          | 2D          | NONE",
    })
    void theEffectOfAPlayOnTheTrick(String cuts, boolean quadClears, String top, String candidate, String expected)
            throws Exception {
        PresidentOptions options = PresidentOptions.defaults()
                .withTwoCuts(TwoCuts.valueOf(cuts.trim().toUpperCase())).withQuadClears(quadClears);
        Play topPlay = top.trim().equals("-") ? null : play(top);

        Optional<TrickEndReason> reason =
                PlayEffects.clearReason(play(candidate), topPlay, RankOrder.standard(), options);

        assertThat(reason.map(Enum::name).orElse("NONE"), is(expected.trim()));
    }
}

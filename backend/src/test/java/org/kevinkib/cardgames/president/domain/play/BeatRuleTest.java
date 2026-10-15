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
}

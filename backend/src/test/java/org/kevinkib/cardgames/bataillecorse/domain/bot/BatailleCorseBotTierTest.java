package org.kevinkib.cardgames.bataillecorse.domain.bot;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class BatailleCorseBotTierTest {

    /** The reaction times of frontend/src/model/Difficulty.ts, tier 0 to 8. */
    private static final List<Integer> FRONTEND_REACTION_TIMES = List.of(2100, 1800, 1500, 1200, 900, 700, 600, 500, 400);

    @Test
    void thereAreNineTiersWithTheReactionTimesOfTheOldLocalAi() {
        List<Integer> times = Arrays.stream(BatailleCorseBotTier.values())
                .map(BatailleCorseBotTier::reactionTimeMs).toList();

        assertThat(times, is(FRONTEND_REACTION_TIMES));
    }

    @Test
    void tiersAreNumberedFromZeroInOrder() {
        for (int i = 0; i < BatailleCorseBotTier.values().length; i++) {
            assertThat(BatailleCorseBotTier.values()[i].index(), is(i));
        }
    }

    @Test
    void theJitterIsOneHundredMillisecondsEitherWay() {
        assertThat(BatailleCorseBotTier.JITTER_MS, is(100));
    }

    @Test
    void aTierIsFoundByItsIndexOption() {
        assertThat(BatailleCorseBotTier.fromOption("0"), is(BatailleCorseBotTier.TRAINING));
        assertThat(BatailleCorseBotTier.fromOption("8"), is(BatailleCorseBotTier.LEGEND));
    }

    @Test
    void anAbsentOrUnreadableOptionFallsBackToTheDefaultTier() {
        assertThat(BatailleCorseBotTier.fromOption(null), is(BatailleCorseBotTier.BRONZE));
        assertThat(BatailleCorseBotTier.fromOption("x"), is(BatailleCorseBotTier.BRONZE));
        assertThat(BatailleCorseBotTier.fromOption("9"), is(BatailleCorseBotTier.BRONZE));
    }

    @Test
    void theDefaultTierIsOne() {
        assertThat(BatailleCorseBotTier.DEFAULT.index(), is(1));
    }
}

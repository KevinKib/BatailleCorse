package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class MorrisBotLevelTest {

    @Test
    void theLevelsAreRandomGreedyAndPerfectInThatOrder() {
        assertThat(Arrays.stream(MorrisBotLevel.values()).map(MorrisBotLevel::key).toList(),
                is(List.of("random", "greedy", "perfect")));
    }

    @Test
    void greedyIsTheDefault() {
        assertThat(MorrisBotLevel.DEFAULT, is(MorrisBotLevel.GREEDY));
    }

    @Test
    void anOptionValueNamesItsLevelAndAnythingElseGivesTheDefault() {
        assertThat(MorrisBotLevel.fromOption("random"), is(MorrisBotLevel.RANDOM));
        assertThat(MorrisBotLevel.fromOption("perfect"), is(MorrisBotLevel.PERFECT));
        assertThat(MorrisBotLevel.fromOption("greedy"), is(MorrisBotLevel.GREEDY));
        assertThat(MorrisBotLevel.fromOption(null), is(MorrisBotLevel.DEFAULT));
        assertThat(MorrisBotLevel.fromOption("grandmaster"), is(MorrisBotLevel.DEFAULT));
    }

    @Test
    void eachLevelBuildsItsStrategy() {
        assertThat(MorrisBotLevel.RANDOM.bot(Positions.FIRST), instanceOf(RandomBot.class));
        assertThat(MorrisBotLevel.GREEDY.bot(Positions.FIRST), instanceOf(GreedyBot.class));
        assertThat(MorrisBotLevel.PERFECT.bot(Positions.FIRST), instanceOf(PerfectBot.class));
    }
}

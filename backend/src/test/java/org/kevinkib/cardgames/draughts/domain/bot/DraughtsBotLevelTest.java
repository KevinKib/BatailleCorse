package org.kevinkib.cardgames.draughts.domain.bot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.draughts.domain.DraughtsFactory;

class DraughtsBotLevelTest {

    @Test
    void keysAreTheSeatOptionValuesWeakestFirst() {
        assertThat(Arrays.stream(DraughtsBotLevel.values()).map(DraughtsBotLevel::key))
                .containsExactly("random", "greedy", "expert");
    }

    @Test
    void theFactoryAdvertisesTheSameLevelsAndDefault() {
        assertThat(DraughtsFactory.LEVELS).isEqualTo(Arrays.stream(DraughtsBotLevel.values())
                .map(DraughtsBotLevel::key).toList());
        assertThat(DraughtsFactory.DEFAULT_LEVEL).isEqualTo(DraughtsBotLevel.DEFAULT.key());
        assertThat(DraughtsBotLevel.DEFAULT).isEqualTo(DraughtsBotLevel.GREEDY);
    }

    @Test
    void anUnknownOrAbsentValueGivesTheDefault() {
        assertThat(DraughtsBotLevel.fromOption("expert")).isEqualTo(DraughtsBotLevel.EXPERT);
        assertThat(DraughtsBotLevel.fromOption("random")).isEqualTo(DraughtsBotLevel.RANDOM);
        assertThat(DraughtsBotLevel.fromOption("godlike")).isEqualTo(DraughtsBotLevel.DEFAULT);
        assertThat(DraughtsBotLevel.fromOption(null)).isEqualTo(DraughtsBotLevel.DEFAULT);
    }

    @Test
    void eachLevelBuildsItsStrategy() {
        var budget = ExpertDraughtsBot.Budget.DEFAULT;

        assertThat(DraughtsBotLevel.RANDOM.bot(BotFixtures.CONSTANT, budget)).isInstanceOf(RandomDraughtsBot.class);
        assertThat(DraughtsBotLevel.GREEDY.bot(BotFixtures.CONSTANT, budget)).isInstanceOf(GreedyDraughtsBot.class);
        assertThat(DraughtsBotLevel.EXPERT.bot(BotFixtures.CONSTANT, budget)).isInstanceOf(ExpertDraughtsBot.class);
    }
}

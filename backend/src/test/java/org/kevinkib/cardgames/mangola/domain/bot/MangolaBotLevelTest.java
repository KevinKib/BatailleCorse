package org.kevinkib.cardgames.mangola.domain.bot;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class MangolaBotLevelTest {

    private static final ExpertMangolaBot.Budget BUDGET = new ExpertMangolaBot.Budget(0, 100);

    @Test
    void theKeysAreRandomGreedyAndExpertAndTheDefaultIsGreedy() {
        assertThat(MangolaBotLevel.RANDOM.key(), is("random"));
        assertThat(MangolaBotLevel.GREEDY.key(), is("greedy"));
        assertThat(MangolaBotLevel.EXPERT.key(), is("expert"));
        assertThat(MangolaBotLevel.DEFAULT, is(MangolaBotLevel.GREEDY));
    }

    @Test
    void anOptionValueGivesItsLevelAndAnythingElseTheDefault() {
        assertThat(MangolaBotLevel.fromOption("expert"), is(MangolaBotLevel.EXPERT));
        assertThat(MangolaBotLevel.fromOption("random"), is(MangolaBotLevel.RANDOM));
        assertThat(MangolaBotLevel.fromOption(null), is(MangolaBotLevel.GREEDY));
        assertThat(MangolaBotLevel.fromOption("nonsense"), is(MangolaBotLevel.GREEDY));
    }

    @Test
    void eachLevelBuildsItsOwnStrategy() {
        assertThat(MangolaBotLevel.RANDOM.bot(BotFixtures.CONSTANT, BUDGET), instanceOf(RandomMangolaBot.class));
        assertThat(MangolaBotLevel.GREEDY.bot(BotFixtures.CONSTANT, BUDGET), instanceOf(GreedyMangolaBot.class));
        assertThat(MangolaBotLevel.EXPERT.bot(BotFixtures.CONSTANT, BUDGET), instanceOf(ExpertMangolaBot.class));
    }
}

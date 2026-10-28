package org.kevinkib.cardgames.ninetynine.domain.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.kevinkib.cardgames.ninetynine.domain.CardEffect;
import org.kevinkib.cardgames.ninetynine.domain.bot.NinetyNineDecision.Play;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Whatever the level, a bot only ever plays a legal card with a legal Ace value. */
class NinetyNineBotLegalityTest {

    @ParameterizedTest
    @EnumSource(NinetyNineBotLevel.class)
    void everyLevelPlaysASafeCardOfItsHandOnAThousandRandomViews(NinetyNineBotLevel level) {
        Random viewRandom = new Random(7);
        NinetyNineBotStrategy strategy = NinetyNineBots.forLevel(level, new Random(11));

        for (int i = 0; i < 1000; i++) {
            NinetyNineBotView view = NinetyNineBotViews.randomView(viewRandom);

            Play play = (Play) strategy.decide(view);

            Card card = play.card();
            assertThat(view.hand(), hasItem(card));
            assertThat(CardEffect.isSafe(view.total(), card), is(true));
            if (card.getRank() == FrenchRank.ACE) {
                assertThat(CardEffect.aceValues(view.total()), hasItem(play.aceValue()));
            } else {
                assertThat(play.aceValue(), nullValue());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(NinetyNineBotLevel.class)
    void aStuckSeatPlaysTheCardThatGoesLeastOver99(NinetyNineBotLevel level) {
        NinetyNineBotStrategy strategy = NinetyNineBots.forLevel(level, new Random(1));
        NinetyNineBotView stuck = NinetyNineBotViews.view(3, 99, "AH 2H 3H", "", 30);

        Play play = (Play) strategy.decide(stuck);

        assertThat(play.card(), is(NinetyNineBotViews.c("AH")));
        assertThat(play.aceValue(), is(1));
    }

    @ParameterizedTest
    @EnumSource(NinetyNineBotLevel.class)
    void aStuckSeatAlwaysPlaysACardOfItsHand(NinetyNineBotLevel level) {
        Random viewRandom = new Random(5);
        NinetyNineBotStrategy strategy = NinetyNineBots.forLevel(level, new Random(3));
        for (int i = 0; i < 200; i++) {
            NinetyNineBotView view = NinetyNineBotViews.randomStuckView(viewRandom);
            assertThat(CardEffect.safeCards(view.total(), view.hand()).isEmpty(), is(true));

            Play play = (Play) strategy.decide(view);

            assertThat(view.hand(), hasItem(play.card()));
            if (play.card().getRank() == FrenchRank.ACE) {
                assertThat(play.aceValue() == 1 || play.aceValue() == 11, is(true));
            } else {
                assertThat(play.aceValue(), nullValue());
            }
        }
    }

    @Test
    void theLevelsMapToTheirStrategies() {
        assertThat(NinetyNineBots.forLevel(NinetyNineBotLevel.EASY, new Random(1)),
                instanceOf(RandomNinetyNineBot.class));
        assertThat(NinetyNineBots.forLevel(NinetyNineBotLevel.MEDIUM, new Random(1)),
                instanceOf(GreedyNinetyNineBot.class));
        assertThat(NinetyNineBots.forLevel(NinetyNineBotLevel.HARD, new Random(1)),
                instanceOf(SamplingNinetyNineBot.class));
    }

    @Test
    void theTuningIsTwoHundredSamplesUnderTwentyMilliseconds() {
        assertThat(NinetyNineBotTuning.SAMPLES, is(200));
        assertThat(NinetyNineBotTuning.CAP_NANOS, is(20_000_000L));
    }
}

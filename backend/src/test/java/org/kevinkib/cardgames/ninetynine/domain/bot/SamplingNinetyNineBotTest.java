package org.kevinkib.cardgames.ninetynine.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.ninetynine.domain.bot.NinetyNineDecision.Play;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

class SamplingNinetyNineBotTest {

    @Test
    void isDeterministicWithASeededRandom() {
        Random viewRandom = new Random(5);
        // A frozen clock: the time cap must not decide how many samples each bot gets (it made this test flaky).
        SamplingNinetyNineBot first = new SamplingNinetyNineBot(new Random(42), NinetyNineBotTuning.SAMPLES,
                NinetyNineBotTuning.CAP_NANOS, () -> 0L);
        SamplingNinetyNineBot second = new SamplingNinetyNineBot(new Random(42), NinetyNineBotTuning.SAMPLES,
                NinetyNineBotTuning.CAP_NANOS, () -> 0L);

        for (int i = 0; i < 40; i++) {
            NinetyNineBotView view = NinetyNineBotViews.randomView(viewRandom);

            assertThat(first.decide(view), is(second.decide(view)));
        }
    }

    @Test
    void playsTheCardThatLeavesTheNextSeatCertainlyStuck() {
        // Total 90, two seats. The only unseen cards are four 8s and two Jacks (the opponent holds three of
        // them, three stay in the stock): after the 2 the total is 92 and an 8 or a Jack goes over, so the
        // opponent is stuck whatever the deal; after the 9 or the 4 the total stays 90 and an 8 is playable
        // (the opponent always holds one, there are only two Jacks).
        List<Card> hand = NinetyNineBotViews.cards("9H 2H 4H");
        List<Card> unseen = NinetyNineBotViews.cards("8H 8D 8C 8S JH JD");
        List<Card> pile = new ArrayList<>(NinetyNineBotViews.fullDeck());
        pile.removeAll(hand);
        pile.removeAll(unseen);
        NinetyNineBotView view = NinetyNineBotViews.view(2, 90, hand, pile, 3,
                org.kevinkib.cardgames.ninetynine.domain.Direction.CLOCKWISE);

        for (long seed = 0; seed < 5; seed++) {
            Play play = (Play) new SamplingNinetyNineBot(new Random(seed)).decide(view);

            assertThat(play.card(), is(NinetyNineBotViews.c("2H")));
        }
    }

    @Test
    void stopsSamplingWhenTheCapIsReached() {
        AtomicLong clock = new AtomicLong();
        // every reading of the clock is 5 ms later than the previous one: the 20 ms cap ends the loop early
        SamplingNinetyNineBot bot = new SamplingNinetyNineBot(new Random(1), 200, 20_000_000L,
                () -> clock.addAndGet(5_000_000L));

        bot.decide(NinetyNineBotViews.view(4, 60, "2H 5D 7S", "", 20));

        assertThat(bot.lastSampleCount(), greaterThanOrEqualTo(1));
        assertThat(bot.lastSampleCount(), lessThan(10));
    }

    @Test
    void usesAllTheSamplesWhenTimeIsNotAnIssue() {
        SamplingNinetyNineBot bot = new SamplingNinetyNineBot(new Random(1), 200, Long.MAX_VALUE / 2,
                System::nanoTime);

        bot.decide(NinetyNineBotViews.view(4, 60, "2H 5D 7S", "", 20));

        assertThat(bot.lastSampleCount(), is(200));
    }

    @Test
    void aTwelveSeatDecisionStaysNearTheCap() {
        SamplingNinetyNineBot bot = new SamplingNinetyNineBot(new Random(2));
        NinetyNineBotView view = NinetyNineBotViews.view(12, 94, "AH 2D 6S", "", 16);
        bot.decide(view); // warm-up

        long start = System.nanoTime();
        bot.decide(view);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        // the cap is 20 ms; the margin absorbs a loaded CI machine
        assertThat(elapsedMillis, lessThanOrEqualTo(150L));
    }
}

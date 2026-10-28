package org.kevinkib.cardgames.ninetynine.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.ninetynine.domain.bot.NinetyNineDecision.Play;
import org.kevinkib.cards.domain.Card;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

class RandomNinetyNineBotTest {

    @Test
    void picksEverySafeCardAndNeverAnUnsafeOne() {
        RandomNinetyNineBot bot = new RandomNinetyNineBot(new Random(3));
        // 8H goes over 99 at 95 and is never played
        NinetyNineBotView view = NinetyNineBotViews.view(4, 95, "8H 2D 4S", "", 20);
        Set<Card> played = new HashSet<>();

        for (int i = 0; i < 300; i++) {
            played.add(((Play) bot.decide(view)).card());
        }

        assertThat(played, containsInAnyOrder(NinetyNineBotViews.c("2D"), NinetyNineBotViews.c("4S")));
    }

    @Test
    void picksBothValuesOfAnAceWhenBothAreAllowed() {
        RandomNinetyNineBot bot = new RandomNinetyNineBot(new Random(3));
        NinetyNineBotView view = NinetyNineBotViews.view(4, 40, "AH", "", 20);
        Set<Integer> values = new HashSet<>();

        for (int i = 0; i < 200; i++) {
            values.add(((Play) bot.decide(view)).aceValue());
        }

        assertThat(values, containsInAnyOrder(1, 11));
    }

    @Test
    void theSameSeedGivesTheSameChoices() {
        NinetyNineBotView view = NinetyNineBotViews.view(4, 30, "2H 5D AS", "", 20);
        RandomNinetyNineBot first = new RandomNinetyNineBot(new Random(99));
        RandomNinetyNineBot second = new RandomNinetyNineBot(new Random(99));

        for (int i = 0; i < 20; i++) {
            assertThat(first.decide(view), is(second.decide(view)));
        }
    }
}

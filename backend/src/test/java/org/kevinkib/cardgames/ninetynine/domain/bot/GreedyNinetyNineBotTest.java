package org.kevinkib.cardgames.ninetynine.domain.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kevinkib.cardgames.ninetynine.domain.bot.NinetyNineDecision.Play;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** The medium bot: push the total up with a plain card, keep the escape cards (K, 10, 4, 9) for later. */
class GreedyNinetyNineBotTest {

    private final GreedyNinetyNineBot bot = new GreedyNinetyNineBot();

    @ParameterizedTest(name = "total {0}, hand [{1}] -> {2}")
    @CsvSource({
            // the highest plain card, the K kept
            "50, 'KH 7D 3S', 7D",
            // a plain card is preferred over a 9
            "50, 'KH 9D 3S', 3S",
            // an unsafe card is not a candidate, the highest safe plain card wins
            "95, '8H 9D 2S', 2S",
            // no plain card is safe: 9 first, then 4, then 10, the K last
            "99, 'KH 4H 9H', 9H",
            "99, 'KH 4H 10H', 4H",
            "99, 'KH 10H 3H', 10H",
            "99, 'KH 10H 4H', 4H",
            "98, 'KH 10H 2H', 10H",
            "99, 'KH AH 2H', KH",
            // the highest plain card is played even when it is a Jack over a 7
            "60, '7D JC 2H', JC",
            // a 10 is an escape card, not a plain one
            "60, '10D 2C 9S', 2C",
    })
    void playsTheDocumentedCard(int total, String hand, String expected) {
        Play play = (Play) bot.decide(NinetyNineBotViews.view(4, total, hand, "", 20));

        assertThat(play.card(), is(NinetyNineBotViews.c(expected)));
    }

    @ParameterizedTest(name = "total {0}, hand [{1}] -> Ace {2}")
    @CsvSource({
            // 11 pushes higher than any other plain card
            "50, 'AH 3S 9D', 11",
            "80, 'AH 9D 8S', 11",
            // 11 would go over: the Ace counts 1 and still beats the others
            "90, 'AH 9D 4S', 1",
            "95, 'AH 9D 4S', 1",
            // 11 (94) beats the 7 (90)
            "83, 'AH 9D 7D', 11",
    })
    void picksTheAceValueThatPushesTheTotalHighest(int total, String hand, int expectedValue) {
        Play play = (Play) bot.decide(NinetyNineBotViews.view(4, total, hand, "", 20));

        assertThat(play.card(), is(NinetyNineBotViews.c("AH")));
        assertThat(play.aceValue(), is(expectedValue));
    }

    @Test
    void isDeterministic() {
        NinetyNineBotView view = NinetyNineBotViews.view(5, 71, "6H 6D 6C", "", 20);

        assertThat(bot.decide(view), is(bot.decide(view)));
        assertThat(((Play) bot.decide(view)).card(), is(NinetyNineBotViews.c("6H")));
    }
}

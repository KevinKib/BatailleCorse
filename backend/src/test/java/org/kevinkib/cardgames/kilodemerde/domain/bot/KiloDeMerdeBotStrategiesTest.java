package org.kevinkib.cardgames.kilodemerde.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeRules;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeck;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.kilodemerde.domain.bot.KiloBotViews.c;
import static org.kevinkib.cardgames.kilodemerde.domain.bot.KiloBotViews.pass;
import static org.kevinkib.cardgames.kilodemerde.domain.bot.KiloBotViews.view;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KiloDeMerdeBotStrategiesTest {

    private static List<List<Card>> randomHands(int count) {
        Random random = new Random(7);
        List<List<Card>> hands = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            List<Card> deck = new ArrayList<>(KiloDeck.handDeck(3 + random.nextInt(8)));
            Collections.shuffle(deck, random);
            hands.add(deck.subList(0, 4));
        }
        return hands;
    }

    @Test
    void everyLevelReturnsACardOfItsHandOnARandomView() {
        for (KiloDeMerdeBotLevel level : KiloDeMerdeBotLevel.values()) {
            KiloDeMerdeBotStrategy bot = KiloDeMerdeBots.forLevel(level, new Random(1));
            for (List<Card> hand : randomHands(1000)) {
                assertThat(level + " " + hand, hand.contains(bot.choosePass(view(hand))), is(true));
            }
        }
    }

    @Test
    void everyLevelRefusesAnEmptyHand() {
        for (KiloDeMerdeBotLevel level : KiloDeMerdeBotLevel.values()) {
            KiloDeMerdeBotStrategy bot = KiloDeMerdeBots.forLevel(level, new Random(1));
            assertThrows(IllegalStateException.class, () -> bot.choosePass(view("")));
        }
    }

    @Test
    void easyPassesEveryCardOfItsHandEventually() {
        KiloDeMerdeBotStrategy bot = KiloDeMerdeBots.forLevel(KiloDeMerdeBotLevel.EASY, new Random(3));
        Set<Card> passed = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            passed.add(bot.choosePass(view("KS KH QD 2C")));
        }
        assertThat(passed.size(), is(4));
    }

    @Test
    void easyIsDeterministicWithASeededGenerator() {
        List<Card> first = new ArrayList<>();
        List<Card> second = new ArrayList<>();
        KiloDeMerdeBotStrategy a = KiloDeMerdeBots.forLevel(KiloDeMerdeBotLevel.EASY, new Random(9));
        KiloDeMerdeBotStrategy b = KiloDeMerdeBots.forLevel(KiloDeMerdeBotLevel.EASY, new Random(9));
        for (int i = 0; i < 20; i++) {
            first.add(a.choosePass(view("KS KH QD 2C")));
            second.add(b.choosePass(view("KS KH QD 2C")));
        }
        assertThat(first, is(second));
    }

    @Test
    void mediumEqualsTheDefaultPassOnRandomViews() {
        KiloDeMerdeBotStrategy bot = KiloDeMerdeBots.forLevel(KiloDeMerdeBotLevel.MEDIUM, new Random(1));
        for (List<Card> hand : randomHands(1000)) {
            assertThat(bot.choosePass(view(hand)), is(KiloDeMerdeRules.defaultPass(hand)));
        }
    }

    @Test
    void mediumPassesFromTheRankItHoldsFewestOf() {
        KiloDeMerdeBotStrategy bot = KiloDeMerdeBots.forLevel(KiloDeMerdeBotLevel.MEDIUM, new Random(1));
        assertThat(bot.choosePass(view("KS KH KD 2C")), is(c("2C")));
    }

    @Test
    void hardKeepsItsRankAndPassesTheOddCardOut() {
        KiloDeMerdeBotStrategy bot = KiloDeMerdeBots.forLevel(KiloDeMerdeBotLevel.HARD, new Random(1));
        assertThat(bot.choosePass(view("KS KH KD 2C")), is(c("2C")));
    }

    @Test
    void hardNeverPassesItsTargetRankWhenItHasAChoice() {
        KiloDeMerdeBotStrategy bot = KiloDeMerdeBots.forLevel(KiloDeMerdeBotLevel.HARD, new Random(1));
        for (List<Card> hand : randomHands(1000)) {
            Card passed = bot.choosePass(view(hand));
            long best = hand.stream().mapToLong(card -> hand.stream().filter(o -> o.isSameRankAs(card)).count())
                    .max().orElseThrow();
            long passedCount = hand.stream().filter(o -> o.isSameRankAs(passed)).count();
            // a card of the most held rank is only passed when every card is of that rank
            assertThat(hand.toString(), passedCount < best || best == hand.size(), is(true));
        }
    }

    @Test
    void hardSwitchesToARankThatKeepsArrivingWhenOwnCountsTie() {
        // 2-2 split: own count ties; a Queen received twice (2 + 0.5 x 2 = 3) beats the Kings (2 + 0)
        KiloDeMerdeBotStrategy bot = KiloDeMerdeBots.forLevel(KiloDeMerdeBotLevel.HARD, new Random(1));
        KiloDeMerdeBotView view = view("KS KH QD QC", List.of(pass(1, "2C", "QS"), pass(2, "3C", "QH")));
        assertThat(bot.choosePass(view).isSameRankAs(c("KS")), is(true));
    }

    @Test
    void hardPassesFirstARankItHasPassedBefore() {
        // 2-1-1: target is the Kings; of the two odd cards it passed a Queen before, so a Queen goes again
        KiloDeMerdeBotStrategy bot = KiloDeMerdeBots.forLevel(KiloDeMerdeBotLevel.HARD, new Random(1));
        KiloDeMerdeBotView view = view("KS KH QD JC", List.of(pass(1, "QS", "9C")));
        assertThat(bot.choosePass(view), is(c("QD")));
    }

    @Test
    void hardWithoutHistoryPassesTheRankHeldFewestOf() {
        KiloDeMerdeBotStrategy bot = KiloDeMerdeBots.forLevel(KiloDeMerdeBotLevel.HARD, new Random(1));
        assertThat(bot.choosePass(view("KS KH KD QC")), is(c("QC")));
    }

    @Test
    void hardWithAFourOfAKindStillPassesACardOfItsHand() {
        KiloDeMerdeBotStrategy bot = KiloDeMerdeBots.forLevel(KiloDeMerdeBotLevel.HARD, new Random(1));
        List<Card> hand = List.of(c("KS"), c("KH"), c("KD"), c("KC"));
        assertThat(hand.contains(bot.choosePass(view(hand))), is(true));
    }
}

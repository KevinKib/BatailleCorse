package org.kevinkib.cardgames.blackjack.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.blackjack.domain.Action;
import org.kevinkib.cardgames.blackjack.domain.BlackjackOptions.MaxBet;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kevinkib.cardgames.blackjack.domain.bot.BlackjackBotViews.betting;
import static org.kevinkib.cardgames.blackjack.domain.bot.BlackjackBotViews.hand;
import static org.kevinkib.cardgames.blackjack.domain.bot.BlackjackBotViews.playing;

class BlackjackBotStrategiesTest {

    private static final List<Action> ALL = List.of(Action.HIT, Action.STAND, Action.DOUBLE, Action.SPLIT);

    // ---- medium

    @Test
    void mediumBetsTenCappedByTheStackAndTheOption() {
        BlackjackBotStrategy medium = new BasicStrategyBlackjackBot();
        assertEquals(10, medium.decideBet(betting(10, MaxBet.FIFTY, 100, 100)));
        assertEquals(7, medium.decideBet(betting(10, MaxBet.FIFTY, 7, 100)));
        assertEquals(10, medium.decideBet(betting(10, MaxBet.HUNDRED, 100)));
    }

    @Test
    void mediumPlaysTheBasicStrategy() {
        BlackjackBotStrategy medium = new BasicStrategyBlackjackBot();
        assertEquals(Action.DOUBLE, medium.decidePlay(playing(5, hand(11, false, false, 10), 6, ALL, 90, 100)));
        assertEquals(Action.SPLIT, medium.decidePlay(playing(5, hand(16, false, true, 10), 10, ALL, 90, 100)));
        assertEquals(Action.STAND, medium.decidePlay(playing(5, hand(17, false, false, 10), 10, ALL, 90, 100)));
    }

    // ---- easy

    @Test
    void easyIsDeterministicWithASeed() {
        List<Integer> first = bets(new RandomBlackjackBot(new Random(7)));
        List<Integer> second = bets(new RandomBlackjackBot(new Random(7)));
        assertEquals(first, second);
        assertTrue(first.stream().distinct().count() > 3, "bets vary");
    }

    private static List<Integer> bets(BlackjackBotStrategy bot) {
        List<Integer> bets = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            bets.add(bot.decideBet(betting(10, MaxBet.FIFTY, 100, 100)));
        }
        return bets;
    }

    // ---- legality of every level

    @Test
    void everyLevelIsLegalOnRandomViews() {
        Random views = new Random(11);
        for (BlackjackBotLevel level : BlackjackBotLevel.values()) {
            BlackjackBotStrategy bot = BlackjackBots.forLevel(level, new Random(3));
            for (int i = 0; i < 1000; i++) {
                int stack = 1 + views.nextInt(150);
                int other = 1 + views.nextInt(300);
                MaxBet maxBet = MaxBet.values()[views.nextInt(3)];
                int left = 1 + views.nextInt(10);
                BlackjackBotView bet = views.nextBoolean()
                        ? betting(left, maxBet, stack, other) : betting(left, maxBet, stack);
                int amount = bot.decideBet(bet);
                assertTrue(amount >= bet.minBet() && amount <= bet.maxBet(), level + " bet " + amount);

                boolean soft = views.nextBoolean();
                int total = soft ? 13 + views.nextInt(9) : 4 + views.nextInt(18);
                boolean pair = views.nextInt(4) == 0 && !soft && total % 2 == 0;
                List<Action> actions = new ArrayList<>(List.of(Action.HIT, Action.STAND));
                if (views.nextBoolean()) {
                    actions.add(Action.DOUBLE);
                }
                if (pair && views.nextBoolean()) {
                    actions.add(Action.SPLIT);
                }
                BlackjackBotView play = playing(left, hand(total, soft, pair, 1 + views.nextInt(20)),
                        2 + views.nextInt(10), actions, stack, other);
                assertTrue(actions.contains(bot.decidePlay(play)), level + " play");
            }
        }
    }

    @Test
    void refusesWhenThereIsNothingToDecide() {
        BlackjackBotView playingView = playing(5, hand(11, false, false, 10), 6, ALL, 90, 100);
        BlackjackBotView bettingView = betting(5, MaxBet.FIFTY, 90, 100);
        for (BlackjackBotLevel level : BlackjackBotLevel.values()) {
            BlackjackBotStrategy bot = BlackjackBots.forLevel(level, new Random(1));
            assertThrows(IllegalStateException.class, () -> bot.decideBet(playingView));
            assertThrows(IllegalStateException.class, () -> bot.decidePlay(bettingView));
        }
    }

    @Test
    void levelsMapToTheirStrategy() {
        Random random = new Random(1);
        assertSame(RandomBlackjackBot.class, BlackjackBots.forLevel(BlackjackBotLevel.EASY, random).getClass());
        assertSame(BasicStrategyBlackjackBot.class,
                BlackjackBots.forLevel(BlackjackBotLevel.MEDIUM, random).getClass());
        assertSame(TournamentBlackjackBot.class, BlackjackBots.forLevel(BlackjackBotLevel.HARD, random).getClass());
    }
}

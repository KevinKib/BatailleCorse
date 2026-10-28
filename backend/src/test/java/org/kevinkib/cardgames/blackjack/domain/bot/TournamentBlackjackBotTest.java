package org.kevinkib.cardgames.blackjack.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.blackjack.domain.Action;
import org.kevinkib.cardgames.blackjack.domain.BlackjackOptions.MaxBet;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.kevinkib.cardgames.blackjack.domain.bot.BlackjackBotViews.betting;
import static org.kevinkib.cardgames.blackjack.domain.bot.BlackjackBotViews.hand;
import static org.kevinkib.cardgames.blackjack.domain.bot.BlackjackBotViews.playing;

/** One hand-written view per row of the betting table (spec 10.3) and per last-hand deviation. */
class TournamentBlackjackBotTest {

    private static final List<Action> ALL = List.of(Action.HIT, Action.STAND, Action.DOUBLE);
    private static final List<Action> NO_DOUBLE = List.of(Action.HIT, Action.STAND);

    private final TournamentBlackjackBot bot = new TournamentBlackjackBot();

    // ---- betting

    @Test
    void soloFallsBackToTheFlatBet() {
        assertEquals(10, bot.decideBet(betting(5, MaxBet.FIFTY, 100)));
    }

    @Test
    void unreachableLeadBetsOne() {
        // lead 60 > cap 50 x 1 hand left... use 2 hands left: lead 150 > 50 x 2
        assertEquals(1, bot.decideBet(betting(2, MaxBet.FIFTY, 200, 50)));
    }

    @Test
    void leadingOnTheLastTwoHandsMatchesTheSecondsLargestBet() {
        // lead 20, secondCap = min(50, 80) = 50, 20 <= 100: min(cap 50, 50) = 50
        assertEquals(50, bot.decideBet(betting(2, MaxBet.FIFTY, 100, 80)));
        // second only has 15: secondCap 15, lead 85 > 30 but also not > cap x left (50 x 2 = 100): bet 1
        assertEquals(1, bot.decideBet(betting(2, MaxBet.FIFTY, 100, 15)));
        // second has 30: secondCap 30, lead 70 > 60: bet 1
        assertEquals(1, bot.decideBet(betting(2, MaxBet.FIFTY, 100, 30)));
        // second has 60: secondCap 50, lead 40 <= 100: min(cap 50, 50) = 50
        assertEquals(50, bot.decideBet(betting(2, MaxBet.FIFTY, 100, 60)));
        // my stack is the small cap: lead 10, cap 30, secondCap 30: 30
        assertEquals(30, bot.decideBet(betting(1, MaxBet.FIFTY, 30, 20)));
    }

    @Test
    void behindOnTheLastThreeHandsBetsTheCap() {
        assertEquals(50, bot.decideBet(betting(3, MaxBet.FIFTY, 90, 100)));
        assertEquals(25, bot.decideBet(betting(1, MaxBet.TWENTY_FIVE, 90, 91)));
        assertEquals(40, bot.decideBet(betting(2, MaxBet.FIFTY, 40, 100)));
    }

    @Test
    void farBehindBetsTheCap() {
        // 6 hands left: behind by more than 50 x 5 = 250 is out of reach of the table, test with a smaller cap
        // maxBet 25, 5 hands left: behind by more than 25 x 4 = 100
        assertEquals(25, bot.decideBet(betting(5, MaxBet.TWENTY_FIVE, 50, 160)));
    }

    @Test
    void otherwiseTenPlusFivePerTwentyFivePointsBehind() {
        assertEquals(10, bot.decideBet(betting(8, MaxBet.FIFTY, 100, 100)));
        assertEquals(10, bot.decideBet(betting(8, MaxBet.FIFTY, 120, 100)));
        assertEquals(10, bot.decideBet(betting(8, MaxBet.FIFTY, 90, 100)));
        assertEquals(15, bot.decideBet(betting(8, MaxBet.FIFTY, 75, 100)));
        assertEquals(20, bot.decideBet(betting(8, MaxBet.FIFTY, 50, 100)));
    }

    @Test
    void theBetIsCappedByTheStack() {
        assertEquals(4, bot.decideBet(betting(1, MaxBet.FIFTY, 4, 100)));
    }

    // ---- play

    @Test
    void playsTheBasicStrategyBeforeTheLastHand() {
        assertEquals(Action.STAND,
                bot.decidePlay(playing(5, hand(12, false, false, 10), 4, ALL, 90, 100)));
        assertEquals(Action.HIT,
                bot.decidePlay(playing(5, hand(15, false, false, 10), 10, ALL, 20, 100)));
    }

    @Test
    void lastHandBehindWhereOnlyADoubledWinOvertakesDoublesLowHardAndSoftHands() {
        // mine 90 (bet 10 already taken), best 120: a win ends at 110, a doubled win at 120 is not > 120 ... use 119
        assertEquals(Action.DOUBLE,
                bot.decidePlay(playing(1, hand(8, false, false, 10), 10, ALL, 90, 119)));
        assertEquals(Action.DOUBLE,
                bot.decidePlay(playing(1, hand(18, true, false, 10), 10, ALL, 90, 119)));
        // a hard 12 is not doubled: basic strategy (stand against a 4)
        assertEquals(Action.STAND,
                bot.decidePlay(playing(1, hand(12, false, false, 10), 4, ALL, 90, 119)));
        // double not allowed: basic strategy
        assertEquals(Action.HIT,
                bot.decidePlay(playing(1, hand(8, false, false, 10), 10, NO_DOUBLE, 90, 119)));
    }

    @Test
    void lastHandBehindWhereEvenADoubledWinFallsShortHitsStiffHardHands() {
        // mine 90, best 200: nothing overtakes; hard 14 against a 5 would stand, here it hits
        assertEquals(Action.HIT,
                bot.decidePlay(playing(1, hand(14, false, false, 10), 5, ALL, 90, 200)));
        // 17 still stands
        assertEquals(Action.STAND,
                bot.decidePlay(playing(1, hand(17, false, false, 10), 5, ALL, 90, 200)));
    }

    @Test
    void lastHandBehindWhereAPlainWinOvertakesKeepsTheBasicStrategy() {
        // mine 100, best 105: a win ends at 120
        assertEquals(Action.STAND,
                bot.decidePlay(playing(1, hand(13, false, false, 10), 5, ALL, 100, 105)));
    }

    @Test
    void lastHandLeadingStandsOnTwelveOrMoreAgainstAWeakDealerCard() {
        // basic strategy would hit a 12 against a 2; leading, stand
        assertEquals(Action.STAND,
                bot.decidePlay(playing(1, hand(12, false, false, 10), 2, ALL, 150, 100)));
        // against a 10 the basic strategy applies
        assertEquals(Action.HIT,
                bot.decidePlay(playing(1, hand(12, false, false, 10), 10, ALL, 150, 100)));
    }

    @Test
    void soloAlwaysPlaysTheBasicStrategy() {
        assertEquals(Action.HIT,
                bot.decidePlay(playing(1, hand(12, false, false, 10), 2, ALL, 90)));
    }
}

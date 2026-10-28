package org.kevinkib.cardgames.blackjack.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.blackjack.domain.Action;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BasicStrategyTest {

    private static final List<Action> ALL = List.of(Action.HIT, Action.STAND, Action.DOUBLE, Action.SPLIT);
    private static final List<Action> NO_DOUBLE = List.of(Action.HIT, Action.STAND);

    /** Dealer up cards in column order: 2 to 10, then the Ace. */
    private static final int[] UPS = {2, 3, 4, 5, 6, 7, 8, 9, 10, 11};

    private static void row(int total, boolean soft, boolean pair, String expected, List<Action> available) {
        for (int i = 0; i < UPS.length; i++) {
            Action want = switch (expected.charAt(i)) {
                case 'H' -> Action.HIT;
                case 'S' -> Action.STAND;
                case 'D' -> available.contains(Action.DOUBLE) ? Action.DOUBLE : Action.HIT;
                case 'd' -> available.contains(Action.DOUBLE) ? Action.DOUBLE : Action.STAND;
                case 'P' -> Action.SPLIT;
                default -> throw new AssertionError();
            };
            assertEquals(want, BasicStrategy.decide(total, soft, pair, UPS[i], available),
                    () -> "total " + total + " soft " + soft + " pair " + pair + " up " + UPS[i]);
        }
    }

    @Test
    void hardTotals() {
        for (int total = 4; total <= 8; total++) {
            row(total, false, false, "HHHHHHHHHH", ALL);
        }
        row(9, false, false, "HDDDDHHHHH", ALL);
        row(10, false, false, "DDDDDDDDHH", ALL);
        row(11, false, false, "DDDDDDDDDH", ALL);
        row(12, false, false, "HHSSSHHHHH", ALL);
        for (int total = 13; total <= 16; total++) {
            row(total, false, false, "SSSSSHHHHH", ALL);
        }
        for (int total = 17; total <= 21; total++) {
            row(total, false, false, "SSSSSSSSSS", ALL);
        }
    }

    @Test
    void hardDoubleFallsBackToHitWhenNotAllowed() {
        row(9, false, false, "HDDDDHHHHH", NO_DOUBLE);
        row(11, false, false, "DDDDDDDDDH", NO_DOUBLE);
    }

    @Test
    void softTotals() {
        row(13, true, false, "HHHDDHHHHH", ALL);
        row(14, true, false, "HHHDDHHHHH", ALL);
        row(15, true, false, "HHDDDHHHHH", ALL);
        row(16, true, false, "HHDDDHHHHH", ALL);
        row(17, true, false, "HDDDDHHHHH", ALL);
        row(18, true, false, "SddddSSHHH", ALL);
        for (int total = 19; total <= 21; total++) {
            row(total, true, false, "SSSSSSSSSS", ALL);
        }
    }

    @Test
    void softDoubleFallsBackToHitOrStand() {
        row(17, true, false, "HDDDDHHHHH", NO_DOUBLE);
        row(18, true, false, "SddddSSHHH", NO_DOUBLE);
    }

    @Test
    void pairs() {
        row(2, true, true, "PPPPPPPPPP", ALL);
        row(16, false, true, "PPPPPPPPPP", ALL);
        row(4, false, true, "PPPPPPHHHH", ALL);
        row(6, false, true, "PPPPPPHHHH", ALL);
        row(8, false, true, "HHHPPHHHHH", ALL);
        row(10, false, true, "DDDDDDDDHH", ALL);
        row(12, false, true, "PPPPPHHHHH", ALL);
        row(14, false, true, "PPPPPPHHHH", ALL);
        row(18, false, true, "PPPPPSPPSS", ALL);
        row(20, false, true, "SSSSSSSSSS", ALL);
    }

    @Test
    void pairNotSplittableFallsBackToTheTotal() {
        List<Action> noSplit = List.of(Action.HIT, Action.STAND, Action.DOUBLE);
        row(16, false, true, "SSSSSHHHHH", noSplit);
        row(12, true, true, "HHHHHHHHHH", noSplit);
        row(8, false, true, "HHHHHHHHHH", noSplit);
    }

    @Test
    void neverAnActionOutsideTheAvailableOnes() {
        List<Action> onlyStand = List.of(Action.STAND);
        assertEquals(Action.STAND, BasicStrategy.decide(5, false, false, 10, onlyStand));
        List<Action> onlyHit = List.of(Action.HIT);
        assertEquals(Action.HIT, BasicStrategy.decide(18, false, false, 6, onlyHit));
    }
}

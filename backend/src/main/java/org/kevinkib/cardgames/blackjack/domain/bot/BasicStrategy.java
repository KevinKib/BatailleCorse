package org.kevinkib.cardgames.blackjack.domain.bot;

import org.kevinkib.cardgames.blackjack.domain.Action;

import java.util.List;

/**
 * Basic strategy for 6 decks, dealer stands on all 17, double after split, no surrender, dealer peek (spec section
 * 10.2). Pure tables: columns are the dealer up card 2 to 10 then Ace. H hit, S stand, D double else hit, d double
 * else stand, P split.
 */
public final class BasicStrategy {

    private static final String HARD_8_OR_LESS = "HHHHHHHHHH";
    private static final String HARD_17_PLUS = "SSSSSSSSSS";

    /** Hard totals 9 to 16. */
    private static final String[] HARD = {
            "HDDDDHHHHH", // 9
            "DDDDDDDDHH", // 10
            "DDDDDDDDDH", // 11
            "HHSSSHHHHH", // 12
            "SSSSSHHHHH", // 13
            "SSSSSHHHHH", // 14
            "SSSSSHHHHH", // 15
            "SSSSSHHHHH"};// 16

    /** Soft totals 13 to 18. */
    private static final String[] SOFT = {
            "HHHDDHHHHH", // A,2
            "HHHDDHHHHH", // A,3
            "HHDDDHHHHH", // A,4
            "HHDDDHHHHH", // A,5
            "HDDDDHHHHH", // A,6
            "SddddSSHHH"};// A,7

    /** Pair rows by pair value (index 2 to 11, 11 = Aces); {@code null} when the pair is never split. */
    private static final String[] PAIR = new String[12];

    static {
        PAIR[2] = "PPPPPPHHHH";
        PAIR[3] = "PPPPPPHHHH";
        PAIR[4] = "HHHPPHHHHH";
        PAIR[6] = "PPPPPHHHHH";
        PAIR[7] = "PPPPPPHHHH";
        PAIR[8] = "PPPPPPPPPP";
        PAIR[9] = "PPPPPSPPSS";
        PAIR[11] = "PPPPPPPPPP";
    }

    private BasicStrategy() {
    }

    /**
     * The action of a hand against the dealer's up card, always one of {@code available}.
     *
     * @param total   the best total of the hand
     * @param soft    whether an Ace still counts 11
     * @param pair    whether the hand is two cards of equal value
     * @param upValue the dealer's up card as a play value (2 to 10, 11 for an Ace)
     */
    public static Action decide(int total, boolean soft, boolean pair, int upValue, List<Action> available) {
        int column = upValue - 2;
        if (pair && available.contains(Action.SPLIT)) {
            int pairValue = soft ? 11 : total / 2;
            String row = PAIR[pairValue];
            if (row != null && row.charAt(column) == 'P') {
                return Action.SPLIT;
            }
        }
        char code = soft ? softCode(total, column) : hardCode(total, column);
        return resolve(code, available);
    }

    private static char hardCode(int total, int column) {
        if (total <= 8) {
            return HARD_8_OR_LESS.charAt(column);
        }
        if (total >= 17) {
            return HARD_17_PLUS.charAt(column);
        }
        return HARD[total - 9].charAt(column);
    }

    private static char softCode(int total, int column) {
        if (total <= 12) {
            return 'H';
        }
        if (total >= 19) {
            return 'S';
        }
        return SOFT[total - 13].charAt(column);
    }

    private static Action resolve(char code, List<Action> available) {
        Action wanted = switch (code) {
            case 'H' -> Action.HIT;
            case 'S' -> Action.STAND;
            case 'D' -> available.contains(Action.DOUBLE) ? Action.DOUBLE : Action.HIT;
            case 'd' -> available.contains(Action.DOUBLE) ? Action.DOUBLE : Action.STAND;
            default -> throw new IllegalStateException("Unknown strategy code " + code);
        };
        if (available.contains(wanted)) {
            return wanted;
        }
        if (available.contains(Action.STAND)) {
            return Action.STAND;
        }
        return available.get(0);
    }
}

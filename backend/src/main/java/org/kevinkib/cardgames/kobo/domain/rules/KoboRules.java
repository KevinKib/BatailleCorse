package org.kevinkib.cardgames.kobo.domain.rules;

/** Constants of the game, in one place so that a future options record can override them. */
public final class KoboRules {

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 6;
    public static final int CARDS_PER_PLAYER = 4;
    /** The game ends when a total is strictly above this. */
    public static final int END_SCORE = 100;
    /** Totals exactly equal to one of these drop to {@link #RESET_TO}. */
    public static final int[] RESET_TOTALS = {75, 100};
    public static final int RESET_TO = 50;
    public static final int GIFT_POINTS = 10;
    public static final int FAILED_KOBO_PENALTY = 20;

    private KoboRules() {
    }
}

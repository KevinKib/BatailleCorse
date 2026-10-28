package org.kevinkib.cardgames.blackjack.domain.bot;

/** The numbers of the Blackjack bots; the betting rules that use them are in spec section 10.3. */
final class BlackjackBotTuning {

    /** The flat bet of the {@code medium} level, and the base bet of {@code hard} when nothing calls for more. */
    static final int FLAT_BET = 10;

    /** Extra points bet per {@link #BEHIND_STEP} points behind the leader. */
    static final int BEHIND_BONUS = 5;

    static final int BEHIND_STEP = 25;

    /** With this many hands left or fewer, a trailing {@code hard} bot bets its cap. */
    static final int ALL_IN_HANDS_LEFT = 3;

    /** With this many hands left or fewer, a leading {@code hard} bot matches the second's largest bet. */
    static final int MATCH_SECOND_HANDS_LEFT = 2;

    /** The last-hand deviations apply when this many hands are left (the one in progress). */
    static final int LAST_HAND = 1;

    /** Highest hard total at which a bot far behind on the last hand hits instead of standing. */
    static final int DESPERATE_HIT_MAX = 16;

    /** Lowest hard total at which a leading bot stands against a weak dealer card on the last hand. */
    static final int PROTECT_STAND_MIN = 12;

    /** Dealer up cards 2 to 6 are "weak". */
    static final int WEAK_UP_MAX = 6;

    private BlackjackBotTuning() {
    }
}

package org.kevinkib.cardgames.draughts.domain.bot;

import org.kevinkib.cardgames.draughts.domain.DrawTracker;

/**
 * What a bot needs to know about the game to answer a draw offer: how far the game is, and whether an automatic
 * draw counter is running.
 *
 * <p>The rule (owner-approved, docs/specs/2026-10-28-draughts-design.md, Bots): a bot declines every offer made
 * before ply {@value #MIN_PLIES_TO_ACCEPT_DRAW} ("no draws immediately"); from that ply, and before it when a draw
 * counter is running, it answers by material (accepts unless it is ahead). Bots never offer a draw.
 *
 * @param plies          the moves played so far, by both sides
 * @param counterRunning whether a draw counter is running: a position seen twice, the kings-only count worth
 *                       showing, or a short ending (articles 6.3 and 6.4)
 */
public record DraughtsDrawContext(int plies, boolean counterRunning) {

    /** Before this many plies a bot declines a draw offer, unless a draw counter is running. */
    public static final int MIN_PLIES_TO_ACCEPT_DRAW = 20;

    /** The kings-only count (article 6.2) counts as running from this many plies, as the player sees it. */
    static final int KINGS_ONLY_COUNT_FROM = 30;

    /** A repetition counts as running from the second occurrence of the position. */
    static final int REPETITIONS_COUNT_FROM = 2;

    /** No early-game restriction and no counter: the answer is by material alone. */
    public static final DraughtsDrawContext LATE = new DraughtsDrawContext(MIN_PLIES_TO_ACCEPT_DRAW, false);

    /** The context of a game with {@code plies} played, the given counters and the repetitions of the position. */
    public static DraughtsDrawContext of(int plies, DrawTracker tracker, int repetitions) {
        boolean running = repetitions >= REPETITIONS_COUNT_FROM
                || tracker.kingOnlyPlies() >= KINGS_ONLY_COUNT_FROM
                || tracker.endgameLimitPlies() > 0;
        return new DraughtsDrawContext(plies, running);
    }

    /** Whether the offer is too early for a bot to consider it. */
    boolean tooEarly() {
        return plies < MIN_PLIES_TO_ACCEPT_DRAW && !counterRunning;
    }
}

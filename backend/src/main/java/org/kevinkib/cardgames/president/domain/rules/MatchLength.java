package org.kevinkib.cardgames.president.domain.rules;

import java.util.Collection;

/** V18: when the match ends. */
public enum MatchLength {
    ROUNDS_5(5, 0), ROUNDS_3(3, 0), ROUNDS_10(10, 0), POINTS_11(0, 11), POINTS_21(0, 21);

    /** A points target under {@code zeroSum} may never be reached, so such a match stops after this many rounds anyway. */
    public static final int ZERO_SUM_ROUND_CAP = 10;

    private final int rounds;
    private final int points;

    MatchLength(int rounds, int points) {
        this.rounds = rounds;
        this.points = points;
    }

    /**
     * @param roundsPlayed the rounds scored so far, the one just scored included
     * @param totals       the points of the players still in the match
     */
    public boolean isOver(int roundsPlayed, Scoring scoring, Collection<Integer> totals) {
        if (rounds > 0) {
            return roundsPlayed >= rounds;
        }
        if (totals.stream().anyMatch(total -> total >= points)) {
            return true;
        }
        return scoring == Scoring.ZERO_SUM && roundsPlayed >= ZERO_SUM_ROUND_CAP;
    }
}

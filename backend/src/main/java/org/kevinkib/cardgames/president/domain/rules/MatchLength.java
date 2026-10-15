package org.kevinkib.cardgames.president.domain.rules;

import java.util.Collection;

/** V18: when the match ends. */
public enum MatchLength implements ChoiceOption {
    ROUNDS_5("rounds-5", 5, 0),
    ROUNDS_3("rounds-3", 3, 0),
    ROUNDS_10("rounds-10", 10, 0),
    POINTS_11("points-11", 0, 11),
    POINTS_21("points-21", 0, 21),
    /** Rounds chain until the players leave: only a single player left ends the match (reason FORFEIT). */
    ENDLESS("endless", 0, 0);

    /** A points target under {@code zeroSum} may never be reached, so such a match stops after this many rounds anyway. */
    public static final int ZERO_SUM_ROUND_CAP = 10;

    private final String key;
    private final int rounds;
    private final int points;

    MatchLength(String key, int rounds, int points) {
        this.key = key;
        this.rounds = rounds;
        this.points = points;
    }

    /** The number of rounds of a rounds match; 0 for a points or endless match. */
    public int rounds() {
        return rounds;
    }

    @Override
    public String key() {
        return key;
    }

    /**
     * @param roundsPlayed the rounds scored so far, the one just scored included
     * @param totals       the points of the players still in the match
     */
    public boolean isOver(int roundsPlayed, Scoring scoring, Collection<Integer> totals) {
        if (this == ENDLESS) {
            return false;
        }
        if (rounds > 0) {
            return roundsPlayed >= rounds;
        }
        if (totals.stream().anyMatch(total -> total >= points)) {
            return true;
        }
        return scoring == Scoring.ZERO_SUM && roundsPlayed >= ZERO_SUM_ROUND_CAP;
    }
}

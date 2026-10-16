package org.kevinkib.cardgames.president.domain.bot;

/**
 * Every level-dependent constant of the heuristic bot, in one place so tests can pin them.
 *
 * @param randomPlayProbability chance of playing a random legal set instead of the heuristic's choice
 * @param randomPassProbability chance of passing when following, though a play was possible
 * @param countedRanks          how many of the strongest ranks the bot counts (0 none, 3 is 2, A and K, 13 all)
 * @param controlRanks          how many of the strongest ranks are control cards, kept for the end
 * @param shortHand             the hand size up to which the bot breaks sets and spends control cards
 * @param plansEndGame          whether the bot plans its last plays (a low card kept for last)
 */
public record PresidentBotTuning(
        double randomPlayProbability,
        double randomPassProbability,
        int countedRanks,
        int controlRanks,
        int shortHand,
        boolean plansEndGame) {

    /** A {@code shortHand} that every hand is under: the bot never holds back for the length of its hand. */
    public static final int ANY_HAND = 52;

    private static final int RANKS = 13;

    public PresidentBotTuning {
        if (randomPlayProbability < 0 || randomPlayProbability > 1
                || randomPassProbability < 0 || randomPassProbability > 1) {
            throw new IllegalArgumentException("A probability is between 0 and 1");
        }
        if (countedRanks < 0 || countedRanks > RANKS || controlRanks < 0 || controlRanks > RANKS) {
            throw new IllegalArgumentException("A number of ranks is between 0 and " + RANKS);
        }
        if (shortHand < 0) {
            throw new IllegalArgumentException("A number of cards cannot be negative");
        }
    }
}

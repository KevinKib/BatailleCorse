package org.kevinkib.cardgames.kobo.domain.bot;

/**
 * Every level-dependent constant of the heuristic bot, in one place so tests can pin them.
 *
 * @param reactionMinMs        lower bound of the delay before the bot tries a match (uniform draw)
 * @param reactionMaxMs        upper bound of that delay
 * @param forgetProbability    chance that each remembered card is dropped when the bot ends its turn
 * @param followsMoves         whether public swaps and match gifts relocate what the bot remembers
 * @param matchesOpponents     whether the bot matches the opponents' remembered cards (not only its own)
 * @param announceAt           highest estimated hand value at which the bot announces Kobo
 * @param maxUnknownAtAnnounce most own unknown cards the bot tolerates when it announces
 * @param comparesOpponents    whether the bot checks that every opponent's estimate is higher before announcing
 * @param announceMargin       how much lower than every opponent's estimate its own must be (when it compares)
 */
public record KoboBotTuning(
        int reactionMinMs,
        int reactionMaxMs,
        double forgetProbability,
        boolean followsMoves,
        boolean matchesOpponents,
        int announceAt,
        int maxUnknownAtAnnounce,
        boolean comparesOpponents,
        int announceMargin) {

    public KoboBotTuning {
        if (reactionMinMs < 0 || reactionMaxMs < reactionMinMs) {
            throw new IllegalArgumentException("Invalid reaction bounds");
        }
        if (forgetProbability < 0 || forgetProbability > 1) {
            throw new IllegalArgumentException("A probability is between 0 and 1");
        }
        if (maxUnknownAtAnnounce < 0) {
            throw new IllegalArgumentException("A number of cards cannot be negative");
        }
    }
}

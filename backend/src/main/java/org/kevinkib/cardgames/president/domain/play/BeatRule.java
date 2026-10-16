package org.kevinkib.cardgames.president.domain.play;

import org.kevinkib.cardgames.president.domain.card.RankOrder;

/**
 * Does a play answer the top of the trick. Base game: the same number of cards of a strictly higher rank. With
 * equal play on, a play of the same rank and count is also legal, and it binds the next player to match it or pass
 * (the {@link Constraint#MATCH_OR_PASS} constraint, which accepts nothing higher). Cuts and clears are options
 * that extend this class in V2.
 */
public final class BeatRule {

    private BeatRule() {
    }

    public static boolean beats(Play candidate, Play top, RankOrder order) {
        return candidate.count() == top.count() && order.compare(candidate.rank(), top.rank()) > 0;
    }

    /** The same rank and the same count: an equal play. */
    public static boolean isEqual(Play candidate, Play top) {
        return candidate.count() == top.count() && candidate.rank() == top.rank();
    }

    /** Whether the candidate is a legal answer to the top under the constraint. */
    public static boolean answers(Play candidate, Play top, RankOrder order, Constraint constraint,
                                  boolean equalPlay) {
        return switch (constraint) {
            case FREE -> true;
            case BEAT -> beats(candidate, top, order) || (equalPlay && isEqual(candidate, top));
            case MATCH_OR_PASS -> isEqual(candidate, top);
        };
    }

    public static void check(Play candidate, Play top, RankOrder order) throws PlayDoesNotBeatException {
        check(candidate, top, order, Constraint.BEAT, false);
    }

    public static void check(Play candidate, Play top, RankOrder order, Constraint constraint, boolean equalPlay)
            throws PlayDoesNotBeatException {
        if (answers(candidate, top, order, constraint, equalPlay)) {
            return;
        }
        if (constraint == Constraint.MATCH_OR_PASS) {
            throw new PlayDoesNotBeatException("The play must be " + top.count() + " card(s) of rank "
                    + top.rank() + ", or a pass");
        }
        throw new PlayDoesNotBeatException("The play must be " + top.count()
                + " card(s) of a higher rank than " + top.rank());
    }
}

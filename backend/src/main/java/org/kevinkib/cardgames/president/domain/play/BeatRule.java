package org.kevinkib.cardgames.president.domain.play;

import org.kevinkib.cardgames.president.domain.card.RankOrder;
import org.kevinkib.cardgames.president.domain.rules.TwoCuts;

/**
 * Does a play answer the top of the trick. Base game: the same number of cards of a strictly higher rank. With
 * equal play on, a play of the same rank and count is also legal, and it binds the next player to match it or pass
 * (the {@link Constraint#MATCH_OR_PASS} constraint, which accepts nothing higher). The 2 cuts answer any play of their
 * kind under {@link Constraint#BEAT} (never the binding); what a cut or a quad does to the trick is {@link PlayEffects}'s.
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

    /**
     * A cut: under {@code single} one top-rank card on a play of any count, under {@code ladder} k top-rank cards on a
     * set of k+1 cards of a lower rank. A top-rank card on a top-rank play is an equal play, not a cut.
     */
    public static boolean isCut(Play candidate, Play top, RankOrder order, TwoCuts twoCuts) {
        if (top == null || twoCuts == TwoCuts.OFF || candidate.rank() != order.topRank()
                || top.rank() == candidate.rank()) {
            return false;
        }
        return switch (twoCuts) {
            case SINGLE -> candidate.count() == 1;
            case LADDER -> candidate.count() + 1 == top.count();
            case OFF -> false;
        };
    }

    /** Whether the candidate is a legal answer to the top under the constraint, without cuts. */
    public static boolean answers(Play candidate, Play top, RankOrder order, Constraint constraint,
                                  boolean equalPlay) {
        return answers(candidate, top, order, constraint, equalPlay, TwoCuts.OFF);
    }

    /** Whether the candidate is a legal answer to the top under the constraint; a cut never answers the binding. */
    public static boolean answers(Play candidate, Play top, RankOrder order, Constraint constraint,
                                  boolean equalPlay, TwoCuts twoCuts) {
        return switch (constraint) {
            case FREE -> true;
            case BEAT -> beats(candidate, top, order) || (equalPlay && isEqual(candidate, top))
                    || isCut(candidate, top, order, twoCuts);
            case MATCH_OR_PASS -> isEqual(candidate, top);
        };
    }

    public static void check(Play candidate, Play top, RankOrder order) throws PlayDoesNotBeatException {
        check(candidate, top, order, Constraint.BEAT, false);
    }

    public static void check(Play candidate, Play top, RankOrder order, Constraint constraint, boolean equalPlay)
            throws PlayDoesNotBeatException {
        check(candidate, top, order, constraint, equalPlay, TwoCuts.OFF);
    }

    public static void check(Play candidate, Play top, RankOrder order, Constraint constraint, boolean equalPlay,
                             TwoCuts twoCuts) throws PlayDoesNotBeatException {
        if (answers(candidate, top, order, constraint, equalPlay, twoCuts)) {
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

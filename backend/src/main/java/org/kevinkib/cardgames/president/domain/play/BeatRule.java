package org.kevinkib.cardgames.president.domain.play;

import org.kevinkib.cardgames.president.domain.card.RankOrder;

/**
 * Does a play answer the top of the trick: the same number of cards of a strictly higher rank, or (equal play, a fixed
 * rule) a play of the same rank and count, which binds the next player to match it or pass
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
     * set of k+1 cards of a lower rank. Under {@code single} a lone top-rank card is a cut on a top-rank play too (owner feedback);
     * under {@code ladder} a top-rank card on a top-rank play is an equal play, not a cut.
     */
    public static boolean isCut(Play candidate, Play top, RankOrder order) {
        // a fixed rule: one 2 played alone closes the trick, on any play (a play of 2s included)
        return top != null && candidate.rank() == order.topRank() && candidate.count() == 1;
    }

    /** Whether the candidate is a legal answer to the top under the constraint, without cuts. */
    public static boolean answers(Play candidate, Play top, RankOrder order, Constraint constraint) {
        return switch (constraint) {
            case FREE -> true;
            case BEAT -> beats(candidate, top, order) || isEqual(candidate, top)
                    || isCut(candidate, top, order);
            // the binding asks for the same rank and count, but a lone 2 is always allowed: it closes the trick
            case MATCH_OR_PASS -> isEqual(candidate, top) || isCut(candidate, top, order);
        };
    }

    public static void check(Play candidate, Play top, RankOrder order) throws PlayDoesNotBeatException {
        check(candidate, top, order, Constraint.BEAT);
    }

    public static void check(Play candidate, Play top, RankOrder order, Constraint constraint)
            throws PlayDoesNotBeatException {
        if (answers(candidate, top, order, constraint)) {
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

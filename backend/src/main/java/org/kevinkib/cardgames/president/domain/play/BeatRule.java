package org.kevinkib.cardgames.president.domain.play;

import org.kevinkib.cardgames.president.domain.card.RankOrder;

/**
 * Does a play answer the top of the trick: the same number of cards of a strictly higher rank, or (equal play, a fixed
 * rule) a play of the same rank and count, which binds the next player to match it or pass
 * (the {@link Constraint#MATCH_OR_PASS} constraint, which accepts nothing higher, a 2 included unless 2 is the bound
 * rank). The 2 cuts as a group of the same size as the play it answers (n twos on n cards, any n), and, with the
 * {@code reducedCut} option, with one 2 less on a pair or a triple; a cut never answers the binding. What a cut or a
 * quad does to the trick is {@link PlayEffects}'s.
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

    /** A cut without the reduced-cut option. */
    public static boolean isCut(Play candidate, Play top, RankOrder order) {
        return isCut(candidate, top, order, false);
    }

    /**
     * A cut: top-rank cards (2s) as many as the cards of the top, whatever their rank (a lone 2 on a lone card, a pair
     * of 2s on a pair, a triple on a triple). With {@code reducedCut}, one 2 less also cuts a pair or a triple (one 2
     * on a pair, two 2s on a triple). A single 2 never cuts a triple.
     */
    public static boolean isCut(Play candidate, Play top, RankOrder order, boolean reducedCut) {
        if (top == null || candidate.rank() != order.topRank()) {
            return false;
        }
        if (candidate.count() == top.count()) {
            return true;
        }
        return reducedCut && (top.count() == 2 || top.count() == 3) && candidate.count() == top.count() - 1;
    }

    /** Whether the candidate is a legal answer to the top under the constraint, without the reduced cut. */
    public static boolean answers(Play candidate, Play top, RankOrder order, Constraint constraint) {
        return answers(candidate, top, order, constraint, false);
    }

    public static boolean answers(Play candidate, Play top, RankOrder order, Constraint constraint,
                                  boolean reducedCut) {
        return switch (constraint) {
            case FREE -> true;
            case BEAT -> beats(candidate, top, order) || isEqual(candidate, top)
                    || isCut(candidate, top, order, reducedCut);
            // the binding asks for the same rank and count: a 2 is playable only when it is the bound rank
            case MATCH_OR_PASS -> isEqual(candidate, top);
        };
    }

    public static void check(Play candidate, Play top, RankOrder order) throws PlayDoesNotBeatException {
        check(candidate, top, order, Constraint.BEAT, false);
    }

    public static void check(Play candidate, Play top, RankOrder order, Constraint constraint)
            throws PlayDoesNotBeatException {
        check(candidate, top, order, constraint, false);
    }

    public static void check(Play candidate, Play top, RankOrder order, Constraint constraint, boolean reducedCut)
            throws PlayDoesNotBeatException {
        if (answers(candidate, top, order, constraint, reducedCut)) {
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

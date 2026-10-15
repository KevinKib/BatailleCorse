package org.kevinkib.cardgames.president.domain.play;

import org.kevinkib.cardgames.president.domain.card.RankOrder;

/**
 * Does a play beat the top of the trick. Base game: the same number of cards of a strictly higher rank.
 * Equal plays, cuts and clears are options that extend this class in V1.
 */
public final class BeatRule {

    private BeatRule() {
    }

    public static boolean beats(Play candidate, Play top, RankOrder order) {
        return candidate.count() == top.count() && order.compare(candidate.rank(), top.rank()) > 0;
    }

    public static void check(Play candidate, Play top, RankOrder order) throws PlayDoesNotBeatException {
        if (!beats(candidate, top, order)) {
            throw new PlayDoesNotBeatException("The play must be " + top.count()
                    + " card(s) of a higher rank than " + top.rank());
        }
    }
}

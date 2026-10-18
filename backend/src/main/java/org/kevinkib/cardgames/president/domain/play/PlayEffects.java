package org.kevinkib.cardgames.president.domain.play;

import org.kevinkib.cardgames.president.domain.card.RankOrder;
import org.kevinkib.cardgames.president.domain.event.TrickEndReason;

import java.util.Optional;

/**
 * What a play does to the trick on its own, besides being legal: 2s that cut ({@link TrickEndReason#CUT}: as many 2s
 * as the play they answer, or one less on a pair or a triple with the {@code reducedCut} option) end it, and so does
 * a quad played at once ({@link TrickEndReason#QUAD}); the player then leads again. The four cards of a rank completed
 * in succession belong to equal play and are decided by the round, not here.
 *
 * <p>2s led (one, two or three) also end the trick at once (owner decision of 2026-10-18); a quad is a QUAD.
 */
public final class PlayEffects {

    private PlayEffects() {
    }

    /** Why the play closes the trick without the reduced-cut option; see the four-argument form. */
    public static Optional<TrickEndReason> clearReason(Play play, Play top, RankOrder order) {
        return clearReason(play, top, order, false);
    }

    /**
     * @param play the play just made
     * @param top  the play it answered, {@code null} for a lead
     * @return why the play closes the trick, nothing when the trick goes on
     */
    public static Optional<TrickEndReason> clearReason(Play play, Play top, RankOrder order, boolean reducedCut) {
        if (cuts(play, top, order, reducedCut)) {
            return Optional.of(TrickEndReason.CUT);
        }
        if (play.count() == Completion.RANK_SIZE) {
            return Optional.of(TrickEndReason.QUAD);
        }
        return Optional.empty();
    }

    private static boolean cuts(Play play, Play top, RankOrder order, boolean reducedCut) {
        if (top == null) {
            return play.count() < Completion.RANK_SIZE && play.rank() == order.topRank();
        }
        return BeatRule.isCut(play, top, order, reducedCut);
    }
}

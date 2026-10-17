package org.kevinkib.cardgames.president.domain.play;

import org.kevinkib.cardgames.president.domain.card.RankOrder;
import org.kevinkib.cardgames.president.domain.event.TrickEndReason;

import java.util.Optional;

/**
 * What a play does to the trick on its own, besides being legal: a lone 2 ends it ({@link TrickEndReason#CUT}), and so
 * does a quad played at once ({@link TrickEndReason#QUAD}); the player then leads again. Both are fixed rules, with no
 * option. The four cards of a rank completed in succession belong to equal play and are decided by the round, not here.
 *
 * <p>A lone 2 ends the trick wherever it is played: on any play (a play of 2s included), under the equal-play binding,
 * and as a lead (the spec's "it simply wins the trick at once").
 */
public final class PlayEffects {

    private PlayEffects() {
    }

    /**
     * @param play the play just made
     * @param top  the play it answered, {@code null} for a lead
     * @return why the play closes the trick, nothing when the trick goes on
     */
    public static Optional<TrickEndReason> clearReason(Play play, Play top, RankOrder order) {
        if (cuts(play, top, order)) {
            return Optional.of(TrickEndReason.CUT);
        }
        if (play.count() == Completion.RANK_SIZE) {
            return Optional.of(TrickEndReason.QUAD);
        }
        return Optional.empty();
    }

    private static boolean cuts(Play play, Play top, RankOrder order) {
        if (top == null) {
            return play.count() == 1 && play.rank() == order.topRank();
        }
        return BeatRule.isCut(play, top, order);
    }
}

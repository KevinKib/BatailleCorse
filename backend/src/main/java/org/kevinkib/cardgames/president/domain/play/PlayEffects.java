package org.kevinkib.cardgames.president.domain.play;

import org.kevinkib.cardgames.president.domain.card.RankOrder;
import org.kevinkib.cardgames.president.domain.event.TrickEndReason;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cardgames.president.domain.rules.TwoCuts;

import java.util.Optional;

/**
 * What a play does to the trick on its own, besides being legal: a cut ends it ({@link TrickEndReason#CUT}), and with
 * {@code quadClears} so does a quad played at once ({@link TrickEndReason#QUAD}); the player then leads again. The four
 * cards of a rank completed in succession belong to equal play and are decided by the round, not here.
 *
 * <p>A cut ends the trick even when it is not needed to win: under {@code single} a lone 2 ends the trick wherever it
 * is played, a lead included (the spec's "it simply wins the trick at once"); a 2 played on a 2 is an equal play.
 */
public final class PlayEffects {

    private PlayEffects() {
    }

    /**
     * @param play the play just made
     * @param top  the play it answered, {@code null} for a lead
     * @return why the play closes the trick, nothing when the trick goes on
     */
    public static Optional<TrickEndReason> clearReason(Play play, Play top, RankOrder order,
                                                       PresidentOptions options) {
        if (cuts(play, top, order, options.twoCuts())) {
            return Optional.of(TrickEndReason.CUT);
        }
        if (options.quadClears() && play.count() == Completion.RANK_SIZE) {
            return Optional.of(TrickEndReason.QUAD);
        }
        return Optional.empty();
    }

    private static boolean cuts(Play play, Play top, RankOrder order, TwoCuts twoCuts) {
        if (top == null) {
            return twoCuts == TwoCuts.SINGLE && play.count() == 1 && play.rank() == order.topRank();
        }
        return BeatRule.isCut(play, top, order, twoCuts);
    }
}

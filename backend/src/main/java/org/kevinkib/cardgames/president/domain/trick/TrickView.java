package org.kevinkib.cardgames.president.domain.trick;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.play.Constraint;
import org.kevinkib.cardgames.president.domain.play.Play;

import java.util.List;

/**
 * The read model of a trick: an immutable snapshot. {@code passed} lists, by seat, the players who passed since
 * the last play (a play answers everyone, so it empties the list). {@code constraint} is what the next play must
 * answer and {@code chain} the plays of one rank at the top, in succession ({@code null} on an empty trick).
 */
public record TrickView(PlayerId leader, List<TrickPlay> plays, List<PlayerId> passed, Constraint constraint,
                        Chain chain) {

    public TrickView {
        plays = List.copyOf(plays);
        passed = List.copyOf(passed);
    }

    /** A trick of the base game: no binding, no chain. */
    public TrickView(PlayerId leader, List<TrickPlay> plays, List<PlayerId> passed) {
        this(leader, plays, passed, plays.isEmpty() ? Constraint.FREE : Constraint.BEAT, null);
    }

    /** The play to beat, or {@code null} when the leader has not played yet. */
    public Play top() {
        return plays.isEmpty() ? null : plays.get(plays.size() - 1).play();
    }

    /** The seat of the last play, or {@code null} when nothing was played yet. */
    public PlayerId lastPlayer() {
        return plays.isEmpty() ? null : plays.get(plays.size() - 1).seat();
    }

    /** Whether the completion window is open: the chain at the top holds two plays or more. */
    public boolean completionOpen() {
        return chain != null && chain.plays() >= 2;
    }
}

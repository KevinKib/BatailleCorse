package org.kevinkib.cardgames.president.domain.trick;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.play.Play;

import java.util.List;

/** The read model of a trick: an immutable snapshot. */
public record TrickView(PlayerId leader, List<TrickPlay> plays) {

    public TrickView {
        plays = List.copyOf(plays);
    }

    /** The play to beat, or {@code null} when the leader has not played yet. */
    public Play top() {
        return plays.isEmpty() ? null : plays.get(plays.size() - 1).play();
    }

    /** The seat of the last play, or {@code null} when nothing was played yet. */
    public PlayerId lastPlayer() {
        return plays.isEmpty() ? null : plays.get(plays.size() - 1).seat();
    }
}

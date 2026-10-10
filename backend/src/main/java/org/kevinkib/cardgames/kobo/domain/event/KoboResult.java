package org.kevinkib.cardgames.kobo.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;

/** Outcome of the whole match: ongoing, or finished with the winners (several when tied). */
public record KoboResult(List<PlayerId> winners, Reason reason) {

    public enum Reason { SCORE, FORFEIT }

    public static final KoboResult ONGOING = new KoboResult(List.of(), null);

    public boolean isFinished() {
        return reason != null;
    }
}

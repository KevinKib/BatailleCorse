package org.kevinkib.cardgames.president.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;

/** Outcome of the whole match: ongoing, or finished with the winners (several when tied). */
public record PresidentResult(List<PlayerId> winners, Reason reason) {

    public enum Reason { POINTS, FORFEIT }

    public static final PresidentResult ONGOING = new PresidentResult(List.of(), null);

    public PresidentResult {
        winners = List.copyOf(winners);
    }

    public boolean isFinished() {
        return reason != null;
    }
}

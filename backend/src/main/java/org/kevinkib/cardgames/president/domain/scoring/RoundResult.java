package org.kevinkib.cardgames.president.domain.scoring;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.exchange.Title;

import java.util.List;
import java.util.Map;

/**
 * How a round was scored: the finishing order of the players still in the match (best first), the titles
 * it earned, the points of the round and the totals after it.
 */
public record RoundResult(
        int round,
        List<PlayerId> finishingOrder,
        Map<PlayerId, Title> titles,
        Map<PlayerId, Integer> points,
        Map<PlayerId, Integer> totals) {

    public RoundResult {
        finishingOrder = List.copyOf(finishingOrder);
        titles = Map.copyOf(titles);
        points = Map.copyOf(points);
        totals = Map.copyOf(totals);
    }
}

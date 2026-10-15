package org.kevinkib.cardgames.president.domain.scoring;

import org.kevinkib.cardgames.game.PlayerId;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** The points of the players still in the match. */
public record Standings(Map<PlayerId, Integer> totals) {

    public Standings {
        totals = Map.copyOf(totals);
    }

    /** The points of a player; 0 for a player who left the match. */
    public int points(PlayerId player) {
        return totals.getOrDefault(player, 0);
    }

    /** The players from the most points to the fewest; a tie keeps seat order. */
    public List<PlayerId> ranked() {
        return totals.keySet().stream()
                .sorted(Comparator.<PlayerId>comparingInt(this::points).reversed()
                        .thenComparing(PlayerId::id))
                .toList();
    }

    /** Every player holding the most points (several on a tie), in seat order; empty when nobody is left. */
    public List<PlayerId> leaders() {
        if (totals.isEmpty()) {
            return List.of();
        }
        int max = totals.values().stream().mapToInt(Integer::intValue).max().getAsInt();
        return totals.entrySet().stream()
                .filter(e -> e.getValue() == max)
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(PlayerId::id))
                .toList();
    }
}

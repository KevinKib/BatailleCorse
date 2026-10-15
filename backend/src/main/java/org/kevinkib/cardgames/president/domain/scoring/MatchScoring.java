package org.kevinkib.cardgames.president.domain.scoring;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.exchange.Title;
import org.kevinkib.cardgames.president.domain.exchange.Titles;
import org.kevinkib.cardgames.president.domain.rules.Scoring;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Variant V18, pure: the points a finishing order earns under each scoring option. */
public final class MatchScoring {

    private MatchScoring() {
    }

    /**
     * @param finishingOrder the players still in the match, best place first
     * @return the points of each player, in the order of the finishing order
     */
    public static Map<PlayerId, Integer> points(Scoring scoring, List<PlayerId> finishingOrder) {
        Map<PlayerId, Integer> points = new LinkedHashMap<>();
        int players = finishingOrder.size();
        if (players == 0) {
            return points;
        }
        List<Title> titles = players >= 2 ? Titles.forActivePlayers(players) : List.of();
        for (int place = 0; place < players; place++) {
            int value = switch (scoring) {
                case RANK -> players - 1 - place;
                case TITLES -> players < 2 ? 0 : titlePoints(titles.get(place));
                case ZERO_SUM -> zeroSum(place, players);
            };
            points.put(finishingOrder.get(place), value);
        }
        return points;
    }

    private static int titlePoints(Title title) {
        return switch (title) {
            case PRESIDENT -> 2;
            case VICE_PRESIDENT -> 1;
            default -> 0;
        };
    }

    /** +k for the k-th pair of places counted from the middle outwards, -k from the bottom; 0 in the middle of an odd table. */
    private static int zeroSum(int place, int players) {
        int pairs = players / 2;
        if (place < pairs) {
            return pairs - place;
        }
        int fromBottom = players - 1 - place;
        if (fromBottom < pairs) {
            return -(pairs - fromBottom);
        }
        return 0;
    }
}

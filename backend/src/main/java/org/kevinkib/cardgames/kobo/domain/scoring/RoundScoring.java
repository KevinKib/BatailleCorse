package org.kevinkib.cardgames.kobo.domain.scoring;

import org.kevinkib.cardgames.kobo.domain.rules.KoboRules;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure scoring of a round: round points, then the 10 given points. */
public final class RoundScoring {

    private RoundScoring() {
    }

    /** The announcer wins when nobody has strictly fewer points (a tie with the lowest counts). */
    public static boolean announcerWins(Map<PlayerId, Integer> handValues, PlayerId announcer) {
        int mine = handValues.get(announcer);
        return handValues.entrySet().stream()
                .filter(e -> !e.getKey().equals(announcer))
                .allMatch(e -> e.getValue() >= mine);
    }

    /**
     * The round as scored before a winning announcer has chosen who takes the 10 points: the hand values and the
     * round points, the totals as they stand plus those points, no gift yet. The final {@link #score}
     * replaces it once the gift is given.
     */
    public static RoundResult provisional(Map<PlayerId, Integer> handValues, PlayerId announcer,
                                          Map<PlayerId, Integer> totalsBefore) {
        Map<PlayerId, Integer> roundPoints = new LinkedHashMap<>();
        Map<PlayerId, Integer> totals = new LinkedHashMap<>();
        for (Map.Entry<PlayerId, Integer> hand : handValues.entrySet()) {
            int points = hand.getKey().equals(announcer) ? 0 : hand.getValue();
            roundPoints.put(hand.getKey(), points);
            totals.put(hand.getKey(), totalsBefore.get(hand.getKey()) + points);
        }
        return new RoundResult(Map.copyOf(handValues), roundPoints, Map.copyOf(totalsBefore), totals,
                announcer, announcerWins(handValues, announcer), null);
    }

    public static RoundResult score(Map<PlayerId, Integer> handValues, PlayerId announcer, PlayerId giftTo,
                                    Map<PlayerId, Integer> totalsBefore) {
        boolean won = announcer != null && announcerWins(handValues, announcer);
        if (won) {
            if (giftTo == null || giftTo.equals(announcer) || !handValues.containsKey(giftTo)) {
                throw new IllegalArgumentException("A winning announcer must give 10 points to another player");
            }
        } else if (giftTo != null) {
            throw new IllegalArgumentException("Only a winning announcer gives points");
        }

        Map<PlayerId, Integer> roundPoints = new LinkedHashMap<>();
        Map<PlayerId, Integer> totals = new LinkedHashMap<>();
        for (Map.Entry<PlayerId, Integer> hand : handValues.entrySet()) {
            PlayerId player = hand.getKey();
            int points = hand.getValue();
            if (announcer != null && player.equals(announcer)) {
                points = won ? 0 : points + KoboRules.FAILED_KOBO_PENALTY;
            }
            roundPoints.put(player, points);
            totals.put(player, totalsBefore.get(player) + points);
        }
        if (won) {
            totals.merge(giftTo, KoboRules.GIFT_POINTS, Integer::sum);
        }
        return new RoundResult(Map.copyOf(handValues), roundPoints, Map.copyOf(totalsBefore), totals,
                announcer, won, won ? giftTo : null);
    }
}

package org.kevinkib.cardgames.kobo.domain;

import org.kevinkib.cardgames.game.PlayerId;

import java.util.Map;
import java.util.Set;

/**
 * How a round was scored. {@code giftTo} is the player who received the announcer's 10 points (null
 * when there was no gift); {@code reset} lists the players whose total fell to 50 (75 or 100).
 */
public record RoundResult(
        Map<PlayerId, Integer> handValues,
        Map<PlayerId, Integer> roundPoints,
        Map<PlayerId, Integer> totalsBefore,
        Map<PlayerId, Integer> totalsAfter,
        PlayerId announcer,
        boolean announcerWon,
        PlayerId giftTo,
        Set<PlayerId> reset) {
}

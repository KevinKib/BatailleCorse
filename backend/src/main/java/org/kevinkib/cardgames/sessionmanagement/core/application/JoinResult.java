package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.PlayerId;

public record JoinResult(PlayerId playerId, String token) {
}

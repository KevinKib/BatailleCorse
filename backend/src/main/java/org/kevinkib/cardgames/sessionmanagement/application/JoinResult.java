package org.kevinkib.cardgames.sessionmanagement.application;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionToken;

public record JoinResult(PlayerId playerId, SessionToken token) {
}

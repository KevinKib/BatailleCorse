package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionToken;

public record JoinResult(PlayerId playerId, SessionToken token) {
}

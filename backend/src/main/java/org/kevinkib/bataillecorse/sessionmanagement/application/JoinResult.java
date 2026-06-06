package org.kevinkib.bataillecorse.sessionmanagement.application;

import org.kevinkib.bataillecorse.core.domain.PlayerId;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionToken;

public record JoinResult(PlayerId playerId, SessionToken token) {
}

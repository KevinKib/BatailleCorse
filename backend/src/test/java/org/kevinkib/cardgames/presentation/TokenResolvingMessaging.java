package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;

/**
 * Base of the recording messaging doubles. Lobby views are addressed by token; the double maps the token back to its
 * seat and records through {@code sendToSeat}, which keeps the existing per-seat assertions.
 */
public abstract class TokenResolvingMessaging extends GameMessagingService {

    public SessionService resolver;

    protected TokenResolvingMessaging() {
        super(null, null);
    }

    @Override
    public void sendToToken(GameId gameId, String token, Object payload) {
        sendToSeat(gameId, resolver.findPlayerIdByToken(gameId, token).orElseThrow(), payload);
    }
}

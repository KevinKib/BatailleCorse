package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.GameId;

/**
 * Tells the public game lists that a room is gone. The repository no longer knows the game type of an evicted room,
 * so every list is refreshed; with a handful of game types this is the simplest choice.
 */
public class PublicLobbiesEvictionNotifier implements GameEvictionListener {

    private final SessionService sessions;

    public PublicLobbiesEvictionNotifier(SessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    public void onEvicted(GameId id) {
        sessions.publicListsChanged();
    }
}

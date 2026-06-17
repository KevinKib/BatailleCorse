package org.kevinkib.cardgames.sessionmanagement.presence.application;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameEvictionListener;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ConnectionRegistry;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitLog;

/** Clears presence's per-game state when core evicts a game. */
public class PresenceEvictionCleanup implements GameEvictionListener {

    private final ConnectionRegistry connectionRegistry;
    private final ForfeitLog forfeitLog;

    public PresenceEvictionCleanup(ConnectionRegistry connectionRegistry, ForfeitLog forfeitLog) {
        this.connectionRegistry = connectionRegistry;
        this.forfeitLog = forfeitLog;
    }

    @Override
    public void onEvicted(GameId id) {
        connectionRegistry.removeGame(id);
        forfeitLog.removeGame(id);
    }
}

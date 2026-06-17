package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.GameId;

/** Notified when a game is evicted, so downstream contexts can drop their per-game state. */
public interface GameEvictionListener {
    void onEvicted(GameId id);
}

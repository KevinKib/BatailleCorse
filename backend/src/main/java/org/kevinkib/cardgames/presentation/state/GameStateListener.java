package org.kevinkib.cardgames.presentation.state;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.presentation.dto.event.EventData;

/** Notified after every state broadcast of a game, whatever path changed the state (the bot coordinators). */
@FunctionalInterface
public interface GameStateListener<G extends Game> {

    void onBroadcast(G game, String eventType, EventData eventData);
}

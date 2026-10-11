package org.kevinkib.cardgames.bataillecorse.presentation;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.presentation.dto.event.EventData;

/** Notified after every state broadcast of a Bataille Corse game, whatever path changed the state. */
@FunctionalInterface
public interface BatailleCorseStateListener {

    void onBroadcast(BatailleCorse game, String eventType, EventData eventData);
}

package org.kevinkib.cardgames.bullshit.presentation;

import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.presentation.dto.event.EventData;

/** Notified after every state broadcast of a Bullshit game, whatever path changed the state. */
@FunctionalInterface
public interface BullshitStateListener {

    void onBroadcast(Bullshit game, String eventType, EventData eventData);
}

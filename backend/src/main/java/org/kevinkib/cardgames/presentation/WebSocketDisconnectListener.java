package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.sessionmanagement.presence.application.PresenceService;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

public class WebSocketDisconnectListener {

    private final PresenceService presenceService;

    public WebSocketDisconnectListener(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        presenceService.onDisconnect(event.getSessionId());
    }
}

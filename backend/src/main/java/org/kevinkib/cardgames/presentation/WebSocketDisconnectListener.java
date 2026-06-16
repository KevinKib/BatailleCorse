package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.sessionmanagement.presence.application.PresenceService;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

public class WebSocketDisconnectListener {

    private final PresenceService forfeitService;

    public WebSocketDisconnectListener(PresenceService forfeitService) {
        this.forfeitService = forfeitService;
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        forfeitService.onDisconnect(event.getSessionId());
    }
}

package org.kevinkib.cardgames.presentation;

import org.springframework.context.event.EventListener;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

public class WebSocketDisconnectListener {

    private final DisconnectForfeitService forfeitService;

    public WebSocketDisconnectListener(DisconnectForfeitService forfeitService) {
        this.forfeitService = forfeitService;
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        forfeitService.onDisconnect(event.getSessionId());
    }
}

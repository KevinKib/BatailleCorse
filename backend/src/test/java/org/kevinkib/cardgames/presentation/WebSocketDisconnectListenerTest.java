package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.CloseStatus;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class WebSocketDisconnectListenerTest {

    private static final class RecordingService extends DisconnectForfeitService {
        String lastSessionId;
        RecordingService() { super(null, null, null, null, null, new ForfeitReasonRegistry()); }
        @Override public void onDisconnect(String sessionId) { this.lastSessionId = sessionId; }
    }

    @Test
    void whenDisconnectEvent_thenForwardsSessionId() {
        var service = new RecordingService();
        var listener = new WebSocketDisconnectListener(service);
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).build();
        var event = new SessionDisconnectEvent(this, message, "sess-xyz", CloseStatus.NORMAL);

        listener.onDisconnect(event);

        assertThat(service.lastSessionId, is("sess-xyz"));
    }
}

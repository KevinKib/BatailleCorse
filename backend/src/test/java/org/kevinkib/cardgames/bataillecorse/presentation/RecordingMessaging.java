package org.kevinkib.cardgames.bataillecorse.presentation;

import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.api.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Records what is sent to a game topic instead of touching a real broker. */
public final class RecordingMessaging extends GameMessagingService {

    public final List<Response> toGame = Collections.synchronizedList(new ArrayList<>());

    public RecordingMessaging() {
        super(null, null);
    }

    @Override
    public void sendToGame(String gameId, Object payload) {
        toGame.add((Response) payload);
    }

    public Response last() {
        return toGame.get(toGame.size() - 1);
    }
}

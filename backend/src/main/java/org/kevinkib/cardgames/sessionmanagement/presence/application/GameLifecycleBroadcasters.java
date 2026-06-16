package org.kevinkib.cardgames.sessionmanagement.presence.application;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;

import java.util.List;

public class GameLifecycleBroadcasters {

    private final List<GameLifecycleBroadcaster> broadcasters;

    public GameLifecycleBroadcasters(List<GameLifecycleBroadcaster> broadcasters) {
        this.broadcasters = broadcasters;
    }

    public GameLifecycleBroadcaster broadcasterFor(Game game) {
        return broadcasters.stream()
                .filter(b -> b.supports(game))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No lifecycle broadcaster for " + game.getClass().getSimpleName()));
    }
}

package org.kevinkib.cardgames.game;

import java.util.UUID;

public record GameId(UUID uuid) {

    public GameId(String id) {
        this(UUID.fromString(id));
    }

    public static GameId generate() {
        return new GameId(UUID.randomUUID());
    }
}

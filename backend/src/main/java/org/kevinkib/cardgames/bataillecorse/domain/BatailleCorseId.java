package org.kevinkib.cardgames.bataillecorse.domain;

import java.util.UUID;

public record BatailleCorseId(UUID uuid) {

    public BatailleCorseId(String id) {
        this(UUID.fromString(id));
    }

    public static BatailleCorseId generate() {
        return new BatailleCorseId(UUID.randomUUID());
    }

}

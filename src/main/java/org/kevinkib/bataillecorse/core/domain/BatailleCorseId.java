package org.kevinkib.bataillecorse.core.domain;

import java.util.UUID;

public record BatailleCorseId(UUID uuid) {

    public static BatailleCorseId generate() {
        return new BatailleCorseId(UUID.randomUUID());
    }

}

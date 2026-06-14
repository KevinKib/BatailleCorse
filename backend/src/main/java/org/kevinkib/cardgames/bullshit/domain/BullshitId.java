package org.kevinkib.cardgames.bullshit.domain;

import java.util.UUID;

public record BullshitId(UUID uuid) {

    public BullshitId(String id) {
        this(UUID.fromString(id));
    }

    public static BullshitId generate() {
        return new BullshitId(UUID.randomUUID());
    }
}

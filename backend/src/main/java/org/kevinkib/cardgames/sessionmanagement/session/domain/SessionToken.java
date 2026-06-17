package org.kevinkib.cardgames.sessionmanagement.session.domain;

import java.util.UUID;

public record SessionToken(UUID uuid) {

    public SessionToken(String id) {
        this(UUID.fromString(id));
    }

    public static SessionToken generate() {
        return new SessionToken(UUID.randomUUID());
    }

}

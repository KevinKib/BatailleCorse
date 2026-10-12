package org.kevinkib.cardgames.sessionmanagement.core.domain;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record SessionToken(UUID uuid) {

    /**
     * Reads a token presented by a client. A value that is not a UUID is an invalid token, not an error: it
     * becomes a token no seat holds (generated tokens are random version-4 UUIDs, this one is name-based
     * version 3), so lookups simply find nobody.
     */
    public SessionToken(String id) {
        this(parse(id));
    }

    public static SessionToken generate() {
        return new SessionToken(UUID.randomUUID());
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException | NullPointerException malformed) {
            return UUID.nameUUIDFromBytes(String.valueOf(id).getBytes(StandardCharsets.UTF_8));
        }
    }

}

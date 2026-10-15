package org.kevinkib.cardgames.mangola.domain;

import java.util.Arrays;

/** The lobby choice of who starts (and so owns side A): the host, the guest, or drawn once at creation. */
public enum FirstPlayer {
    HOST("host"), GUEST("guest"), RANDOM("random");

    public static final FirstPlayer DEFAULT = RANDOM;

    private final String key;

    FirstPlayer(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static FirstPlayer fromKey(String key) {
        return Arrays.stream(values()).filter(f -> f.key.equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown first player: " + key));
    }
}

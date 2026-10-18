package org.kevinkib.cardgames.threemenmorris.domain;

import java.util.Arrays;

/** The lobby choice of the host's colour: fixed, or drawn once when the game is created. */
public enum HostColour {
    WHITE("white"), BLACK("black"), RANDOM("random");

    public static final HostColour DEFAULT = RANDOM;

    private final String key;

    HostColour(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static HostColour fromKey(String key) {
        return Arrays.stream(values()).filter(c -> c.key.equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown host colour: " + key));
    }
}

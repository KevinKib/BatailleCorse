package org.kevinkib.cardgames.mangola.domain;

import java.util.Arrays;

/**
 * The way both loops run seen from above with side A at the bottom: the host's choice in the lobby. The pits and
 * their loops are the same in both directions; clockwise is the anticlockwise board mirrored left to right (column
 * {@code a} on side A's right when anticlockwise). It is the board's orientation, so the engine and the bots do not
 * read it: it is carried by the game for the clients to draw.
 */
public enum SowingDirection {
    ANTICLOCKWISE("anticlockwise"), CLOCKWISE("clockwise");

    public static final SowingDirection DEFAULT = ANTICLOCKWISE;

    private final String key;

    SowingDirection(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static SowingDirection fromKey(String key) {
        return Arrays.stream(values()).filter(d -> d.key.equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown sowing direction: " + key));
    }
}

package org.kevinkib.cardgames.kilodemerde.domain.bot;

import java.util.Arrays;

/** The strength of a Kilo de merde computer seat, a seat option of the lobby ({@code level}). */
public enum KiloDeMerdeBotLevel {
    EASY("easy"), MEDIUM("medium"), HARD("hard");

    public static final KiloDeMerdeBotLevel DEFAULT = MEDIUM;

    private final String key;

    KiloDeMerdeBotLevel(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** The level named by a seat option value; anything absent or unknown gives the default. */
    public static KiloDeMerdeBotLevel fromOption(String value) {
        return Arrays.stream(values())
                .filter(level -> level.key.equals(value))
                .findFirst()
                .orElse(DEFAULT);
    }
}

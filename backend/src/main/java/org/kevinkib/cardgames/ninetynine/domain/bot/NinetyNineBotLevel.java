package org.kevinkib.cardgames.ninetynine.domain.bot;

import java.util.Arrays;

/** The strength of a 99 computer seat, a seat option of the lobby ({@code level}). */
public enum NinetyNineBotLevel {
    EASY("easy"), MEDIUM("medium"), HARD("hard");

    public static final NinetyNineBotLevel DEFAULT = MEDIUM;

    private final String key;

    NinetyNineBotLevel(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** The level named by a seat option value; anything absent or unknown gives the default. */
    public static NinetyNineBotLevel fromOption(String value) {
        return Arrays.stream(values())
                .filter(level -> level.key.equals(value))
                .findFirst()
                .orElse(DEFAULT);
    }
}

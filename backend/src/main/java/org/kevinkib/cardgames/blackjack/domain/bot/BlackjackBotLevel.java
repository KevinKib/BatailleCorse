package org.kevinkib.cardgames.blackjack.domain.bot;

import java.util.Arrays;

/** The strength of a Blackjack computer seat, a seat option of the lobby ({@code level}). */
public enum BlackjackBotLevel {
    EASY("easy"), MEDIUM("medium"), HARD("hard");

    public static final BlackjackBotLevel DEFAULT = MEDIUM;

    private final String key;

    BlackjackBotLevel(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** The level named by a seat option value; anything absent or unknown gives the default. */
    public static BlackjackBotLevel fromOption(String value) {
        return Arrays.stream(values())
                .filter(level -> level.key.equals(value))
                .findFirst()
                .orElse(DEFAULT);
    }
}

package org.kevinkib.cardgames.kobo.domain.bot;

import java.util.Arrays;

/** The strength of a Kobo computer seat, a seat option of the lobby ({@code level}). */
public enum KoboBotLevel {
    EASY("easy", new KoboBotTuning(2500, 4000, 0.25, false, false, 9, 2, false, 0)),
    MEDIUM("medium", new KoboBotTuning(1300, 2500, 0.08, true, true, 6, 1, true, 0)),
    HARD("hard", new KoboBotTuning(700, 1300, 0.0, true, true, 5, 1, true, 2));

    public static final KoboBotLevel DEFAULT = MEDIUM;

    private final String key;
    private final KoboBotTuning tuning;

    KoboBotLevel(String key, KoboBotTuning tuning) {
        this.key = key;
        this.tuning = tuning;
    }

    public String key() {
        return key;
    }

    public KoboBotTuning tuning() {
        return tuning;
    }

    /** The level named by a seat option value; anything absent or unknown gives the default. */
    public static KoboBotLevel fromOption(String value) {
        return Arrays.stream(values())
                .filter(level -> level.key.equals(value))
                .findFirst()
                .orElse(DEFAULT);
    }
}

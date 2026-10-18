package org.kevinkib.cardgames.threemenmorris.domain;

import java.util.Arrays;

/** The variants a host may pick in the lobby: an option key and the rules it stands for. */
public enum MorrisVariant {

    TAPATAN("tapatan", MorrisRules.TAPATAN),
    ACHI("achi", MorrisRules.ACHI);

    public static final MorrisVariant DEFAULT = TAPATAN;

    private final String key;
    private final MorrisRules rules;

    MorrisVariant(String key, MorrisRules rules) {
        this.key = key;
        this.rules = rules;
    }

    public String key() {
        return key;
    }

    public MorrisRules rules() {
        return rules;
    }

    public static MorrisVariant fromKey(String key) {
        return Arrays.stream(values())
                .filter(variant -> variant.key.equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Three Men's Morris variant: " + key));
    }

    public static MorrisVariant of(MorrisRules rules) {
        return Arrays.stream(values())
                .filter(variant -> variant.rules.equals(rules))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No variant has these rules: " + rules));
    }
}

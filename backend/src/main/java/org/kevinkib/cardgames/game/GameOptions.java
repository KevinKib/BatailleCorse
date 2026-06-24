package org.kevinkib.cardgames.game;

import java.util.Map;
import java.util.Optional;

/** Opaque, game-agnostic creation options. The session core stores and forwards this without
 *  interpreting any key; each game owns its own key namespace and reads only its own keys. */
public record GameOptions(Map<String, String> values) {

    private static final GameOptions NONE = new GameOptions(Map.of());

    public GameOptions {
        values = Map.copyOf(values);
    }

    public static GameOptions none() {
        return NONE;
    }

    public static GameOptions of(Map<String, String> values) {
        return values.isEmpty() ? NONE : new GameOptions(values);
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(key));
    }
}

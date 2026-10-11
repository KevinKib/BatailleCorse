package org.kevinkib.cardgames.game;

import java.util.List;

/**
 * Declares one game option so a client can render and edit it without knowing the game.
 *
 * @param key          the option key, e.g. {@code claimMode}
 * @param type         how it is edited
 * @param values       the permitted values, in display order
 * @param defaultValue the value used when the host sets nothing; one of {@code values}
 * @param labelKey     locale key of the option's label, e.g. {@code options.bullshit.claimMode}
 */
public record OptionDescriptor(String key, OptionType type, List<String> values, String defaultValue,
                               String labelKey) {

    public OptionDescriptor {
        values = List.copyOf(values);
    }
}

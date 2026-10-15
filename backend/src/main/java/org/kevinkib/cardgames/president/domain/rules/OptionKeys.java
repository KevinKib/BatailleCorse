package org.kevinkib.cardgames.president.domain.rules;

import java.util.Optional;

/** The keys of the option values: finding a constant by the key the lobby sends. */
final class OptionKeys {

    private OptionKeys() {
    }

    /** {@code KING_OF_HEARTS} to {@code kingOfHearts}. */
    static String camel(String constant) {
        StringBuilder key = new StringBuilder();
        boolean upper = false;
        for (char c : constant.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else {
                key.append(upper ? Character.toUpperCase(c) : Character.toLowerCase(c));
                upper = false;
            }
        }
        return key.toString();
    }

    static <E extends Enum<E> & ChoiceOption> Optional<E> find(Class<E> type, String key) {
        for (E value : type.getEnumConstants()) {
            if (value.key().equals(key)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}

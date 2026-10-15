package org.kevinkib.cardgames.president.domain.rules;

/**
 * An option value as the lobby names it: the camel-case form of the constant ({@code KING_OF_HEARTS} is
 * {@code kingOfHearts}), unless the constant gives its own key ({@code rounds-5}).
 */
public interface ChoiceOption {

    default String key() {
        return OptionKeys.camel(((Enum<?>) this).name());
    }
}

package org.kevinkib.cardgames.president.domain.rules;

/** V1: what a pass means for the rest of the trick. */
public enum AfterPass implements ChoiceOption {
    /** A player who passed may play again when the turn comes back in the same trick. */
    REENTER,
    /** A pass is final until the trick ends. */
    OUT
}

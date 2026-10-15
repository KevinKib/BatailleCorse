package org.kevinkib.cardgames.president.domain.round;

import org.kevinkib.cardgames.president.domain.rules.PresidentException;

/** A named card is not in the player's hand (or is named more often than the hand holds it). */
public class CardsNotInHandException extends PresidentException {

    public CardsNotInHandException() {
        super("Those cards are not all in your hand");
    }
}

package org.kevinkib.cardgames.bullshit.domain;

public class CardsNotInHandException extends Exception {

    public CardsNotInHandException(PlayerId playerId) {
        super("Player " + playerId + " does not hold all of the discarded cards");
    }
}

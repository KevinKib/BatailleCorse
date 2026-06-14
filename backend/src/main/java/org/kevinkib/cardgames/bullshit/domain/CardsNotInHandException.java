package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cardgames.bullshit.domain.player.PlayerId;

public class CardsNotInHandException extends Exception {

    public CardsNotInHandException(PlayerId playerId) {
        super("Player " + playerId + " does not hold all of the discarded cards");
    }
}

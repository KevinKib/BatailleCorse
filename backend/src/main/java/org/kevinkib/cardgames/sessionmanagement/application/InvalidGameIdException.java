package org.kevinkib.cardgames.sessionmanagement.application;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseId;

public class InvalidGameIdException extends RuntimeException {

    public InvalidGameIdException(BatailleCorseId id) {
        super("The game with id "+id+" does not exist.");
    }
}

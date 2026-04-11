package org.kevinkib.bataillecorse.sessionmanagement.application;

import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;

public class InvalidGameIdException extends RuntimeException {

    public InvalidGameIdException(BatailleCorseId id) {
        super("The game with id "+id+" does not exist.");
    }
}

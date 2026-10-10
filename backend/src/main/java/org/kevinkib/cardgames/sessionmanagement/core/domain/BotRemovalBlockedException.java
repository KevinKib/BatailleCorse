package org.kevinkib.cardgames.sessionmanagement.core.domain;

import org.kevinkib.cardgames.game.PlayerId;

/** Raised when removing a bot would leave a gap below a human, because a game is dealt to contiguous seats. */
public class BotRemovalBlockedException extends RuntimeException {

    public BotRemovalBlockedException(PlayerId seat) {
        super("Bot at seat " + seat.id() + " cannot be removed while a player sits after it");
    }
}

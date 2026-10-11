package org.kevinkib.cardgames.bataillecorse.domain.bot;

import org.kevinkib.cardgames.bataillecorse.domain.Action;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.game.PlayerId;

/**
 * What a computer seat does when its reaction time is over: slap when the rules in force say the pile
 * is slappable, otherwise send if it may, otherwise nothing. It reads the game's own slap rules, so a
 * rule change needs no change here. The caller holds the game's monitor.
 */
public class BatailleCorseBotStrategy {

    public enum Move { SEND, SLAP, NONE }

    public Move decide(BatailleCorse game, PlayerId seat) {
        if (game.isFinished()) {
            return Move.NONE;
        }
        if (game.slapWouldSucceed()) {
            return Move.SLAP;
        }
        if (game.getAvailableActions(game.getPlayerByIndex(seat.id())).contains(Action.SEND)) {
            return Move.SEND;
        }
        return Move.NONE;
    }
}

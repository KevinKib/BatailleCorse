package org.kevinkib.cardgames.president.presentation;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.play.Play;
import org.kevinkib.cardgames.president.domain.round.Phase;

import java.util.List;

/** Drives whole rounds of a President game through its public commands, for the presentation tests. */
public final class PresidentRounds {

    private PresidentRounds() {
    }

    /** Plays the round with the lowest legal play, passing when nothing beats, until the round is over. */
    public static void playRoundOut(President game) throws Exception {
        while (game.phase() == Phase.PLAYING) {
            PlayerId seat = game.currentSeat();
            List<Play> options = game.legalPlays(seat);
            if (game.trick().plays().isEmpty() || !options.isEmpty()) {
                game.play(seat, options.get(0).cards());
            } else {
                game.pass(seat);
            }
        }
    }

    /**
     * A match of {@code nbPlayers} advanced to the exchange view of round 2: the round played out, everyone
     * ready, every return given back. Searches the seeds for a deal whose round 2 has an exchange.
     */
    public static President inExchangeView(org.kevinkib.cardgames.game.GameId id, int nbPlayers) throws Exception {
        return inExchangeView(id, nbPlayers, game -> true);
    }

    /** As {@link #inExchangeView(org.kevinkib.cardgames.game.GameId, int)}, for a deal the given test accepts. */
    public static President inExchangeView(org.kevinkib.cardgames.game.GameId id, int nbPlayers,
                                           java.util.function.Predicate<President> accepted) throws Exception {
        for (long seed = 1; seed < 500; seed++) {
            President game = new President(id, nbPlayers, seed);
            playRoundOut(game);
            readyAll(game);
            while (game.phase() == Phase.EXCHANGE) {
                var owed = game.pendingReturns().get(0);
                game.giveBack(owed.high(), game.hand(owed.high()).subList(0, owed.count()));
            }
            if (game.phase() == Phase.EXCHANGE_VIEW && accepted.test(game)) {
                return game;
            }
        }
        throw new AssertionError("no seed reaches the exchange view");
    }

    public static void readyAll(President game) throws Exception {
        for (PlayerId seat : game.getPlayerIds()) {
            game.ready(seat);
        }
    }
}

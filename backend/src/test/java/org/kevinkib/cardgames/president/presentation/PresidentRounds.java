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

    public static void readyAll(President game) throws Exception {
        for (PlayerId seat : game.getPlayerIds()) {
            game.ready(seat);
        }
    }
}

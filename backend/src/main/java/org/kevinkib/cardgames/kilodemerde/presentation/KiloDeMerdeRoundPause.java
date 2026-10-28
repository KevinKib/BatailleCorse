package org.kevinkib.cardgames.kilodemerde.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.kilodemerde.domain.Phase;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;

import java.time.Duration;
import java.util.Optional;

/**
 * The pause between two rounds (A11), so that a table of 10 never waits for a click: while the hands are revealed
 * ({@link Phase#ROUND_OVER}) one task is armed, keyed on the round; when it fires it deals the next round or ends
 * the game. A forfeit in the pause re-forms the table but keeps the round, hence the same task.
 */
public class KiloDeMerdeRoundPause extends KiloDeMerdeTimer<Integer> {

    private final KiloDeMerdeGameActions actions;
    private final Duration pause;

    public KiloDeMerdeRoundPause(KiloDeMerdeGameActions actions, BotScheduler scheduler, Duration pause) {
        super(scheduler);
        this.actions = actions;
        this.pause = pause;
    }

    @Override
    protected Optional<Integer> keyOf(KiloDeMerde game) {
        return game.phase() == Phase.ROUND_OVER ? Optional.of(game.round()) : Optional.empty();
    }

    @Override
    protected Duration delayOf(KiloDeMerde game, Integer round) {
        return pause;
    }

    @Override
    protected void fire(GameId id, KiloDeMerde game, Integer round) throws Exception {
        actions.nextRound(id, game, round);
    }
}

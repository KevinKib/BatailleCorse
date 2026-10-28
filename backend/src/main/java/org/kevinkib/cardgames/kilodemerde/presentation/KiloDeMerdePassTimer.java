package org.kevinkib.cardgames.kilodemerde.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.kilodemerde.domain.Phase;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;

import java.time.Duration;
import java.util.Optional;

/**
 * The pass timer (A3): a step ends when every seat has chosen, or when the time is up and the server passes the
 * default card for the seats that have not. Armed while the game is passing, keyed on round, step and number of
 * seats; off when the property {@code kilodemerde.pass-timer-ms} is 0.
 */
public class KiloDeMerdePassTimer extends KiloDeMerdeTimer<KiloDeMerdeStepKey> {

    private final KiloDeMerdeGameActions actions;
    private final KiloDeMerdeDeadlines deadlines;

    public KiloDeMerdePassTimer(KiloDeMerdeGameActions actions, BotScheduler scheduler,
                                KiloDeMerdeDeadlines deadlines) {
        super(scheduler);
        this.actions = actions;
        this.deadlines = deadlines;
    }

    @Override
    protected Optional<KiloDeMerdeStepKey> keyOf(KiloDeMerde game) {
        if (!deadlines.passTimerEnabled() || game.phase() != Phase.PASSING) {
            return Optional.empty();
        }
        return Optional.of(KiloDeMerdeStepKey.of(game));
    }

    @Override
    protected Duration delayOf(KiloDeMerde game, KiloDeMerdeStepKey key) {
        Long endsAt = deadlines.passEndsAt(game);
        return Duration.ofMillis(endsAt == null ? 0 : Math.max(0, endsAt - deadlines.nowMs()));
    }

    @Override
    protected void fire(GameId id, KiloDeMerde game, KiloDeMerdeStepKey key) throws Exception {
        actions.expirePassTimer(id, game, key);
    }
}

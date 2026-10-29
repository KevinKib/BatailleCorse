package org.kevinkib.cardgames.kilodemerde.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;

import java.time.Duration;
import java.util.Optional;

/**
 * The fallback of the ready barrier: the table waits for every seat to confirm it is ready (before the first round and
 * at the end of each round), but never forever. Armed while the game waits, keyed on {@link KiloDeMerdeReadyKey}, so
 * the confirmations (each bumps the version) neither cancel nor restart it; when it fires the seats still missing are
 * counted ready. Off when the property {@code kilodemerde.ready-timeout-ms} is 0.
 */
public class KiloDeMerdeReadyTimeout extends KiloDeMerdeTimer<KiloDeMerdeReadyKey> {

    private final KiloDeMerdeGameActions actions;
    private final KiloDeMerdeDeadlines deadlines;

    public KiloDeMerdeReadyTimeout(KiloDeMerdeGameActions actions, BotScheduler scheduler,
                                   KiloDeMerdeDeadlines deadlines) {
        super(scheduler);
        this.actions = actions;
        this.deadlines = deadlines;
    }

    @Override
    protected Optional<KiloDeMerdeReadyKey> keyOf(KiloDeMerde game) {
        if (!deadlines.readyTimeoutEnabled()) {
            return Optional.empty();
        }
        return Optional.ofNullable(KiloDeMerdeReadyKey.of(game));
    }

    @Override
    protected Duration delayOf(KiloDeMerde game, KiloDeMerdeReadyKey key) {
        Long endsAt = deadlines.readyEndsAt(game);
        return Duration.ofMillis(endsAt == null ? 0 : Math.max(0, endsAt - deadlines.nowMs()));
    }

    @Override
    protected void fire(GameId id, KiloDeMerde game, KiloDeMerdeReadyKey key) throws Exception {
        actions.expireReady(id, game, key);
    }
}

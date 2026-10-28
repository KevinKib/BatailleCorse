package org.kevinkib.cardgames.kilodemerde.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.kilodemerde.domain.Phase;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;

import java.time.Duration;
import java.util.Optional;

/**
 * The race timeout (A5): a race ends when all but one seat have slapped, or when the time is up and every seat that
 * has not slapped draws. Armed while a race is open, keyed on the race number, so the slaps of the seats (each bumps
 * the version) neither cancel nor restart it; off when the property {@code kilodemerde.race-timeout-ms} is 0.
 */
public class KiloDeMerdeRaceTimeout extends KiloDeMerdeTimer<Integer> {

    private final KiloDeMerdeGameActions actions;
    private final KiloDeMerdeDeadlines deadlines;

    public KiloDeMerdeRaceTimeout(KiloDeMerdeGameActions actions, BotScheduler scheduler,
                                  KiloDeMerdeDeadlines deadlines) {
        super(scheduler);
        this.actions = actions;
        this.deadlines = deadlines;
    }

    @Override
    protected Optional<Integer> keyOf(KiloDeMerde game) {
        if (!deadlines.raceTimeoutEnabled() || game.phase() != Phase.RACE) {
            return Optional.empty();
        }
        return Optional.of(game.raceId());
    }

    @Override
    protected Duration delayOf(KiloDeMerde game, Integer raceId) {
        Long endsAt = deadlines.raceEndsAt(game);
        return Duration.ofMillis(endsAt == null ? 0 : Math.max(0, endsAt - deadlines.nowMs()));
    }

    @Override
    protected void fire(GameId id, KiloDeMerde game, Integer raceId) throws Exception {
        actions.closeRace(id, game, raceId);
    }
}

package org.kevinkib.cardgames.kilodemerde.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.kilodemerde.domain.Phase;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameEvictionListener;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When the pass timer and the race timeout of a game end, as server epoch milliseconds. The state sent to the
 * seats (for the countdown) and the timers that fire read the same instant, so the picture and the rule agree.
 *
 * <p>The instant of a step or of a race is fixed the first time somebody asks for it (the state broadcast that
 * announces the step comes before the timer is armed) and stays the same for every seat, every projection and the
 * timer, until the step or the race changes. A step is told apart by its {@link KiloDeMerdeStepKey}, a race by the
 * race number of the game; a rematch is a new game object and starts afresh.
 */
public class KiloDeMerdeDeadlines implements GameEvictionListener {

    private record Stamp<K>(KiloDeMerde game, K key, long endsAt) {
    }

    private final Clock clock;
    private final long passTimerMs;
    private final long raceTimeoutMs;
    private final Map<GameId, Stamp<KiloDeMerdeStepKey>> passes = new ConcurrentHashMap<>();
    private final Map<GameId, Stamp<Integer>> races = new ConcurrentHashMap<>();

    /**
     * @param passTimerMs   how long a step lasts before the server passes for the slow seats; 0 turns the timer off
     * @param raceTimeoutMs how long a race stays open before the seats that did not slap draw; 0 turns it off
     */
    public KiloDeMerdeDeadlines(Clock clock, long passTimerMs, long raceTimeoutMs) {
        this.clock = clock;
        this.passTimerMs = passTimerMs;
        this.raceTimeoutMs = raceTimeoutMs;
    }

    /** No timer at all: the states carry no deadline. */
    public static KiloDeMerdeDeadlines off() {
        return new KiloDeMerdeDeadlines(Clock.systemUTC(), 0, 0);
    }

    public boolean passTimerEnabled() {
        return passTimerMs > 0;
    }

    public boolean raceTimeoutEnabled() {
        return raceTimeoutMs > 0;
    }

    public long nowMs() {
        return clock.millis();
    }

    /** When the step being played ends; null when the timer is off or the game is not passing. */
    public Long passEndsAt(KiloDeMerde game) {
        if (passTimerMs <= 0) {
            return null;
        }
        synchronized (game) {
            if (game.phase() != Phase.PASSING) {
                return null;
            }
            KiloDeMerdeStepKey key = KiloDeMerdeStepKey.of(game);
            Stamp<KiloDeMerdeStepKey> current = passes.get(game.getId());
            if (current == null || current.game() != game || !current.key().equals(key)) {
                current = new Stamp<>(game, key, clock.millis() + passTimerMs);
                passes.put(game.getId(), current);
            }
            return current.endsAt();
        }
    }

    /** When the open race ends; null when the timeout is off or no race is open. */
    public Long raceEndsAt(KiloDeMerde game) {
        if (raceTimeoutMs <= 0) {
            return null;
        }
        synchronized (game) {
            if (game.phase() != Phase.RACE) {
                return null;
            }
            Stamp<Integer> current = races.get(game.getId());
            if (current == null || current.game() != game || current.key() != game.raceId()) {
                current = new Stamp<>(game, game.raceId(), clock.millis() + raceTimeoutMs);
                races.put(game.getId(), current);
            }
            return current.endsAt();
        }
    }

    @Override
    public void onEvicted(GameId id) {
        passes.remove(id);
        races.remove(id);
    }
}

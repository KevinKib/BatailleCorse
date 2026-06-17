package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.core.application.port.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.List;

public class GameCleanupService {

    private static final Logger log = LoggerFactory.getLogger(GameCleanupService.class);

    public static final Duration FINISHED_GRACE = Duration.ofMinutes(2);
    public static final Duration IDLE_TTL = Duration.ofMinutes(30);

    private final SessionRepository repository;
    private final List<GameEvictionListener> listeners;

    public GameCleanupService(SessionRepository repository, List<GameEvictionListener> listeners) {
        this.repository = repository;
        this.listeners = listeners;
    }

    @Scheduled(fixedDelayString = "PT1M")
    public void sweep() {
        List<GameId> evicted = repository.evictStale(FINISHED_GRACE, IDLE_TTL);
        if (!evicted.isEmpty()) {
            log.info("Evicted {} stale game(s): {}", evicted.size(), evicted);
            evicted.forEach(id -> listeners.forEach(l -> l.onEvicted(id)));
        }
    }
}

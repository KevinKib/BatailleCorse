package org.kevinkib.cardgames.sessionmanagement.application;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.application.port.SessionRepository;
import org.kevinkib.cardgames.presentation.ForfeitReasonRegistry;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ConnectionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.List;

public class GameCleanupService {

    private static final Logger log = LoggerFactory.getLogger(GameCleanupService.class);

    /** Finished/forfeited games linger this long so a reconnecting loser can still read the result. */
    public static final Duration FINISHED_GRACE = Duration.ofMinutes(2);
    /** Unfinished games with no activity for this long are abandoned and removed. */
    public static final Duration IDLE_TTL = Duration.ofMinutes(30);

    private final SessionRepository repository;
    private final ConnectionRegistry presenceRegistry;
    private final ForfeitReasonRegistry forfeitReasonRegistry;

    public GameCleanupService(SessionRepository repository, ConnectionRegistry presenceRegistry, ForfeitReasonRegistry forfeitReasonRegistry) {
        this.repository = repository;
        this.presenceRegistry = presenceRegistry;
        this.forfeitReasonRegistry = forfeitReasonRegistry;
    }

    @Scheduled(fixedDelayString = "PT1M")
    public void sweep() {
        List<GameId> evicted = repository.evictStale(FINISHED_GRACE, IDLE_TTL);
        if (!evicted.isEmpty()) {
            log.info("Evicted {} stale game(s): {}", evicted.size(), evicted);
            evicted.forEach(presenceRegistry::removeGame);
            evicted.forEach(forfeitReasonRegistry::removeGame);
        }
    }
}

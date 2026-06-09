package org.kevinkib.bataillecorse.sessionmanagement.application;

import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.sessionmanagement.application.port.SessionRepository;
import org.kevinkib.bataillecorse.websocket.presentation.v1.PresenceRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class GameCleanupService {

    /** Finished/forfeited games linger this long so a reconnecting loser can still read the result. */
    public static final Duration FINISHED_GRACE = Duration.ofMinutes(2);
    /** Unfinished games with no activity for this long are abandoned and removed. */
    public static final Duration IDLE_TTL = Duration.ofMinutes(30);

    private final SessionRepository repository;
    private final PresenceRegistry presenceRegistry;

    public GameCleanupService(SessionRepository repository, PresenceRegistry presenceRegistry) {
        this.repository = repository;
        this.presenceRegistry = presenceRegistry;
    }

    @Scheduled(fixedDelayString = "PT1M")
    public void sweep() {
        List<BatailleCorseId> evicted = repository.evictStale(FINISHED_GRACE, IDLE_TTL);
        evicted.forEach(presenceRegistry::removeGame);
    }
}

package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.presentation.dto.PublicLobbyListDto;
import org.kevinkib.cardgames.sessionmanagement.core.application.PublicLobbyList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Publishes the whole public game list of a game type whenever it changed, at most once per {@link #WINDOW_MS}
 * per type. The first change of a window schedules one send; the changes that follow ride on it, and the list is
 * read when the send runs, so the last change is always the one published. Types do not wait for each other.
 * The logs carry only the game type and the count, never a name or an id.
 */
public class PublicLobbyBroadcaster {

    static final long WINDOW_MS = 500;

    private static final Logger log = LoggerFactory.getLogger(PublicLobbyBroadcaster.class);

    private final Function<String, PublicLobbyList> source;
    private final BiConsumer<String, Object> sink;
    private final TaskScheduler scheduler;
    private final Clock clock;
    private final Map<String, Boolean> pending = new ConcurrentHashMap<>();

    /**
     * @param source builds the current list of a game type
     * @param sink   sends a payload to the public topic of a game type
     */
    public PublicLobbyBroadcaster(Function<String, PublicLobbyList> source, BiConsumer<String, Object> sink,
                                  TaskScheduler scheduler, Clock clock) {
        this.source = source;
        this.sink = sink;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    /** Tells that what the list of this game type shows may have changed. Cheap, and safe to call from anywhere. */
    public void changed(String gameType) {
        if (pending.putIfAbsent(gameType, Boolean.TRUE) != null) {
            return;
        }
        try {
            scheduler.schedule(() -> send(gameType), clock.instant().plusMillis(WINDOW_MS));
        } catch (RuntimeException e) {
            pending.remove(gameType);
            log.warn("Public lobbies of {}: could not schedule a send ({})", gameType, e.toString());
        }
    }

    private void send(String gameType) {
        // Cleared first: a change that lands while the list is being built schedules the next send.
        pending.remove(gameType);
        try {
            PublicLobbyListDto dto = PublicLobbyListDto.from(source.apply(gameType));
            sink.accept(gameType, dto);
            log.debug("Public lobbies of {}: {} listed", gameType, dto.lobbies().size());
        } catch (RuntimeException e) {
            log.warn("Public lobbies of {}: send failed ({})", gameType, e.toString());
        }
    }
}

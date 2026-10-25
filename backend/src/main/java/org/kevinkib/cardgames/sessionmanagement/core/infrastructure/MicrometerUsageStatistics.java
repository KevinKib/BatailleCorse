package org.kevinkib.cardgames.sessionmanagement.core.infrastructure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.kevinkib.cardgames.sessionmanagement.core.application.port.UsageStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.IntSupplier;

/**
 * Counts usage events with Micrometer (tag {@code game}) and writes one INFO line per event on the
 * {@value #LOGGER_NAME} logger, so the numbers survive a restart in {@code docker compose logs}. A line holds the
 * event name, the game type and counts: no name, address, token or game id.
 */
public class MicrometerUsageStatistics implements UsageStatistics {

    public static final String LOGGER_NAME = "usage.stats";
    private static final Logger STATS = LoggerFactory.getLogger(LOGGER_NAME);

    private final MeterRegistry registry;

    public MicrometerUsageStatistics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Gauges of what the server holds right now; both reset to zero with a restart. */
    public void registerGauges(IntSupplier openRooms, IntSupplier connectedPlayers) {
        Gauge.builder("cardgames.rooms.open", openRooms::getAsInt)
                .description("Rooms held in memory (lobbies and started games)")
                .register(registry);
        Gauge.builder("cardgames.players.connected", connectedPlayers::getAsInt)
                .description("Seats currently held by a live connection")
                .register(registry);
    }

    @Override
    public void gameCreated(String gameType) {
        count("cardgames.games.created", gameType, 1);
        STATS.info("stats event=game_created game={}", gameType);
    }

    @Override
    public void gameStarted(String gameType, int humans, int bots) {
        count("cardgames.games.started", gameType, 1);
        count("cardgames.games.started.humans", gameType, humans);
        count("cardgames.games.started.bots", gameType, bots);
        STATS.info("stats event=game_started game={} humans={} bots={}", gameType, humans, bots);
    }

    @Override
    public void gameFinished(String gameType) {
        count("cardgames.games.finished", gameType, 1);
        STATS.info("stats event=game_finished game={}", gameType);
    }

    @Override
    public void rematch(String gameType) {
        count("cardgames.games.rematches", gameType, 1);
        STATS.info("stats event=rematch game={}", gameType);
    }

    @Override
    public void seatJoined(String gameType) {
        count("cardgames.seats.joined", gameType, 1);
        STATS.info("stats event=seat_joined game={}", gameType);
    }

    @Override
    public void botAdded(String gameType) {
        count("cardgames.bots.added", gameType, 1);
        STATS.info("stats event=bot_added game={}", gameType);
    }

    private void count(String meter, String gameType, double amount) {
        Counter.builder(meter).tag("game", gameType).register(registry).increment(amount);
    }
}

package org.kevinkib.cardgames.sessionmanagement.core.infrastructure;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

class MicrometerUsageStatisticsTest {

    private SimpleMeterRegistry registry;
    private MicrometerUsageStatistics stats;
    private ListAppender<ILoggingEvent> logs;
    private Logger statsLogger;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        stats = new MicrometerUsageStatistics(registry);
        statsLogger = (Logger) LoggerFactory.getLogger(MicrometerUsageStatistics.LOGGER_NAME);
        logs = new ListAppender<>();
        logs.start();
        statsLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        statsLogger.detachAppender(logs);
    }

    private double count(String meter, String game) {
        return registry.counter(meter, "game", game).count();
    }

    private List<String> lines() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void eachEventIncrementsItsOwnCounterTaggedByGame() {
        stats.gameCreated("mangola");
        stats.gameCreated("mangola");
        stats.gameCreated("kobo");
        stats.gameStarted("mangola", 2, 1);
        stats.gameFinished("mangola");
        stats.rematch("mangola");
        stats.seatJoined("mangola");
        stats.botAdded("mangola");

        assertThat(count("cardgames.games.created", "mangola"), is(2.0));
        assertThat(count("cardgames.games.created", "kobo"), is(1.0));
        assertThat(count("cardgames.games.started", "mangola"), is(1.0));
        assertThat(count("cardgames.games.finished", "mangola"), is(1.0));
        assertThat(count("cardgames.games.rematches", "mangola"), is(1.0));
        assertThat(count("cardgames.seats.joined", "mangola"), is(1.0));
        assertThat(count("cardgames.bots.added", "mangola"), is(1.0));
    }

    @Test
    void startedGamesAlsoCountTheirHumanAndBotSeats() {
        stats.gameStarted("mangola", 2, 1);

        assertThat(count("cardgames.games.started.humans", "mangola"), is(2.0));
        assertThat(count("cardgames.games.started.bots", "mangola"), is(1.0));
    }

    @Test
    void eachEventWritesOneInfoLineWithOnlyEventAndGame() {
        stats.gameCreated("mangola");
        stats.gameStarted("mangola", 2, 1);
        stats.gameFinished("kobo");
        stats.rematch("kobo");
        stats.seatJoined("kobo");
        stats.botAdded("kobo");

        assertThat(lines(), contains(
                "stats event=game_created game=mangola",
                "stats event=game_started game=mangola humans=2 bots=1",
                "stats event=game_finished game=kobo",
                "stats event=rematch game=kobo",
                "stats event=seat_joined game=kobo",
                "stats event=bot_added game=kobo"));
        assertThat(logs.list.get(0).getLevel(), is(Level.INFO));
    }

    @Test
    void gaugesFollowTheirSuppliers() {
        AtomicInteger rooms = new AtomicInteger(3);
        AtomicInteger connected = new AtomicInteger(5);
        stats.registerGauges(rooms::get, connected::get);

        assertThat(registry.get("cardgames.rooms.open").gauge().value(), is(3.0));
        assertThat(registry.get("cardgames.players.connected").gauge().value(), is(5.0));

        rooms.set(0);
        connected.set(1);

        assertThat(registry.get("cardgames.rooms.open").gauge().value(), is(0.0));
        assertThat(registry.get("cardgames.players.connected").gauge().value(), is(1.0));
    }
}

package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

/** The session use cases report what players do to the {@code UsageStatistics} port, and only that. */
class SessionServiceUsageStatisticsTest {

    private RecordingUsageStatistics stats;
    private SessionService service;
    private GameId id;
    private String hostToken;

    @BeforeEach
    void setUp() {
        stats = new RecordingUsageStatistics();
        service = new SessionService(
                new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new TieredFactory(), new BullshitFactory())),
                stats);
        RoomCreated room = service.createRoom(TieredFactory.GAME_TYPE, "Alice");
        id = new GameId(room.gameId());
        hostToken = room.hostToken();
    }

    @Test
    void whenARoomIsCreated_thenGameCreatedIsReportedWithItsGameType() {
        assertThat(stats.events, contains("created:tiered"));
    }

    @Test
    void whenAHumanJoins_thenSeatJoinedIsReported() {
        service.joinRoom(id, "Bob");

        assertThat(stats.events, contains("created:tiered", "joined:tiered"));
    }

    @Test
    void whenTheRoomIsFull_thenNoSeatJoinedIsReported() {
        for (int i = 0; i < service.maxPlayers(TieredFactory.GAME_TYPE) - 1; i++) {
            service.joinRoom(id, "P" + i);
        }
        stats.events.clear();

        try {
            service.joinRoom(id, "Late");
        } catch (RoomFullException expected) {
            // refused: nothing to count
        }

        assertThat(stats.events, empty());
    }

    @Test
    void whenTheHostAddsABot_thenBotAddedIsReported() {
        service.addBot(id, hostToken);

        assertThat(stats.events, contains("created:tiered", "bot:tiered"));
    }

    @Test
    void whenTheGameStarts_thenGameStartedIsReportedWithHumansAndBots() {
        service.joinRoom(id, "Bob");
        service.addBot(id, hostToken);

        service.startGame(id, hostToken);

        assertThat(stats.events.get(stats.events.size() - 1), org.hamcrest.Matchers.is("started:tiered:2h1b"));
    }

    @Test
    void whenStartIsRefused_thenNothingIsReported() {
        try {
            service.startGame(id, "not-the-host-token");
        } catch (RuntimeException expected) {
            // refused: nothing to count
        }

        assertThat(stats.events, contains("created:tiered"));
    }

    @Test
    void givenAStartedGame_whenPlayAgain_thenOneRematchIsReportedPerReopen() {
        service.addBot(id, hostToken);
        service.startGame(id, hostToken);
        stats.events.clear();

        service.playAgain(id, "Alice");
        service.playAgain(id, "Bob");

        assertThat(stats.events, contains("rematch:tiered"));
    }

    @Test
    void givenAFinishedGameNotYetReported_whenPlayAgain_thenFinishedIsReportedBeforeTheRematch() {
        service.addBot(id, hostToken);
        service.startGame(id, hostToken).forfeit(new PlayerId(1));
        stats.events.clear();

        service.playAgain(id, "Alice");

        assertThat(stats.events, contains("finished:tiered", "rematch:tiered"));
    }

    @Test
    void givenFinishedGames_whenRecordFinishedGamesRunsTwice_thenEachGameIsReportedOnce() {
        service.addBot(id, hostToken);
        service.startGame(id, hostToken).forfeit(new PlayerId(1));
        stats.events.clear();

        service.recordFinishedGames();
        service.recordFinishedGames();

        assertThat(stats.events, contains("finished:tiered"));
    }

    @Test
    void givenAGameStillRunning_whenRecordFinishedGames_thenNothingIsReported() {
        service.addBot(id, hostToken);
        service.startGame(id, hostToken);
        stats.events.clear();

        service.recordFinishedGames();

        assertThat(stats.events, empty());
    }
}

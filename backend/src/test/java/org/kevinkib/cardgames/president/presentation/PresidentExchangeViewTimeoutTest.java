package org.kevinkib.cardgames.president.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.PresidentFactory;
import org.kevinkib.cardgames.president.domain.round.Phase;
import org.kevinkib.cardgames.president.presentation.dto.PresidentDto;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.bot.ManualBotScheduler;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/** The timer that stops waiting for the exchange view, with a manual scheduler and the real broadcaster. */
class PresidentExchangeViewTimeoutTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private InMemorySessionRepository repository;
    private RecordingMessaging messaging;
    private SeatStateBroadcaster<President, PresidentDto> broadcaster;
    private PresidentGameActions actions;
    private ManualBotScheduler scheduler;
    private PresidentExchangeViewTimeout timeout;

    @BeforeEach
    void setUp() {
        repository = new InMemorySessionRepository(Clock.systemUTC());
        SessionService sessions = new SessionService(repository, new GameFactories(List.of(new PresidentFactory())));
        messaging = new RecordingMessaging(sessions);
        broadcaster = new SeatStateBroadcaster<>(messaging, sessions::botSeats, sessions::seatNames,
                (game, viewer, names, bots) -> PresidentDto.forViewer(game, viewer, names, bots));
        actions = new PresidentGameActions(sessions, broadcaster);
        scheduler = new ManualBotScheduler();
        timeout = new PresidentExchangeViewTimeout(actions, scheduler, TIMEOUT);
        broadcaster.addListener(timeout);
    }

    private President register(President game) {
        SessionGame session = SessionGame.create(game.getId(), game.getPlayerIds(), PresidentFactory.GAME_TYPE);
        session.claimHost("Alice");
        session.claimNextFreeSeat("Bob");
        session.claimNextFreeSeat("Carl");
        repository.save(game, session);
        return game;
    }

    private President inTheView() throws Exception {
        return register(PresidentRounds.inExchangeView(GameId.generate(), 3));
    }

    private void announce(President game) {
        broadcaster.broadcast(game, "EXCHANGE", new EmptyEventData(), "A state.");
    }

    @Test
    void enteringTheViewArmsOneTaskForTheTimeout() throws Exception {
        President game = inTheView();

        announce(game);

        assertThat(scheduler.pending().size(), is(1));
        assertThat(scheduler.pending().get(0).delay(), is(TIMEOUT));
    }

    @Test
    void anotherBroadcastOfTheSameViewDoesNotRestartTheTimer() throws Exception {
        President game = inTheView();
        announce(game);
        ManualBotScheduler.Entry armed = scheduler.pending().get(0);

        actions.seeExchange(game.getId(), new PlayerId(0), game.round());

        assertThat(scheduler.pending(), is(List.of(armed)));
        assertThat(scheduler.all().size(), is(1));
        assertThat(armed.isCancelled(), is(false));
    }

    @Test
    void theFiredTimerClosesTheViewAndBroadcastsIt() throws Exception {
        President game = inTheView();
        announce(game);
        messaging.clear();

        scheduler.runNext();

        assertThat(game.phase(), is(Phase.PLAYING));
        assertThat(messaging.payloads.stream().map(Response::getEventType).toList(), hasItem("EXCHANGE_CLOSED"));
    }

    @Test
    void thePlayBeginningCancelsTheTimer() throws Exception {
        President game = inTheView();
        announce(game);
        ManualBotScheduler.Entry armed = scheduler.pending().get(0);

        for (PlayerId seat : game.getPlayerIds()) {
            actions.seeExchange(game.getId(), seat, game.round());
        }

        assertThat(game.phase(), is(Phase.PLAYING));
        assertThat(armed.isCancelled(), is(true));
        assertThat(scheduler.pending(), is(empty()));
    }

    @Test
    void aTableOutsideTheViewArmsNothing() throws Exception {
        President game = inTheView();
        PresidentFixturesSeen.all(game);

        announce(game);

        assertThat(scheduler.all(), is(empty()));
    }

    @Test
    void evictionCancelsTheTimer() throws Exception {
        President game = inTheView();
        announce(game);
        ManualBotScheduler.Entry armed = scheduler.pending().get(0);

        timeout.onEvicted(game.getId());

        assertThat(armed.isCancelled(), is(true));
    }

    @Test
    void aTimerThatFiresAfterTheViewClosedChangesNothing() throws Exception {
        President game = inTheView();
        announce(game);
        ManualBotScheduler.Entry armed = scheduler.pending().get(0);
        PresidentFixturesSeen.all(game); // the play began with no broadcast
        int version = game.version();
        messaging.clear();

        armed.run();

        assertThat(game.version(), is(version));
        assertThat(messaging.payloads, is(empty()));
    }

    @Test
    void aNewGameUnderTheSameIdIsANewTableWithItsOwnTimer() throws Exception {
        GameId id = GameId.generate();
        President first = register(PresidentRounds.inExchangeView(id, 3));
        announce(first);
        ManualBotScheduler.Entry firstTimer = scheduler.pending().get(0);

        President second = register(PresidentRounds.inExchangeView(id, 3));
        announce(second);

        assertThat(firstTimer.isCancelled(), is(true));
        assertThat(scheduler.pending().size(), is(1));
        scheduler.runNext();
        assertThat(second.phase(), is(Phase.PLAYING));
        assertThat(first.phase(), is(Phase.EXCHANGE_VIEW));
    }

    @Test
    void theNextRoundsViewGetsANewTimer() throws Exception {
        President game = inTheView();
        announce(game);
        scheduler.runNext();
        assertThat(game.phase(), is(Phase.PLAYING));
        announce(game);
        assertThat(scheduler.pending(), is(empty()));
    }

    /** Acknowledges the exchange for every seat straight on the game, without any broadcast. */
    private static final class PresidentFixturesSeen {
        static void all(President game) throws Exception {
            org.kevinkib.cardgames.president.domain.PresidentFixtures.seeExchangeAll(game);
        }
    }
}

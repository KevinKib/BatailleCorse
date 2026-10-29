package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorrisFactory;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lock order: a game action holds the game's monitor, then calls into the session service (seat names, bot seats,
 * tokens) while it broadcasts. The service must therefore never wait for a game's monitor while it holds its own,
 * otherwise a sweep that meets a broadcast in flight deadlocks the whole server. Three Men's Morris is used because
 * its {@code isFinished} is synchronized on the game.
 */
class SessionServiceLockOrderTest {

    private SessionService service;
    private GameId id;
    private String hostToken;
    private Game game;

    @BeforeEach
    void setUp() {
        service = new SessionService(
                new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new ThreeMenMorrisFactory())));
        RoomCreated room = service.createRoom(ThreeMenMorrisFactory.GAME_TYPE, "Alice");
        id = new GameId(room.gameId());
        hostToken = room.hostToken();
        service.addBot(id, hostToken);
        game = service.startGame(id, hostToken);
    }

    /**
     * Holds the game's monitor, starts {@code sessionCall} on another thread and waits until it is blocked on that
     * monitor (or done), then checks that the service's own lock is still free by asking it for the bot seats, as a
     * broadcast does. Every thread is a daemon: a deadlock fails the test instead of hanging the build.
     */
    private boolean serviceStaysFreeWhileTheGameIsLocked(Consumer<SessionService> sessionCall) throws Exception {
        CompletableFuture<Boolean> answered = new CompletableFuture<>();
        CountDownLatch release = new CountDownLatch(1);
        Thread action = new Thread(() -> {
            synchronized (game) {
                Thread sweeper = new Thread(() -> sessionCall.accept(service));
                sweeper.setDaemon(true);
                sweeper.start();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (sweeper.getState() != Thread.State.BLOCKED && sweeper.isAlive() && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }
                CompletableFuture<Void> lookup = CompletableFuture.runAsync(() -> service.botSeats(id), daemonExecutor());
                try {
                    lookup.get(2, TimeUnit.SECONDS);
                    answered.complete(true);
                } catch (Exception e) {
                    answered.complete(false);
                }
                try {
                    release.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        action.setDaemon(true);
        action.start();
        boolean free = answered.get(10, TimeUnit.SECONDS);
        release.countDown();
        return free;
    }

    private static java.util.concurrent.Executor daemonExecutor() {
        return task -> {
            Thread thread = new Thread(task);
            thread.setDaemon(true);
            thread.start();
        };
    }

    @Test
    void givenAGameActionHoldingTheGame_whenTheSweepRecordsFinishedGames_thenTheServiceLockStaysFree() throws Exception {
        assertTrue(serviceStaysFreeWhileTheGameIsLocked(SessionService::recordFinishedGames),
                "recordFinishedGames held the service lock while waiting for the game's monitor");
    }

    @Test
    void givenAGameActionHoldingTheGame_whenSomeonePlaysAgain_thenTheServiceLockStaysFree() throws Exception {
        assertTrue(serviceStaysFreeWhileTheGameIsLocked(s -> s.playAgain(id, "Bob")),
                "playAgain held the service lock while waiting for the game's monitor");
    }
}

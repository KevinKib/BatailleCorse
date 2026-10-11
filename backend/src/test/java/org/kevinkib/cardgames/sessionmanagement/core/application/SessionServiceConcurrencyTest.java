package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

/** Races on the single lobby lock: every scenario is replayed many times with threads released by a latch. */
class SessionServiceConcurrencyTest {

    private static final int ROUNDS = 300;

    private SessionService service;
    private GameId id;
    private String hostToken;

    private void newRoom() {
        service = new SessionService(
                new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new BullshitFactory())));
        RoomCreated room = service.createRoom("bullshit", "Alice");
        id = new GameId(room.gameId());
        hostToken = room.hostToken();
    }

    /** Runs the tasks at the same instant and returns each outcome (value or thrown exception). */
    private static List<Object> race(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                try {
                    outcomes.add(future.get());
                } catch (ExecutionException e) {
                    outcomes.add(e.getCause());
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void givenOneFreeSeat_whenTwoJoinAtOnce_thenExactlyOneWinsAndTokensAreDistinct() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            newRoom();
            for (int i = 0; i < 4; i++) {
                service.addBot(id, hostToken);
            }

            List<Object> outcomes = race(List.of(
                    () -> service.joinRoom(id, "Bob"),
                    () -> service.joinRoom(id, "Carol")));

            long winners = outcomes.stream().filter(o -> o instanceof JoinResult).count();
            long refused = outcomes.stream().filter(o -> o instanceof RoomFullException).count();
            assertThat("round " + round, winners, is(1L));
            assertThat("round " + round, refused, is(1L));
        }
    }

    @Test
    void givenSeveralFreeSeats_whenManyJoinAtOnce_thenEachGetsItsOwnSeatAndToken() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            newRoom();
            List<Callable<Object>> joins = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                String name = "P" + i;
                joins.add(() -> service.joinRoom(id, name));
            }

            List<Object> outcomes = race(joins);

            Set<Integer> seats = new HashSet<>();
            Set<String> tokens = new HashSet<>();
            for (Object outcome : outcomes) {
                assertThat("round " + round, outcome, instanceOf(JoinResult.class));
                JoinResult result = (JoinResult) outcome;
                seats.add(result.playerId().id());
                tokens.add(result.token());
            }
            assertThat("round " + round, seats.size(), is(5));
            assertThat("round " + round, tokens.size(), is(5));
        }
    }

    @Test
    void givenABurstOfMutations_thenEveryVersionIsDistinctAndStrictlyIncreasing() throws Exception {
        for (int round = 0; round < ROUNDS / 3; round++) {
            newRoom();
            String bobToken = service.joinRoom(id, "Bob").token();
            long before = service.lobbyView(id, hostToken).lobbyVersion();
            List<Callable<Object>> mutations = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                String name = "N" + i;
                mutations.add(() -> {
                    List<AddressedLobbyView> views = service.renameSeat(id, bobToken, name);
                    return views.stream().filter(v -> v.token().equals(hostToken)).findFirst().orElseThrow()
                            .view().lobbyVersion();
                });
            }

            List<Object> outcomes = race(mutations);

            List<Long> versions = new ArrayList<>();
            outcomes.forEach(o -> versions.add((Long) o));
            Collections.sort(versions);
            for (int i = 0; i < versions.size(); i++) {
                assertThat("round " + round, versions.get(i), is(before + 1 + i));
            }
        }
    }
}

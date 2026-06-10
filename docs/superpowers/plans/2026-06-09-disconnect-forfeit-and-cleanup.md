# Disconnect Auto-Loss, Exit Confirmation, and Repository Cleanup — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a multiplayer player's WebSocket drops mid-game, the opponent sees a 60s countdown and wins automatically if they don't return; leaving an in-progress game asks for confirmation (and forfeits immediately in multiplayer); finished/abandoned games are evicted from the in-memory repository.

**Architecture:** A new domain concession path (`BatailleCorse.concede`) is the single terminal action shared by explicit forfeit and disconnect auto-loss. The backend tracks WebSocket presence via an explicit `/app/presence` message into a `PresenceRegistry`; a `DisconnectForfeitService` schedules/cancels a 60s forfeit task on Spring's `TaskScheduler` and broadcasts connection events over the existing `/topic/game/{id}` STOMP channel. The in-memory repository moves from `ArrayList` to `Map` keyed by id with a `lastActivityAt` timestamp, and a `@Scheduled` `GameCleanupService` evicts finished (short grace) and abandoned (long idle) games. The frontend sends presence on entry and reconnect, renders the countdown from a server deadline, and guards `/room/:id` navigation with a confirm dialog.

**Tech Stack:** Java 21 / Spring Boot (STOMP over WebSocket, JUnit 5 + Hamcrest), Vue 3 `<script setup>` + TypeScript + Pinia + PrimeVue, Vitest.

---

## Conventions for this plan

**Backend tests** (per project rule): no Mockito on domain classes; use real objects/builders; name tests `givenX_whenY_thenZ`; `@Nested` groups. Run from the `backend` directory. There is **no Maven wrapper** in this repo — use the IntelliJ-bundled `mvn` (or your configured `mvn`); do not invent `./mvnw`. Command form: `cd backend && mvn -Dtest=ClassName test`.

**Frontend unit tests**: Vitest, co-located `*.test.ts`. Run: `cd frontend && npx vitest run src/<path>`.

**Frontend build gate** (per project rule): the real type/build check is `cd frontend && npm run build` (vite build). A bare `vue-tsc` invocation gives a false pass; do not rely on it. Worktrees may lack `node_modules` — run `npm install` first if `npx vitest` / `npm run build` fail to resolve.

**Git**: `git add` every newly created file immediately after writing it. Commit after each task's tests pass. Branch is already `claude/jolly-goldwasser-ca0134`.

---

## File Structure

**Backend — create:**
- `core/domain/...` — (modify only) `BatailleCorse.java`, `Result.java` test additions
- `websocket/presentation/v1/api/PresencePayload.java` — STOMP payload for `/app/presence`
- `websocket/presentation/v1/dto/event/ConnectionEventData.java` — disconnect/reconnect event body
- `websocket/presentation/v1/dto/event/ForfeitEventData.java` — forfeit event body
- `websocket/presentation/v1/Seat.java` — `(BatailleCorseId, PlayerId)` value object
- `websocket/presentation/v1/PresenceRegistry.java` — `sessionId → Seat` mapping
- `websocket/presentation/v1/DisconnectForfeitService.java` — timer + forfeit orchestration + broadcasts
- `websocket/presentation/v1/WebSocketDisconnectListener.java` — `SessionDisconnectEvent` → service
- `sessionmanagement/application/GameCleanupService.java` — scheduled eviction sweep

**Backend — modify:**
- `core/domain/BatailleCorse.java` — add `concede(PlayerId)`
- `sessionmanagement/application/port/SessionRepository.java` — add `touch`, `remove`, `evictStale`
- `sessionmanagement/infrastructure/InMemorySessionRepository.java` — `Map` storage + `Clock` + new methods
- `sessionmanagement/application/SessionService.java` — add `touch`
- `websocket/presentation/v1/dto/event/EventType.java` — add `OPPONENT_DISCONNECTED`, `OPPONENT_RECONNECTED`, `FORFEIT`
- `websocket/presentation/v1/BatailleCorseWebSocketController.java` — `/app/presence`, `/app/forfeit`, `touch` on actions
- `config/AppConfig.java` — `Clock`, `TaskScheduler`, `@EnableScheduling`, pass clock to repo

**Frontend — modify:**
- `service/WebSocketService.ts` — presence persistence + re-send on reconnect
- `application/GameSession.ts` — send presence (multiplayer), handle connection events
- `application/GameEvent.ts` — add `opponent-connection` event
- `state/BatailleCorse.store.ts` — `opponentConnection` ref + event wiring + `forfeit()`
- `view/alpha/GameScreen.vue` — countdown banner + leave-confirm guard + `beforeunload`
- `main.ts` — (no change expected; guard lives in component via `onBeforeRouteLeave`)

---

# Phase 1 — Domain: terminal-by-forfeit

### Task 1: `BatailleCorse.concede(PlayerId loser)`

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/core/domain/BatailleCorse.java`
- Test: `backend/src/test/java/org/kevinkib/bataillecorse/core/domain/BatailleCorseConcedeTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/org/kevinkib/bataillecorse/core/domain/BatailleCorseConcedeTest.java`:

```java
package org.kevinkib.bataillecorse.core.domain;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class BatailleCorseConcedeTest {

    private BatailleCorse newTwoPlayerGame() {
        return new BatailleCorse(BatailleCorseId.generate(), 2);
    }

    @Test
    void givenOngoingGame_whenSeatZeroConcedes_thenSeatOneWins() {
        BatailleCorse game = newTwoPlayerGame();

        game.concede(new PlayerId(0));

        assertThat(game.isFinished(), is(true));
        assertThat(game.getWinner().id(), is(new PlayerId(1)));
    }

    @Test
    void givenAlreadyFinishedGame_whenOtherSeatConcedes_thenWinnerUnchanged() {
        BatailleCorse game = newTwoPlayerGame();
        game.concede(new PlayerId(0)); // seat 1 wins

        game.concede(new PlayerId(1)); // must be a no-op

        assertThat(game.getWinner().id(), is(new PlayerId(1)));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -Dtest=BatailleCorseConcedeTest test`
Expected: COMPILE FAILURE — `cannot find symbol: method concede(PlayerId)`.

- [ ] **Step 3: Implement `concede`**

In `BatailleCorse.java`, add this method after `grab(...)` (around line 86):

```java
    /**
     * Ends the game by concession: the given seat loses, so the other player wins,
     * regardless of card counts. Used by explicit forfeit and disconnect auto-loss.
     * No-op if the game is already finished (handles the natural-win vs. timer race).
     */
    public synchronized void concede(PlayerId loser) {
        if (isFinished()) {
            return;
        }
        Player winner = players.stream()
                .filter(player -> !player.id().equals(loser))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No opponent for seat " + loser));
        this.result = new Result(winner);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -Dtest=BatailleCorseConcedeTest test`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/core/domain/BatailleCorse.java \
        backend/src/test/java/org/kevinkib/bataillecorse/core/domain/BatailleCorseConcedeTest.java
git commit -m "feat(domain): add BatailleCorse.concede for forfeit/auto-loss"
```

---

# Phase 2 — Repository: Map storage + activity tracking + eviction

### Task 2: Map-based storage with injectable Clock

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/port/SessionRepository.java`
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/infrastructure/InMemorySessionRepository.java`
- Test: `backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/infrastructure/InMemorySessionRepositoryTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/infrastructure/InMemorySessionRepositoryTest.java`:

```java
package org.kevinkib.bataillecorse.sessionmanagement.infrastructure;

import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.PlayerId;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionGame;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemorySessionRepositoryTest {

    /** Test clock whose instant we can advance. */
    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant start) { this.now = start; }
        void advance(Duration d) { this.now = this.now.plus(d); }
        @Override public Instant instant() { return now; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }

    private BatailleCorse newGame(BatailleCorseId id) {
        return new BatailleCorse(id, 2);
    }

    @Test
    void givenSavedGame_whenLoad_thenReturnsIt() {
        var repo = new InMemorySessionRepository(Clock.systemUTC());
        var id = BatailleCorseId.generate();
        var game = newGame(id);
        repo.save(game, SessionGame.create(id, game.getPlayers()));

        assertThat(repo.load(id), is(game));
    }

    @Test
    void givenUnknownId_whenLoad_thenThrows() {
        var repo = new InMemorySessionRepository(Clock.systemUTC());
        assertThrows(IllegalArgumentException.class, () -> repo.load(BatailleCorseId.generate()));
    }

    @Test
    void givenUnfinishedGameIdleBeyondTtl_whenEvictStale_thenRemoved() {
        var clock = new MutableClock(Instant.parse("2026-06-09T00:00:00Z"));
        var repo = new InMemorySessionRepository(clock);
        var id = BatailleCorseId.generate();
        var game = newGame(id);
        repo.save(game, SessionGame.create(id, game.getPlayers()));

        clock.advance(Duration.ofMinutes(31));
        List<BatailleCorseId> evicted = repo.evictStale(Duration.ofMinutes(2), Duration.ofMinutes(30));

        assertThat(evicted, contains(id));
        assertThrows(IllegalArgumentException.class, () -> repo.load(id));
    }

    @Test
    void givenUnfinishedGameWithinTtl_whenEvictStale_thenKept() {
        var clock = new MutableClock(Instant.parse("2026-06-09T00:00:00Z"));
        var repo = new InMemorySessionRepository(clock);
        var id = BatailleCorseId.generate();
        var game = newGame(id);
        repo.save(game, SessionGame.create(id, game.getPlayers()));

        clock.advance(Duration.ofMinutes(10));
        List<BatailleCorseId> evicted = repo.evictStale(Duration.ofMinutes(2), Duration.ofMinutes(30));

        assertThat(evicted, is(empty()));
        assertThat(repo.load(id), is(game));
    }

    @Test
    void givenFinishedGamePastGrace_whenEvictStale_thenRemovedEvenWithinIdleTtl() {
        var clock = new MutableClock(Instant.parse("2026-06-09T00:00:00Z"));
        var repo = new InMemorySessionRepository(clock);
        var id = BatailleCorseId.generate();
        var game = newGame(id);
        repo.save(game, SessionGame.create(id, game.getPlayers()));
        game.concede(new PlayerId(0)); // now finished
        repo.touch(id);                // grace counts from here

        clock.advance(Duration.ofMinutes(3)); // > 2m grace, < 30m idle
        List<BatailleCorseId> evicted = repo.evictStale(Duration.ofMinutes(2), Duration.ofMinutes(30));

        assertThat(evicted, contains(id));
    }

    @Test
    void givenTouch_whenIdleMeasured_thenResetsFromTouchInstant() {
        var clock = new MutableClock(Instant.parse("2026-06-09T00:00:00Z"));
        var repo = new InMemorySessionRepository(clock);
        var id = BatailleCorseId.generate();
        var game = newGame(id);
        repo.save(game, SessionGame.create(id, game.getPlayers()));

        clock.advance(Duration.ofMinutes(20));
        repo.touch(id);                       // reset activity
        clock.advance(Duration.ofMinutes(20)); // 20m since touch, < 30m
        List<BatailleCorseId> evicted = repo.evictStale(Duration.ofMinutes(2), Duration.ofMinutes(30));

        assertThat(evicted, is(empty()));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -Dtest=InMemorySessionRepositoryTest test`
Expected: COMPILE FAILURE — no `InMemorySessionRepository(Clock)` constructor; no `touch`/`evictStale`.

- [ ] **Step 3: Extend the `SessionRepository` interface**

Replace the body of `SessionRepository.java` with:

```java
package org.kevinkib.bataillecorse.sessionmanagement.application.port;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.PlayerId;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionGame;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionToken;

import java.time.Duration;
import java.util.List;

public interface SessionRepository {

    void save(BatailleCorse batailleCorse, SessionGame sessionGame);

    BatailleCorse load(BatailleCorseId id);

    SessionToken loadSessionToken(BatailleCorseId batailleCorseId, PlayerId playerId);

    SessionGame loadSessionGame(BatailleCorseId id);

    /** Records that the game saw activity now, resetting its idle/grace clock. */
    void touch(BatailleCorseId id);

    /** Removes a game and its session. No-op if absent. */
    void remove(BatailleCorseId id);

    /**
     * Removes games whose idle time exceeds the relevant threshold: {@code finishedGrace}
     * for finished games, {@code idleTtl} for unfinished ones. Returns the evicted ids.
     */
    List<BatailleCorseId> evictStale(Duration finishedGrace, Duration idleTtl);
}
```

- [ ] **Step 4: Rewrite `InMemorySessionRepository` with Map storage**

Replace the entire body of `InMemorySessionRepository.java` with:

```java
package org.kevinkib.bataillecorse.sessionmanagement.infrastructure;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.PlayerId;
import org.kevinkib.bataillecorse.sessionmanagement.application.port.SessionRepository;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionGame;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionToken;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySessionRepository implements SessionRepository {

    private final Clock clock;
    private final Map<BatailleCorseId, BatailleCorse> games = new ConcurrentHashMap<>();
    private final Map<BatailleCorseId, SessionGame> sessionGames = new ConcurrentHashMap<>();
    private final Map<BatailleCorseId, Instant> lastActivityAt = new ConcurrentHashMap<>();

    public InMemorySessionRepository(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void save(BatailleCorse batailleCorse, SessionGame sessionGame) {
        BatailleCorseId id = batailleCorse.getId();
        games.put(id, batailleCorse);
        sessionGames.put(id, sessionGame);
        lastActivityAt.put(id, clock.instant());
    }

    @Override
    public BatailleCorse load(BatailleCorseId id) {
        BatailleCorse game = games.get(id);
        if (game == null) {
            throw new IllegalArgumentException("Unknown game " + id);
        }
        return game;
    }

    @Override
    public SessionToken loadSessionToken(BatailleCorseId batailleCorseId, PlayerId playerId) {
        return loadSessionGame(batailleCorseId)
                .findTokenByPlayer(playerId)
                .orElseThrow(IllegalArgumentException::new);
    }

    @Override
    public SessionGame loadSessionGame(BatailleCorseId id) {
        SessionGame sessionGame = sessionGames.get(id);
        if (sessionGame == null) {
            throw new IllegalArgumentException("Unknown game " + id);
        }
        return sessionGame;
    }

    @Override
    public void touch(BatailleCorseId id) {
        if (games.containsKey(id)) {
            lastActivityAt.put(id, clock.instant());
        }
    }

    @Override
    public void remove(BatailleCorseId id) {
        games.remove(id);
        sessionGames.remove(id);
        lastActivityAt.remove(id);
    }

    @Override
    public List<BatailleCorseId> evictStale(Duration finishedGrace, Duration idleTtl) {
        Instant now = clock.instant();
        List<BatailleCorseId> evicted = new ArrayList<>();
        for (Map.Entry<BatailleCorseId, BatailleCorse> entry : games.entrySet()) {
            BatailleCorseId id = entry.getKey();
            Instant last = lastActivityAt.getOrDefault(id, Instant.EPOCH);
            Duration idle = Duration.between(last, now);
            Duration threshold = entry.getValue().isFinished() ? finishedGrace : idleTtl;
            if (idle.compareTo(threshold) >= 0) {
                evicted.add(id);
            }
        }
        evicted.forEach(this::remove);
        return evicted;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && mvn -Dtest=InMemorySessionRepositoryTest test`
Expected: PASS (6 tests).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/port/SessionRepository.java \
        backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/infrastructure/InMemorySessionRepository.java \
        backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/infrastructure/InMemorySessionRepositoryTest.java
git commit -m "feat(repo): map-backed session repository with activity tracking and eviction"
```

### Task 3: Wire `Clock` into config; add `SessionService.touch`

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/config/AppConfig.java`
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/SessionService.java`
- Modify: `backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/application/SessionServiceTest.java`

- [ ] **Step 1: Update the existing `SessionServiceTest` setUp to the new constructor**

In `SessionServiceTest.java`, change the `setUp` (lines 25-28) to pass a clock:

```java
    @BeforeEach
    void setUp() {
        service = new SessionService(new InMemorySessionRepository(java.time.Clock.systemUTC()));
    }
```

- [ ] **Step 2: Add a `touch` delegation test**

Add this `@Nested` block inside `SessionServiceTest` (after the existing nested classes, before the closing brace):

```java
    @Nested
    class TouchTest {

        @Test
        void givenExistingGame_whenTouch_thenDoesNotThrow() {
            var game = service.createGame(2, GameMode.MULTIPLAYER);
            service.touch(game.getId()); // smoke: delegation wired
        }
    }
```

- [ ] **Step 3: Run to verify it fails**

Run: `cd backend && mvn -Dtest=SessionServiceTest test`
Expected: COMPILE FAILURE — `cannot find symbol: method touch(BatailleCorseId)`.

- [ ] **Step 4: Add `touch` to `SessionService`**

In `SessionService.java`, add after `getGame(...)` (around line 86):

```java
    public void touch(BatailleCorseId id) {
        repository.touch(id);
    }
```

- [ ] **Step 5: Update `AppConfig` — Clock bean, TaskScheduler, scheduling**

Replace `AppConfig.java` with:

```java
package org.kevinkib.bataillecorse.config;

import org.kevinkib.bataillecorse.sessionmanagement.application.SessionService;
import org.kevinkib.bataillecorse.sessionmanagement.application.port.SessionRepository;
import org.kevinkib.bataillecorse.sessionmanagement.infrastructure.InMemorySessionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Clock;

@Configuration
@EnableScheduling
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public SessionService sessionService() {
        return new SessionService(sessionRepository());
    }

    @Bean
    public SessionRepository sessionRepository() {
        return new InMemorySessionRepository(clock());
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("game-sched-");
        scheduler.initialize();
        return scheduler;
    }
}
```

- [ ] **Step 6: Run to verify it passes**

Run: `cd backend && mvn -Dtest=SessionServiceTest test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/config/AppConfig.java \
        backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/SessionService.java \
        backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/application/SessionServiceTest.java
git commit -m "feat(config): inject Clock, add TaskScheduler, enable scheduling; SessionService.touch"
```

### Task 4: `Seat` value object + `PresenceRegistry`

`PresenceRegistry`/`Seat` depend only on core domain, and the cleanup sweep (Task 5) clears presence for evicted games — so build them first.

**Files:**
- Create: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/Seat.java`
- Create: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/PresenceRegistry.java`
- Test: `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/PresenceRegistryTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/PresenceRegistryTest.java`:

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.PlayerId;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class PresenceRegistryTest {

    private final BatailleCorseId gameId = BatailleCorseId.generate();

    @Test
    void givenBoundSession_whenSeatOf_thenReturnsSeat() {
        var registry = new PresenceRegistry();
        var seat = new Seat(gameId, new PlayerId(0));
        registry.bind("sess-1", seat);

        assertThat(registry.seatOf("sess-1").orElseThrow(), is(seat));
    }

    @Test
    void givenBoundSession_whenUnbind_thenReturnsSeatAndForgets() {
        var registry = new PresenceRegistry();
        var seat = new Seat(gameId, new PlayerId(1));
        registry.bind("sess-2", seat);

        assertThat(registry.unbind("sess-2").orElseThrow(), is(seat));
        assertThat(registry.seatOf("sess-2").isEmpty(), is(true));
    }

    @Test
    void whenUnbindUnknownSession_thenEmpty() {
        var registry = new PresenceRegistry();
        assertThat(registry.unbind("nope").isEmpty(), is(true));
    }

    @Test
    void givenSessionsForGame_whenRemoveGame_thenAllForgotten() {
        var registry = new PresenceRegistry();
        registry.bind("a", new Seat(gameId, new PlayerId(0)));
        registry.bind("b", new Seat(gameId, new PlayerId(1)));
        var otherGame = BatailleCorseId.generate();
        registry.bind("c", new Seat(otherGame, new PlayerId(0)));

        registry.removeGame(gameId);

        assertThat(registry.seatOf("a").isEmpty(), is(true));
        assertThat(registry.seatOf("b").isEmpty(), is(true));
        assertThat(registry.seatOf("c").isPresent(), is(true));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd backend && mvn -Dtest=PresenceRegistryTest test`
Expected: COMPILE FAILURE — `Seat` / `PresenceRegistry` do not exist.

- [ ] **Step 3: Create `Seat`**

Create `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/Seat.java`:

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.PlayerId;

/** Identifies one player's seat in one game; the unit a presence/forfeit timer is keyed by. */
public record Seat(BatailleCorseId gameId, PlayerId playerId) {
}
```

- [ ] **Step 4: Create `PresenceRegistry`**

Create `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/PresenceRegistry.java`:

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Maps STOMP session ids to the seat they occupy, so a disconnect can be attributed. */
@Component
public class PresenceRegistry {

    private final Map<String, Seat> seatBySession = new ConcurrentHashMap<>();

    public void bind(String sessionId, Seat seat) {
        seatBySession.put(sessionId, seat);
    }

    public Optional<Seat> seatOf(String sessionId) {
        return Optional.ofNullable(seatBySession.get(sessionId));
    }

    public Optional<Seat> unbind(String sessionId) {
        return Optional.ofNullable(seatBySession.remove(sessionId));
    }

    public void removeGame(BatailleCorseId gameId) {
        seatBySession.values().removeIf(seat -> seat.gameId().equals(gameId));
    }
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `cd backend && mvn -Dtest=PresenceRegistryTest test`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/Seat.java \
        backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/PresenceRegistry.java \
        backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/PresenceRegistryTest.java
git commit -m "feat(presence): Seat value object and PresenceRegistry session-to-seat mapping"
```

### Task 5: Scheduled `GameCleanupService`

**Files:**
- Create: `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/GameCleanupService.java`
- Test: `backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/application/GameCleanupServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/application/GameCleanupServiceTest.java`:

```java
package org.kevinkib.bataillecorse.sessionmanagement.application;

import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.sessionmanagement.application.port.SessionRepository;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionGame;
import org.kevinkib.bataillecorse.websocket.presentation.v1.PresenceRegistry;

import java.time.Duration;
import java.util.List;
import java.util.ArrayList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

class GameCleanupServiceTest {

    /** Hand-rolled stub repository (no Mockito), recording evictStale args and returning a fixed id. */
    private static final class StubRepository implements SessionRepository {
        Duration lastFinishedGrace;
        Duration lastIdleTtl;
        final List<BatailleCorseId> toEvict = new ArrayList<>();
        public void save(BatailleCorse b, SessionGame s) {}
        public BatailleCorse load(BatailleCorseId id) { return null; }
        public org.kevinkib.bataillecorse.sessionmanagement.domain.SessionToken loadSessionToken(BatailleCorseId i, org.kevinkib.bataillecorse.core.domain.PlayerId p) { return null; }
        public SessionGame loadSessionGame(BatailleCorseId id) { return null; }
        public void touch(BatailleCorseId id) {}
        public void remove(BatailleCorseId id) {}
        public List<BatailleCorseId> evictStale(Duration finishedGrace, Duration idleTtl) {
            this.lastFinishedGrace = finishedGrace;
            this.lastIdleTtl = idleTtl;
            return new ArrayList<>(toEvict);
        }
    }

    @Test
    void givenEvictedGames_whenSweep_thenPresenceCleared() {
        var repo = new StubRepository();
        var id = BatailleCorseId.generate();
        repo.toEvict.add(id);
        var registry = new PresenceRegistry();
        var service = new GameCleanupService(repo, registry);

        var spySession = "sess-1";
        registry.bind(spySession, new org.kevinkib.bataillecorse.websocket.presentation.v1.Seat(
                id, new org.kevinkib.bataillecorse.core.domain.PlayerId(1)));

        service.sweep();

        // The evicted game's presence entries are gone.
        assertThat(registry.seatOf(spySession).isEmpty(), org.hamcrest.Matchers.is(true));
    }

    @Test
    void whenSweep_thenUsesConfiguredThresholds() {
        var repo = new StubRepository();
        var service = new GameCleanupService(repo, new PresenceRegistry());

        service.sweep();

        assertThat(List.of(repo.lastFinishedGrace, repo.lastIdleTtl),
                contains(GameCleanupService.FINISHED_GRACE, GameCleanupService.IDLE_TTL));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd backend && mvn -Dtest=GameCleanupServiceTest test`
Expected: COMPILE FAILURE — `GameCleanupService` does not exist (and `PresenceRegistry`/`Seat` if Task 5 not done).

- [ ] **Step 3: Implement `GameCleanupService`**

Create `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/GameCleanupService.java`:

```java
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
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd backend && mvn -Dtest=GameCleanupServiceTest test`
Expected: PASS (2 tests). (Requires Task 5's `PresenceRegistry` + `Seat`.)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/GameCleanupService.java \
        backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/application/GameCleanupServiceTest.java
git commit -m "feat(cleanup): scheduled eviction sweep clearing presence for evicted games"
```

---

# Phase 3 — Backend presence, disconnect timer, forfeit

### Task 6: New event types + event-data DTOs

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/event/EventType.java`
- Create: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/event/ConnectionEventData.java`
- Create: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/event/ForfeitEventData.java`
- Create: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/api/PresencePayload.java`

- [ ] **Step 1: Add the new `EventType` values**

Replace the enum body in `EventType.java`:

```java
public enum EventType {

    CREATE,
    SEND,
    SLAP,
    GRAB,
    JOIN,
    OPPONENT_DISCONNECTED,
    OPPONENT_RECONNECTED,
    FORFEIT;

    @Override
    public String toString() {
        return name();
    }
}
```

- [ ] **Step 2: Create `ConnectionEventData`**

Create `ConnectionEventData.java`:

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event;

/**
 * Body for OPPONENT_DISCONNECTED / OPPONENT_RECONNECTED.
 * @param disconnectedSeat the seat that dropped (so the recipient can ignore its own id)
 * @param deadlineEpochMs   when the auto-loss fires (epoch millis); null on reconnect
 */
public record ConnectionEventData(Integer disconnectedSeat, Long deadlineEpochMs) implements EventData {
}
```

- [ ] **Step 3: Create `ForfeitEventData`**

Create `ForfeitEventData.java`:

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event;

/** Body for FORFEIT: the seat that lost (explicitly or by auto-loss). */
public record ForfeitEventData(Integer loserSeat) implements EventData {
}
```

- [ ] **Step 4: Create `PresencePayload`**

Create `PresencePayload.java`:

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1.api;

/** Client asserts it is present at the given game seat (identified by its token). */
public record PresencePayload(String gameId, String token) {
}
```

- [ ] **Step 5: Compile**

Run: `cd backend && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/event/EventType.java \
        backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/event/ConnectionEventData.java \
        backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/event/ForfeitEventData.java \
        backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/api/PresencePayload.java
git commit -m "feat(events): add disconnect/reconnect/forfeit event types and payloads"
```

### Task 7: `DisconnectForfeitService`

**Files:**
- Create: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/DisconnectForfeitService.java`
- Test: `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/DisconnectForfeitServiceTest.java`

This service holds the timer logic and broadcasts. For tests we drive it with a hand-rolled `TaskScheduler` that captures the scheduled `Runnable` and an instant, a real `SessionService` over a real repository, a fixed `Clock`, and a recording `GameMessagingService` subclass.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/DisconnectForfeitServiceTest.java`:

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.PlayerId;
import org.kevinkib.bataillecorse.sessionmanagement.application.SessionService;
import org.kevinkib.bataillecorse.sessionmanagement.domain.GameMode;
import org.kevinkib.bataillecorse.sessionmanagement.infrastructure.InMemorySessionRepository;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.Response;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

class DisconnectForfeitServiceTest {

    /** Captures the most recently scheduled task; run() invokes it; cancel flips a flag. */
    private static final class CapturingScheduler implements TaskScheduler {
        Runnable lastTask;
        Instant lastTime;
        boolean cancelled;
        public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
            this.lastTask = task;
            this.lastTime = startTime;
            this.cancelled = false;
            return new FakeFuture();
        }
        // Unused overloads:
        public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) { throw new UnsupportedOperationException(); }
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, java.time.Duration period) { throw new UnsupportedOperationException(); }
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, java.time.Duration period) { throw new UnsupportedOperationException(); }
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, java.time.Duration delay) { throw new UnsupportedOperationException(); }
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, java.time.Duration delay) { throw new UnsupportedOperationException(); }

        private final class FakeFuture implements ScheduledFuture<Object> {
            public long getDelay(java.util.concurrent.TimeUnit unit) { return 0; }
            public int compareTo(java.util.concurrent.Delayed o) { return 0; }
            public boolean cancel(boolean mayInterrupt) { cancelled = true; return true; }
            public boolean isCancelled() { return cancelled; }
            public boolean isDone() { return false; }
            public Object get() { return null; }
            public Object get(long t, java.util.concurrent.TimeUnit u) { return null; }
        }
    }

    /** Records every broadcast instead of touching a real broker. */
    private static final class RecordingMessaging extends GameMessagingService {
        final List<Response> sent = new ArrayList<>();
        RecordingMessaging() { super(null); }
        @Override public void sendToGame(String gameId, Object payload) { sent.add((Response) payload); }
    }

    private SessionService sessionService;
    private CapturingScheduler scheduler;
    private RecordingMessaging messaging;
    private PresenceRegistry registry;
    private DisconnectForfeitService service;
    private BatailleCorseId gameId;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-09T12:00:00Z"), ZoneOffset.UTC);
        sessionService = new SessionService(new InMemorySessionRepository(clock));
        scheduler = new CapturingScheduler();
        messaging = new RecordingMessaging();
        registry = new PresenceRegistry();
        service = new DisconnectForfeitService(sessionService, messaging, registry, scheduler, clock);

        BatailleCorse game = sessionService.createGame(2, GameMode.MULTIPLAYER);
        gameId = game.getId();
    }

    private List<String> eventTypes() {
        return messaging.sent.stream().map(Response::getEventType).toList();
    }

    @Test
    void givenSeatBound_whenDisconnect_thenSchedulesAndBroadcastsDisconnected() {
        service.onPresence("sess-0", gameId, new PlayerId(0));

        service.onDisconnect("sess-0");

        assertThat(scheduler.lastTask != null, is(true));
        assertThat(eventTypes(), contains("OPPONENT_DISCONNECTED"));
    }

    @Test
    void givenPendingForfeit_whenReconnect_thenCancelsAndBroadcastsReconnected() {
        service.onPresence("sess-0", gameId, new PlayerId(0));
        service.onDisconnect("sess-0");

        service.onPresence("sess-0b", gameId, new PlayerId(0)); // same seat, new session

        assertThat(scheduler.cancelled, is(true));
        assertThat(eventTypes(), contains("OPPONENT_DISCONNECTED", "OPPONENT_RECONNECTED"));
    }

    @Test
    void givenPendingForfeit_whenTimerFires_thenGameConcededAndForfeitBroadcast() {
        service.onPresence("sess-0", gameId, new PlayerId(0));
        service.onDisconnect("sess-0");

        scheduler.lastTask.run(); // simulate the 60s elapsing

        BatailleCorse game = sessionService.getGame(gameId);
        assertThat(game.isFinished(), is(true));
        assertThat(game.getWinner().id(), is(new PlayerId(1)));
        assertThat(eventTypes(), contains("OPPONENT_DISCONNECTED", "FORFEIT"));
    }

    @Test
    void givenFinishedGame_whenDisconnect_thenNoScheduleNoBroadcast() {
        sessionService.getGame(gameId).concede(new PlayerId(1)); // already over
        service.onPresence("sess-0", gameId, new PlayerId(0));

        service.onDisconnect("sess-0");

        assertThat(scheduler.lastTask == null, is(true));
        assertThat(eventTypes().isEmpty(), is(true));
    }

    @Test
    void whenForfeitCalledDirectly_thenConcedesAndBroadcastsForfeit() {
        service.forfeit(new Seat(gameId, new PlayerId(1)));

        BatailleCorse game = sessionService.getGame(gameId);
        assertThat(game.getWinner().id(), is(new PlayerId(0)));
        assertThat(eventTypes(), contains("FORFEIT"));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd backend && mvn -Dtest=DisconnectForfeitServiceTest test`
Expected: COMPILE FAILURE — `DisconnectForfeitService` does not exist.

- [ ] **Step 3: Implement `DisconnectForfeitService`**

Create `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/DisconnectForfeitService.java`:

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.PlayerId;
import org.kevinkib.bataillecorse.sessionmanagement.application.InvalidGameIdException;
import org.kevinkib.bataillecorse.sessionmanagement.application.SessionService;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.Response;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.SuccessResponse;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.BatailleCorseDto;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.ConnectionEventData;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.EventType;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.ForfeitEventData;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Detects multiplayer disconnects and runs the 60s auto-loss timer, plus the
 * shared forfeit path. Reconnect (a fresh presence for the same seat) cancels a
 * pending timer. The timer firing — and explicit forfeit — both run {@link #forfeit}.
 */
@Service
public class DisconnectForfeitService {

    /** Grace before a dropped player auto-loses. */
    public static final Duration FORFEIT_GRACE = Duration.ofSeconds(60);

    private final SessionService sessionService;
    private final GameMessagingService messaging;
    private final PresenceRegistry registry;
    private final TaskScheduler scheduler;
    private final Clock clock;

    private final Map<Seat, ScheduledFuture<?>> pendingForfeits = new ConcurrentHashMap<>();

    public DisconnectForfeitService(SessionService sessionService,
                                    GameMessagingService messaging,
                                    PresenceRegistry registry,
                                    TaskScheduler scheduler,
                                    Clock clock) {
        this.sessionService = sessionService;
        this.messaging = messaging;
        this.registry = registry;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    /** Records presence; if this seat had a pending forfeit, cancels it and announces the return. */
    public void onPresence(String sessionId, BatailleCorseId gameId, PlayerId playerId) {
        Seat seat = new Seat(gameId, playerId);
        registry.bind(sessionId, seat);

        ScheduledFuture<?> pending = pendingForfeits.remove(seat);
        if (pending != null) {
            pending.cancel(false);
            broadcastReconnected(seat);
        }
    }

    /** Attributes a dropped session to a seat and, if the game is live, starts the auto-loss timer. */
    public void onDisconnect(String sessionId) {
        Optional<Seat> maybeSeat = registry.unbind(sessionId);
        if (maybeSeat.isEmpty()) {
            return;
        }
        Seat seat = maybeSeat.get();

        BatailleCorse game = findGame(seat.gameId());
        if (game == null || game.isFinished()) {
            return;
        }

        Instant deadline = clock.instant().plus(FORFEIT_GRACE);
        ScheduledFuture<?> task = scheduler.schedule(() -> forfeit(seat), deadline);
        pendingForfeits.put(seat, task);
        broadcastDisconnected(seat, deadline.toEpochMilli());
    }

    /** Terminal path shared by the timer and explicit /app/forfeit. Idempotent on a finished game. */
    public void forfeit(Seat seat) {
        pendingForfeits.remove(seat);

        BatailleCorse game = findGame(seat.gameId());
        if (game == null || game.isFinished()) {
            return;
        }
        game.concede(seat.playerId());
        sessionService.touch(seat.gameId()); // start the finished-grace clock
        broadcast(seat.gameId(), new SuccessResponse(
                EventType.FORFEIT,
                new ForfeitEventData(seat.playerId().id()),
                "Player " + seat.playerId() + " forfeited.",
                BatailleCorseDto.from(game)));
    }

    private void broadcastDisconnected(Seat seat, long deadlineEpochMs) {
        BatailleCorse game = findGame(seat.gameId());
        if (game == null) {
            return;
        }
        broadcast(seat.gameId(), new SuccessResponse(
                EventType.OPPONENT_DISCONNECTED,
                new ConnectionEventData(seat.playerId().id(), deadlineEpochMs),
                "Player " + seat.playerId() + " disconnected.",
                BatailleCorseDto.from(game)));
    }

    private void broadcastReconnected(Seat seat) {
        BatailleCorse game = findGame(seat.gameId());
        if (game == null) {
            return;
        }
        broadcast(seat.gameId(), new SuccessResponse(
                EventType.OPPONENT_RECONNECTED,
                new ConnectionEventData(seat.playerId().id(), null),
                "Player " + seat.playerId() + " reconnected.",
                BatailleCorseDto.from(game)));
    }

    private void broadcast(BatailleCorseId gameId, Response response) {
        messaging.sendToGame(gameId.toString(), response);
    }

    private BatailleCorse findGame(BatailleCorseId gameId) {
        try {
            return sessionService.getGame(gameId);
        } catch (InvalidGameIdException e) {
            return null;
        }
    }
}
```

> **Note on `messaging.sendToGame(gameId.toString(), ...)`:** the existing controllers pass the raw `payload.gameId()` String to `sendToGame`. Confirm `BatailleCorseId.toString()` yields that same id string (it is the value used to build `new BatailleCorseId(id)` from the client). If `BatailleCorseId` does not expose the id via `toString()`, use its accessor instead (e.g. `gameId.value()` / `gameId.id()`) — check `BatailleCorseId.java` and adjust this one call. The test uses `Response.getEventType()` only, so it passes regardless; this note is for the live broadcast destination.

- [ ] **Step 4: Verify the broadcast destination id**

Read `backend/src/main/java/org/kevinkib/bataillecorse/core/domain/BatailleCorseId.java`. If it is a record like `record BatailleCorseId(String id)` with default `toString`, replace `gameId.toString()` in `broadcast(...)` with `gameId.id()`. If it overrides `toString()` to return the id string, leave as is.

- [ ] **Step 5: Run to verify it passes**

Run: `cd backend && mvn -Dtest=DisconnectForfeitServiceTest test`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/DisconnectForfeitService.java \
        backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/DisconnectForfeitServiceTest.java
git commit -m "feat(forfeit): disconnect timer + shared forfeit path with connection broadcasts"
```

### Task 8: Disconnect event listener

**Files:**
- Create: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/WebSocketDisconnectListener.java`
- Test: `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/WebSocketDisconnectListenerTest.java`

For this test we use a tiny recording subclass of `DisconnectForfeitService`. Since `DisconnectForfeitService` is a concrete class with a multi-arg constructor, the recorder calls `super(null, null, null, null, null)` and overrides `onDisconnect`.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/WebSocketDisconnectListenerTest.java`:

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.CloseStatus;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class WebSocketDisconnectListenerTest {

    private static final class RecordingService extends DisconnectForfeitService {
        String lastSessionId;
        RecordingService() { super(null, null, null, null, null); }
        @Override public void onDisconnect(String sessionId) { this.lastSessionId = sessionId; }
    }

    @Test
    void whenDisconnectEvent_thenForwardsSessionId() {
        var service = new RecordingService();
        var listener = new WebSocketDisconnectListener(service);
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).build();
        var event = new SessionDisconnectEvent(this, message, "sess-xyz", CloseStatus.NORMAL);

        listener.onDisconnect(event);

        assertThat(service.lastSessionId, is("sess-xyz"));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd backend && mvn -Dtest=WebSocketDisconnectListenerTest test`
Expected: COMPILE FAILURE — `WebSocketDisconnectListener` does not exist.

- [ ] **Step 3: Implement the listener**

Create `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/WebSocketDisconnectListener.java`:

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/** Bridges Spring's STOMP disconnect event into the forfeit service. */
@Component
public class WebSocketDisconnectListener {

    private final DisconnectForfeitService forfeitService;

    public WebSocketDisconnectListener(DisconnectForfeitService forfeitService) {
        this.forfeitService = forfeitService;
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        forfeitService.onDisconnect(event.getSessionId());
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd backend && mvn -Dtest=WebSocketDisconnectListenerTest test`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/WebSocketDisconnectListener.java \
        backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/WebSocketDisconnectListenerTest.java
git commit -m "feat(forfeit): forward STOMP disconnect events to the forfeit service"
```

### Task 9: Controller `/app/presence` + `/app/forfeit`; `touch` on actions

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/BatailleCorseWebSocketController.java`

This wires the new endpoints into the existing controller, which already resolves a seat from `(gameId, token)`. We also `touch` the repository after each successful action so abandoned-but-active games don't get swept.

- [ ] **Step 1: Add the dependency and update the constructor**

In `BatailleCorseWebSocketController.java`, add a field and constructor param for `DisconnectForfeitService`. Replace the field block + constructor (lines 32-38) with:

```java
    private final SessionService sessionService;
    private final GameMessagingService gameMessagingService;
    private final DisconnectForfeitService disconnectForfeitService;

    public BatailleCorseWebSocketController(SessionService sessionService,
                                            GameMessagingService gameMessagingService,
                                            DisconnectForfeitService disconnectForfeitService) {
        this.sessionService = sessionService;
        this.gameMessagingService = gameMessagingService;
        this.disconnectForfeitService = disconnectForfeitService;
    }
```

- [ ] **Step 2: Add imports**

Add to the import block:

```java
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.PresencePayload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
```

- [ ] **Step 3: Add `touch` after each successful action**

In `send(...)`, immediately after `batailleCorse.send(player);` (line 77) add:

```java
            sessionService.touch(gameId);
```

In `slap(...)`, immediately after `boolean successfulSlap = batailleCorse.slap(player);` (line 107) add:

```java
            sessionService.touch(gameId);
```

In `grab(...)`, immediately after `batailleCorse.grab(player);` (line 139) add:

```java
            sessionService.touch(gameId);
```

- [ ] **Step 4: Add the `/presence` and `/forfeit` handlers**

Add these two methods before the closing brace of the controller:

```java
    @MessageMapping("/presence")
    public void presence(@Payload PresencePayload payload, SimpMessageHeaderAccessor headers) {
        BatailleCorseId gameId = new BatailleCorseId(payload.gameId());
        try {
            PlayerId playerId = sessionService
                    .findPlayerIdByToken(gameId, new SessionToken(payload.token()))
                    .orElseThrow(InvalidTokenException::new);
            disconnectForfeitService.onPresence(headers.getSessionId(), gameId, playerId);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    @MessageMapping("/forfeit")
    public void forfeit(GameActionPayload payload) {
        BatailleCorseId gameId = new BatailleCorseId(payload.gameId());
        try {
            PlayerId playerId = sessionService
                    .findPlayerIdByToken(gameId, new SessionToken(payload.token()))
                    .orElseThrow(InvalidTokenException::new);
            disconnectForfeitService.forfeit(new Seat(gameId, playerId));
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
```

- [ ] **Step 5: Compile + run the full backend suite**

Run: `cd backend && mvn test`
Expected: BUILD SUCCESS, all tests green (existing + new). The Spring context wiring (`DisconnectForfeitService`, `PresenceRegistry`, `GameCleanupService`, `TaskScheduler`, `Clock`) resolves via component scan + `AppConfig` beans.

> If any existing test constructs `BatailleCorseWebSocketController` directly, update it to pass a `DisconnectForfeitService` (search: `new BatailleCorseWebSocketController(`). The recording/stub pattern from Task 7's `RecordingMessaging` and Task 8's `RecordingService` applies.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/BatailleCorseWebSocketController.java
git commit -m "feat(ws): /app/presence and /app/forfeit endpoints; touch repo on actions"
```

---

# Phase 4 — Frontend: presence, countdown, exit confirmation

### Task 10: WebSocketService presence persistence + re-send on reconnect

**Files:**
- Modify: `frontend/src/service/WebSocketService.ts`

No new unit test file (this class wraps the live STOMP client and has no existing tests; it's covered manually via the build gate and by GameSession tests through the port interface). Keep the change minimal and mechanical.

- [ ] **Step 1: Add presence state + re-send on connect**

In `WebSocketService.ts`:

(a) Add a field next to `currentGameId` (after line 11):

```typescript
  private currentPresence: string | null = null;
```

(b) In `onConnect`, after the re-subscribe block (after line 37, inside the `if (this.currentGameId) { ... }` sibling), add a presence re-send:

```typescript
        // Re-assert presence after every (re)connect so the server can re-bind
        // this session to its seat and cancel any pending disconnect timer.
        if (this.currentPresence) {
          this.publish('/app/presence', this.currentPresence);
        }
```

(c) Add two public methods after `unsubscribeFromGame()` (after line 65):

```typescript
  public setPresence(body: string) {
    this.currentPresence = body;
    if (this.client?.connected) {
      this.publish('/app/presence', body);
    }
  }

  public clearPresence() {
    this.currentPresence = null;
  }
```

(d) In `unsubscribeFromGame()`, also clear presence — change it to:

```typescript
  public unsubscribeFromGame() {
    this.currentGameSubscription?.unsubscribe();
    this.currentGameSubscription = null;
    this.currentGameId = null;
    this.currentPresence = null;
  }
```

- [ ] **Step 2: Verify build**

Run: `cd frontend && npm run build`
Expected: build succeeds (no type errors).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/service/WebSocketService.ts
git commit -m "feat(ws-client): persist presence and re-send on reconnect"
```

### Task 11: `GameSession` — send presence + handle connection events

**Files:**
- Modify: `frontend/src/application/GameSession.ts`
- Modify: `frontend/src/application/GameEvent.ts`
- Test: `frontend/src/application/GameSession.test.ts`

- [ ] **Step 1: Add the `opponent-connection` GameEvent**

In `GameEvent.ts`, add to the union (after the `opponent-name-change` line):

```typescript
  | { type: 'opponent-connection'; status: 'disconnected' | 'connected'; disconnectedSeat: number; deadlineEpochMs: number | null };
```

- [ ] **Step 2: Extend the `WebSocketPort` interface and test harness**

In `GameSession.ts`, extend `WebSocketPort` (lines 12-15):

```typescript
export interface WebSocketPort {
  publish(destination: string, body?: string): void;
  subscribeToGame(gameId: string): void;
  setPresence(body: string): void;
  clearPresence(): void;
}
```

In `GameSession.test.ts`, update the mock `webSocket` in `makeSession()` (lines 24-27) to record presence:

```typescript
  const presence: string[] = [];
  const webSocket: WebSocketPort = {
    publish: (dest, body) => published.push({ dest, body }),
    subscribeToGame: (id) => subscribed.push(id),
    setPresence: (body) => presence.push(body),
    clearPresence: () => { presence.length = 0; },
  };
```

and add `presence` to the returned object:

```typescript
  return { session, events, published, subscribed, presence };
```

- [ ] **Step 3: Write the failing tests**

Add this `describe` block in `GameSession.test.ts` (inside the top-level `describe('GameSession', ...)`):

```typescript
  describe('presence + connection events', () => {
    it('sends presence after joining a multiplayer game', async () => {
      const { session, presence } = makeSession();
      // join() does network fetches; drive presence via the public seam instead:
      // simulate a multiplayer create flow.
      session.create('multiplayer', 'Bob');
      await session.onResponse(buildCreateResponse('game-7', { 0: 'tok-mp' }));
      expect(presence.some(p => p.includes('game-7') && p.includes('tok-mp'))).toBe(true);
    });

    it('does NOT send presence in solo mode', async () => {
      const { session, presence } = makeSession();
      session.create('solo', 'Solo');
      await session.onResponse(buildCreateResponse('game-8', { 0: 'a', 1: 'b' }));
      expect(presence.length).toBe(0);
    });

    it('emits opponent-connection on OPPONENT_DISCONNECTED', async () => {
      const { session, events } = makeSession();
      session.create('multiplayer', 'Bob');
      await session.onResponse(buildCreateResponse('game-9', { 0: 'tok' }));
      await session.onResponse(buildResponse({
        eventType: 'OPPONENT_DISCONNECTED',
        eventData: { disconnectedSeat: 1, deadlineEpochMs: 1234 },
      }));
      expect(events).toContainEqual({
        type: 'opponent-connection', status: 'disconnected', disconnectedSeat: 1, deadlineEpochMs: 1234,
      });
    });

    it('emits opponent-connection on OPPONENT_RECONNECTED', async () => {
      const { session, events } = makeSession();
      session.create('multiplayer', 'Bob');
      await session.onResponse(buildCreateResponse('game-10', { 0: 'tok' }));
      await session.onResponse(buildResponse({
        eventType: 'OPPONENT_RECONNECTED',
        eventData: { disconnectedSeat: 1, deadlineEpochMs: null },
      }));
      expect(events).toContainEqual({
        type: 'opponent-connection', status: 'connected', disconnectedSeat: 1, deadlineEpochMs: null,
      });
    });
  });
```

> `buildResponse` is already imported in this test file (line 12). It must produce a `Response` with the given `eventType`/`eventData` and a valid `state` (so `BatailleCorse.fromJSON` succeeds in `processEvent`). Confirm `buildResponse` in `frontend/src/model/fixtures.ts` defaults `state` to a valid game JSON; if it requires a `state`, pass `state: buildGame()` (already imported) — adjust the calls above to include `state: buildGame()`.

- [ ] **Step 4: Run to verify it fails**

Run: `cd frontend && npx vitest run src/application/GameSession.test.ts`
Expected: FAIL — presence not sent / events not emitted.

- [ ] **Step 5: Implement presence sending**

In `GameSession.ts`, add a private helper (e.g. after `emitPerspective()`, around line 153):

```typescript
  /** Multiplayer-only: tell the server which seat this client occupies, so a drop can be attributed. */
  private sendPresence(): void {
    if (this.mode !== 'multiplayer' || !this.gameId) return;
    const token = this.playerTokens[this.myPlayerIndex];
    if (!token) return;
    this.webSocket.setPresence(JSON.stringify({ gameId: this.gameId, token }));
  }
```

Call `this.sendPresence()`:

(a) In `processEvent`, at the end of the `if (response.eventType === 'CREATE')` block (after line 245 `this.callbacks.onEvent({ type: 'game-id-change', gameId: this.gameId });`):

```typescript
      this.sendPresence();
```

(b) In `join(...)`, after `this.webSocket.subscribeToGame(id);` (line 94):

```typescript
    this.sendPresence();
```

(c) In `restoreSession(...)`, at the end (after `this.emitPerspective();`, line 146) — but `gameId` may not be set yet there. Presence on reload is best sent from `hydrate(...)`, which sets `this.gameId`. Add to the end of `hydrate(...)` (after line 125):

```typescript
    // Note: mode/seat are restored via restoreSession() which GameScreen calls
    // right after hydrate(); presence is (re)sent from there.
```

Instead, send presence from `restoreSession` after perspective is known, guarding on a known gameId. Since `restoreSession` runs after `hydrate` in `GameScreen.onMounted`, `this.gameId` is set. Add at the end of `restoreSession(...)`:

```typescript
    this.sendPresence();
```

- [ ] **Step 6: Implement connection-event handling**

In `processEvent`, add handling before the final `state-update` (e.g. after the `SEND` block, around line 306):

```typescript
    if (response.eventType === 'OPPONENT_DISCONNECTED' || response.eventType === 'OPPONENT_RECONNECTED') {
      const data = response.eventData as unknown as { disconnectedSeat: number; deadlineEpochMs: number | null };
      this.callbacks.onEvent({
        type: 'opponent-connection',
        status: response.eventType === 'OPPONENT_DISCONNECTED' ? 'disconnected' : 'connected',
        disconnectedSeat: Number(data.disconnectedSeat),
        deadlineEpochMs: data.deadlineEpochMs ?? null,
      });
    }
```

The existing `state-update` at the end still runs (state is included in these broadcasts), which is harmless.

- [ ] **Step 7: Run to verify it passes**

Run: `cd frontend && npx vitest run src/application/GameSession.test.ts`
Expected: PASS (existing + 4 new tests).

- [ ] **Step 8: Commit**

```bash
git add frontend/src/application/GameSession.ts frontend/src/application/GameEvent.ts frontend/src/application/GameSession.test.ts
git commit -m "feat(session): send presence in multiplayer; surface opponent connection events"
```

### Task 12: Store — `opponentConnection` ref, event wiring, `forfeit()`

**Files:**
- Modify: `frontend/src/state/BatailleCorse.store.ts`

- [ ] **Step 1: Add the ref**

In `BatailleCorse.store.ts`, after `const opponentName = ref<string | null>(null);` (line 29):

```typescript
  const opponentConnection = ref<{ status: 'disconnected' | 'connected'; disconnectedSeat: number; deadlineEpochMs: number | null } | null>(null);
```

- [ ] **Step 2: Wire the event**

In the `onEvent` switch (after the `opponent-name-change` case, line 54):

```typescript
          case 'opponent-connection': opponentConnection.value = event; break;
```

- [ ] **Step 3: Expose ref + `forfeit()` in the store's return**

`GameSession` needs a public `forfeit()`. Add it to `GameSession.ts` (near `slap`/`grab`, around line 206):

```typescript
  forfeit(playerIndex: number): void {
    this.webSocket.publish('/app/forfeit', JSON.stringify({
      gameId: this.gameId,
      token: this.playerTokens[playerIndex],
    }));
  }
```

Then in the store's returned object (after `grab:` line 88):

```typescript
    forfeit:              (playerIndex: number) => session.forfeit(playerIndex),
```

and add `opponentConnection` to both the destructured refs returned and the return object (alongside `opponentName`, lines 73 and the return block):

```typescript
    opponentConnection,
```

- [ ] **Step 4: Verify build**

Run: `cd frontend && npm run build`
Expected: build succeeds.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/state/BatailleCorse.store.ts frontend/src/application/GameSession.ts
git commit -m "feat(store): opponentConnection state and forfeit() action"
```

### Task 13: GameScreen — disconnect countdown banner

**Files:**
- Modify: `frontend/src/view/alpha/GameScreen.vue`

- [ ] **Step 1: Destructure the new ref**

In the `storeToRefs` destructuring (lines 165-166), add `opponentConnection`:

```typescript
const { state: batailleCorse, mode, myPlayerIndex, waiting, myName, opponentName, opponentConnection,
        lastSend, lastGrab, lastSlap, lastSuccessfulSlap, lastErroneousSlap } = storeToRefs(batailleCorseStore);
```

- [ ] **Step 2: Add countdown state + logic in `<script setup>`**

Add near the other computeds (after the `isGameOver`/`didIWin` block, ~line 282):

```typescript
// --- Opponent disconnect countdown ---
// Driven by a server-provided absolute deadline; the local clock only renders
// the remaining seconds. Cleared on reconnect or when the game ends.
const now = ref(Date.now());
let countdownTimer: ReturnType<typeof setInterval> | null = null;

const opponentDisconnected = computed(() =>
  mode.value === 'multiplayer'
  && opponentConnection.value?.status === 'disconnected'
  && opponentConnection.value.disconnectedSeat !== myPlayerIndex.value
  && !isGameOver.value);

const secondsRemaining = computed(() => {
  const deadline = opponentConnection.value?.deadlineEpochMs;
  if (!deadline) return 0;
  return Math.max(0, Math.ceil((deadline - now.value) / 1000));
});

watch(opponentDisconnected, (active) => {
  if (active && countdownTimer === null) {
    now.value = Date.now();
    countdownTimer = setInterval(() => { now.value = Date.now(); }, 250);
  } else if (!active && countdownTimer !== null) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
});
```

Add `clearInterval` to the existing `onBeforeUnmount` (line 358-363):

```typescript
onBeforeUnmount(() => {
  animation.cancelAllAnimations();
  batailleCorseStore.cancelAutoGrab();
  webSocketService.unsubscribeFromGame();
  cancelEndScreen();
  if (countdownTimer !== null) clearInterval(countdownTimer);
});
```

- [ ] **Step 3: Add the banner to the template**

Add just before the `<div v-if="showEndOverlay" ...>` block (before line 95):

```html
    <div v-if="opponentDisconnected" class="disconnect-overlay" data-cy="disconnect-overlay">
      <div class="disconnect-card">
        <h2 class="disconnect-title">{{ opponentLabel }} disconnected</h2>
        <p class="disconnect-sub">Waiting for them to return…</p>
        <p class="disconnect-countdown" data-cy="disconnect-countdown">
          You win in {{ secondsRemaining }}s
        </p>
      </div>
    </div>
```

- [ ] **Step 4: Add styles**

Add to the `<style scoped>` block (e.g. after the `.waiting-copied` rule, ~line 687):

```css
.disconnect-overlay {
  position: absolute;
  inset: 0;
  z-index: 1900;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  pointer-events: none;
  padding-top: 12vh;
}

.disconnect-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  background: rgba(0, 0, 0, 0.72);
  border: 1px solid rgba(248, 113, 113, 0.45);
  border-radius: 14px;
  padding: 18px 28px;
  text-align: center;
}

.disconnect-title {
  font-family: "Gabarito", sans-serif;
  font-size: 1.2rem;
  font-weight: 700;
  color: #f87171;
  margin: 0;
}

.disconnect-sub {
  font-size: 0.78rem;
  color: rgba(255, 255, 255, 0.6);
  margin: 0;
}

.disconnect-countdown {
  font-size: 1rem;
  font-weight: 800;
  color: #f5c842;
  margin: 4px 0 0;
}
```

- [ ] **Step 5: Verify build**

Run: `cd frontend && npm run build`
Expected: build succeeds.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/view/alpha/GameScreen.vue
git commit -m "feat(ui): opponent-disconnected countdown banner"
```

### Task 14: GameScreen — leave confirmation + forfeit on confirm

**Files:**
- Modify: `frontend/src/view/alpha/GameScreen.vue`

Uses Vue Router's `onBeforeRouteLeave` plus a `beforeunload` listener. On confirmed leave in multiplayer, publish forfeit before navigating.

- [ ] **Step 1: Add imports**

In the `vue-router` import (line 160), add `onBeforeRouteLeave`:

```typescript
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router';
```

- [ ] **Step 2: Add the in-progress guard + forfeit**

Add in `<script setup>` (e.g. after the countdown block from Task 13):

```typescript
// --- Leave confirmation ---
// An in-progress game prompts before leaving. Confirming in multiplayer forfeits
// (the opponent wins immediately); solo just leaves. A finished game leaves freely.
const isInProgress = computed(() =>
  !!batailleCorse.value && !isGameOver.value && !isWaiting.value);

onBeforeRouteLeave(() => {
  if (!isInProgress.value) return true;
  const message = mode.value === 'multiplayer'
    ? 'Leave the game? You will forfeit and your opponent wins.'
    : 'Leave the game? Your current game will be lost.';
  const confirmed = window.confirm(message);
  if (!confirmed) return false;
  if (mode.value === 'multiplayer') {
    batailleCorseStore.forfeit(myPlayerIndex.value);
  }
  return true;
});

// Browser close/refresh: native prompt only (we cannot reliably send a forfeit on
// unload — a hard close falls back to the server disconnect timer).
function handleBeforeUnload(event: BeforeUnloadEvent) {
  if (!isInProgress.value) return;
  event.preventDefault();
  event.returnValue = '';
}
```

- [ ] **Step 3: Register/unregister the `beforeunload` listener**

In the existing `onMounted` (after line 355 `webSocketService.subscribeToGame(gameId);`):

```typescript
  window.addEventListener('beforeunload', handleBeforeUnload);
```

In `onBeforeUnmount` (add a line):

```typescript
  window.removeEventListener('beforeunload', handleBeforeUnload);
```

- [ ] **Step 4: Verify build**

Run: `cd frontend && npm run build`
Expected: build succeeds.

- [ ] **Step 5: Manual smoke (optional but recommended)**

Run the app (`/run` skill or project dev command). In a multiplayer game, click **Back** → confirm dialog appears → confirm → opponent's client shows VICTORY immediately (FORFEIT). Cancel → stays in game. Reload during a game → native prompt. Verify a finished game's **Back to home** does NOT prompt.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/view/alpha/GameScreen.vue
git commit -m "feat(ui): leave-confirmation guard with immediate forfeit in multiplayer"
```

---

# Phase 5 — Full verification

### Task 15: End-to-end verification

- [ ] **Step 1: Backend — full suite**

Run: `cd backend && mvn test`
Expected: BUILD SUCCESS, all tests pass. Confirm the Spring context loads (the app-context test, if present, exercises bean wiring for `DisconnectForfeitService`, `PresenceRegistry`, `GameCleanupService`, `TaskScheduler`, `Clock`).

- [ ] **Step 2: Frontend — unit tests + build gate**

Run: `cd frontend && npx vitest run`
Expected: all tests pass.

Run: `cd frontend && npm run build`
Expected: vite build succeeds (the authoritative type gate).

- [ ] **Step 3: Manual multiplayer scenarios** (two browser tabs/windows)

  1. **Hard disconnect → auto-loss:** start a multiplayer game (tab A creates, tab B joins). Close tab B. Tab A shows "disconnected — You win in Ns" counting down; at 0 it shows VICTORY.
  2. **Disconnect → reconnect:** repeat, but reload tab B within 60s. Tab A's banner clears and play resumes.
  3. **Exit confirm forfeit:** tab B clicks Back → confirms → tab A shows VICTORY immediately.
  4. **Solo unaffected:** a solo game's disconnect/timer never triggers; Back still prompts (no forfeit message), navigates.

- [ ] **Step 4: Final commit (if any docs/notes changed)**

```bash
git add -A
git commit -m "chore: verification notes for disconnect/forfeit/cleanup"
```

---

## Notes / Risks

- **`SessionDisconnectEvent` timing:** SockJS sessions fire disconnect on transport close; a heartbeat-timeout close also triggers it. The 60s grace + reconnect-cancel covers transient network blips and reloads. A page reload drops then re-establishes the socket within seconds, well under the grace.
- **Broadcast destination id:** Task 7 Step 4 verifies `BatailleCorseId` → id-string conversion used by `GameMessagingService.sendToGame`. This is the one place the value-object-to-string mapping matters.
- **`buildResponse`/`buildGame` fixtures:** Task 11 assumes these exist in `frontend/src/model/fixtures.ts` (imported by the existing test). If `buildResponse` requires an explicit `state`, pass `state: buildGame()`.
- **Existing controller-construction tests:** Task 9 Step 5 flags updating any test that directly constructs `BatailleCorseWebSocketController` for the new constructor arg.
- **Thresholds** (`FORFEIT_GRACE=60s`, `FINISHED_GRACE=2m`, `IDLE_TTL=30m`, sweep every `1m`) are named constants; tune in one place each.

# Bullshit Multiplayer Lobby (Open Room + Host Start) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a Bullshit game be played by 2–6 players via an open room: the creator opens a room, players join a shared link up to 6, and the host clicks Start once ≥ 2 are present, dealing the aggregate to exactly those who joined.

**Architecture:** The gathering phase is a generic **session/lobby concern**, not a domain concern. A lobby is a `SessionGame` with **no `Game` aggregate** (created only at Start with K = joined players). Per-game player bounds live on `GameFactory`. The repository distinguishes lobby-vs-started via `findGame(): Optional<Game>`. Both per-seat views carry a `started` boolean discriminator so the client branches unambiguously. Only Bullshit is wired; BatailleCorse's existing flow is untouched.

**Tech Stack:** Java 17 (records, sealed types) + Spring Boot STOMP + JUnit 5/Hamcrest + Maven (IntelliJ-bundled `mvn`, no wrapper); Vue 3 + TS + Pinia + Vue Router + Vitest 4 + @vue/test-utils + happy-dom.

**Source spec:** `docs/superpowers/specs/2026-06-16-bullshit-multiplayer-lobby-design.md`

**Verification notes (from project memory):**
- Backend tests run via IntelliJ-bundled `mvn` (no `./mvnw`). Run from `backend/`: `mvn -q test`.
- Frontend worktrees lack `node_modules` and there is no type-check script — the real gate is `npm run build` (vite). `npx vue-tsc` alone gives a false pass. Run frontend commands from `frontend/`.
- `git add` every newly created file immediately after writing it.

---

## File Structure

### Backend — create
- `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/application/GameAlreadyStartedException.java`
- `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/application/RoomFullException.java`
- `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/application/NotHostException.java`
- `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/application/NotEnoughPlayersException.java`
- `backend/src/main/java/org/kevinkib/cardgames/presentation/dto/LobbyDto.java`
- `backend/src/main/java/org/kevinkib/cardgames/presentation/LobbyBroadcaster.java`

### Backend — modify
- `game/GameFactory.java` (add `minPlayers()`/`maxPlayers()`)
- `bullshit/domain/BullshitFactory.java`, `bataillecorse/domain/BatailleCorseFactory.java`, `test/.../game/FakeGameFactory.java` (implement bounds)
- `sessionmanagement/application/GameFactories.java` (bounds lookups)
- `sessionmanagement/application/port/SessionRepository.java` + `sessionmanagement/infrastructure/InMemorySessionRepository.java` (`saveLobby`, `findGame`, lobby touch/evict)
- `sessionmanagement/application/SessionService.java` (`createRoom`/`joinRoom`/`startGame`/`findGame`/`minPlayers`/`maxPlayers`)
- `bullshit/presentation/dto/BullshitDto.java` (`started=true`)
- `bullshit/presentation/dto/event/BullshitEventType.java` (`START`)
- `bullshit/presentation/BullshitWebSocketController.java` (create→createRoom; add `/bullshit/start`)
- `bullshit/presentation/BullshitRestController.java` (join→joinRoom; GET branches lobby/game)
- `config/AppConfig.java` (LobbyBroadcaster bean)
- Existing tests touched: `BullshitWebSocketControllerTest.java` (create tests)

### Frontend — create
- `frontend/src/model/bullshit/LobbyView.ts`

### Frontend — modify
- `frontend/src/model/bullshit/BullshitState.ts` (`started: true`)
- `frontend/src/application/BullshitSession.ts` (`startGame`; create payload; union state typing)
- `frontend/src/state/Bullshit.store.ts` (lobby phase; `game`/`lobby`/`isHost`/`canStart`; drop `waiting`)
- `frontend/src/view/bullshit/BullshitGameScreen.vue` (lobby branch + Start; read `store.game`)
- `frontend/src/view/bullshit/BullshitStartGame.vue` (button label)
- Existing specs touched: `BullshitSession.test.ts`, `Bullshit.store.test.ts`, `BullshitGameScreen.test.ts`

---

# PART 1 — BACKEND

### Task 1: Player bounds on `GameFactory`

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/cardgames/game/GameFactory.java`
- Modify: `backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/BullshitFactory.java`
- Modify: `backend/src/main/java/org/kevinkib/cardgames/bataillecorse/domain/BatailleCorseFactory.java`
- Modify: `backend/src/test/java/org/kevinkib/cardgames/game/FakeGameFactory.java`
- Modify: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/application/GameFactories.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/application/GameFactoriesTest.java`

- [ ] **Step 1: Write the failing test**

Create `GameFactoriesTest.java`:
```java
package org.kevinkib.cardgames.sessionmanagement.application;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class GameFactoriesTest {

    private final GameFactories factories =
            new GameFactories(List.of(new BatailleCorseFactory(), new BullshitFactory()));

    @Test
    void givenBullshit_whenLookupBounds_thenTwoToSix() {
        assertThat(factories.minPlayers("bullshit"), is(2));
        assertThat(factories.maxPlayers("bullshit"), is(6));
    }

    @Test
    void givenBatailleCorse_whenLookupBounds_thenTwoToTwo() {
        assertThat(factories.minPlayers("bataille-corse"), is(2));
        assertThat(factories.maxPlayers("bataille-corse"), is(2));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run from `backend/`: `mvn -q -Dtest=GameFactoriesTest test`
Expected: compile failure — `minPlayers`/`maxPlayers` undefined.

- [ ] **Step 3: Add the methods to the interface**

`game/GameFactory.java`:
```java
package org.kevinkib.cardgames.game;

public interface GameFactory {

    /** Stable identifier used to select this game when creating a session. */
    String gameType();

    /** Fewest players the game can start with. */
    int minPlayers();

    /** Most players the game can seat. */
    int maxPlayers();

    Game create(GameId id, int nbPlayers);
}
```

- [ ] **Step 4: Implement on each factory**

`bullshit/domain/BullshitFactory.java` — add inside the class:
```java
    @Override
    public int minPlayers() {
        return 2;
    }

    @Override
    public int maxPlayers() {
        return 6;
    }
```

`bataillecorse/domain/BatailleCorseFactory.java` — add inside the class:
```java
    @Override
    public int minPlayers() {
        return 2;
    }

    @Override
    public int maxPlayers() {
        return 2;
    }
```

`test/.../game/FakeGameFactory.java` — add inside the class:
```java
    @Override
    public int minPlayers() {
        return 2;
    }

    @Override
    public int maxPlayers() {
        return 4;
    }
```

- [ ] **Step 5: Add lookups to `GameFactories`**

`sessionmanagement/application/GameFactories.java` — add inside the class:
```java
    public int minPlayers(String gameType) {
        return factoryFor(gameType).minPlayers();
    }

    public int maxPlayers(String gameType) {
        return factoryFor(gameType).maxPlayers();
    }
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `mvn -q -Dtest=GameFactoriesTest test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/game/GameFactory.java \
        backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/BullshitFactory.java \
        backend/src/main/java/org/kevinkib/cardgames/bataillecorse/domain/BatailleCorseFactory.java \
        backend/src/test/java/org/kevinkib/cardgames/game/FakeGameFactory.java \
        backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/application/GameFactories.java \
        backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/application/GameFactoriesTest.java
git commit -m "feat(session): per-game player bounds on GameFactory"
```

---

### Task 2: Lobby exceptions

**Files:**
- Create: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/application/GameAlreadyStartedException.java`
- Create: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/application/RoomFullException.java`
- Create: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/application/NotHostException.java`
- Create: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/application/NotEnoughPlayersException.java`

No standalone test — these are exercised by Task 4/5/6 tests. This task is plain creation + a compile check.

- [ ] **Step 1: Create the four exceptions**

`GameAlreadyStartedException.java`:
```java
package org.kevinkib.cardgames.sessionmanagement.application;

import org.kevinkib.cardgames.game.GameId;

public class GameAlreadyStartedException extends RuntimeException {
    public GameAlreadyStartedException(GameId id) {
        super("Game " + id + " has already started.");
    }
}
```

`RoomFullException.java`:
```java
package org.kevinkib.cardgames.sessionmanagement.application;

import org.kevinkib.cardgames.game.GameId;

public class RoomFullException extends RuntimeException {
    public RoomFullException(GameId id) {
        super("Room " + id + " is full.");
    }
}
```

`NotHostException.java`:
```java
package org.kevinkib.cardgames.sessionmanagement.application;

import org.kevinkib.cardgames.game.GameId;

public class NotHostException extends RuntimeException {
    public NotHostException(GameId id) {
        super("Only the host may start game " + id + ".");
    }
}
```

`NotEnoughPlayersException.java`:
```java
package org.kevinkib.cardgames.sessionmanagement.application;

import org.kevinkib.cardgames.game.GameId;

public class NotEnoughPlayersException extends RuntimeException {
    public NotEnoughPlayersException(GameId id, int present, int required) {
        super("Game " + id + " needs " + required + " players to start; only " + present + " present.");
    }
}
```

- [ ] **Step 2: Compile**

Run from `backend/`: `mvn -q -o compile` (or `mvn -q test-compile`)
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/application/GameAlreadyStartedException.java \
        backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/application/RoomFullException.java \
        backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/application/NotHostException.java \
        backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/application/NotEnoughPlayersException.java
git commit -m "feat(session): lobby lifecycle exceptions"
```

---

### Task 3: Repository lobby support (`saveLobby`, `findGame`, lobby touch/evict)

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/application/port/SessionRepository.java`
- Modify: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/infrastructure/InMemorySessionRepository.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/infrastructure/InMemorySessionRepositoryLobbyTest.java`

- [ ] **Step 1: Write the failing test**

Create `InMemorySessionRepositoryLobbyTest.java`:
```java
package org.kevinkib.cardgames.sessionmanagement.infrastructure;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.FakeGame;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionGame;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

class InMemorySessionRepositoryLobbyTest {

    private SessionGame lobbyOf(GameId id) {
        SessionGame lobby = SessionGame.create(id, List.of(new PlayerId(0), new PlayerId(1)), "fake");
        lobby.claim(new PlayerId(0), "Host");
        return lobby;
    }

    @Test
    void givenSavedLobby_whenFindGame_thenEmpty() {
        var repo = new InMemorySessionRepository(Clock.systemUTC());
        GameId id = GameId.generate();
        repo.saveLobby(lobbyOf(id));

        assertThat(repo.findGame(id).isPresent(), is(false));
        assertThat(repo.loadSessionGame(id).isClaimed(new PlayerId(0)), is(true));
    }

    @Test
    void givenStartedGame_whenFindGame_thenPresent() {
        var repo = new InMemorySessionRepository(Clock.systemUTC());
        GameId id = GameId.generate();
        repo.saveLobby(lobbyOf(id));
        repo.save(new FakeGame(id, 2), lobbyOf(id));

        assertThat(repo.findGame(id).isPresent(), is(true));
    }

    @Test
    void givenStaleLobby_whenEvictStale_thenLobbyEvicted() {
        Instant t0 = Instant.parse("2026-06-16T00:00:00Z");
        var clock = Clock.fixed(t0, ZoneOffset.UTC);
        var repo = new InMemorySessionRepository(clock);
        GameId id = GameId.generate();
        repo.saveLobby(lobbyOf(id));

        List<GameId> evicted = repo.evictStale(Duration.ZERO, Duration.ZERO);

        assertThat(evicted, contains(id));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q -Dtest=InMemorySessionRepositoryLobbyTest test`
Expected: compile failure — `saveLobby`/`findGame` undefined.

- [ ] **Step 3: Add to the port**

`port/SessionRepository.java` — add `import java.util.Optional;` and these methods to the interface:
```java
    /** Stores a session that has no game yet (a lobby). */
    void saveLobby(SessionGame sessionGame);

    /** The started game for this id, or empty if it is still a lobby / unknown. */
    Optional<Game> findGame(GameId id);
```

- [ ] **Step 4: Implement in `InMemorySessionRepository`**

Add `import java.util.Optional;`. Then:

Add the method:
```java
    @Override
    public void saveLobby(SessionGame sessionGame) {
        GameId id = sessionGame.id();
        sessionGames.put(id, sessionGame);
        lastActivityAt.put(id, clock.instant());
    }

    @Override
    public Optional<Game> findGame(GameId id) {
        return Optional.ofNullable(games.get(id));
    }
```

Replace `touch` so it also keeps lobbies alive:
```java
    @Override
    public void touch(GameId id) {
        if (games.containsKey(id) || sessionGames.containsKey(id)) {
            lastActivityAt.put(id, clock.instant());
        }
    }
```

Replace `evictStale` so it iterates every tracked session (lobbies included). A lobby has no game, so it ages out on `idleTtl`:
```java
    @Override
    public List<GameId> evictStale(Duration finishedGrace, Duration idleTtl) {
        Instant now = clock.instant();
        List<GameId> evicted = new ArrayList<>();
        for (GameId id : sessionGames.keySet()) {
            Instant last = lastActivityAt.getOrDefault(id, Instant.EPOCH);
            Duration idle = Duration.between(last, now);
            Game game = games.get(id);
            Duration threshold = (game != null && game.isFinished()) ? finishedGrace : idleTtl;
            if (idle.compareTo(threshold) >= 0) {
                evicted.add(id);
            }
        }
        evicted.forEach(this::remove);
        return evicted;
    }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -q -Dtest=InMemorySessionRepositoryLobbyTest test`
Expected: PASS.

- [ ] **Step 6: Run the full suite to confirm no eviction regressions**

Run: `mvn -q test`
Expected: PASS (the `evictStale` rewrite preserves prior started-game behavior — every started game is still in `sessionGames`).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/application/port/SessionRepository.java \
        backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/infrastructure/InMemorySessionRepository.java \
        backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/infrastructure/InMemorySessionRepositoryLobbyTest.java
git commit -m "feat(session): repository lobby storage (saveLobby/findGame, lobby eviction)"
```

---

### Task 4: `SessionService.createRoom` + `findGame`/bounds passthroughs

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/application/SessionService.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/application/SessionLobbyTest.java`

- [ ] **Step 1: Write the failing test**

Create `SessionLobbyTest.java` (this file accumulates lobby tests across Tasks 4–6):
```java
package org.kevinkib.cardgames.sessionmanagement.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class SessionLobbyTest {

    private SessionService service;

    @BeforeEach
    void setUp() {
        service = new SessionService(
                new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new BullshitFactory())));
    }

    @Test
    void givenBullshit_whenCreateRoom_thenMaxSeatsHostClaimedNoGame() {
        SessionGame lobby = service.createRoom("bullshit", "Alice");

        assertThat(lobby.seats().size(), is(6));
        assertThat(lobby.isClaimed(new PlayerId(0)), is(true));
        assertThat(lobby.seats().get(0).name(), is("Alice"));
        assertThat(lobby.isClaimed(new PlayerId(1)), is(false));
        assertThat(service.findGame(lobby.id()).isPresent(), is(false));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q -Dtest=SessionLobbyTest test`
Expected: compile failure — `createRoom`/`findGame` undefined.

- [ ] **Step 3: Implement `createRoom`, `findGame`, and bounds passthroughs**

`SessionService.java` — add `import java.util.stream.IntStream;` and `import org.kevinkib.cardgames.game.Game;` is already present. Add these methods:
```java
    public SessionGame createRoom(String gameType, String hostName) {
        GameId id = GameId.generate();
        int max = gameFactories.maxPlayers(gameType);
        List<PlayerId> seats = IntStream.range(0, max).mapToObj(PlayerId::new).toList();
        SessionGame lobby = SessionGame.create(id, seats, gameType);
        PlayerId host = new PlayerId(0);
        lobby.claim(host, resolveName(host, hostName));
        repository.saveLobby(lobby);
        return lobby;
    }

    public java.util.Optional<Game> findGame(GameId id) {
        return repository.findGame(id);
    }

    public int minPlayers(String gameType) {
        return gameFactories.minPlayers(gameType);
    }

    public int maxPlayers(String gameType) {
        return gameFactories.maxPlayers(gameType);
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=SessionLobbyTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/application/SessionService.java \
        backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/application/SessionLobbyTest.java
git commit -m "feat(session): createRoom opens a lobby with host in seat 0"
```

---

### Task 5: `SessionService.joinRoom`

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/application/SessionService.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/application/SessionLobbyTest.java`

- [ ] **Step 1: Add the failing tests**

Append to `SessionLobbyTest.java` (inside the class):
```java
    @Test
    void givenOpenRoom_whenJoinRoom_thenNextSeatClaimed() {
        SessionGame lobby = service.createRoom("bullshit", "Alice");

        JoinResult first = service.joinRoom(lobby.id(), "Bob");
        JoinResult second = service.joinRoom(lobby.id(), "Cara");

        assertThat(first.playerId(), is(new PlayerId(1)));
        assertThat(second.playerId(), is(new PlayerId(2)));
        assertThat(service.getGameSession(lobby.id()).seats().get(1).name(), is("Bob"));
    }

    @Test
    void givenFullRoom_whenJoinRoom_thenThrowsRoomFull() {
        SessionGame lobby = service.createRoom("bullshit", "Alice");
        for (int i = 1; i < 6; i++) {
            service.joinRoom(lobby.id(), "P" + i);
        }

        org.junit.jupiter.api.Assertions.assertThrows(
                RoomFullException.class, () -> service.joinRoom(lobby.id(), "Late"));
    }

    @Test
    void givenStartedGame_whenJoinRoom_thenThrowsAlreadyStarted() {
        SessionGame lobby = service.createRoom("bullshit", "Alice");
        service.joinRoom(lobby.id(), "Bob");
        service.startGame(lobby.id(), lobby.findTokenByPlayer(new PlayerId(0)).orElseThrow());

        org.junit.jupiter.api.Assertions.assertThrows(
                GameAlreadyStartedException.class, () -> service.joinRoom(lobby.id(), "Late"));
    }
```

(Note: the `startGame` reference compiles only after Task 6. If executing strictly task-by-task, comment out the `givenStartedGame_whenJoinRoom_*` test body until Task 6, then re-enable it. The subagent should add it now and re-enable in Task 6 — flag this in the handoff.)

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q -Dtest=SessionLobbyTest test`
Expected: compile failure — `joinRoom` undefined (and `startGame` if the third test is enabled).

- [ ] **Step 3: Implement `joinRoom`**

`SessionService.java` — add:
```java
    public JoinResult joinRoom(GameId id, String name) {
        if (repository.findGame(id).isPresent()) {
            throw new GameAlreadyStartedException(id);
        }
        SessionGame lobby = repository.loadSessionGame(id);
        PlayerId free = lobby.seats().stream()
                .filter(seat -> !seat.isClaimed())
                .map(SessionPlayer::id)
                .findFirst()
                .orElseThrow(() -> new RoomFullException(id));
        lobby.claim(free, resolveName(free, name));
        SessionToken token = lobby.findTokenByPlayer(free)
                .orElseThrow(() -> new IllegalStateException("Seat " + free.id() + " has no token"));
        return new JoinResult(free, token);
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=SessionLobbyTest test`
Expected: PASS for the `joinRoom`/`RoomFull` tests (the `startGame` test lands in Task 6).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/application/SessionService.java \
        backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/application/SessionLobbyTest.java
git commit -m "feat(session): joinRoom claims next free seat, rejects full/started"
```

---

### Task 6: `SessionService.startGame`

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/application/SessionService.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/application/SessionLobbyTest.java`

- [ ] **Step 1: Add the failing tests**

Append to `SessionLobbyTest.java` (inside the class). Also re-enable the `givenStartedGame_whenJoinRoom_thenThrowsAlreadyStarted` test from Task 5 if it was commented out.
```java
    @Test
    void givenHostAndEnoughPlayers_whenStartGame_thenAggregateDealtToJoined() {
        SessionGame lobby = service.createRoom("bullshit", "Alice");
        service.joinRoom(lobby.id(), "Bob");
        service.joinRoom(lobby.id(), "Cara");
        SessionToken hostToken = lobby.findTokenByPlayer(new PlayerId(0)).orElseThrow();

        var game = service.startGame(lobby.id(), hostToken);

        assertThat(service.findGame(lobby.id()).isPresent(), is(true));
        assertThat(game.getPlayerIds().size(), is(3));
    }

    @Test
    void givenNonHostToken_whenStartGame_thenThrowsNotHost() {
        SessionGame lobby = service.createRoom("bullshit", "Alice");
        JoinResult bob = service.joinRoom(lobby.id(), "Bob");

        org.junit.jupiter.api.Assertions.assertThrows(
                NotHostException.class, () -> service.startGame(lobby.id(), bob.token()));
    }

    @Test
    void givenOnlyHost_whenStartGame_thenThrowsNotEnoughPlayers() {
        SessionGame lobby = service.createRoom("bullshit", "Alice");
        SessionToken hostToken = lobby.findTokenByPlayer(new PlayerId(0)).orElseThrow();

        org.junit.jupiter.api.Assertions.assertThrows(
                NotEnoughPlayersException.class, () -> service.startGame(lobby.id(), hostToken));
    }

    @Test
    void givenAlreadyStarted_whenStartAgain_thenThrowsAlreadyStarted() {
        SessionGame lobby = service.createRoom("bullshit", "Alice");
        service.joinRoom(lobby.id(), "Bob");
        SessionToken hostToken = lobby.findTokenByPlayer(new PlayerId(0)).orElseThrow();
        service.startGame(lobby.id(), hostToken);

        org.junit.jupiter.api.Assertions.assertThrows(
                GameAlreadyStartedException.class, () -> service.startGame(lobby.id(), hostToken));
    }
```

Add the import at the top of the file: `import org.kevinkib.cardgames.sessionmanagement.domain.SessionToken;` (verify it is present; add if missing).

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q -Dtest=SessionLobbyTest test`
Expected: compile failure — `startGame` undefined.

- [ ] **Step 3: Implement `startGame`**

`SessionService.java` — add:
```java
    public Game startGame(GameId id, SessionToken hostToken) {
        if (repository.findGame(id).isPresent()) {
            throw new GameAlreadyStartedException(id);
        }
        SessionGame lobby = repository.loadSessionGame(id);
        PlayerId actor = lobby.findPlayerByToken(hostToken)
                .orElseThrow(() -> new NotHostException(id));
        if (actor.id() != 0) {
            throw new NotHostException(id);
        }
        int claimed = (int) lobby.seats().stream().filter(SessionPlayer::isClaimed).count();
        int min = gameFactories.minPlayers(lobby.gameType());
        if (claimed < min) {
            throw new NotEnoughPlayersException(id, claimed, min);
        }
        Game game = gameFactories.factoryFor(lobby.gameType()).create(id, claimed);
        repository.save(game, lobby);
        return game;
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=SessionLobbyTest test`
Expected: PASS (all lobby tests including the re-enabled join-after-start).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/application/SessionService.java \
        backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/application/SessionLobbyTest.java
git commit -m "feat(session): startGame deals aggregate to joined players (host-only, >=min)"
```

---

### Task 7: Generic `LobbyDto`

**Files:**
- Create: `backend/src/main/java/org/kevinkib/cardgames/presentation/dto/LobbyDto.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/presentation/dto/LobbyDtoTest.java`

- [ ] **Step 1: Write the failing test**

Create `LobbyDtoTest.java`:
```java
package org.kevinkib.cardgames.presentation.dto;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionGame;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class LobbyDtoTest {

    private SessionGame lobbyWith(int claimedSeats, int maxSeats) {
        List<PlayerId> seats = new java.util.ArrayList<>();
        for (int i = 0; i < maxSeats; i++) {
            seats.add(new PlayerId(i));
        }
        SessionGame lobby = SessionGame.create(GameId.generate(), seats, "bullshit");
        for (int i = 0; i < claimedSeats; i++) {
            lobby.claim(new PlayerId(i), "P" + i);
        }
        return lobby;
    }

    @Test
    void givenHostViewerAtMin_whenForViewer_thenCanStartTrueAndNotStarted() {
        SessionGame lobby = lobbyWith(2, 6);

        LobbyDto dto = LobbyDto.forViewer(lobby, 2, 6, new PlayerId(0));

        assertThat(dto.started(), is(false));
        assertThat(dto.hostSeat(), is(0));
        assertThat(dto.mySeat(), is(0));
        assertThat(dto.minPlayers(), is(2));
        assertThat(dto.maxPlayers(), is(6));
        assertThat(dto.players().size(), is(6));
        assertThat(dto.players().get(0).joined(), is(true));
        assertThat(dto.players().get(2).joined(), is(false));
        assertThat(dto.canStart(), is(true));
    }

    @Test
    void givenNonHostViewer_whenForViewer_thenCanStartFalse() {
        SessionGame lobby = lobbyWith(2, 6);

        LobbyDto dto = LobbyDto.forViewer(lobby, 2, 6, new PlayerId(1));

        assertThat(dto.mySeat(), is(1));
        assertThat(dto.canStart(), is(false));
    }

    @Test
    void givenHostBelowMin_whenForViewer_thenCanStartFalse() {
        SessionGame lobby = lobbyWith(1, 6);

        LobbyDto dto = LobbyDto.forViewer(lobby, 2, 6, new PlayerId(0));

        assertThat(dto.canStart(), is(false));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q -Dtest=LobbyDtoTest test`
Expected: compile failure — `LobbyDto` does not exist.

- [ ] **Step 3: Create `LobbyDto`**

`presentation/dto/LobbyDto.java`:
```java
package org.kevinkib.cardgames.presentation.dto;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionGame;

import java.util.List;

/** Generic per-viewer projection of a not-yet-started session (a lobby). No secrets. */
public record LobbyDto(
        boolean started,
        String gameId,
        List<LobbyPlayerDto> players,
        int hostSeat,
        int mySeat,
        int minPlayers,
        int maxPlayers,
        boolean canStart) {

    public record LobbyPlayerDto(int seat, String name, boolean joined) {
    }

    private static final int HOST_SEAT = 0;

    public static LobbyDto forViewer(SessionGame lobby, int minPlayers, int maxPlayers, PlayerId viewer) {
        List<LobbyPlayerDto> players = lobby.seats().stream()
                .map(seat -> new LobbyPlayerDto(seat.id().id(), seat.name(), seat.isClaimed()))
                .toList();

        long claimed = lobby.seats().stream().filter(seat -> seat.isClaimed()).count();
        boolean viewerIsHost = viewer.id() == HOST_SEAT;
        boolean canStart = viewerIsHost && claimed >= minPlayers;

        return new LobbyDto(
                false,
                lobby.id().uuid().toString(),
                players,
                HOST_SEAT,
                viewer.id(),
                minPlayers,
                maxPlayers,
                canStart);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=LobbyDtoTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/presentation/dto/LobbyDto.java \
        backend/src/test/java/org/kevinkib/cardgames/presentation/dto/LobbyDtoTest.java
git commit -m "feat(presentation): generic per-viewer LobbyDto with started discriminator"
```

---

### Task 8: `started=true` discriminator on `BullshitDto`

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/cardgames/bullshit/presentation/dto/BullshitDto.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/bullshit/presentation/dto/BullshitDtoStartedTest.java`

- [ ] **Step 1: Write the failing test**

Create `BullshitDtoStartedTest.java`:
```java
package org.kevinkib.cardgames.bullshit.presentation.dto;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class BullshitDtoStartedTest {

    @Test
    void givenGame_whenForViewer_thenStartedTrue() {
        Bullshit game = new Bullshit(GameId.generate(), 2);

        BullshitDto dto = BullshitDto.forViewer(game, new PlayerId(0));

        assertThat(dto.started(), is(true));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q -Dtest=BullshitDtoStartedTest test`
Expected: compile failure — `started()` accessor missing.

- [ ] **Step 3: Add `started` as the first record component (constant true)**

`bullshit/presentation/dto/BullshitDto.java` — add `boolean started` as the first component and pass `true` in `forViewer`:
```java
public record BullshitDto(
        boolean started,
        String id,
        String gameType,
        List<CardDto> myHand,
        List<String> availableActions,
        List<BullshitPlayerDto> players,
        ClaimTargetDto currentTarget,
        int discardPileSize,
        TableDto table,
        PendingWinnerDto pendingWinner,
        OutcomeDto outcome) {

    public static BullshitDto forViewer(Bullshit game, PlayerId viewer) {
        // ... unchanged body up to the return ...
        return new BullshitDto(
                true,
                game.getId().uuid().toString(),
                BullshitFactory.GAME_TYPE,
                myHand,
                availableActions,
                players,
                ClaimTargetDto.from(game.getCurrentTarget()),
                game.getDiscardPileSize(),
                TableDto.from(game),
                PendingWinnerDto.from(game),
                OutcomeDto.from(game));
    }
}
```
(Leave the `viewerPlayer`/`myHand`/`availableActions`/`players`/`currentPlayerId` computation lines exactly as they are; only the record header and the `new BullshitDto(...)` call change.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=BullshitDtoStartedTest test`
Expected: PASS.

- [ ] **Step 5: Run the full suite (other tests construct/inspect BullshitDto)**

Run: `mvn -q test`
Expected: PASS. The added field is positional-first but no existing test uses the canonical constructor directly (they call `forViewer`), so nothing else breaks. If a test fails on a positional `new BullshitDto(...)`, update it to include `true` first.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/bullshit/presentation/dto/BullshitDto.java \
        backend/src/test/java/org/kevinkib/cardgames/bullshit/presentation/dto/BullshitDtoStartedTest.java
git commit -m "feat(bullshit): started=true discriminator on BullshitDto"
```

---

### Task 9: `START` event type + generic `LobbyBroadcaster`

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/cardgames/bullshit/presentation/dto/event/BullshitEventType.java`
- Create: `backend/src/main/java/org/kevinkib/cardgames/presentation/LobbyBroadcaster.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/presentation/LobbyBroadcasterTest.java`

- [ ] **Step 1: Add `START` to the enum**

`bullshit/presentation/dto/event/BullshitEventType.java`:
```java
package org.kevinkib.cardgames.bullshit.presentation.dto.event;

public enum BullshitEventType {
    DISCARD,
    CALL_BULLSHIT,
    START
}
```

- [ ] **Step 2: Write the failing test for the broadcaster**

Create `LobbyBroadcasterTest.java`:
```java
package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.dto.LobbyDto;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.dto.event.LifecycleEventType;
import org.kevinkib.cardgames.sessionmanagement.application.GameFactories;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionGame;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class LobbyBroadcasterTest {

    static final class RecordingMessaging extends GameMessagingService {
        final List<PlayerId> seats = new ArrayList<>();
        final List<Response> payloads = new ArrayList<>();
        RecordingMessaging() { super(null); }
        @Override public void sendToSeat(GameId gameId, PlayerId seat, Object payload) {
            seats.add(seat);
            payloads.add((Response) payload);
        }
    }

    @Test
    void givenTwoClaimedSeats_whenBroadcast_thenOnlyClaimedReceiveLobbyView() {
        SessionGame lobby = SessionGame.create(
                GameId.generate(),
                List.of(new PlayerId(0), new PlayerId(1), new PlayerId(2)),
                "bullshit");
        lobby.claim(new PlayerId(0), "Host");
        lobby.claim(new PlayerId(1), "Bob");

        RecordingMessaging messaging = new RecordingMessaging();
        LobbyBroadcaster broadcaster = new LobbyBroadcaster(
                messaging, new GameFactories(List.of(new BullshitFactory())));

        broadcaster.broadcast(lobby, LifecycleEventType.JOIN.toString(), new EmptyEventData(), "Bob joined.");

        assertThat(messaging.seats, is(List.of(new PlayerId(0), new PlayerId(1))));
        Response toHost = messaging.payloads.get(0);
        assertThat(toHost.getEventType(), is("JOIN"));
        assertThat(((LobbyDto) toHost.getState()).mySeat(), is(0));
        assertThat(((LobbyDto) toHost.getState()).canStart(), is(true));
        assertThat(((LobbyDto) messaging.payloads.get(1).getState()).mySeat(), is(1));
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `mvn -q -Dtest=LobbyBroadcasterTest test`
Expected: compile failure — `LobbyBroadcaster` does not exist.

- [ ] **Step 4: Create `LobbyBroadcaster`**

`presentation/LobbyBroadcaster.java`:
```java
package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.presentation.dto.LobbyDto;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.sessionmanagement.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionPlayer;

/** Sends a per-viewer {@link LobbyDto} to each claimed seat of a not-yet-started session. */
public class LobbyBroadcaster {

    private final GameMessagingService messaging;
    private final GameFactories gameFactories;

    public LobbyBroadcaster(GameMessagingService messaging, GameFactories gameFactories) {
        this.messaging = messaging;
        this.gameFactories = gameFactories;
    }

    public void broadcast(SessionGame lobby, String eventType, EventData eventData, String message) {
        int min = gameFactories.minPlayers(lobby.gameType());
        int max = gameFactories.maxPlayers(lobby.gameType());
        for (SessionPlayer seat : lobby.seats()) {
            if (!seat.isClaimed()) {
                continue;
            }
            LobbyDto view = LobbyDto.forViewer(lobby, min, max, seat.id());
            messaging.sendToSeat(lobby.id(), seat.id(), new SuccessResponse(eventType, eventData, message, view));
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -q -Dtest=LobbyBroadcasterTest test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/bullshit/presentation/dto/event/BullshitEventType.java \
        backend/src/main/java/org/kevinkib/cardgames/presentation/LobbyBroadcaster.java \
        backend/src/test/java/org/kevinkib/cardgames/presentation/LobbyBroadcasterTest.java
git commit -m "feat(presentation): START event + generic LobbyBroadcaster"
```

---

### Task 10: Wire `LobbyBroadcaster` bean

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/cardgames/config/AppConfig.java`

- [ ] **Step 1: Add the bean**

`config/AppConfig.java` — add `import org.kevinkib.cardgames.presentation.LobbyBroadcaster;` and the bean:
```java
    @Bean
    public LobbyBroadcaster lobbyBroadcaster(GameMessagingService gameMessagingService) {
        return new LobbyBroadcaster(gameMessagingService, gameFactories());
    }
```

- [ ] **Step 2: Compile**

Run from `backend/`: `mvn -q test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/config/AppConfig.java
git commit -m "chore(config): register LobbyBroadcaster bean"
```

---

### Task 11: WebSocket controller — `create`→`createRoom`, add `/bullshit/start`

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/cardgames/bullshit/presentation/BullshitWebSocketController.java`
- Modify: `backend/src/test/java/org/kevinkib/cardgames/bullshit/presentation/BullshitWebSocketControllerTest.java`

- [ ] **Step 1: Replace the two create tests; add start tests**

In `BullshitWebSocketControllerTest.java`, delete `givenSoloCreate_whenCreate_thenLobbyInfoNoState` and `givenTooManyPlayers_whenCreate_thenError`. Add (the class already imports `BullshitCreatePayload`, `GameActionPayload`, `Response`, `PlayerId`, `SessionToken`, `GameId`, `Bullshit`):
```java
    @Test
    void givenCreate_whenCreate_thenRoomAckWithHostTokenNoState() {
        Response response = controller.createGame(new BullshitCreatePayload(null, null, "Alice"));

        assertThat(response.isSuccess(), is(true));
        assertThat(response.getEventType(), is("CREATE"));
        assertThat(response.getState(), is(nullValue()));
        BullshitCreateEventData data = (BullshitCreateEventData) response.getEventData();
        assertThat(data.gameType(), is("bullshit"));
        assertThat(data.tokens().size(), is(1));
        assertThat(data.tokens().containsKey(0), is(true));
    }

    @Test
    void givenHostStartsWithEnoughPlayers_whenStart_thenBroadcastsGameToAllSeats() {
        Response create = controller.createGame(new BullshitCreatePayload(null, null, "Alice"));
        BullshitCreateEventData data = (BullshitCreateEventData) create.getEventData();
        GameId id = new GameId(data.gameId());
        sessionService.joinRoom(id, "Bob");
        String hostToken = data.tokens().get(0);

        controller.start(new GameActionPayload(data.gameId(), hostToken));

        assertThat(messaging.seats.size(), is(2));
        assertThat(messaging.payloads.get(0).getEventType(), is("START"));
        assertThat(messaging.payloads.get(0).isSuccess(), is(true));
    }

    @Test
    void givenNonHostStart_whenStart_thenErrorToActingSeatOnly() {
        Response create = controller.createGame(new BullshitCreatePayload(null, null, "Alice"));
        BullshitCreateEventData data = (BullshitCreateEventData) create.getEventData();
        GameId id = new GameId(data.gameId());
        var bob = sessionService.joinRoom(id, "Bob");

        controller.start(new GameActionPayload(data.gameId(), bob.token().uuid().toString()));

        assertThat(messaging.seats.size(), is(1));
        assertThat(messaging.seats.get(0), is(new PlayerId(1)));
        assertThat(messaging.payloads.get(0).isSuccess(), is(false));
        assertThat(messaging.payloads.get(0).getEventType(), is("START"));
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q -Dtest=BullshitWebSocketControllerTest test`
Expected: compile failure — `controller.start` and the new create behavior don't exist yet.

- [ ] **Step 3: Rewrite `createGame` and add `start`**

`bullshit/presentation/BullshitWebSocketController.java`:
- Remove the `MIN_PLAYERS`/`MAX_PLAYERS` constants and the unused `GameMode` import if no longer referenced.
- Add imports: `org.kevinkib.cardgames.presentation.dto.event.EmptyEventData`, `org.kevinkib.cardgames.bullshit.presentation.dto.event.BullshitEventType`, `org.kevinkib.cardgames.sessionmanagement.domain.SessionGame`.
- Keep `Map` import; add `java.util.Map`.

Replace `createGame`:
```java
    @MessageMapping("/bullshit/create")
    @SendTo("/topic/game")
    public Response createGame(@Payload(required = false) BullshitCreatePayload payload) {
        String name = (payload != null) ? payload.name() : null;
        SessionGame lobby = sessionService.createRoom(BullshitFactory.GAME_TYPE, name);
        SessionToken hostToken = lobby.findTokenByPlayer(new PlayerId(0))
                .orElseThrow(() -> new IllegalStateException("Host seat has no token"));
        Map<Integer, String> tokens = Map.of(0, hostToken.uuid().toString());

        return new SuccessResponse(
                LifecycleEventType.CREATE.toString(),
                new BullshitCreateEventData(lobby.id().uuid().toString(), BullshitFactory.GAME_TYPE, tokens),
                "Room created",
                null);
    }
```

Add `start` (after `createGame`):
```java
    @MessageMapping("/bullshit/start")
    public void start(@Payload GameActionPayload payload) {
        GameId gameId = new GameId(payload.gameId());
        SessionToken token = new SessionToken(payload.token());
        PlayerId actor = sessionService.findPlayerIdByToken(gameId, token).orElse(null);
        if (actor == null) {
            return;
        }
        try {
            Bullshit game = (Bullshit) sessionService.startGame(gameId, token);
            sessionService.touch(gameId);
            broadcaster.broadcast(game, BullshitEventType.START.toString(), new EmptyEventData(), "Game started.");
        } catch (Exception e) {
            System.err.println(e.getMessage());
            messaging.sendToSeat(gameId, actor, new ErrorResponse(
                    BullshitEventType.START.toString(), e.getMessage(), null));
        }
    }
```
(`ErrorResponse` is already imported in this file.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=BullshitWebSocketControllerTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/bullshit/presentation/BullshitWebSocketController.java \
        backend/src/test/java/org/kevinkib/cardgames/bullshit/presentation/BullshitWebSocketControllerTest.java
git commit -m "feat(bullshit): create opens a room; /bullshit/start deals to joined players"
```

---

### Task 12: REST controller — join→joinRoom + lobby broadcast; GET branches lobby/game

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/cardgames/bullshit/presentation/BullshitRestController.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/bullshit/presentation/BullshitRestControllerTest.java`

Read the existing `BullshitRestControllerTest.java` first to match its construction style (it builds a `BullshitRestController` with a `SessionService` + broadcaster). The constructor signature changes (adds `LobbyBroadcaster`), so update its `@BeforeEach` accordingly.

- [ ] **Step 1: Write/adjust the failing tests**

Ensure these behaviors are covered (add any missing; adapt existing `join`/`get` tests to the room flow). The controller is built as:
```java
controller = new BullshitRestController(sessionService, broadcaster, lobbyBroadcaster);
```
where `lobbyBroadcaster = new LobbyBroadcaster(messaging, gameFactories)` (reuse the test's existing `GameFactories`/messaging doubles; if the test currently builds only `broadcaster`, add a `LobbyBroadcaster` alongside it).

Tests:
```java
    @Test
    void givenLobby_whenGetWithHostToken_thenReturnsLobbyView() {
        SessionGame lobby = sessionService.createRoom("bullshit", "Alice");
        String token = lobby.findTokenByPlayer(new PlayerId(0)).orElseThrow().uuid().toString();

        ResponseEntity<?> response = controller.getGame(lobby.id().uuid().toString(), token);

        assertThat(response.getStatusCode().value(), is(200));
        assertThat(response.getBody(), instanceOf(LobbyDto.class));
        assertThat(((LobbyDto) response.getBody()).started(), is(false));
    }

    @Test
    void givenStartedGame_whenGetWithSeatToken_thenReturnsGameView() {
        SessionGame lobby = sessionService.createRoom("bullshit", "Alice");
        sessionService.joinRoom(lobby.id(), "Bob");
        SessionToken hostToken = lobby.findTokenByPlayer(new PlayerId(0)).orElseThrow();
        sessionService.startGame(lobby.id(), hostToken);

        ResponseEntity<?> response = controller.getGame(lobby.id().uuid().toString(), hostToken.uuid().toString());

        assertThat(response.getStatusCode().value(), is(200));
        assertThat(response.getBody(), instanceOf(BullshitDto.class));
        assertThat(((BullshitDto) response.getBody()).started(), is(true));
    }

    @Test
    void givenOpenRoom_whenJoin_thenSeatReturnedAndLobbyBroadcast() {
        SessionGame lobby = sessionService.createRoom("bullshit", "Alice");

        ResponseEntity<JoinResponseDto> response =
                controller.joinGame(lobby.id().uuid().toString(), new JoinGamePayload("Bob"));

        assertThat(response.getStatusCode().value(), is(200));
        assertThat(response.getBody().playerId(), is(1));
    }

    @Test
    void givenStartedGame_whenJoin_then409() {
        SessionGame lobby = sessionService.createRoom("bullshit", "Alice");
        sessionService.joinRoom(lobby.id(), "Bob");
        sessionService.startGame(lobby.id(), lobby.findTokenByPlayer(new PlayerId(0)).orElseThrow());

        ResponseEntity<JoinResponseDto> response =
                controller.joinGame(lobby.id().uuid().toString(), new JoinGamePayload("Late"));

        assertThat(response.getStatusCode().value(), is(409));
    }
```
Add imports as needed: `LobbyDto`, `BullshitDto`, `SessionGame`, `PlayerId`, `SessionToken`, `instanceOf`. Verify `JoinGamePayload`'s constructor arity (it is `JoinGamePayload(String name)` — confirm by reading the record; adjust the literal if different).

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q -Dtest=BullshitRestControllerTest test`
Expected: compile failure (constructor arity, `getGame` return type / `joinRoom` wiring).

- [ ] **Step 3: Rewrite the controller**

`bullshit/presentation/BullshitRestController.java`:
```java
package org.kevinkib.cardgames.bullshit.presentation;

import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.bullshit.presentation.dto.BullshitDto;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.LobbyBroadcaster;
import org.kevinkib.cardgames.presentation.api.JoinGamePayload;
import org.kevinkib.cardgames.presentation.dto.JoinResponseDto;
import org.kevinkib.cardgames.presentation.dto.LobbyDto;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.dto.event.LifecycleEventType;
import org.kevinkib.cardgames.sessionmanagement.application.GameAlreadyStartedException;
import org.kevinkib.cardgames.sessionmanagement.application.JoinResult;
import org.kevinkib.cardgames.sessionmanagement.application.RoomFullException;
import org.kevinkib.cardgames.sessionmanagement.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/bullshit")
public class BullshitRestController {

    private final SessionService sessionService;
    private final BullshitStateBroadcaster broadcaster;
    private final LobbyBroadcaster lobbyBroadcaster;

    public BullshitRestController(SessionService sessionService,
                                  BullshitStateBroadcaster broadcaster,
                                  LobbyBroadcaster lobbyBroadcaster) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
        this.lobbyBroadcaster = lobbyBroadcaster;
    }

    @GetMapping("/game/{id}")
    public ResponseEntity<?> getGame(@PathVariable String id,
                                     @RequestParam(name = "token", required = false) String token) {
        if (token == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        GameId gameId;
        SessionGame session;
        try {
            gameId = new GameId(id);
            session = sessionService.getGameSession(gameId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        if (!BullshitFactory.GAME_TYPE.equals(session.gameType())) {
            return ResponseEntity.notFound().build();
        }
        PlayerId seat = sessionService.findPlayerIdByToken(gameId, new SessionToken(token)).orElse(null);
        if (seat == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<Game> game = sessionService.findGame(gameId);
        if (game.isPresent()) {
            return ResponseEntity.ok(BullshitDto.forViewer((Bullshit) game.get(), seat));
        }
        int min = sessionService.minPlayers(session.gameType());
        int max = sessionService.maxPlayers(session.gameType());
        return ResponseEntity.ok(LobbyDto.forViewer(session, min, max, seat));
    }

    @PostMapping("/game/{id}/join")
    public ResponseEntity<JoinResponseDto> joinGame(@PathVariable String id,
                                                    @RequestBody(required = false) JoinGamePayload payload) {
        GameId gameId;
        SessionGame session;
        try {
            gameId = new GameId(id);
            session = sessionService.getGameSession(gameId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        if (!BullshitFactory.GAME_TYPE.equals(session.gameType())) {
            return ResponseEntity.notFound().build();
        }
        try {
            String name = (payload != null) ? payload.name() : null;
            JoinResult result = sessionService.joinRoom(gameId, name);
            sessionService.touch(gameId);

            SessionGame updated = sessionService.getGameSession(gameId);
            lobbyBroadcaster.broadcast(updated, LifecycleEventType.JOIN.toString(), new EmptyEventData(),
                    "Player " + (result.playerId().id() + 1) + " joined.");

            return ResponseEntity.ok(new JoinResponseDto(
                    result.playerId().id(), result.token().uuid().toString()));
        } catch (GameAlreadyStartedException | RoomFullException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=BullshitRestControllerTest test`
Expected: PASS.

- [ ] **Step 5: Run the full backend suite**

Run: `mvn -q test`
Expected: PASS. This is the backend completeness gate.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/bullshit/presentation/BullshitRestController.java \
        backend/src/test/java/org/kevinkib/cardgames/bullshit/presentation/BullshitRestControllerTest.java
git commit -m "feat(bullshit): REST join opens room + lobby broadcast; GET branches lobby/game"
```

---

# PART 2 — FRONTEND

> Run all frontend commands from `frontend/`. Tests: `npm run test -- run <file>`. Type/build gate: `npm run build`.

### Task 13: Model — `LobbyView` + `started` on `BullshitState`

**Files:**
- Create: `frontend/src/model/bullshit/LobbyView.ts`
- Modify: `frontend/src/model/bullshit/BullshitState.ts`

- [ ] **Step 1: Create `LobbyView.ts`**

```ts
export interface LobbyPlayer {
  seat: number;
  name: string | null;
  joined: boolean;
}

export interface LobbyView {
  started: false;
  gameId: string;
  players: LobbyPlayer[];
  hostSeat: number;
  mySeat: number;
  minPlayers: number;
  maxPlayers: number;
  canStart: boolean;
}
```

- [ ] **Step 2: Add `started: true` to `BullshitState` and export the union**

`model/bullshit/BullshitState.ts` — add `started: true;` as the first field of `BullshitState`, and at the bottom of the file:
```ts
import type { LobbyView } from './LobbyView';

export type BullshitView = LobbyView | BullshitState;
```
(Add the `import` at the top with the other import; the `BullshitView` alias goes at the end.)

The `BullshitState` interface becomes:
```ts
export interface BullshitState {
  started: true;
  id: string;
  gameType: string;
  myHand: Card[];
  availableActions: string[];
  players: BullshitPlayer[];
  currentTarget: { label: string };
  discardPileSize: number;
  table: TableView;
  pendingWinner: PendingWinnerView;
  outcome: OutcomeView;
}
```

- [ ] **Step 3: Type-check**

Run: `npm run build`
Expected: this may surface type errors in the store/session/screen that consume `state` as a bare `BullshitState`. Those are fixed in Tasks 14–16. If `build` fails only in those three files, that is expected at this step — proceed; do not patch them here. (If it fails anywhere else, fix inline.)

- [ ] **Step 4: Commit**

```bash
git add frontend/src/model/bullshit/LobbyView.ts frontend/src/model/bullshit/BullshitState.ts
git commit -m "feat(frontend): LobbyView model + started discriminator on BullshitState"
```

---

### Task 14: `BullshitSession` — `startGame`, room create payload, union typing

**Files:**
- Modify: `frontend/src/application/BullshitSession.ts`
- Modify: `frontend/src/application/BullshitSession.test.ts`

- [ ] **Step 1: Add the failing test**

Read `BullshitSession.test.ts` to match its harness (it uses a fake `BullshitWebSocketPort`). Add:
```ts
  it('startGame publishes to /app/bullshit/start with gameId and token', () => {
    const published: { destination: string; body?: string }[] = [];
    const ws: BullshitWebSocketPort = {
      publish: (destination, body) => published.push({ destination, body }),
      subscribeToSeat: () => {},
      setLobbyListener: () => {},
    };
    const session = new BullshitSession(ws, { onEvent: () => {} });
    session.restore('game-1', 0, 'tok-0');

    session.startGame();

    const start = published.find(p => p.destination === '/app/bullshit/start');
    expect(start).toBeDefined();
    expect(JSON.parse(start!.body!)).toEqual({ gameId: 'game-1', token: 'tok-0' });
  });
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npm run test -- run src/application/BullshitSession.test.ts`
Expected: FAIL — `session.startGame is not a function`.

- [ ] **Step 3: Implement**

`application/BullshitSession.ts`:
- Update imports: `import type { BullshitView } from '../model/bullshit/BullshitState';` (replace the `BullshitState` import used for hydrate typing).
- In `create`, send only the name:
```ts
  create(name?: string): void {
    this.pendingCreate = true;
    this.webSocket.setLobbyListener(r => this.onLobby(r));
    this.webSocket.publish('/app/bullshit/create', JSON.stringify({ name: name ?? null }));
  }
```
- Change `hydrate` to type the state as the union:
```ts
  async hydrate(): Promise<void> {
    if (!this.gameId || this.myToken === null) return;
    const res = await fetch(`/api/bullshit/game/${this.gameId}?token=${this.myToken}`);
    if (res.ok) {
      const state = await res.json() as BullshitView;
      this.callbacks.onEvent({ type: 'state-update', state });
    }
  }
```
- Update the `BullshitSessionEvent` union's `state-update` payload type to `BullshitView`:
```ts
export type BullshitSessionEvent =
  | { type: 'state-update'; state: BullshitView }
  | { type: 'game-id-change'; gameId: string }
  | { type: 'seat-change'; seat: number }
  | { type: 'event'; eventType: string; eventData: unknown; message: string };
```
- Add `startGame` (after `callBullshit`):
```ts
  startGame(): void {
    this.webSocket.publish('/app/bullshit/start', JSON.stringify({ gameId: this.gameId, token: this.myToken }));
  }
```
- In `onResponse`, the `response.state` is now a `BullshitView`; widen its type. Change the `BullshitResponse` usage so `state` is `BullshitView | null` (update `model/bullshit/BullshitEvents.ts` `BullshitResponse.state` to `BullshitView | null` and its import).

`model/bullshit/BullshitEvents.ts` — change:
```ts
import type { BullshitView } from './BullshitState';
// ...
export interface BullshitResponse {
  success: boolean;
  eventType: string;
  eventData: unknown;
  message: string;
  state: BullshitView | null;
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm run test -- run src/application/BullshitSession.test.ts`
Expected: PASS (existing tests in the file still pass; `create` now omits `nbPlayers`/`mode` — if an existing test asserts those fields, update it to expect `{ name: ... }`).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/application/BullshitSession.ts frontend/src/application/BullshitSession.test.ts \
        frontend/src/model/bullshit/BullshitEvents.ts
git commit -m "feat(frontend): BullshitSession.startGame + room create payload"
```

---

### Task 15: Store — lobby phase, `game`/`lobby`/`isHost`/`canStart`, `startGame`

**Files:**
- Modify: `frontend/src/state/Bullshit.store.ts`
- Modify: `frontend/src/state/Bullshit.store.test.ts`

- [ ] **Step 1: Add/adjust the failing tests**

Read `Bullshit.store.test.ts` for its setup (real `setActivePinia(createPinia())`). Add:
```ts
  it('a lobby view yields phase "lobby" and exposes canStart', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({
      type: 'state-update',
      state: {
        started: false, gameId: 'g1',
        players: [
          { seat: 0, name: 'Alice', joined: true },
          { seat: 1, name: null, joined: false },
        ],
        hostSeat: 0, mySeat: 0, minPlayers: 2, maxPlayers: 6, canStart: false,
      },
    });

    expect(store.phase).toBe('lobby');
    expect(store.isHost).toBe(true);
    expect(store.canStart).toBe(false);
  });

  it('a started view yields phase "playing"', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({
      type: 'state-update',
      state: {
        started: true, id: 'g1', gameType: 'bullshit', myHand: [], availableActions: [],
        players: [{ id: '0', handCount: 5, isCurrentPlayer: true }],
        currentTarget: { label: 'A' }, discardPileSize: 0,
        table: { state: 'NO_CLAIM' }, pendingWinner: { state: 'NONE' }, outcome: { status: 'ONGOING' },
      },
    });

    expect(store.phase).toBe('playing');
  });
```
If existing tests reference `store.waiting` or `store.markCreated`, remove/adapt them — those are deleted in Step 3.

- [ ] **Step 2: Run it to verify it fails**

Run: `npm run test -- run src/state/Bullshit.store.test.ts`
Expected: FAIL — `phase` returns `'connecting'`/no `'lobby'`, and `isHost`/`canStart` undefined.

- [ ] **Step 3: Rewrite the store**

`state/Bullshit.store.ts`:
```ts
import { defineStore } from 'pinia';
import { ref, computed } from 'vue';

import webSocketService from '../service/WebSocketService';
import BullshitSession, { type BullshitSessionEvent } from '../application/BullshitSession';
import type { BullshitState, BullshitView } from '../model/bullshit/BullshitState';
import type { LobbyView } from '../model/bullshit/LobbyView';
import type { CallBullshitEventData } from '../model/bullshit/BullshitEvents';
import type Card from '../model/Card';

export const useBullshitStore = defineStore('bullshit-store', () => {
  const state = ref<BullshitView | null>(null);
  const gameId = ref<string | null>(null);
  const mySeat = ref<number>(0);
  const reveal = ref<CallBullshitEventData | null>(null);
  const selectedCards = ref<Card[]>([]);

  const session = new BullshitSession(webSocketService, {
    onEvent(event: BullshitSessionEvent) { applyEvent(event); },
  });

  function applyEvent(event: BullshitSessionEvent) {
    switch (event.type) {
      case 'state-update': state.value = event.state; break;
      case 'game-id-change': gameId.value = event.gameId; break;
      case 'seat-change': mySeat.value = event.seat; break;
      case 'event':
        if (event.eventType === 'CALL_BULLSHIT') reveal.value = event.eventData as CallBullshitEventData;
        else reveal.value = null;
        break;
    }
  }

  const game = computed<BullshitState | null>(() =>
    state.value && state.value.started ? state.value : null);
  const lobby = computed<LobbyView | null>(() =>
    state.value && !state.value.started ? state.value : null);

  const me = computed(() => game.value?.players.find(p => p.id === String(mySeat.value)) ?? null);
  const isMyTurn = computed(() => me.value?.isCurrentPlayer ?? false);
  const canDiscard = computed(() => game.value?.availableActions.includes('DISCARD') ?? false);
  const canCallBullshit = computed(() => game.value?.availableActions.includes('CALL_BULLSHIT') ?? false);
  const iWon = computed(() =>
    game.value?.outcome.status === 'FINISHED' && game.value.outcome.winnerId === String(mySeat.value));
  const isHost = computed(() => mySeat.value === 0);
  const canStart = computed(() => lobby.value?.canStart ?? false);
  const phase = computed<'connecting' | 'lobby' | 'playing' | 'finished'>(() => {
    if (!state.value) return 'connecting';
    if (!state.value.started) return 'lobby';
    if (state.value.outcome.status === 'FINISHED') return 'finished';
    return 'playing';
  });

  function toggleCard(card: Card) {
    const i = selectedCards.value.findIndex(c => c.name === card.name);
    if (i >= 0) selectedCards.value.splice(i, 1);
    else if (selectedCards.value.length < 4) selectedCards.value.push(card);
  }
  function clearSelection() { selectedCards.value = []; }

  return {
    state, game, lobby, gameId, mySeat, reveal, selectedCards,
    isMyTurn, canDiscard, canCallBullshit, iWon, isHost, canStart, phase,
    applyEvent, toggleCard, clearSelection,
    create: (name?: string) => session.create(name),
    join: (id: string, name?: string) => session.join(id, name),
    restore: (id: string, seat: number, token: string) => session.restore(id, seat, token),
    hydrate: () => session.hydrate(),
    startGame: () => session.startGame(),
    discard: () => { session.discard(selectedCards.value); clearSelection(); },
    callBullshit: () => session.callBullshit(),
  };
});
```
(Removed `waiting` and `markCreated`.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm run test -- run src/state/Bullshit.store.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/state/Bullshit.store.ts frontend/src/state/Bullshit.store.test.ts
git commit -m "feat(frontend): store lobby phase + game/lobby/isHost/canStart, drop waiting"
```

---

### Task 16: Game screen — lobby branch + Start button; read `store.game`

**Files:**
- Modify: `frontend/src/view/bullshit/BullshitGameScreen.vue`
- Modify: `frontend/src/view/bullshit/BullshitGameScreen.test.ts`

- [ ] **Step 1: Adjust the failing test**

Read `BullshitGameScreen.test.ts`. Update the `waiting`-phase test to a `lobby`-phase test and add a Start-button test. Match the existing mounting/mocking approach (it likely stubs the store or uses a real pinia + `applyEvent`). Cover:
- When `phase === 'lobby'`, the lobby panel renders, lists joined players, and shows the share link.
- The Start button renders only when `isHost` and is disabled unless `canStart`; clicking it calls `store.startGame`.

Example assertions (adapt to the file's existing harness):
```ts
  it('shows the lobby with a host Start button enabled at canStart', async () => {
    // arrange a store whose phase==='lobby', isHost===true, canStart===true, lobby.players has Alice+Bob
    // mount BullshitGameScreen with gameId
    const startBtn = wrapper.get('[data-test="start"]');
    expect(startBtn.attributes('disabled')).toBeUndefined();
    await startBtn.trigger('click');
    expect(startSpy).toHaveBeenCalled();
  });
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npm run test -- run src/view/bullshit/BullshitGameScreen.test.ts`
Expected: FAIL (no `[data-test="start"]`, `waiting` panel gone).

- [ ] **Step 3: Update the screen**

`view/bullshit/BullshitGameScreen.vue`:
- Replace the `waiting` block with a `lobby` block.
- Read game state via `store.game` (the narrowed game view) instead of `store.state` in the playing branch.

`<script setup>` — change `opponents` to read `store.game`:
```ts
const opponents = computed(() =>
  (store.game?.players ?? []).filter(p => p.id !== String(store.mySeat)));
```

`<template>` — replace the first `v-if` panel and update the playing branch:
```html
  <div class="bullshit-screen">
    <div v-if="store.phase === 'lobby'" data-test="lobby" class="panel">
      <h2>Lobby</h2>
      <ul class="players">
        <li v-for="p in store.lobby?.players.filter(pl => pl.joined) ?? []" :key="p.seat">
          Player {{ p.seat + 1 }}: {{ p.name }}
        </li>
      </ul>
      <p class="share">Share: <code>{{ joinLink }}</code></p>
      <button
        v-if="store.isHost"
        data-test="start"
        type="button"
        :disabled="!store.canStart"
        @click="store.startGame()">
        Start game
      </button>
      <p v-else class="hint">Waiting for the host to start…</p>
    </div>

    <div v-else-if="store.phase === 'finished'" data-test="end" class="panel">
      <h2>{{ store.iWon ? 'You win!' : 'You lose' }}</h2>
    </div>

    <template v-else>
      <div class="opponents">
        <div v-for="opp in opponents" :key="opp.id" class="opponent" :class="{ active: opp.isCurrentPlayer }">
          <span class="seat">Player {{ opp.id }}</span>
          <CardCounter :count="opp.handCount" />
        </div>
      </div>

      <div class="table">
        <p class="claim">Claim: {{ store.game?.currentTarget.label }}</p>
        <p v-if="store.game?.table.state === 'CLAIM'" class="last-claim">
          Player {{ store.game.table.claimantId }} played {{ store.game.table.count }} card(s) face-down
        </p>
        <p class="pile">Discard pile: {{ store.game?.discardPileSize }}</p>
      </div>

      <div v-if="store.reveal" data-test="reveal" class="reveal">
        <p>
          Player {{ store.reveal.callerSeat }} called bullshit on Player {{ store.reveal.claimantSeat }} —
          claim was {{ store.reveal.truthful ? 'TRUE' : 'FALSE' }} — Player {{ store.reveal.pickerSeat }} takes the pile
        </p>
        <div class="revealed-cards">
          <PlayingCard v-for="(c, i) in store.reveal.revealedCards" :key="i" :rank="c.rank" :suit="c.suit" />
        </div>
      </div>

      <div class="hand">
        <button
          v-for="(card, i) in store.game?.myHand ?? []"
          :key="card.name"
          :data-test="`hand-card-${i}`"
          class="hand-card"
          :class="{ selected: isSelected(card) }"
          type="button"
          @click="store.toggleCard(card)">
          <PlayingCard :rank="card.rank" :suit="card.suit" />
        </button>
      </div>

      <div class="actions">
        <button
          data-test="discard"
          type="button"
          :disabled="!store.isMyTurn || store.selectedCards.length === 0"
          @click="store.discard()">
          Discard as {{ store.game?.currentTarget.label }}
        </button>
        <button
          data-test="call"
          type="button"
          :disabled="!store.canCallBullshit"
          @click="store.callBullshit()">
          Call Bullshit
        </button>
      </div>
    </template>
  </div>
```
Add a `.players { list-style: none; padding: 0; }` and `.hint { opacity: 0.7; }` rule to `<style scoped>` (optional polish).

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm run test -- run src/view/bullshit/BullshitGameScreen.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/view/bullshit/BullshitGameScreen.vue frontend/src/view/bullshit/BullshitGameScreen.test.ts
git commit -m "feat(frontend): lobby screen + host Start, render game via store.game"
```

---

### Task 17: Start-game entry copy

**Files:**
- Modify: `frontend/src/view/bullshit/BullshitStartGame.vue`

- [ ] **Step 1: Update the create button label**

`view/bullshit/BullshitStartGame.vue` — change the create button text from `Create 2-player game` to `Create game`:
```html
      <button type="button" @click="onCreate">Create game</button>
```
(No behavior change; routing to `/games/bullshit/room/:id` on `store.gameId` watch is unchanged — the host lands on the lobby, which the room screen now renders.)

- [ ] **Step 2: Build gate**

Run: `npm run build`
Expected: BUILD SUCCESS (no remaining type errors across model/session/store/screen).

- [ ] **Step 3: Run the full frontend test suite**

Run: `npm run test -- run`
Expected: PASS. Frontend completeness gate.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/view/bullshit/BullshitStartGame.vue
git commit -m "chore(frontend): generic create-game button label"
```

---

## Final verification

- [ ] Backend: from `backend/`, `mvn -q test` — all green.
- [ ] Frontend: from `frontend/`, `npm run build` then `npm run test -- run` — both green.
- [ ] Manual smoke (optional): create a room in one browser, copy the share link, join in a second; host sees the joiner appear; host clicks Start; both transition to the board; play a discard + call-bullshit round.
```

## Notes for the executor

- `useBullshitBootstrap.ts` needs **no change**: it restores seat+token from localStorage and calls `hydrate()`, which now returns either a `LobbyView` or a `BullshitState`; the store's `phase` derives the screen. Its existing test (`useBullshitBootstrap.test.ts`) should still pass — run it in the final suite; if it asserts on a `waiting` flag, update it.
- The reload-while-waiting gap and the decline-event narration bug tracked earlier are **out of scope** here.
- BatailleCorse is untouched: `createGame`/`joinGame` remain; only Bullshit calls the new room methods.

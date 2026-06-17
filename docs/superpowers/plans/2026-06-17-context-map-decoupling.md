# Context-Map Decoupling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Break the `core ⇄ presence` dependency cycle (Part 1), then make `sessionmanagement.core.application` a published API so the game bounded contexts stop importing `core.domain` (Part 2).

**Architecture:** A behavior-preserving refactor. The full backend suite (currently **265** tests) is the safety net — every task ends with `mvn test` green. Part 1 and Part 2 are independent; **Part 1 is its own PR, Part 2 is a second PR.** No `new failing test first` except where a test must be adapted to a new signature.

**Tech Stack:** Java 17, Spring Boot, JUnit 5 + Hamcrest, Maven. Run Maven from `backend/`; there is no `./mvnw` — use `"C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd"`. Full-suite gate: `mvn test`.

**Source spec:** `docs/superpowers/specs/2026-06-17-context-map-decoupling-design.md`

---

# PART 1 — Break the `core ⇄ presence` cycle (PR 1)

### Task 1: `GameDirectory` port — presence stops importing `SessionService`

**Files:**
- Create: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/core/application/GameDirectory.java`
- Modify: `core/application/SessionService.java` (implement it)
- Modify: `sessionmanagement/presence/application/PresenceService.java` (depend on it)
- Modify: `config/AppConfig.java`
- Modify (test): `sessionmanagement/presence/application/PresenceServiceTest.java`

- [ ] **Step 1: Create the port**
```java
package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;

import java.util.Optional;

/** Core's published view of live games, for downstream contexts (e.g. presence). */
public interface GameDirectory {
    Optional<Game> findGame(GameId id);
    void touch(GameId id);
}
```

- [ ] **Step 2: `SessionService implements GameDirectory`**
In `SessionService.java`, change the class declaration to `public class SessionService implements GameDirectory`. It already has `findGame(GameId): Optional<Game>` and `touch(GameId)` with matching signatures — add `@Override` to both. No body changes.

- [ ] **Step 3: `PresenceService` depends on `GameDirectory`**
In `PresenceService.java`:
- Replace the import `org.kevinkib.cardgames.sessionmanagement.core.application.SessionService` with `org.kevinkib.cardgames.sessionmanagement.core.application.GameDirectory`.
- Change the field and constructor param `SessionService sessionService` → `GameDirectory games`.
- The private helper currently is:
```java
    private Game findGame(GameId gameId) {
        try {
            return sessionService.getGame(gameId);
        } catch (InvalidGameIdException e) {
            return null;
        }
    }
```
Replace it with:
```java
    private Game findGame(GameId gameId) {
        return games.findGame(gameId).orElse(null);
    }
```
- Replace the `sessionService.touch(seat.gameId())` call in `forfeit(...)` with `games.touch(seat.gameId())`.
- Remove the now-unused `import ...core.application.InvalidGameIdException;` and `import ...core.application.SessionService;`.

- [ ] **Step 4: Wire the bean**
In `AppConfig.java`, add a `GameDirectory` import and a bean, and pass it to `presenceService`:
```java
    @Bean
    public GameDirectory gameDirectory() {
        return sessionService();
    }
```
Change the `presenceService(...)` bean body to pass `gameDirectory()` as the first argument instead of `sessionService()`.

- [ ] **Step 5: Update `PresenceServiceTest`**
Its `setUp` builds a real `SessionService` and passes it to `PresenceService`. Since `SessionService` now implements `GameDirectory`, the existing `new PresenceService(sessionService, ...)` still compiles (widening to the interface). No change needed unless the field is typed `SessionService` — if so leave it; the constructor accepts it. Run to confirm.

- [ ] **Step 6: Full suite**
Run `mvn test` from `backend/`. Expected: BUILD SUCCESS, 265 tests.

- [ ] **Step 7: Commit**
```bash
git add -A backend/
git commit -m "refactor(session): GameDirectory port so presence no longer depends on SessionService"
```

---

### Task 2: `GameEvictionListener` — core stops importing presence

**Files:**
- Create: `core/application/GameEvictionListener.java`
- Create: `presence/application/PresenceEvictionCleanup.java`
- Modify: `core/application/GameCleanupService.java`
- Modify: `config/AppConfig.java`
- Modify (test): `sessionmanagement/core/application/GameCleanupServiceTest.java`

- [ ] **Step 1: Create the port**
```java
package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.GameId;

/** Notified when a game is evicted, so downstream contexts can drop their per-game state. */
public interface GameEvictionListener {
    void onEvicted(GameId id);
}
```

- [ ] **Step 2: Create the presence adapter**
```java
package org.kevinkib.cardgames.sessionmanagement.presence.application;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameEvictionListener;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ConnectionRegistry;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitLog;

/** Clears presence's per-game state when core evicts a game. */
public class PresenceEvictionCleanup implements GameEvictionListener {

    private final ConnectionRegistry connectionRegistry;
    private final ForfeitLog forfeitLog;

    public PresenceEvictionCleanup(ConnectionRegistry connectionRegistry, ForfeitLog forfeitLog) {
        this.connectionRegistry = connectionRegistry;
        this.forfeitLog = forfeitLog;
    }

    @Override
    public void onEvicted(GameId id) {
        connectionRegistry.removeGame(id);
        forfeitLog.removeGame(id);
    }
}
```

- [ ] **Step 3: Rewire `GameCleanupService`**
Replace its `ConnectionRegistry`/`ForfeitLog` fields with `List<GameEvictionListener>`. New full file:
```java
package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.core.application.port.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.List;

public class GameCleanupService {

    private static final Logger log = LoggerFactory.getLogger(GameCleanupService.class);

    public static final Duration FINISHED_GRACE = Duration.ofMinutes(2);
    public static final Duration IDLE_TTL = Duration.ofMinutes(30);

    private final SessionRepository repository;
    private final List<GameEvictionListener> listeners;

    public GameCleanupService(SessionRepository repository, List<GameEvictionListener> listeners) {
        this.repository = repository;
        this.listeners = listeners;
    }

    @Scheduled(fixedDelayString = "PT1M")
    public void sweep() {
        List<GameId> evicted = repository.evictStale(FINISHED_GRACE, IDLE_TTL);
        if (!evicted.isEmpty()) {
            log.info("Evicted {} stale game(s): {}", evicted.size(), evicted);
            evicted.forEach(id -> listeners.forEach(l -> l.onEvicted(id)));
        }
    }
}
```

- [ ] **Step 4: Wire in `AppConfig`**
- Add imports for `GameEvictionListener` and `PresenceEvictionCleanup`.
- Add a bean:
```java
    @Bean
    public GameEvictionListener presenceEvictionCleanup() {
        return new PresenceEvictionCleanup(connectionRegistry(), forfeitLog());
    }
```
- Change the `gameCleanupService()` bean to take the listeners list:
```java
    @Bean
    public GameCleanupService gameCleanupService(List<GameEvictionListener> evictionListeners) {
        return new GameCleanupService(sessionRepository(), evictionListeners);
    }
```
(Spring injects all `GameEvictionListener` beans. Remove the old `connectionRegistry()`/`forfeitLog()` arguments from this bean.)

- [ ] **Step 5: Update `GameCleanupServiceTest`**
The test's `StubRepository` stays. Replace the construction `new GameCleanupService(repo, registry, new InMemoryForfeitLog())` with the listener form. Rewrite the two tests so:
- the "presence cleared" test passes a real `InMemoryConnectionRegistry` + `InMemoryForfeitLog` wrapped in a `PresenceEvictionCleanup`, binds a seat, sweeps, asserts the registry entry is gone:
```java
var connections = new InMemoryConnectionRegistry();
var forfeits = new InMemoryForfeitLog();
var service = new GameCleanupService(repo, List.of(new PresenceEvictionCleanup(connections, forfeits)));
connections.bind("sess-1", new Seat(id, new PlayerId(1)));
service.sweep();
assertThat(connections.seatOf("sess-1").isEmpty(), is(true));
```
- the "thresholds" test constructs `new GameCleanupService(repo, List.of())` (empty listeners) and asserts the `evictStale` args unchanged.
Update imports: `InMemoryConnectionRegistry`, `InMemoryForfeitLog`, `PresenceEvictionCleanup`, `Seat` (from `presence.domain`), `java.util.List`.

- [ ] **Step 6: Full suite**
Run `mvn test`. Expected: 265 green. (Confirm `ApplicationContextTest` passes — proves the listener list wires.)

- [ ] **Step 7: Commit**
```bash
git add -A backend/
git commit -m "refactor(session): GameEvictionListener inverts cleanup so core no longer imports presence"
```

**End of PR 1.** Verify with: `grep -rn "presence" backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/core` returns nothing.

---

# PART 2 — Publish `core.application`; games stop importing `core.domain` (PR 2)

> Each task is a coherent slice ending green. Order matters: relocate shared value types first (GameMode), then the token boundary, then exceptions, then the projections/facade methods, then the controllers, then the gate.

### Task 3: Relocate `GameMode` to `core.application`

**Files:**
- Move: `core/domain/GameMode.java` → `core/application/GameMode.java`
- Modify imports: `core/application/SessionService.java`, `bullshit/presentation/api/BullshitCreatePayload.java`, `bataillecorse/presentation/BatailleCorseWebSocketController.java`, `presentation/api/CreateGamePayload.java` (if it references GameMode), and any test referencing `GameMode`.

- [ ] **Step 1: Move the file + change its package**
```bash
git mv backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/core/domain/GameMode.java \
       backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/core/application/GameMode.java
```
Change its `package` line to `package org.kevinkib.cardgames.sessionmanagement.core.application;`.

- [ ] **Step 2: Rewrite references**
Run from `backend/`:
```bash
grep -rl 'core\.domain\.GameMode' src --include=*.java | while read f; do \
  sed -i 's/core\.domain\.GameMode/core.application.GameMode/g' "$f"; done
```

- [ ] **Step 3: Full suite**
`mvn test` → 265 green.

- [ ] **Step 4: Commit**
```bash
git add -A backend/
git commit -m "refactor(session): publish GameMode in core.application"
```

---

### Task 4: String tokens at the boundary

Goal: no caller wraps `new SessionToken(...)` or reads `SessionToken` off the facade. `SessionToken` stays a `core.domain` internal.

**Files:**
- Modify: `core/application/SessionService.java`, `core/application/JoinResult.java`
- Modify (callers): `bullshit/presentation/BullshitWebSocketController.java`, `bullshit/presentation/BullshitRestController.java`, `bataillecorse/presentation/BatailleCorseWebSocketController.java`, `bataillecorse/presentation/BatailleCorseRestController.java`, `presentation/LifecycleController.java`
- Modify (tests): any test calling these with `SessionToken`.

- [ ] **Step 1: `JoinResult` carries a String token**
```java
package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.PlayerId;

public record JoinResult(PlayerId playerId, String token) {
}
```

- [ ] **Step 2: `SessionService` boundary signatures use String**
- `joinGame`/`joinRoom`: build `JoinResult` from the claimed seat's token string: replace `return new JoinResult(claimed.id(), claimed.token());` with `return new JoinResult(claimed.id(), claimed.token().uuid().toString());` (in both methods).
- `startGame(GameId id, SessionToken hostToken)` → `startGame(GameId id, String hostToken)`; inside, resolve via `lobby.findPlayerByToken(new SessionToken(hostToken))` (wrap internally).
- `findPlayerIdByToken(GameId gameId, SessionToken token)` → `findPlayerIdByToken(GameId gameId, String token)`; body `return repository.loadSessionGame(gameId).findPlayerByToken(new SessionToken(token));`.
- Rename `loadTokenByPlayerId(GameId, PlayerId): SessionToken` → `tokenForSeat(GameId gameId, PlayerId playerId): String`; body `return repository.loadSessionToken(gameId, playerId).uuid().toString();`.
- `SessionToken` import stays (used internally). `JoinResult` no longer needs it.

- [ ] **Step 3: Update callers (drop the wrapping)**
- `BullshitWebSocketController`: `start` — `sessionService.findPlayerIdByToken(gameId, payload.token())` and `sessionService.startGame(gameId, payload.token())`; delete the `SessionToken token = new SessionToken(...)` line and the `import ...SessionToken`. `discard`/`callBullshit` — `findPlayerIdByToken(gameId, payload.token())`.
- `BullshitRestController`: `findPlayerIdByToken(gameId, token)` (drop `new SessionToken`); the join response already does `result.token()` — change `result.token().uuid().toString()` to just `result.token()`. Remove `import ...SessionToken`.
- `BatailleCorseWebSocketController`: all four `findPlayerIdByToken(gameId, new SessionToken(payload.token()))` → `findPlayerIdByToken(gameId, payload.token())`; the create token-map loop replaces `SessionToken token = sessionService.loadTokenByPlayerId(...); tokens.put(i, token.uuid().toString());` with `tokens.put(i, sessionService.tokenForSeat(batailleCorse.getId(), new PlayerId(i)));`. Remove `import ...SessionToken`.
- `BatailleCorseRestController`: `result.token().uuid().toString()` → `result.token()`.
- `LifecycleController`: both `findPlayerIdByToken(gameId, new SessionToken(payload.token()))` → `findPlayerIdByToken(gameId, payload.token())`. Remove `import ...SessionToken`.

- [ ] **Step 4: Update tests**
Any test calling `findPlayerIdByToken`/`startGame` with a `SessionToken`, or reading `JoinResult.token()` as a `SessionToken`, or calling `loadTokenByPlayerId`: pass/expect `String`. Specifically `BullshitWebSocketControllerTest` and `BullshitRestControllerTest` use `loadTokenByPlayerId(...).uuid().toString()` → change to `tokenForSeat(...)`; `SessionLobbyTest`/`SessionServiceTest` use `loadTokenByPlayerId` and `findPlayerIdByToken(..., token)` / `startGame(id, hostToken)` with `SessionToken` — switch those to the String forms (`startGame(id, hostToken)` where `hostToken` is now the String from `tokenForSeat`/`findTokenByPlayer(...).uuid().toString()`). Adjust each to compile against the new signatures; assertions unchanged.

- [ ] **Step 5: Full suite**
`mvn test` → 265 green.

- [ ] **Step 6: Commit**
```bash
git add -A backend/
git commit -m "refactor(session): String tokens at the core.application boundary"
```

---

### Task 5: Published exceptions + domain-internal rename

Domain keeps throwing its own exceptions (now internally named); the facade translates to published `core.application` exceptions that games catch.

**Files:**
- Rename: `core/domain/RoomFullException.java` → `core/domain/NoFreeSeatException.java`; `core/domain/SeatUnavailableException.java` → `core/domain/SeatTakenException.java`
- Create: `core/application/RoomFullException.java`, `core/application/SeatUnavailableException.java`
- Modify: `core/domain/SessionGame.java` (throw the renamed ones), `core/application/SessionService.java` (translate)
- Modify (callers/tests): `bullshit/presentation/BullshitRestController.java`, `bataillecorse/presentation/BatailleCorseRestController.java`, `core/domain/SessionGameTest.java`

- [ ] **Step 1: Rename the domain exceptions**
```bash
git mv backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/core/domain/RoomFullException.java \
       backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/core/domain/NoFreeSeatException.java
git mv backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/core/domain/SeatUnavailableException.java \
       backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/core/domain/SeatTakenException.java
```
Rename the class in each file to match (`NoFreeSeatException`, `SeatTakenException`); keep the constructor/message.

- [ ] **Step 2: `SessionGame` throws the renamed types**
In `SessionGame.java`: `claimNextFreeSeat` throws `new NoFreeSeatException(id)` (was `RoomFullException`); `claimSeat` throws `new SeatTakenException(playerId)` (was `SeatUnavailableException`). Update those two `throw` sites.

- [ ] **Step 3: Create the published application exceptions**
`core/application/RoomFullException.java`:
```java
package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.GameId;

public class RoomFullException extends RuntimeException {
    public RoomFullException(GameId id) {
        super("Room " + id + " is full.");
    }
}
```
`core/application/SeatUnavailableException.java`:
```java
package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.PlayerId;

public class SeatUnavailableException extends RuntimeException {
    public SeatUnavailableException(PlayerId playerId) {
        super("Seat " + playerId.id() + " is already claimed.");
    }
}
```

- [ ] **Step 4: Translate in `SessionService`**
- `joinRoom`: wrap the claim so a domain `NoFreeSeatException` becomes the published one:
```java
    public JoinResult joinRoom(GameId id, String name) {
        if (repository.findGame(id).isPresent()) {
            throw new GameAlreadyStartedException(id);
        }
        SessionGame lobby = repository.loadSessionGame(id);
        try {
            SessionPlayer claimed = lobby.claimNextFreeSeat(name);
            return new JoinResult(claimed.id(), claimed.token().uuid().toString());
        } catch (NoFreeSeatException e) {
            throw new RoomFullException(id);
        }
    }
```
- `joinGame`: translate `SeatTakenException` → published `SeatUnavailableException`:
```java
    public JoinResult joinGame(GameId gameId, String name) {
        SessionGame sessionGame = repository.loadSessionGame(gameId);
        try {
            SessionPlayer claimed = sessionGame.claimSeat(JOINER_SEAT, name);
            return new JoinResult(claimed.id(), claimed.token().uuid().toString());
        } catch (SeatTakenException e) {
            throw new SeatUnavailableException(JOINER_SEAT);
        }
    }
```
Add imports for the domain `NoFreeSeatException`/`SeatTakenException`.

- [ ] **Step 5: Game controllers catch the published exceptions**
- `BullshitRestController`: change `import ...core.domain.RoomFullException` → `import ...core.application.RoomFullException` (the `catch (GameAlreadyStartedException | RoomFullException e)` now binds the application one).
- `BatailleCorseRestController`: change `import ...core.domain.SeatUnavailableException` → `import ...core.application.SeatUnavailableException`.

- [ ] **Step 6: Update `SessionGameTest`**
Its `ClaimNextFreeSeatTest` expects `RoomFullException` from `claimNextFreeSeat`; `ClaimSeatTest` expects `SeatUnavailableException` from `claimSeat`. Change those `assertThrows` to `NoFreeSeatException.class` and `SeatTakenException.class` respectively (domain test sees the domain types). Update imports (same package `core.domain`, so no import needed).

- [ ] **Step 7: Full suite**
`mvn test` → 265 green.

- [ ] **Step 8: Commit**
```bash
git add -A backend/
git commit -m "refactor(session): publish RoomFull/SeatUnavailable in application, domain throws internal NoFreeSeat/SeatTaken"
```

---

### Task 6: `RoomCreated` + `gameType()` — kill the `SessionGame` leak in Bullshit's WS create

**Files:**
- Create: `core/application/RoomCreated.java`
- Modify: `core/application/SessionService.java` (createRoom return; add gameType)
- Modify: `bullshit/presentation/BullshitWebSocketController.java`
- Modify (tests): `BullshitWebSocketControllerTest`, `SessionLobbyTest`, `BullshitRestControllerTest` (createRoom usages)

- [ ] **Step 1: Create the published record**
```java
package org.kevinkib.cardgames.sessionmanagement.core.application;

public record RoomCreated(String gameId, String hostToken) {
}
```

- [ ] **Step 2: `SessionService.createRoom` returns it; add `gameType`**
```java
    public RoomCreated createRoom(String gameType, String hostName) {
        GameId id = GameId.generate();
        SessionGame lobby = SessionGame.create(id, gameFactories.maxPlayers(gameType), gameType);
        SessionPlayer host = lobby.claimHost(hostName);
        repository.saveLobby(lobby);
        return new RoomCreated(id.uuid().toString(), host.token().uuid().toString());
    }

    public String gameType(GameId id) {
        return repository.loadSessionGame(id).gameType();
    }
```
(`claimHost` returns the `SessionPlayer` — confirm; it does in `SessionGame`. `gameType` throws `IllegalArgumentException` for an unknown id, same as `loadSessionGame` today.)

- [ ] **Step 3: Update `BullshitWebSocketController.createGame`**
```java
    @MessageMapping("/bullshit/create")
    @SendTo("/topic/game")
    public Response createGame(@Payload(required = false) BullshitCreatePayload payload) {
        String name = (payload != null) ? payload.name() : null;
        RoomCreated room = sessionService.createRoom(BullshitFactory.GAME_TYPE, name);
        Map<Integer, String> tokens = Map.of(0, room.hostToken());

        return new SuccessResponse(
                LifecycleEventType.CREATE.toString(),
                new BullshitCreateEventData(room.gameId(), BullshitFactory.GAME_TYPE, tokens),
                "Room created",
                null);
    }
```
Remove the `import ...core.domain.SessionGame;` and the `PlayerId`/`SessionToken` usages that existed only for the old host-token lookup. Add `import ...core.application.RoomCreated;`.

- [ ] **Step 4: Update tests**
`BullshitWebSocketControllerTest`, `SessionLobbyTest`, `BullshitRestControllerTest` call `createRoom(...)` and read `.id()`/`.findTokenByPlayer(...)` off the returned `SessionGame`. Where a test needs the gameId/host token after `createRoom`, use `RoomCreated.gameId()` / `.hostToken()`. Where a test needs the full lobby (to call `joinRoom`/`startGame`), it can re-fetch via the service (e.g. `new GameId(room.gameId())`). Adjust each call site; assertions unchanged.

- [ ] **Step 5: Full suite**
`mvn test` → 265 green.

- [ ] **Step 6: Commit**
```bash
git add -A backend/
git commit -m "refactor(session): createRoom returns RoomCreated; add gameType() (no SessionGame in WS create)"
```

---

### Task 7: `LobbyView` projection — relocate `LobbyDto` into `core.application`

**Files:**
- Create: `core/application/LobbyView.java` (with nested `LobbyPlayer`)
- Delete: `presentation/dto/LobbyDto.java`
- Modify: `core/application/SessionService.java` (add `lobbyView`, `lobbyViews`)
- Modify: `bullshit/presentation/BullshitRestController.java`, `presentation/LobbyBroadcaster.java`
- Move (test): `presentation/dto/LobbyDtoTest.java` → `core/application/LobbyViewTest.java`

- [ ] **Step 1: Create `LobbyView`** (same shape/logic as today's `LobbyDto`, in core.application, reading the aggregate)
```java
package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;

import java.util.List;

/** Generic per-viewer projection of a not-yet-started session (a lobby). Published; no secrets. */
public record LobbyView(
        boolean started,
        String gameId,
        List<LobbyPlayer> players,
        int hostSeat,
        int mySeat,
        int minPlayers,
        int maxPlayers,
        boolean canStart) {

    public record LobbyPlayer(int seat, String name, boolean joined) {
    }

    static LobbyView forViewer(SessionGame lobby, int minPlayers, int maxPlayers, PlayerId viewer) {
        List<LobbyPlayer> players = lobby.seats().stream()
                .map(seat -> new LobbyPlayer(seat.id().id(), seat.name(), seat.isClaimed()))
                .toList();
        boolean canStart = lobby.isHost(viewer) && lobby.claimedCount() >= minPlayers;
        return new LobbyView(false, lobby.id().uuid().toString(), players,
                SessionGame.HOST_SEAT.id(), viewer.id(), minPlayers, maxPlayers, canStart);
    }
}
```
(`forViewer` is package-private — only `SessionService` builds it.)

- [ ] **Step 2: Add facade methods to `SessionService`**
```java
    public LobbyView lobbyView(GameId id, String token) {
        SessionGame lobby = repository.loadSessionGame(id);
        PlayerId viewer = lobby.findPlayerByToken(new SessionToken(token))
                .orElseThrow(InvalidTokenException::new);
        return LobbyView.forViewer(lobby, minPlayers(lobby.gameType()), maxPlayers(lobby.gameType()), viewer);
    }

    public List<LobbyView> lobbyViews(GameId id) {
        SessionGame lobby = repository.loadSessionGame(id);
        int min = minPlayers(lobby.gameType());
        int max = maxPlayers(lobby.gameType());
        return lobby.seats().stream()
                .filter(SessionPlayer::isClaimed)
                .map(seat -> LobbyView.forViewer(lobby, min, max, seat.id()))
                .toList();
    }
```

- [ ] **Step 3: `BullshitRestController` returns `LobbyView`**
Replace the whole `getGame` body (keeping the `token == null → 403` guard first):
```java
    @GetMapping("/game/{id}")
    public ResponseEntity<?> getGame(@PathVariable String id,
                                     @RequestParam(name = "token", required = false) String token) {
        if (token == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        GameId gameId;
        String type;
        try {
            gameId = new GameId(id);
            type = sessionService.gameType(gameId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        if (!BullshitFactory.GAME_TYPE.equals(type)) {
            return ResponseEntity.notFound().build();
        }
        PlayerId seat = sessionService.findPlayerIdByToken(gameId, token).orElse(null);
        if (seat == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<Game> game = sessionService.findGame(gameId);
        if (game.isPresent()) {
            return ResponseEntity.ok(BullshitDto.forViewer((Bullshit) game.get(), seat));
        }
        return ResponseEntity.ok(sessionService.lobbyView(gameId, token));
    }
```
Also rewrite `joinGame` so it never touches `SessionGame` (it currently uses `getGameSession` for both the gameType guard and the post-join broadcast):
```java
    @PostMapping("/game/{id}/join")
    public ResponseEntity<JoinResponseDto> joinGame(@PathVariable String id,
                                                    @RequestBody(required = false) JoinGamePayload payload) {
        GameId gameId;
        String type;
        try {
            gameId = new GameId(id);
            type = sessionService.gameType(gameId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        if (!BullshitFactory.GAME_TYPE.equals(type)) {
            return ResponseEntity.notFound().build();
        }
        try {
            String name = (payload != null) ? payload.name() : null;
            JoinResult result = sessionService.joinRoom(gameId, name);
            sessionService.touch(gameId);
            lobbyBroadcaster.broadcast(gameId, LifecycleEventType.JOIN.toString(), new EmptyEventData(),
                    "Player " + (result.playerId().id() + 1) + " joined.");
            return ResponseEntity.ok(new JoinResponseDto(result.playerId().id(), result.token()));
        } catch (GameAlreadyStartedException | RoomFullException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
```
Remove `import ...core.domain.SessionGame;` and the `presentation.dto.LobbyDto` import; add `import ...core.application.LobbyView;`. (`RoomFullException` is the `core.application` one per Task 5; `result.token()` is already a String per Task 4.)

- [ ] **Step 4: `LobbyBroadcaster` takes a `GameId`, pulls views from the facade**
```java
package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.sessionmanagement.core.application.LobbyView;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;

public class LobbyBroadcaster {

    private final GameMessagingService messaging;
    private final SessionService sessionService;

    public LobbyBroadcaster(GameMessagingService messaging, SessionService sessionService) {
        this.messaging = messaging;
        this.sessionService = sessionService;
    }

    public void broadcast(GameId gameId, String eventType, EventData eventData, String message) {
        for (LobbyView view : sessionService.lobbyViews(gameId)) {
            messaging.sendToSeat(gameId, new PlayerId(view.mySeat()),
                    new SuccessResponse(eventType, eventData, message, view));
        }
    }
}
```
Update the `lobbyBroadcaster(...)` bean in `AppConfig` to pass `sessionService()` instead of `gameFactories()`.

- [ ] **Step 5: Delete `LobbyDto`, move its test**
```bash
git rm backend/src/main/java/org/kevinkib/cardgames/presentation/dto/LobbyDto.java
```
Recreate `LobbyDtoTest` as `core/application/LobbyViewTest.java`: package `core.application`; class `LobbyViewTest`; `LobbyDto`→`LobbyView`, `LobbyPlayerDto`→`LobbyPlayer`. Because `forViewer` is package-private and the test is now in the same package, it can call `LobbyView.forViewer(...)` directly (keep the existing assertions). `git rm` the old test.

- [ ] **Step 6: Full suite**
`mvn test` → 265 green.

- [ ] **Step 7: Commit**
```bash
git add -A backend/
git commit -m "refactor(session): LobbyView projection in core.application; broadcaster + REST off the aggregate"
```

---

### Task 8: `SeatView` projection — kill the `SessionPlayer` leak

**Files:**
- Create: `core/application/SeatView.java`
- Modify: `core/application/SessionService.java` (replace `getSeats` with `seats`)
- Modify: `presentation/dto/SessionViewDto.java`, `presentation/SessionRestController.java`, `bataillecorse/presentation/BatailleCorseRestController.java`
- Modify (tests): `core/application/SessionServiceTest.java`, `core/application/SessionLobbyTest.java`

- [ ] **Step 1: Create the projection**
```java
package org.kevinkib.cardgames.sessionmanagement.core.application;

public record SeatView(int seat, String name, boolean joined) {
}
```

- [ ] **Step 2: `SessionService.seats` replaces `getSeats`**
```java
    public List<SeatView> seats(GameId gameId) {
        return repository.loadSessionGame(gameId).seats().stream()
                .map(s -> new SeatView(s.id().id(), s.name(), s.isClaimed()))
                .toList();
    }
```
Remove the old `getSeats` method and the now-unused `SessionPlayer` import if nothing else uses it. (Check: `rematch`/`startGame` use `SessionPlayer::isClaimed` via `claimedCount`/`seatCount` on `SessionGame`, not here. `isSeatClaimed` uses `loadSessionGame`. If `SessionPlayer` is still referenced elsewhere in `SessionService`, keep the import.)

- [ ] **Step 3: `SessionViewDto.from(List<SeatView>)`**
```java
package org.kevinkib.cardgames.presentation.dto;

import org.kevinkib.cardgames.sessionmanagement.core.application.SeatView;

import java.util.List;

public record SessionViewDto(List<SeatDto> players) {

    public static SessionViewDto from(List<SeatView> seats) {
        List<SeatDto> dtos = seats.stream()
                .map(seat -> new SeatDto(seat.seat(), seat.name(), seat.joined()))
                .toList();
        return new SessionViewDto(dtos);
    }
}
```

- [ ] **Step 4: Update the callers**
- `SessionRestController`: `List<SeatView> seats = sessionService.seats(gameId);` (replace `getSeats`); change the `SessionPlayer` import to `SeatView`.
- `BatailleCorseRestController`: `SessionViewDto sessionView = SessionViewDto.from(sessionService.seats(gameId));`; remove `import ...core.domain.SessionPlayer;`.

- [ ] **Step 5: Update the tests**
- `SessionServiceTest`: the name/claim tests use `service.getSeats(id).get(n).name()` / `.isClaimed()`. Change to `service.seats(id).get(n).name()` / `.joined()` (a `SeatView` exposes `name()`/`joined()`), and the `SessionPlayer` import to `SeatView`.
- `SessionLobbyTest`: `givenOpenRoom_whenJoinRoom_thenNextSeatClaimed` reads `service.getGameSession(lobby.id()).seats().get(1).name()`. Replace with `service.seats(lobby.id()).get(1).name()` (removes a `getGameSession` caller too).

- [ ] **Step 6: Full suite**
`mvn test` → 265 green.

- [ ] **Step 7: Commit**
```bash
git add -A backend/
git commit -m "refactor(session): SeatView projection; getSeats removed from the published API"
```

---

### Task 9: Rematch facade — kill the `SessionGame` reach in BatailleCorse's WS rematch

**Files:**
- Modify: `core/application/SessionService.java` (add `requestRematch`; remove `getGameSession`)
- Modify: `bataillecorse/presentation/BatailleCorseWebSocketController.java`
- Modify (test): `core/application/SessionServiceTest.java` (its `RematchTest` calls `getGameSession(id).requestRematch(...)`)

- [ ] **Step 1: Add `requestRematch` to `SessionService`**
```java
    /** Records this seat's rematch request; returns true when all seats have requested. */
    public boolean requestRematch(GameId id, PlayerId playerId) {
        SessionGame session = repository.loadSessionGame(id);
        session.requestRematch(playerId);
        return session.isRematchUnanimous();
    }
```
Then remove the now-unused `getGameSession(GameId): SessionGame` method (verify no remaining callers first: `grep -rn "getGameSession" backend/src` should show only the ones being removed in this task).

- [ ] **Step 2: Update `BatailleCorseWebSocketController.rematch`**
Replace the body's session calls:
```java
            PlayerId playerId = sessionService
                    .findPlayerIdByToken(gameId, payload.token())
                    .orElseThrow(InvalidTokenException::new);

            boolean unanimous = sessionService.requestRematch(gameId, playerId);

            Response response;
            if (unanimous) {
                BatailleCorse fresh = (BatailleCorse) sessionService.rematch(gameId);
                response = new SuccessResponse(
                        LifecycleEventType.REMATCH.toString(),
                        new RematchEventData(RematchStatus.STARTED, new PlayerIdDto(String.valueOf(playerId.id()))),
                        "Rematch started.",
                        BatailleCorseDto.from(fresh));
            } else {
                BatailleCorse current = sessionService.getGame(gameId, BatailleCorse.class);
                response = new SuccessResponse(
                        LifecycleEventType.REMATCH.toString(),
                        new RematchEventData(RematchStatus.PENDING, new PlayerIdDto(String.valueOf(playerId.id()))),
                        "Rematch requested.",
                        BatailleCorseDto.from(current));
            }
            gameMessagingService.sendToGame(payload.gameId(), response);
```
(`SessionToken` import already removed in Task 4.)

- [ ] **Step 3: Update `SessionServiceTest.RematchTest`**
The `givenRequestedRematch_whenRematch_thenRequestFlagsCleared` test sets up unanimity via `service.getGameSession(id).requestRematch(p0)` / `(p1)`. Replace those two lines with `service.requestRematch(id, new PlayerId(0));` / `service.requestRematch(id, new PlayerId(1));`. Assertions unchanged.

- [ ] **Step 4: Full suite**
`mvn test` → 265 green.

- [ ] **Step 5: Commit**
```bash
git add -A backend/
git commit -m "refactor(session): requestRematch facade; getGameSession removed (no aggregate in BC rematch)"
```

---

### Task 10: Verify the boundary + final gate

**Files:** none (verification only).

- [ ] **Step 1: No game context imports `core.domain`**
Run from `backend/`:
```bash
grep -rn "sessionmanagement\.core\.domain" src/main/java/org/kevinkib/cardgames/bullshit src/main/java/org/kevinkib/cardgames/bataillecorse
```
Expected: **no matches**. If any remain, fix that controller/DTO to use the published `core.application` type and re-run.

- [ ] **Step 2: Confirm the published surface is the only seam**
```bash
grep -rn "sessionmanagement\.core\.domain" src/main/java/org/kevinkib/cardgames/presentation
```
Expected: matches only in places that are the deferred shared-presentation follow-up (none should remain after Tasks 7–8 — `LobbyBroadcaster`, `SessionViewDto`, `SessionRestController` were all migrated). If any unexpected match remains, fix it.

- [ ] **Step 3: Full suite**
`mvn test` → 265 green.

- [ ] **Step 4: Commit (if Step 1/2 required fixes; otherwise nothing to commit)**
```bash
git add -A backend/ && git commit -m "chore(session): verify game contexts no longer import core.domain"
```

**End of PR 2.**

---

## Notes for the executor

- **Pure refactor: never change behavior.** If a test's assertions would need to change to pass (beyond signature/type adjustments), stop — something moved wrong.
- The `presentation.*` wildcard imports in the two BatailleCorse files are a trap: when a symbol leaves a package, the wildcard silently stops covering it — add the explicit `core.application` import.
- Out of scope (do NOT do): relocating `SessionRestController`/session DTOs out of shared `presentation`; renaming the `sessionmanagement` umbrella; any frontend change. Tasks 7–8 adapt those shared files only enough to keep them compiling against the new published types.

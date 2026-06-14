# BatailleCorse Presentation Split (Slice 2b-i) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the BatailleCorse-flavoured top-level `presentation` package into game-agnostic shared plumbing + a `bataillecorse.presentation` package, with the only seam at the 100%-overlap lifecycle layer (a verb-based `GameLifecycleBroadcaster`); no rendering abstraction, no wire-format change, no behaviour change.

**Architecture:** Phases 1–4 generalise the envelope, add a typed session accessor, introduce the lifecycle verb-seam, and split the event-type enum — all in place in the existing `presentation` package, each ending green. Phase 5 performs the package moves (BatailleCorse-specific files → `bataillecorse.presentation`; `Application` → root package so component-scan still covers the moved `@Controller`s) and finalises the shared package. The existing 189 tests — including the `*IT` tests that boot the full Spring context — are the safety net.

**Tech Stack:** Java 17, Spring Boot, STOMP WebSockets, Maven (no wrapper). Run from repo root:
- Full suite: `mvn -f backend/pom.xml test`. Use the IntelliJ-bundled Maven; there is **no** `./mvnw` — never invent one.
- Bundled mvn (if `mvn` not on PATH):
  ```bash
  export JAVA_HOME="C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\jbr"
  MVN="/c/Program Files/JetBrains/IntelliJ IDEA 2025.3.3/plugins/maven/lib/maven3/bin/mvn"
  "$MVN" -f backend/pom.xml test
  ```
- Single test: append `-Dtest=ClassName` (or `-Dtest=ClassName#method`).
- A clean run (`clean test`) is required after package moves — incremental compiles report stale BUILD SUCCESS.

The whole plan is **one MR**. Baseline before starting: **189 tests green**.

Spec: `docs/superpowers/specs/2026-06-14-bataillecorse-presentation-split-design.md`.

---

## File classification (locked decisions)

**Stay shared in `presentation` (no card-game knowledge):**
- Plumbing: `GameMessagingService`, `StompSessionSeatRegistry`, `Seat`, `ForfeitReason`, `ForfeitReasonRegistry`, `WebSocketConfiguration`, `WebSocketDisconnectListener`.
- `DisconnectForfeitService` (refactored — no DTO building).
- Envelope: `api/Response`, `api/SuccessResponse`, `api/ErrorResponse` (generalised: `state` → `Object`, `eventType` → `String`).
- Event data with only primitives: `dto/event/EventData`, `EmptyEventData`, `ForfeitEventData`, `OpponentDisconnectedEventData`, `OpponentReconnectedEventData`, `RematchStatus`.
- New shared: `LifecycleEventType`, `GameLifecycleBroadcaster`, `GameLifecycleBroadcasters`, `LifecycleController`, `SessionRestController`.
- Session DTOs: `dto/SeatDto`, `dto/SessionViewDto`, `dto/JoinResponseDto`.

**Move to `bataillecorse.presentation` (BatailleCorse game state):**
- Controllers: `BatailleCorseWebSocketController`, `GameRestController` → renamed `BatailleCorseRestController`.
- DTOs: `BatailleCorseDto`, `CardDto`, `PileDto`, `PlayerDto`, `PlayerIdDto`, `BatailleCorseIdDto`.
- Event data: `CreateEventData`, `JoinEventData`, `RematchEventData`, `SendEventData`, `SlapEventData`, `GrabEventData`.
- New: `BatailleCorseEventType` (SEND/SLAP/GRAB), `BatailleCorseLifecycleBroadcaster`.

**Move to root package `org.kevinkib.cardgames`:** `Application` (so `@SpringBootApplication` component-scan covers `bataillecorse.presentation`).

**Event-type values:** `LifecycleEventType` = CREATE, JOIN, FORFEIT, REMATCH, OPPONENT_DISCONNECTED, OPPONENT_RECONNECTED. `BatailleCorseEventType` = SEND, SLAP, GRAB. The emitted strings are identical to today's `EventType`, so the wire format and IT assertions are unchanged.

---

## Phase 1 — Generalise the response envelope (in place)

### Task 1.1: `Response`/`SuccessResponse`/`ErrorResponse` carry `Object` state + `String` eventType

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/cardgames/presentation/api/Response.java`
- Modify: `backend/src/main/java/org/kevinkib/cardgames/presentation/api/SuccessResponse.java`
- Modify: `backend/src/main/java/org/kevinkib/cardgames/presentation/api/ErrorResponse.java`
- Modify callers: `presentation/BatailleCorseWebSocketController.java`, `presentation/DisconnectForfeitService.java`, `presentation/GameRestController.java`
- Modify tests: `presentation/BatailleCorseControllerIT.java`, `presentation/GameRestControllerIT.java`, `presentation/BatailleCorseWebSocketControllerIT.java`, and any test calling `getState().getX()`

- [ ] **Step 1: Generalise `Response`**

Replace the type of `state` and `eventType`:
```java
package org.kevinkib.cardgames.presentation.api;

import org.kevinkib.cardgames.presentation.dto.event.EventData;

public abstract class Response {

    private final boolean success;
    private final String eventType;
    private final EventData eventData;
    private final String message;
    private final Object state;

    public Response(boolean success, String eventType, EventData eventData, String message, Object state) {
        this.success = success;
        this.eventType = eventType;
        this.eventData = eventData;
        this.message = message;
        this.state = state;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getEventType() {
        return eventType;
    }

    public EventData getEventData() {
        return eventData;
    }

    public String getMessage() {
        return message;
    }

    public Object getState() {
        return state;
    }
}
```
(Drops the `BatailleCorseDto` and `EventType` imports.)

- [ ] **Step 2: `SuccessResponse` / `ErrorResponse` take `String` eventType + `Object` state**

`SuccessResponse.java`:
```java
package org.kevinkib.cardgames.presentation.api;

import org.kevinkib.cardgames.presentation.dto.event.EventData;

public class SuccessResponse extends Response {

    public SuccessResponse(String eventType, EventData eventData, String message, Object state) {
        super(true, eventType, eventData, message, state);
    }
}
```
`ErrorResponse.java`:
```java
package org.kevinkib.cardgames.presentation.api;

import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;

public class ErrorResponse extends Response {

    public ErrorResponse(String eventType, String message, Object state) {
        super(false, eventType, new EmptyEventData(), message, state);
    }
}
```

- [ ] **Step 3: Update callers to pass `EventType.X.toString()`**

In `BatailleCorseWebSocketController.java`, `DisconnectForfeitService.java`, `GameRestController.java`, every `new SuccessResponse(eventType, ...)` / `new ErrorResponse(eventType, ...)` currently passes an `EventType` value (or a local `EventType eventType` variable). Append `.toString()` so a `String` is passed. Concretely:
- Where a method holds `EventType eventType = EventType.SEND;` and calls `new SuccessResponse(eventType, ...)`, change to `new SuccessResponse(eventType.toString(), ...)` (and likewise the `ErrorResponse`).
- Where the call passes a literal like `EventType.REMATCH`, change to `EventType.REMATCH.toString()`.
- The `state` argument (a `BatailleCorseDto`) is unchanged — it widens to `Object` automatically.

- [ ] **Step 4: Fix tests that read concrete state off `getState()`**

`getState()` now returns `Object`. Search and cast:
```bash
grep -rn "getState()\." backend/src/test --include=*.java
```
For each (e.g. in `BatailleCorseControllerIT`, `GameRestControllerIT`, `BatailleCorseWebSocketControllerIT`, `DisconnectForfeitServiceTest`), wrap the receiver: `((BatailleCorseDto) response.getState()).getPlayers()`. Add `import org.kevinkib.cardgames.presentation.dto.BatailleCorseDto;` where needed. Assertions on `getEventType()` (already a `String`) are unchanged.

- [ ] **Step 5: Full clean suite, expect 189 green**
```bash
mvn -f backend/pom.xml clean test
```
Expected: BUILD SUCCESS, `Tests run: 189, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**
```bash
git add -A
git commit -m "refactor(presentation): generalise Response envelope (Object state, String eventType)"
```

---

## Phase 2 — Typed session accessor (removes raw casts)

### Task 2.1: `SessionService.getGame(GameId, Class<T>)`

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/application/SessionService.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/application/SessionServiceTest.java`

- [ ] **Step 1: Write the failing test**

Add to `SessionServiceTest` (it already has `service` wired with a `BatailleCorseFactory`):
```java
    @Nested
    class TypedGetGameTest {

        @Test
        void givenMatchingType_whenGetGame_thenReturnsTypedGame() {
            var game = service.createGame(2, GameMode.SOLO);

            org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse typed =
                    service.getGame(game.getId(), org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse.class);

            assertThat(typed.getId(), is(game.getId()));
        }

        @Test
        void givenWrongType_whenGetGame_thenThrows() {
            var game = service.createGame(2, GameMode.SOLO);

            assertThrows(IllegalStateException.class,
                    () -> service.getGame(game.getId(), org.kevinkib.cardgames.game.FakeGame.class));
        }
    }
```

- [ ] **Step 2: Run, expect FAIL** — `mvn -f backend/pom.xml test -Dtest=SessionServiceTest` → no method `getGame(GameId, Class)`.

- [ ] **Step 3: Implement the typed overload**

In `SessionService.java`, add below the existing `getGame(GameId)`:
```java
    public <T extends Game> T getGame(GameId id, Class<T> type) throws InvalidGameIdException {
        Game game = getGame(id);
        if (!type.isInstance(game)) {
            throw new IllegalStateException(
                    "Game " + id + " is " + game.getClass().getSimpleName() + ", not " + type.getSimpleName());
        }
        return type.cast(game);
    }
```
(`Game` is already imported from Phase-2a work.)

- [ ] **Step 4: Run, expect PASS** — full suite green.

- [ ] **Step 5: Commit**
```bash
git add -A
git commit -m "feat(session): typed getGame(GameId, Class<T>) accessor"
```

### Task 2.2: Replace presentation casts with the typed accessor

**Files:**
- Modify: `presentation/BatailleCorseWebSocketController.java`, `presentation/GameRestController.java`

- [ ] **Step 1: Replace raw casts in the WS controller**

In `BatailleCorseWebSocketController.java`, replace each
`BatailleCorse batailleCorse = (BatailleCorse) sessionService.getGame(gameId);`
with
`BatailleCorse batailleCorse = sessionService.getGame(gameId, BatailleCorse.class);`
and replace
`BatailleCorse batailleCorse = (BatailleCorse) sessionService.createGame(NB_PLAYERS, mode, name);`
with
`BatailleCorse batailleCorse = (BatailleCorse) sessionService.createGame(NB_PLAYERS, mode, name);`
— **leave the `createGame`/`rematch` casts as-is** (those return `Game` from a non-id-keyed call; the typed accessor is id-based). Only the `getGame(gameId)` sites change. Also change `BatailleCorse current = (BatailleCorse) sessionService.getGame(gameId);` to the typed form.

- [ ] **Step 2: Replace raw casts in `GameRestController`**

Replace both `BatailleCorse game = (BatailleCorse) sessionService.getGame(gameId);` with `BatailleCorse game = sessionService.getGame(gameId, BatailleCorse.class);`.

- [ ] **Step 3: Full suite green.**
```bash
mvn -f backend/pom.xml test
```

- [ ] **Step 4: Commit**
```bash
git add -A
git commit -m "refactor(presentation): use typed getGame, drop raw getGame casts"
```

> Note: the `(BatailleCorse) sessionService.createGame(...)` / `rematch(...)` casts remain until Phase 3/5; `createGame`/`rematch` are not id-keyed so the typed accessor does not apply. They are local to `bataillecorse.presentation` after Phase 5 and acceptable there.

---

## Phase 3 — Lifecycle verb-seam

### Task 3.1: `GameLifecycleBroadcaster` + resolver + BatailleCorse impl; refactor `DisconnectForfeitService`

**Files:**
- Create: `presentation/GameLifecycleBroadcaster.java`
- Create: `presentation/GameLifecycleBroadcasters.java`
- Create: `presentation/BatailleCorseLifecycleBroadcaster.java` (moved to `bataillecorse.presentation` in Phase 5)
- Modify: `presentation/DisconnectForfeitService.java`
- Modify: `config/AppConfig.java`
- Modify test: `presentation/DisconnectForfeitServiceTest.java`

- [ ] **Step 1: Create the seam interface**

`GameLifecycleBroadcaster.java`:
```java
package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.Game;

/** Per-game broadcaster for lifecycle events. The contract names what happened, never the state shape. */
public interface GameLifecycleBroadcaster {

    boolean supports(Game game);

    void disconnected(Game game, Seat seat, long deadlineEpochMs);

    void reconnected(Game game, Seat seat);

    void forfeited(Game game, Seat seat, ForfeitReason reason);
}
```

- [ ] **Step 2: Create the resolver**

`GameLifecycleBroadcasters.java`:
```java
package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.Game;

import java.util.List;

public class GameLifecycleBroadcasters {

    private final List<GameLifecycleBroadcaster> broadcasters;

    public GameLifecycleBroadcasters(List<GameLifecycleBroadcaster> broadcasters) {
        this.broadcasters = broadcasters;
    }

    public GameLifecycleBroadcaster broadcasterFor(Game game) {
        return broadcasters.stream()
                .filter(b -> b.supports(game))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No lifecycle broadcaster for " + game.getClass().getSimpleName()));
    }
}
```

- [ ] **Step 3: Create `BatailleCorseLifecycleBroadcaster`**

This carries the three broadcast bodies currently inside `DisconnectForfeitService` (the `BatailleCorseDto.from(...)` construction). `BatailleCorseControllerIT`/`DisconnectForfeitServiceTest` assert these exact broadcasts.

`presentation/BatailleCorseLifecycleBroadcaster.java`:
```java
package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.presentation.dto.BatailleCorseDto;
import org.kevinkib.cardgames.presentation.dto.event.EventType;
import org.kevinkib.cardgames.presentation.dto.event.ForfeitEventData;
import org.kevinkib.cardgames.presentation.dto.event.OpponentDisconnectedEventData;
import org.kevinkib.cardgames.presentation.dto.event.OpponentReconnectedEventData;

public class BatailleCorseLifecycleBroadcaster implements GameLifecycleBroadcaster {

    private final GameMessagingService messaging;
    private final ForfeitReasonRegistry forfeitReasonRegistry;

    public BatailleCorseLifecycleBroadcaster(GameMessagingService messaging,
                                             ForfeitReasonRegistry forfeitReasonRegistry) {
        this.messaging = messaging;
        this.forfeitReasonRegistry = forfeitReasonRegistry;
    }

    @Override
    public boolean supports(Game game) {
        return game instanceof BatailleCorse;
    }

    @Override
    public void disconnected(Game game, Seat seat, long deadlineEpochMs) {
        BatailleCorse bc = (BatailleCorse) game;
        messaging.sendToGame(seat.gameId().uuid().toString(), new SuccessResponse(
                EventType.OPPONENT_DISCONNECTED.toString(),
                new OpponentDisconnectedEventData(seat.playerId().id(), deadlineEpochMs),
                "Player " + seat.playerId() + " disconnected.",
                BatailleCorseDto.from(bc)));
    }

    @Override
    public void reconnected(Game game, Seat seat) {
        BatailleCorse bc = (BatailleCorse) game;
        messaging.sendToGame(seat.gameId().uuid().toString(), new SuccessResponse(
                EventType.OPPONENT_RECONNECTED.toString(),
                new OpponentReconnectedEventData(seat.playerId().id()),
                "Player " + seat.playerId() + " reconnected.",
                BatailleCorseDto.from(bc)));
    }

    @Override
    public void forfeited(Game game, Seat seat, ForfeitReason reason) {
        BatailleCorse bc = (BatailleCorse) game;
        messaging.sendToGame(seat.gameId().uuid().toString(), new SuccessResponse(
                EventType.FORFEIT.toString(),
                new ForfeitEventData(seat.playerId().id()),
                "Player " + seat.playerId() + " forfeited.",
                BatailleCorseDto.from(bc, forfeitReasonRegistry.reasonsBySeat(seat.gameId()))));
    }
}
```
(`reason` is recorded by the service before this call; the DTO reads it back from the registry, matching today's behaviour.)

- [ ] **Step 4: Refactor `DisconnectForfeitService` to delegate**

New version (presence/timer/forfeit logic only; no DTO building; resolves the broadcaster):
```java
package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.application.InvalidGameIdException;
import org.kevinkib.cardgames.sessionmanagement.application.SessionService;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Detects multiplayer disconnects and runs the 60s auto-loss timer, plus the
 * shared forfeit path. Reconnect cancels a pending timer. Broadcasting is delegated
 * to the per-game {@link GameLifecycleBroadcaster}; this service knows only the lifecycle.
 */
public class DisconnectForfeitService {

    /** Grace before a dropped player auto-loses. */
    public static final Duration FORFEIT_GRACE = Duration.ofSeconds(60);

    private final SessionService sessionService;
    private final StompSessionSeatRegistry registry;
    private final TaskScheduler scheduler;
    private final Clock clock;
    private final ForfeitReasonRegistry forfeitReasonRegistry;
    private final GameLifecycleBroadcasters broadcasters;

    private final Map<Seat, ScheduledFuture<?>> pendingForfeits = new ConcurrentHashMap<>();

    public DisconnectForfeitService(SessionService sessionService,
                                    StompSessionSeatRegistry registry,
                                    TaskScheduler scheduler,
                                    Clock clock,
                                    ForfeitReasonRegistry forfeitReasonRegistry,
                                    GameLifecycleBroadcasters broadcasters) {
        this.sessionService = sessionService;
        this.registry = registry;
        this.scheduler = scheduler;
        this.clock = clock;
        this.forfeitReasonRegistry = forfeitReasonRegistry;
        this.broadcasters = broadcasters;
    }

    public void onPresence(String sessionId, GameId gameId, PlayerId playerId) {
        Seat seat = new Seat(gameId, playerId);
        registry.bind(sessionId, seat);

        ScheduledFuture<?> pending = pendingForfeits.remove(seat);
        if (pending != null) {
            pending.cancel(false);
            Game game = findGame(seat.gameId());
            if (game != null) {
                broadcasters.broadcasterFor(game).reconnected(game, seat);
            }
        }
    }

    public void onDisconnect(String sessionId) {
        Optional<Seat> maybeSeat = registry.unbind(sessionId);
        if (maybeSeat.isEmpty()) {
            return;
        }
        Seat seat = maybeSeat.get();

        Game game = findGame(seat.gameId());
        if (game == null || game.isFinished()) {
            return;
        }

        Instant deadline = clock.instant().plus(FORFEIT_GRACE);
        ScheduledFuture<?> task = scheduler.schedule(() -> forfeit(seat, ForfeitReason.DISCONNECTED), deadline);
        pendingForfeits.put(seat, task);
        broadcasters.broadcasterFor(game).disconnected(game, seat, deadline.toEpochMilli());
    }

    public void forfeit(Seat seat, ForfeitReason reason) {
        pendingForfeits.remove(seat);

        Game game = findGame(seat.gameId());
        if (game == null || game.isFinished()) {
            return;
        }
        game.forfeit(seat.playerId());
        forfeitReasonRegistry.record(seat, reason);
        sessionService.touch(seat.gameId());
        broadcasters.broadcasterFor(game).forfeited(game, seat, reason);
    }

    private Game findGame(GameId gameId) {
        try {
            return sessionService.getGame(gameId);
        } catch (InvalidGameIdException e) {
            return null;
        }
    }
}
```

- [ ] **Step 5: Wire beans in `AppConfig`**

Add imports for `GameLifecycleBroadcaster`, `GameLifecycleBroadcasters`, `BatailleCorseLifecycleBroadcaster`, and `java.util.List`. Add beans and update the `disconnectForfeitService` bean:
```java
    @Bean
    public GameLifecycleBroadcaster batailleCorseLifecycleBroadcaster(GameMessagingService gameMessagingService) {
        return new BatailleCorseLifecycleBroadcaster(gameMessagingService, forfeitReasonRegistry());
    }

    @Bean
    public GameLifecycleBroadcasters gameLifecycleBroadcasters(java.util.List<GameLifecycleBroadcaster> broadcasters) {
        return new GameLifecycleBroadcasters(broadcasters);
    }

    @Bean
    public DisconnectForfeitService disconnectForfeitService(GameMessagingService gameMessagingService,
                                                             GameLifecycleBroadcasters gameLifecycleBroadcasters) {
        return new DisconnectForfeitService(
                sessionService(), stompSessionSeatRegistry(), taskScheduler(), clock(),
                forfeitReasonRegistry(), gameLifecycleBroadcasters);
    }
```
The `gameMessagingService` param on `disconnectForfeitService` is no longer used inside it — drop it from that bean method's signature; keep `GameMessagingService` injected only where still needed (the broadcaster bean). Final `disconnectForfeitService` signature: `(GameLifecycleBroadcasters gameLifecycleBroadcasters)` plus the locally-constructed beans. Adjust to:
```java
    @Bean
    public DisconnectForfeitService disconnectForfeitService(GameLifecycleBroadcasters gameLifecycleBroadcasters) {
        return new DisconnectForfeitService(
                sessionService(), stompSessionSeatRegistry(), taskScheduler(), clock(),
                forfeitReasonRegistry(), gameLifecycleBroadcasters);
    }
```

- [ ] **Step 6: Update `DisconnectForfeitServiceTest` construction**

The test builds `new DisconnectForfeitService(sessionService, messaging, registry, scheduler, clock, forfeitReasonRegistry)`. Change it to construct the broadcaster + resolver and pass them:
```java
        var broadcaster = new BatailleCorseLifecycleBroadcaster(messaging, forfeitReasonRegistry);
        var broadcasters = new GameLifecycleBroadcasters(java.util.List.of(broadcaster));
        service = new DisconnectForfeitService(sessionService, registry, scheduler, clock, forfeitReasonRegistry, broadcasters);
```
The assertions on `messaging.sent` (event types, state) are unchanged — the broadcaster sends through the same `RecordingMessaging`. Keep the `((BatailleCorseDto) ...getState())` casts from Phase 1.

- [ ] **Step 7: Full clean suite, expect 189 green** (the `*IT` tests verify the rewired beans load and broadcast identically).
```bash
mvn -f backend/pom.xml clean test
```

- [ ] **Step 8: Commit**
```bash
git add -A
git commit -m "refactor(presentation): lifecycle verb-seam (GameLifecycleBroadcaster); DisconnectForfeitService delegates rendering"
```

---

## Phase 4 — Split `EventType` into lifecycle + BatailleCorse enums

### Task 4.1: `LifecycleEventType` (shared) + `BatailleCorseEventType`

**Files:**
- Create: `presentation/dto/event/LifecycleEventType.java`
- Create: `presentation/dto/event/BatailleCorseEventType.java` (moved to `bataillecorse.presentation` in Phase 5)
- Delete: `presentation/dto/event/EventType.java`
- Modify: every file referencing `EventType` (controller, broadcaster, tests, ITs)

- [ ] **Step 1: Create the two enums**

`LifecycleEventType.java`:
```java
package org.kevinkib.cardgames.presentation.dto.event;

public enum LifecycleEventType {

    CREATE, JOIN, FORFEIT, REMATCH, OPPONENT_DISCONNECTED, OPPONENT_RECONNECTED;

    @Override
    public String toString() {
        return name();
    }
}
```
`BatailleCorseEventType.java`:
```java
package org.kevinkib.cardgames.presentation.dto.event;

public enum BatailleCorseEventType {

    SEND, SLAP, GRAB;

    @Override
    public String toString() {
        return name();
    }
}
```

- [ ] **Step 2: Repoint references**

Find every `EventType.` use:
```bash
grep -rn "EventType" backend/src --include=*.java
```
Repoint each value to its new enum: `EventType.SEND/SLAP/GRAB` → `BatailleCorseEventType.*`; `EventType.CREATE/JOIN/FORFEIT/REMATCH/OPPONENT_DISCONNECTED/OPPONENT_RECONNECTED` → `LifecycleEventType.*`. Update imports in each touched file (controller, `BatailleCorseLifecycleBroadcaster`, ITs, unit tests). Then delete `EventType.java`:
```bash
git rm backend/src/main/java/org/kevinkib/cardgames/presentation/dto/event/EventType.java
```

- [ ] **Step 3: Verify no stale references and run clean suite**
```bash
grep -rn "\bEventType\b" backend/src --include=*.java || echo "CLEAN"
mvn -f backend/pom.xml clean test
```
Expected: `CLEAN`, BUILD SUCCESS, 189 green (string values unchanged, so IT assertions still match).

- [ ] **Step 4: Commit**
```bash
git add -A
git commit -m "refactor(presentation): split EventType into LifecycleEventType + BatailleCorseEventType"
```

---

## Phase 5 — Package moves: `bataillecorse.presentation` + shared finalisation

> Mechanical relocation. After each `git mv`/`sed` block, compile; the final gate is a clean full run including the context-booting `*IT` tests. Work from repo root: `cd "$(git rev-parse --show-toplevel)"`.

### Task 5.1: Move `Application` to the root package (fix component scan)

**Files:**
- Move: `presentation/Application.java` → `org.kevinkib.cardgames.Application`

- [ ] **Step 1: Move and repackage**
```bash
cd "$(git rev-parse --show-toplevel)"
git mv backend/src/main/java/org/kevinkib/cardgames/presentation/Application.java \
       backend/src/main/java/org/kevinkib/cardgames/Application.java
sed -i 's#^package org\.kevinkib\.cardgames\.presentation;#package org.kevinkib.cardgames;#' \
       backend/src/main/java/org/kevinkib/cardgames/Application.java
```

- [ ] **Step 2: Clean compile + the context test**
```bash
mvn -f backend/pom.xml clean test -Dtest=ApplicationContextTest
```
Expected: BUILD SUCCESS (component scan now covers all `org.kevinkib.cardgames.*`, including `bataillecorse.presentation` created below). Then run the full suite to confirm nothing else moved yet: `mvn -f backend/pom.xml test` → 189 green.

- [ ] **Step 3: Commit**
```bash
git add -A
git commit -m "refactor: move Application to root package so component-scan covers all contexts"
```

### Task 5.2: Move BatailleCorse game-state DTOs

**Files:** move six DTOs to `bataillecorse/presentation/dto/`.

- [ ] **Step 1: Move and repackage**
```bash
cd "$(git rev-parse --show-toplevel)"
SRC=backend/src/main/java/org/kevinkib/cardgames/presentation/dto
DST=backend/src/main/java/org/kevinkib/cardgames/bataillecorse/presentation/dto
mkdir -p "$DST"
for f in BatailleCorseDto CardDto PileDto PlayerDto PlayerIdDto BatailleCorseIdDto; do
  git mv "$SRC/$f.java" "$DST/$f.java"
done
# repackage the moved files
sed -i 's#^package org\.kevinkib\.cardgames\.presentation\.dto;#package org.kevinkib.cardgames.bataillecorse.presentation.dto;#' "$DST"/*.java
# repoint every import of the moved DTOs across the codebase
grep -rlZ "org.kevinkib.cardgames.presentation.dto.\(BatailleCorseDto\|CardDto\|PileDto\|PlayerDto\|PlayerIdDto\|BatailleCorseIdDto\)" backend/src --include=*.java \
  | xargs -0 sed -i -E 's#org\.kevinkib\.cardgames\.presentation\.dto\.(BatailleCorseDto|CardDto|PileDto|PlayerDto|PlayerIdDto|BatailleCorseIdDto)#org.kevinkib.cardgames.bataillecorse.presentation.dto.\1#g'
```

- [ ] **Step 2: Same-package fallout**

The moved DTOs referenced each other via the same package (e.g. `BatailleCorseDto` uses `PlayerDto`, `PileDto`, `CardDto`; `PileDto` uses `CardDto`). Since they all moved together into the same new package, no new imports are needed between them. But the **shared** event data and DTOs that used the moved types now need imports: `JoinEventData`/`SendEventData`/`SlapEventData`/`GrabEventData`/`CreateEventData`/`RematchEventData` (still in shared `dto/event` at this point) reference `PlayerIdDto`/`BatailleCorseIdDto`/`SeatDto`. The `grep`+`sed` above already repointed their imports. Verify the still-shared `SessionViewDto`/`JoinEventData` references to `SeatDto` (which is staying shared) are untouched — `SeatDto` was not in the move list, so its FQN is unchanged.

- [ ] **Step 3: Compile**
```bash
grep -rn "presentation\.dto\.\(BatailleCorseDto\|CardDto\|PileDto\|PlayerDto\|PlayerIdDto\|BatailleCorseIdDto\)" backend/src --include=*.java || echo "CLEAN"
mvn -f backend/pom.xml test-compile
```
Expected: `CLEAN`, BUILD SUCCESS.

- [ ] **Step 4: Run full clean suite, 189 green. Commit.**
```bash
mvn -f backend/pom.xml clean test
git add -A
git commit -m "refactor(presentation): move BatailleCorse game-state DTOs to bataillecorse.presentation.dto"
```

### Task 5.3: Move BatailleCorse event data + `BatailleCorseEventType`

**Files:** move `CreateEventData`, `JoinEventData`, `RematchEventData`, `SendEventData`, `SlapEventData`, `GrabEventData`, `BatailleCorseEventType` to `bataillecorse/presentation/dto/event/`.

- [ ] **Step 1: Move and repackage**
```bash
cd "$(git rev-parse --show-toplevel)"
SRC=backend/src/main/java/org/kevinkib/cardgames/presentation/dto/event
DST=backend/src/main/java/org/kevinkib/cardgames/bataillecorse/presentation/dto/event
mkdir -p "$DST"
for f in CreateEventData JoinEventData RematchEventData SendEventData SlapEventData GrabEventData BatailleCorseEventType; do
  git mv "$SRC/$f.java" "$DST/$f.java"
done
sed -i 's#^package org\.kevinkib\.cardgames\.presentation\.dto\.event;#package org.kevinkib.cardgames.bataillecorse.presentation.dto.event;#' "$DST"/*.java
# these moved files implement the shared EventData interface and use shared RematchStatus / SeatDto — add imports
for f in "$DST"/CreateEventData.java "$DST"/JoinEventData.java "$DST"/RematchEventData.java "$DST"/SendEventData.java "$DST"/SlapEventData.java "$DST"/GrabEventData.java; do
  sed -i '/^package /a import org.kevinkib.cardgames.presentation.dto.event.EventData;' "$f"
done
sed -i '/^package /a import org.kevinkib.cardgames.presentation.dto.event.RematchStatus;' "$DST"/RematchEventData.java
sed -i '/^package /a import org.kevinkib.cardgames.presentation.dto.SeatDto;' "$DST"/JoinEventData.java
# repoint imports of the moved event-data types across the codebase
grep -rlZ "org.kevinkib.cardgames.presentation.dto.event.\(CreateEventData\|JoinEventData\|RematchEventData\|SendEventData\|SlapEventData\|GrabEventData\|BatailleCorseEventType\)" backend/src --include=*.java \
  | xargs -0 sed -i -E 's#org\.kevinkib\.cardgames\.presentation\.dto\.event\.(CreateEventData|JoinEventData|RematchEventData|SendEventData|SlapEventData|GrabEventData|BatailleCorseEventType)#org.kevinkib.cardgames.bataillecorse.presentation.dto.event.\1#g'
```

- [ ] **Step 2: Verify imports compile** — some moved files may now have a duplicate or unused import (e.g. if a file already imported `EventData` via FQN). Compile and fix any duplicate-import errors reported:
```bash
mvn -f backend/pom.xml test-compile
```
Expected: BUILD SUCCESS. If a "duplicate import" or "cannot find symbol" appears, open the named file and correct that single import line.

- [ ] **Step 3: Full clean suite green. Commit.**
```bash
mvn -f backend/pom.xml clean test
git add -A
git commit -m "refactor(presentation): move BatailleCorse event data + BatailleCorseEventType"
```

### Task 5.4: Move the BatailleCorse controllers + broadcaster; extract shared `LifecycleController` and `SessionRestController`

**Files:**
- Move: `presentation/BatailleCorseWebSocketController.java` → `bataillecorse/presentation/`
- Move: `presentation/BatailleCorseLifecycleBroadcaster.java` → `bataillecorse/presentation/`
- Move + rename: `presentation/GameRestController.java` → `bataillecorse/presentation/BatailleCorseRestController.java`
- Create: `presentation/LifecycleController.java` (shared; `/presence`, `/forfeit`)
- Create: `presentation/SessionRestController.java` (shared; `GET /api/game/{id}/session`)
- Modify: `config/AppConfig.java` imports

- [ ] **Step 1: Extract `/presence` and `/forfeit` into a shared `LifecycleController`**

Create `presentation/LifecycleController.java` with the two handlers currently in `BatailleCorseWebSocketController` (they are pure delegation — copy their bodies verbatim):
```java
package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.application.InvalidTokenException;
import org.kevinkib.cardgames.sessionmanagement.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionToken;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.PresencePayload;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class LifecycleController {

    private final SessionService sessionService;
    private final DisconnectForfeitService disconnectForfeitService;

    public LifecycleController(SessionService sessionService, DisconnectForfeitService disconnectForfeitService) {
        this.sessionService = sessionService;
        this.disconnectForfeitService = disconnectForfeitService;
    }

    @MessageMapping("/presence")
    public void presence(@Payload PresencePayload payload, SimpMessageHeaderAccessor headers) {
        GameId gameId = new GameId(payload.gameId());
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
        GameId gameId = new GameId(payload.gameId());
        try {
            PlayerId playerId = sessionService
                    .findPlayerIdByToken(gameId, new SessionToken(payload.token()))
                    .orElseThrow(InvalidTokenException::new);
            disconnectForfeitService.forfeit(new Seat(gameId, playerId), ForfeitReason.RESIGNED);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
}
```
Delete the `presence(...)` and `forfeit(...)` methods (and any now-unused imports) from `BatailleCorseWebSocketController`.

- [ ] **Step 2: Move the BatailleCorse WS controller + broadcaster**
```bash
cd "$(git rev-parse --show-toplevel)"
SRC=backend/src/main/java/org/kevinkib/cardgames/presentation
DST=backend/src/main/java/org/kevinkib/cardgames/bataillecorse/presentation
mkdir -p "$DST"
git mv "$SRC/BatailleCorseWebSocketController.java" "$DST/BatailleCorseWebSocketController.java"
git mv "$SRC/BatailleCorseLifecycleBroadcaster.java" "$DST/BatailleCorseLifecycleBroadcaster.java"
sed -i 's#^package org\.kevinkib\.cardgames\.presentation;#package org.kevinkib.cardgames.bataillecorse.presentation;#' \
  "$DST/BatailleCorseWebSocketController.java" "$DST/BatailleCorseLifecycleBroadcaster.java"
```
Both now live outside `presentation` but use shared types (`GameMessagingService`, `DisconnectForfeitService`, `Seat`, `ForfeitReason`, `ForfeitReasonRegistry`, `GameLifecycleBroadcaster`, `api.*`, shared `dto.event.*`). Add the needed shared imports to each. Required shared imports for `BatailleCorseWebSocketController`: `GameMessagingService`, `Seat` (if used), `api.*` payloads/responses already imported via `presentation.api.*` and `presentation.dto.event.*` — convert those still-shared ones (e.g. `EmptyEventData` via `EventData`) by adding explicit imports. Required for `BatailleCorseLifecycleBroadcaster`: `org.kevinkib.cardgames.presentation.GameLifecycleBroadcaster`, `GameMessagingService`, `Seat`, `ForfeitReason`, `ForfeitReasonRegistry`.

- [ ] **Step 3: Move + rename the REST controller; extract shared `SessionRestController`**

```bash
git mv backend/src/main/java/org/kevinkib/cardgames/presentation/GameRestController.java \
       backend/src/main/java/org/kevinkib/cardgames/bataillecorse/presentation/BatailleCorseRestController.java
sed -i -e 's#^package org\.kevinkib\.cardgames\.presentation;#package org.kevinkib.cardgames.bataillecorse.presentation;#' \
       -e 's#class GameRestController#class BatailleCorseRestController#' \
       -e 's#public GameRestController#public BatailleCorseRestController#' \
       backend/src/main/java/org/kevinkib/cardgames/bataillecorse/presentation/BatailleCorseRestController.java
```
Then **remove** the `getSession` (`GET /api/game/{id}/session`) handler from `BatailleCorseRestController` (it is game-agnostic) and put it in a new shared `presentation/SessionRestController.java`:
```java
package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.application.InvalidGameIdException;
import org.kevinkib.cardgames.sessionmanagement.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionPlayer;
import org.kevinkib.cardgames.presentation.dto.SessionViewDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SessionRestController {

    private final SessionService sessionService;

    public SessionRestController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/game/{id}/session")
    public ResponseEntity<SessionViewDto> getSession(@PathVariable String id) {
        try {
            GameId gameId = new GameId(id);
            List<SessionPlayer> seats = sessionService.getSeats(gameId);
            return ResponseEntity.ok(SessionViewDto.from(seats));
        } catch (InvalidGameIdException | IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
```
`BatailleCorseRestController` keeps `GET /api/game/{id}` (state) and `POST /api/game/{id}/join`; add its needed shared imports (`SessionViewDto`, `JoinResponseDto`, `JoinEventData` now in `bataillecorse.presentation.dto.event`, `api.*`).

- [ ] **Step 4: Update `AppConfig` imports**

`AppConfig` references `BatailleCorseLifecycleBroadcaster` (now in `bataillecorse.presentation`) — update its import. No other AppConfig change (controllers are component-scanned, not beans).

- [ ] **Step 5: Compile, fixing imports iteratively**
```bash
mvn -f backend/pom.xml test-compile 2>&1 | grep -E "ERROR.*\.java:|BUILD" | head -40
```
Resolve each "cannot find symbol"/"package does not exist" by adding the correct shared or `bataillecorse.presentation` import to the named file. Repeat until BUILD SUCCESS.

- [ ] **Step 6: Move the corresponding tests**

Move BatailleCorse-specific presentation tests to `bataillecorse/presentation` test package and repackage; leave shared-plumbing tests in `presentation`:
```bash
cd "$(git rev-parse --show-toplevel)"
TSRC=backend/src/test/java/org/kevinkib/cardgames/presentation
TDST=backend/src/test/java/org/kevinkib/cardgames/bataillecorse/presentation
TDSTDTO=$TDST/dto
mkdir -p "$TDST" "$TDSTDTO"
for f in BatailleCorseWebSocketController BatailleCorseControllerIT BatailleCorseWebSocketControllerIT GameRestControllerIT GameRestControllerTest; do
  git mv "$TSRC/$f.java" "$TDST/$f.java" 2>/dev/null || true
done
for f in BatailleCorseDtoTest PlayerDtoTest; do
  git mv "$TSRC/dto/$f.java" "$TDSTDTO/$f.java" 2>/dev/null || true
done
# repackage moved test files
sed -i 's#^package org\.kevinkib\.cardgames\.presentation;#package org.kevinkib.cardgames.bataillecorse.presentation;#' "$TDST"/*.java
sed -i 's#^package org\.kevinkib\.cardgames\.presentation\.dto;#package org.kevinkib.cardgames.bataillecorse.presentation.dto;#' "$TDSTDTO"/*.java
```
Rename `GameRestControllerTest`/`GameRestControllerIT` classes to `BatailleCorseRestControllerTest`/`...IT` if you renamed the files; otherwise keep file/class names aligned. Add imports in the moved tests for any shared types they reference (`api.*`, shared `dto.event.*`, `presentation.DisconnectForfeitService`, `presentation.GameMessagingService`, `presentation.StompSessionSeatRegistry`, `presentation.Seat`, `presentation.ForfeitReason*`, `presentation.GameLifecycleBroadcasters`, `presentation.BatailleCorse...`→ now `bataillecorse.presentation.*`). The `DisconnectForfeitServiceTest` stays in `presentation` but references `BatailleCorseLifecycleBroadcaster` (now in `bataillecorse.presentation`) and `BatailleCorseDto` — add those imports.

- [ ] **Step 7: Final clean full suite — the real gate**
```bash
mvn -f backend/pom.xml clean test
```
Expected: BUILD SUCCESS, `Tests run: 189+` (the new Phase-2/3 tests added a few), 0 failures. The `*IT` tests confirm the moved controllers are component-scanned and broadcast identically.

- [ ] **Step 8: Verify no `(BatailleCorse)` cast leaks outside `bataillecorse.presentation`**
```bash
grep -rn "(BatailleCorse)" backend/src/main/java/org/kevinkib/cardgames/presentation --include=*.java || echo "SHARED CLEAN"
```
Expected: `SHARED CLEAN` (any remaining `(BatailleCorse)` casts are inside `bataillecorse.presentation`, which is allowed).

- [ ] **Step 9: Commit**
```bash
git add -A
git commit -m "refactor(presentation): move BatailleCorse controllers/broadcaster; extract shared LifecycleController + SessionRestController"
```

### Task 5.5: Add the new focused tests + final gate + MR

**Files:**
- Create: `presentation/GameLifecycleBroadcastersTest.java`
- Create: `bataillecorse/presentation/BatailleCorseLifecycleBroadcasterTest.java`

- [ ] **Step 1: `GameLifecycleBroadcastersTest` (shared, uses `FakeGame`)**
```java
package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.game.FakeGame;
import org.kevinkib.cardgames.game.GameId;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameLifecycleBroadcastersTest {

    private final GameLifecycleBroadcaster bcBroadcaster =
            new org.kevinkib.cardgames.bataillecorse.presentation.BatailleCorseLifecycleBroadcaster(null, null);
    private final GameLifecycleBroadcasters broadcasters =
            new GameLifecycleBroadcasters(List.of(bcBroadcaster));

    @Test
    void givenBatailleCorse_whenResolve_thenReturnsBatailleCorseBroadcaster() {
        BatailleCorse game = new BatailleCorse(GameId.generate(), 2);

        assertThat(broadcasters.broadcasterFor(game), sameInstance(bcBroadcaster));
    }

    @Test
    void givenUnsupportedGame_whenResolve_thenThrows() {
        FakeGame game = new FakeGame(GameId.generate(), 2);

        assertThrows(IllegalStateException.class, () -> broadcasters.broadcasterFor(game));
    }
}
```

- [ ] **Step 2: Run, expect PASS** — `mvn -f backend/pom.xml test -Dtest=GameLifecycleBroadcastersTest`.

- [ ] **Step 3: `BatailleCorseLifecycleBroadcasterTest` (asserts the broadcast it sends)**

Use a recording `GameMessagingService` double (hand-rolled, no Mockito) capturing `sendToGame` payloads, and a real `ForfeitReasonRegistry`. Assert `forfeited(...)` sends a `SuccessResponse` with `getEventType()` `"FORFEIT"` and a `BatailleCorseDto` state.
```java
package org.kevinkib.cardgames.bataillecorse.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.BatailleCorseDto;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.ForfeitReason;
import org.kevinkib.cardgames.presentation.ForfeitReasonRegistry;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.Seat;
import org.kevinkib.cardgames.presentation.api.Response;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class BatailleCorseLifecycleBroadcasterTest {

    private Object lastPayload;

    private final GameMessagingService messaging = new GameMessagingService(new SimpMessagingTemplate(
            (message, timeout) -> true) {
        @Override
        public void convertAndSend(String destination, Object payload) {
            lastPayload = payload;
        }
    });
    private final ForfeitReasonRegistry reasons = new ForfeitReasonRegistry();
    private final BatailleCorseLifecycleBroadcaster broadcaster =
            new BatailleCorseLifecycleBroadcaster(messaging, reasons);

    @Test
    void givenForfeit_whenBroadcast_thenSendsForfeitResponseWithBatailleCorseState() {
        BatailleCorse game = new BatailleCorse(GameId.generate(), 2);
        Seat seat = new Seat(game.getId(), new PlayerId(0));
        reasons.record(seat, ForfeitReason.RESIGNED);

        broadcaster.forfeited(game, seat, ForfeitReason.RESIGNED);

        assertThat(lastPayload, instanceOf(Response.class));
        Response response = (Response) lastPayload;
        assertThat(response.getEventType(), is("FORFEIT"));
        assertThat(response.getState(), instanceOf(BatailleCorseDto.class));
    }
}
```
(If `SimpMessagingTemplate` cannot be subclassed cleanly with a lambda `MessageChannel`, instead pass a hand-written `MessageChannel` stub returning `true` from `send`. Adjust to whatever compiles; the assertion target is the captured payload.)

- [ ] **Step 4: Run, expect PASS.**

- [ ] **Step 5: Final clean full suite**
```bash
mvn -f backend/pom.xml clean test
```
Expected: BUILD SUCCESS, all green.

- [ ] **Step 6: Commit, push, open MR**
```bash
git add -A
git commit -m "test(presentation): cover lifecycle broadcaster resolver + BatailleCorse broadcaster"
git push -u origin <branch>
```
Open a merge request titled `refactor: BatailleCorse presentation split (Slice 2b-i)`. Body: shared game-agnostic plumbing vs `bataillecorse.presentation`; the lifecycle verb-seam (`GameLifecycleBroadcaster`) with no rendering abstraction; `Application` moved to the root package for component scan; typed `getGame`; all transitional casts gone from shared code; wire format unchanged; full suite green including the context-booting `*IT` tests.

---

## Self-review notes

- **Spec coverage:** shared plumbing list (Task 5.x) ✓; generic `Response`/`Object` state + `String` eventType (Task 1.1) ✓; `LifecycleEventType`/`BatailleCorseEventType` split (Task 4.1) ✓; lifecycle verb-seam `GameLifecycleBroadcaster` + resolver + BC impl, `DisconnectForfeitService` delegates (Task 3.1) ✓; `LifecycleController` presence/forfeit (Task 5.4) ✓; BatailleCorse DTOs/event data/controllers moved (Tasks 5.2–5.4) ✓; typed `SessionService.getGame` + cast removal (Phase 2, Task 5.8 grep) ✓; `Application` move for component scan (Task 5.1) ✓; new focused tests (Task 5.5) ✓; session DTOs stay shared + `SessionRestController` (Task 5.4) ✓.
- **No rendering abstraction** introduced (spec's explicit exclusion) — confirmed: only the verb-seam exists.
- **Type consistency:** `GameLifecycleBroadcaster.{supports,disconnected,reconnected,forfeited}`, `GameLifecycleBroadcasters.broadcasterFor`, `SessionService.getGame(GameId, Class<T>)`, `Response(boolean, String, EventData, String, Object)`, `LifecycleEventType`/`BatailleCorseEventType` used consistently across tasks.
- **Wire format unchanged:** event strings preserved (enum `name()` values identical); `state` still serialises the same `BatailleCorseDto`. IT assertions on `getEventType()` strings unaffected; `getState()` casts added where needed.
- **Safety net:** every phase ends on a clean full run; Phase 5 leans on `git mv`+`sed` (mechanical, like Slice 2a) with the context-booting `*IT` tests as the integration gate.
- **Known soft spots flagged for the implementer:** the `BatailleCorseLifecycleBroadcasterTest` messaging double may need a hand-written `MessageChannel` stub (Step 3 note); event-data moves may surface duplicate-import warnings to clean up (Task 5.3 Step 2).

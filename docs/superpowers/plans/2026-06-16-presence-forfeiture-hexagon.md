# Presence/Forfeiture Hexagon Extraction — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the hidden presence/forfeiture concern out of the top-level `presentation` package into a properly layered `sessionmanagement.presence` sub-module (domain/application/port/infrastructure), turn its two mutable registries into repositories behind ports, and invert the lifecycle-broadcast seam so dependencies always point inward to `sessionmanagement`.

**Architecture:** This is a **relocation refactor**, not new behavior. Each task moves one cohesive group of symbols in dependency order and must leave the whole module compiling and the full test suite green — that green suite is the safety net (there is no "write a failing test first" except the one test that must be rewritten because a port replaces Spring's `TaskScheduler`). Behavior is identical throughout; only packages, type names, and the broadcaster-seam direction change.

**Tech Stack:** Java 17, Spring Boot, JUnit 5 + Hamcrest, Maven (IntelliJ-bundled `mvn`, no wrapper).

**Source spec:** `docs/superpowers/specs/2026-06-16-presence-forfeiture-hexagon-design.md`

**Verification (from project memory):** run Maven from `backend/`. There is no `./mvnw`; the IntelliJ-bundled mvn is at `C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd`. The full-suite gate is `mvn test` (currently green at 265 tests).

---

## Target package layout (end state)

```
sessionmanagement/presence/
  domain/            Seat, ForfeitReason
  application/       PresenceService, GameLifecycleBroadcasters
  port/              ConnectionRegistry, ForfeitLog, ForfeitScheduler, ScheduledForfeit, GameLifecycleBroadcaster
  infrastructure/    InMemoryConnectionRegistry, InMemoryForfeitLog, TaskSchedulerForfeitScheduler
```

Stays in `presentation`: `LifecycleController`, `WebSocketDisconnectListener`, `SeatSubscriptionInterceptor`, `WebSocketConfiguration`, `GameMessagingService`, `LobbyBroadcaster`, `api/*`, `dto/*`. Per-game `*.presentation` keeps `BatailleCorseLifecycleBroadcaster` / `BullshitLifecycleBroadcaster` (now implementing the relocated port).

## Symbol renames (reference for every task)

| Old (in `presentation`) | New |
|---|---|
| `Seat` | `sessionmanagement.presence.domain.Seat` |
| `ForfeitReason` | `sessionmanagement.presence.domain.ForfeitReason` |
| `StompSessionSeatRegistry` | port `…presence.port.ConnectionRegistry` + `…presence.infrastructure.InMemoryConnectionRegistry` |
| `ForfeitReasonRegistry` | port `…presence.port.ForfeitLog` + `…presence.infrastructure.InMemoryForfeitLog` |
| (injected `TaskScheduler`+`Clock`) | port `…presence.port.ForfeitScheduler` (+ `ScheduledForfeit`) + `…presence.infrastructure.TaskSchedulerForfeitScheduler` |
| `GameLifecycleBroadcaster` (interface) | `…presence.port.GameLifecycleBroadcaster` |
| `GameLifecycleBroadcasters` (resolver) | `…presence.application.GameLifecycleBroadcasters` |
| `DisconnectForfeitService` | `…presence.application.PresenceService` |

---

### Task 1: Move `Seat` and `ForfeitReason` into `presence.domain`

**Files:**
- Create: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/presence/domain/Seat.java`
- Create: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/presence/domain/ForfeitReason.java`
- Delete: `backend/src/main/java/org/kevinkib/cardgames/presentation/Seat.java`, `backend/src/main/java/org/kevinkib/cardgames/presentation/ForfeitReason.java`
- Modify (imports): `presentation/DisconnectForfeitService.java`, `presentation/StompSessionSeatRegistry.java`, `presentation/ForfeitReasonRegistry.java`, `presentation/LifecycleController.java`, `presentation/GameLifecycleBroadcaster.java`, `bataillecorse/presentation/BatailleCorseLifecycleBroadcaster.java`, `bullshit/presentation/BullshitLifecycleBroadcaster.java`, `bataillecorse/presentation/BatailleCorseRestController.java`, `sessionmanagement/application/GameCleanupService.java`
- Modify (test imports): `presentation/DisconnectForfeitServiceTest.java`, `presentation/StompSessionSeatRegistryTest.java`, `presentation/ForfeitReasonRegistryTest.java`, `sessionmanagement/application/GameCleanupServiceTest.java`, `bataillecorse/presentation/BatailleCorseLifecycleBroadcasterTest.java`

- [ ] **Step 1: Create the two domain types in the new package**

`presence/domain/Seat.java`:
```java
package org.kevinkib.cardgames.sessionmanagement.presence.domain;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

public record Seat(GameId gameId, PlayerId playerId) {
}
```

`presence/domain/ForfeitReason.java`:
```java
package org.kevinkib.cardgames.sessionmanagement.presence.domain;

/**
 * Why a seat left the game, as classified by the session layer.
 * The card-game domain never sees this — to it, both are simply a concede.
 */
public enum ForfeitReason {
    RESIGNED,
    DISCONNECTED
}
```

- [ ] **Step 2: Delete the old files**

```bash
git rm backend/src/main/java/org/kevinkib/cardgames/presentation/Seat.java \
       backend/src/main/java/org/kevinkib/cardgames/presentation/ForfeitReason.java
```

- [ ] **Step 3: Fix every reference**

Add `import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;` and/or `import org.kevinkib.cardgames.sessionmanagement.presence.domain.ForfeitReason;` to each file below. Files that previously relied on the same-package `Seat`/`ForfeitReason` (the `presentation` package) or on `import org.kevinkib.cardgames.presentation.*;` now need explicit imports.

- `presentation/DisconnectForfeitService.java` — uses both. Add both imports.
- `presentation/StompSessionSeatRegistry.java` — uses `Seat`. Add Seat import.
- `presentation/ForfeitReasonRegistry.java` — uses both. Add both imports.
- `presentation/LifecycleController.java` — uses both (`new Seat(...)`, `ForfeitReason.RESIGNED`). Add both imports.
- `presentation/GameLifecycleBroadcaster.java` — signatures use both. Add both imports.
- `bataillecorse/presentation/BatailleCorseLifecycleBroadcaster.java` — has `import org.kevinkib.cardgames.presentation.*;` which no longer covers them. Add both explicit imports.
- `bullshit/presentation/BullshitLifecycleBroadcaster.java` — replace `import org.kevinkib.cardgames.presentation.ForfeitReason;` → `…presence.domain.ForfeitReason;` and `import org.kevinkib.cardgames.presentation.Seat;` → `…presence.domain.Seat;` (keep its `import …presentation.GameLifecycleBroadcaster;` for now — moved in Task 5).
- `bataillecorse/presentation/BatailleCorseRestController.java` — has `import …presentation.*;`. It does not name `Seat` directly but `ForfeitReasonRegistry.reasonsBySeat` returns `Map<Integer,ForfeitReason>` and uses no `Seat` literal; add `import …presence.domain.ForfeitReason;` only if the file references `ForfeitReason` by name (it does not currently — skip unless the compiler asks).
- `sessionmanagement/application/GameCleanupService.java` — references `Seat`? No (only the registries). No change here in this task.

Tests:
- `presentation/DisconnectForfeitServiceTest.java` — uses `new Seat(...)`, `ForfeitReason.RESIGNED`. Add both imports.
- `presentation/StompSessionSeatRegistryTest.java` — uses `new Seat(...)`. Add Seat import.
- `presentation/ForfeitReasonRegistryTest.java` — uses `new Seat(...)`, `ForfeitReason.*`. Add both imports.
- `sessionmanagement/application/GameCleanupServiceTest.java` — uses fully-qualified `new org.kevinkib.cardgames.presentation.Seat(...)` on line ~50. Change that FQN to `new org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat(...)`.
- `bataillecorse/presentation/BatailleCorseLifecycleBroadcasterTest.java` — if it constructs `Seat`/uses `ForfeitReason`, add the new imports (check the file and add as needed).

- [ ] **Step 4: Compile + full suite**

Run from `backend/`: `mvn test`
Expected: BUILD SUCCESS, 265 tests pass. (If a `presentation.*` wildcard file fails to resolve `Seat`/`ForfeitReason`, add the explicit import there.)

- [ ] **Step 5: Commit**

```bash
git add -A backend/
git commit -m "refactor(session): move Seat/ForfeitReason into presence.domain"
```

---

### Task 2: Introduce `ConnectionRegistry` port + `InMemoryConnectionRegistry` adapter

**Files:**
- Create: `…/sessionmanagement/presence/port/ConnectionRegistry.java`
- Create: `…/sessionmanagement/presence/infrastructure/InMemoryConnectionRegistry.java`
- Delete: `presentation/StompSessionSeatRegistry.java`
- Modify: `presentation/DisconnectForfeitService.java`, `sessionmanagement/application/GameCleanupService.java`, `config/AppConfig.java`
- Move test: `presentation/StompSessionSeatRegistryTest.java` → `…/presence/infrastructure/InMemoryConnectionRegistryTest.java`
- Modify test: `sessionmanagement/application/GameCleanupServiceTest.java`

- [ ] **Step 1: Create the port**

`presence/port/ConnectionRegistry.java`:
```java
package org.kevinkib.cardgames.sessionmanagement.presence.port;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;

import java.util.Optional;

/** Tracks which transport connection currently holds which seat. */
public interface ConnectionRegistry {
    void bind(String connectionId, Seat seat);
    Optional<Seat> seatOf(String connectionId);
    Optional<Seat> unbind(String connectionId);
    void removeGame(GameId gameId);
}
```

- [ ] **Step 2: Create the in-memory adapter (body identical to the old registry)**

`presence/infrastructure/InMemoryConnectionRegistry.java`:
```java
package org.kevinkib.cardgames.sessionmanagement.presence.infrastructure;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ConnectionRegistry;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryConnectionRegistry implements ConnectionRegistry {

    private final Map<String, Seat> seatByConnection = new ConcurrentHashMap<>();

    @Override
    public void bind(String connectionId, Seat seat) {
        seatByConnection.put(connectionId, seat);
    }

    @Override
    public Optional<Seat> seatOf(String connectionId) {
        return Optional.ofNullable(seatByConnection.get(connectionId));
    }

    @Override
    public Optional<Seat> unbind(String connectionId) {
        return Optional.ofNullable(seatByConnection.remove(connectionId));
    }

    @Override
    public void removeGame(GameId gameId) {
        seatByConnection.values().removeIf(seat -> seat.gameId().equals(gameId));
    }
}
```

- [ ] **Step 3: Delete the old registry**

```bash
git rm backend/src/main/java/org/kevinkib/cardgames/presentation/StompSessionSeatRegistry.java
```

- [ ] **Step 4: Update consumers to the port type**

- `presentation/DisconnectForfeitService.java`: change field/ctor param type `StompSessionSeatRegistry registry` → `ConnectionRegistry registry`; remove `import …presentation.StompSessionSeatRegistry` (same-package, none) and add `import org.kevinkib.cardgames.sessionmanagement.presence.port.ConnectionRegistry;`. The method calls (`bind`/`unbind`) are unchanged.
- `sessionmanagement/application/GameCleanupService.java`: change field/ctor param `StompSessionSeatRegistry presenceRegistry` → `ConnectionRegistry presenceRegistry`; replace `import …presentation.StompSessionSeatRegistry;` → `import …presence.port.ConnectionRegistry;`. `evicted.forEach(presenceRegistry::removeGame)` unchanged.

- [ ] **Step 5: Update `AppConfig` bean**

In `config/AppConfig.java`: replace the `stompSessionSeatRegistry()` bean with:
```java
    @Bean
    public ConnectionRegistry connectionRegistry() {
        return new InMemoryConnectionRegistry();
    }
```
Update imports: remove `import …presentation.StompSessionSeatRegistry;`, add `import …presence.port.ConnectionRegistry;` and `import …presence.infrastructure.InMemoryConnectionRegistry;`. Update the two call sites `stompSessionSeatRegistry()` → `connectionRegistry()` (in `disconnectForfeitService(...)` and `gameCleanupService()`).

- [ ] **Step 6: Move + rename the registry test**

Create `…/presence/infrastructure/InMemoryConnectionRegistryTest.java` with the body of `StompSessionSeatRegistryTest`, but: package `…presence.infrastructure`; `import …presence.domain.Seat;`; `import …presence.port.ConnectionRegistry;`; class name `InMemoryConnectionRegistryTest`; every `new StompSessionSeatRegistry()` → `new InMemoryConnectionRegistry()` (declare as `ConnectionRegistry registry = new InMemoryConnectionRegistry();`). Then:
```bash
git rm backend/src/test/java/org/kevinkib/cardgames/presentation/StompSessionSeatRegistryTest.java
```

- [ ] **Step 7: Update `GameCleanupServiceTest`**

In `sessionmanagement/application/GameCleanupServiceTest.java`: replace `import …presentation.StompSessionSeatRegistry;` → `import …presence.infrastructure.InMemoryConnectionRegistry;`, and every `new StompSessionSeatRegistry()` → `new InMemoryConnectionRegistry()`. (The `new …presence.domain.Seat(...)` from Task 1 stays.)

- [ ] **Step 8: Compile + full suite**

Run: `mvn test` → BUILD SUCCESS, 265 tests.

- [ ] **Step 9: Commit**

```bash
git add -A backend/
git commit -m "refactor(session): ConnectionRegistry port + InMemoryConnectionRegistry adapter"
```

---

### Task 3: Introduce `ForfeitLog` port + `InMemoryForfeitLog` adapter

**Files:**
- Create: `…/presence/port/ForfeitLog.java`, `…/presence/infrastructure/InMemoryForfeitLog.java`
- Delete: `presentation/ForfeitReasonRegistry.java`
- Modify: `presentation/DisconnectForfeitService.java`, `bataillecorse/presentation/BatailleCorseLifecycleBroadcaster.java`, `bataillecorse/presentation/BatailleCorseRestController.java`, `sessionmanagement/application/GameCleanupService.java`, `config/AppConfig.java`
- Move test: `presentation/ForfeitReasonRegistryTest.java` → `…/presence/infrastructure/InMemoryForfeitLogTest.java`
- Modify tests: `presentation/DisconnectForfeitServiceTest.java`, `sessionmanagement/application/GameCleanupServiceTest.java`, `bataillecorse/presentation/BatailleCorseLifecycleBroadcasterTest.java`

- [ ] **Step 1: Create the port**

`presence/port/ForfeitLog.java`:
```java
package org.kevinkib.cardgames.sessionmanagement.presence.port;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;

import java.util.Map;

/** Records which seat forfeited a game and why, so the reason can be merged into game state. */
public interface ForfeitLog {
    void record(Seat seat, ForfeitReason reason);
    Map<Integer, ForfeitReason> reasonsBySeat(GameId gameId);
    void removeGame(GameId gameId);
}
```

- [ ] **Step 2: Create the in-memory adapter (body identical to the old registry)**

`presence/infrastructure/InMemoryForfeitLog.java`:
```java
package org.kevinkib.cardgames.sessionmanagement.presence.infrastructure;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitLog;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryForfeitLog implements ForfeitLog {

    private final Map<Seat, ForfeitReason> reasonBySeat = new ConcurrentHashMap<>();

    @Override
    public void record(Seat seat, ForfeitReason reason) {
        reasonBySeat.put(seat, reason);
    }

    @Override
    public Map<Integer, ForfeitReason> reasonsBySeat(GameId gameId) {
        Map<Integer, ForfeitReason> result = new HashMap<>();
        reasonBySeat.forEach((seat, reason) -> {
            if (seat.gameId().equals(gameId)) {
                result.put(seat.playerId().id(), reason);
            }
        });
        return result;
    }

    @Override
    public void removeGame(GameId gameId) {
        reasonBySeat.keySet().removeIf(seat -> seat.gameId().equals(gameId));
    }
}
```

- [ ] **Step 3: Delete the old registry**

```bash
git rm backend/src/main/java/org/kevinkib/cardgames/presentation/ForfeitReasonRegistry.java
```

- [ ] **Step 4: Update consumers to the port type**

- `presentation/DisconnectForfeitService.java`: field/ctor param `ForfeitReasonRegistry forfeitReasonRegistry` → `ForfeitLog forfeitLog` (rename the field too); add `import …presence.port.ForfeitLog;`. Update the call `forfeitReasonRegistry.record(...)` → `forfeitLog.record(...)`.
- `bataillecorse/presentation/BatailleCorseLifecycleBroadcaster.java`: field/ctor param `ForfeitReasonRegistry forfeitReasonRegistry` → `ForfeitLog forfeitLog`; add `import …presence.port.ForfeitLog;` (the `presentation.*` wildcard no longer covers it). Update `forfeitReasonRegistry.reasonsBySeat(...)` → `forfeitLog.reasonsBySeat(...)`.
- `bataillecorse/presentation/BatailleCorseRestController.java`: field/ctor param `ForfeitReasonRegistry forfeitReasonRegistry` → `ForfeitLog forfeitLog`; add `import …presence.port.ForfeitLog;`. Update the `reasonsBySeat` call.
- `sessionmanagement/application/GameCleanupService.java`: field/ctor param `ForfeitReasonRegistry forfeitReasonRegistry` → `ForfeitLog forfeitLog`; replace import; `evicted.forEach(forfeitLog::removeGame)`.

- [ ] **Step 5: Update `AppConfig`**

Replace the `forfeitReasonRegistry()` bean:
```java
    @Bean
    public ForfeitLog forfeitLog() {
        return new InMemoryForfeitLog();
    }
```
Imports: remove `import …presentation.ForfeitReasonRegistry;`; add `import …presence.port.ForfeitLog;`, `import …presence.infrastructure.InMemoryForfeitLog;`. Update call sites `forfeitReasonRegistry()` → `forfeitLog()` in `batailleCorseLifecycleBroadcaster(...)`, `disconnectForfeitService(...)`, `gameCleanupService()`.

- [ ] **Step 6: Move + rename the registry test**

Create `…/presence/infrastructure/InMemoryForfeitLogTest.java` with the body of `ForfeitReasonRegistryTest`: package `…presence.infrastructure`; imports `…presence.domain.ForfeitReason`, `…presence.domain.Seat`, `…presence.port.ForfeitLog`; class `InMemoryForfeitLogTest`; `private final ForfeitLog registry = new InMemoryForfeitLog();`. Then:
```bash
git rm backend/src/test/java/org/kevinkib/cardgames/presentation/ForfeitReasonRegistryTest.java
```

- [ ] **Step 7: Update remaining tests**

- `presentation/DisconnectForfeitServiceTest.java`: replace `import` of nothing (same-package) — it uses `new ForfeitReasonRegistry()` and a field `forfeitReasonRegistry`. Change to `InMemoryForfeitLog` + `import …presence.infrastructure.InMemoryForfeitLog;`; field type `ForfeitLog` (`import …presence.port.ForfeitLog;`). Pass it where the service expects `ForfeitLog`.
- `sessionmanagement/application/GameCleanupServiceTest.java`: `new ForfeitReasonRegistry()` → `new InMemoryForfeitLog()`; replace import.
- `bataillecorse/presentation/BatailleCorseLifecycleBroadcasterTest.java`: if it constructs `new ForfeitReasonRegistry()` and passes it to the broadcaster, change to `new InMemoryForfeitLog()` and update the import.

- [ ] **Step 8: Compile + full suite**

Run: `mvn test` → BUILD SUCCESS, 265 tests.

- [ ] **Step 9: Commit**

```bash
git add -A backend/
git commit -m "refactor(session): ForfeitLog port + InMemoryForfeitLog adapter"
```

---

### Task 4: Introduce `ForfeitScheduler` port + `TaskSchedulerForfeitScheduler` adapter

**Files:**
- Create: `…/presence/port/ForfeitScheduler.java`, `…/presence/port/ScheduledForfeit.java`, `…/presence/infrastructure/TaskSchedulerForfeitScheduler.java`
- Modify: `presentation/DisconnectForfeitService.java`, `config/AppConfig.java`
- Modify test: `presentation/DisconnectForfeitServiceTest.java` (replace the `TaskScheduler` fake with a `ForfeitScheduler` fake)

- [ ] **Step 1: Create the port + handle**

`presence/port/ScheduledForfeit.java`:
```java
package org.kevinkib.cardgames.sessionmanagement.presence.port;

/** Handle to a scheduled forfeit; cancelling it prevents the forfeit from running. */
public interface ScheduledForfeit {
    void cancel();
}
```

`presence/port/ForfeitScheduler.java`:
```java
package org.kevinkib.cardgames.sessionmanagement.presence.port;

import java.time.Instant;

/** Schedules a forfeit to run at a deadline, returning a cancellable handle. */
public interface ForfeitScheduler {
    ScheduledForfeit schedule(Instant deadline, Runnable task);
}
```

- [ ] **Step 2: Create the Spring-backed adapter**

`presence/infrastructure/TaskSchedulerForfeitScheduler.java`:
```java
package org.kevinkib.cardgames.sessionmanagement.presence.infrastructure;

import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitScheduler;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ScheduledForfeit;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

public class TaskSchedulerForfeitScheduler implements ForfeitScheduler {

    private final TaskScheduler taskScheduler;

    public TaskSchedulerForfeitScheduler(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    @Override
    public ScheduledForfeit schedule(Instant deadline, Runnable task) {
        ScheduledFuture<?> future = taskScheduler.schedule(task, deadline);
        return () -> future.cancel(false);
    }
}
```

- [ ] **Step 3: Rewire `DisconnectForfeitService` to the port**

In `presentation/DisconnectForfeitService.java`:
- Replace imports `org.springframework.scheduling.TaskScheduler` and `java.util.concurrent.ScheduledFuture` with `…presence.port.ForfeitScheduler` and `…presence.port.ScheduledForfeit`.
- Field `private final TaskScheduler scheduler;` → `private final ForfeitScheduler scheduler;`.
- Field `private final Map<Seat, ScheduledFuture<?>> pendingForfeits` → `private final Map<Seat, ScheduledForfeit> pendingForfeits`.
- Constructor param type `TaskScheduler scheduler` → `ForfeitScheduler scheduler`.
- In `onPresence`: `ScheduledFuture<?> pending = pendingForfeits.remove(seat); if (pending != null) { pending.cancel(false); …}` → `ScheduledForfeit pending = pendingForfeits.remove(seat); if (pending != null) { pending.cancel(); …}`.
- In `onDisconnect`: `ScheduledFuture<?> task = scheduler.schedule(() -> forfeit(seat, ForfeitReason.DISCONNECTED), deadline);` → `ScheduledForfeit task = scheduler.schedule(deadline, () -> forfeit(seat, ForfeitReason.DISCONNECTED));` (note the **argument order flips** to `(deadline, task)`).
- `Clock clock` stays as-is (a JDK seam; still injected directly).

- [ ] **Step 4: Update `AppConfig`**

Add a bean and inject it:
```java
    @Bean
    public ForfeitScheduler forfeitScheduler() {
        return new TaskSchedulerForfeitScheduler(taskScheduler());
    }
```
Imports: add `…presence.port.ForfeitScheduler`, `…presence.infrastructure.TaskSchedulerForfeitScheduler`. In the `disconnectForfeitService(...)` bean, replace the `taskScheduler()` argument with `forfeitScheduler()`. (Keep the `taskScheduler()` bean itself — the adapter and `@Scheduled` sweep still use it.)

- [ ] **Step 5: Rewrite the scheduler fake in `DisconnectForfeitServiceTest`**

Replace the `CapturingScheduler implements TaskScheduler` inner class (and its `FakeFuture`) with a `ForfeitScheduler` fake. Remove the now-unused imports `org.springframework.scheduling.Trigger`, `org.springframework.scheduling.TaskScheduler`, `java.util.concurrent.ScheduledFuture`. Add `import …presence.port.ForfeitScheduler;`, `import …presence.port.ScheduledForfeit;`.
```java
    /** Captures scheduled forfeits (last one, plus all in order); run() invokes them; cancel flips a flag. */
    private static final class CapturingScheduler implements ForfeitScheduler {
        Runnable lastTask;
        Instant lastTime;
        boolean cancelled;
        final List<Runnable> scheduled = new ArrayList<>();

        @Override
        public ScheduledForfeit schedule(Instant deadline, Runnable task) {
            this.lastTask = task;
            this.lastTime = deadline;
            this.cancelled = false;
            this.scheduled.add(task);
            return () -> this.cancelled = true;
        }
    }
```
The field type stays `CapturingScheduler scheduler;` and the service is still constructed with it (the constructor now takes `ForfeitScheduler`). All assertions (`scheduler.lastTask`, `scheduler.cancelled`, `scheduler.scheduled.get(n).run()`) are unchanged.

- [ ] **Step 6: Compile + full suite**

Run: `mvn test` → BUILD SUCCESS, 265 tests.

- [ ] **Step 7: Commit**

```bash
git add -A backend/
git commit -m "refactor(session): ForfeitScheduler port replaces direct TaskScheduler use"
```

---

### Task 5: Move the `GameLifecycleBroadcaster` port + `GameLifecycleBroadcasters` resolver into `presence`

**Files:**
- Create: `…/presence/port/GameLifecycleBroadcaster.java`, `…/presence/application/GameLifecycleBroadcasters.java`
- Delete: `presentation/GameLifecycleBroadcaster.java`, `presentation/GameLifecycleBroadcasters.java`
- Modify: `presentation/DisconnectForfeitService.java`, `bataillecorse/presentation/BatailleCorseLifecycleBroadcaster.java`, `bullshit/presentation/BullshitLifecycleBroadcaster.java`, `config/AppConfig.java`
- Modify test: `presentation/DisconnectForfeitServiceTest.java` (import path of `GameLifecycleBroadcasters`)

- [ ] **Step 1: Create the port (content identical; new package + Seat/ForfeitReason imports)**

`presence/port/GameLifecycleBroadcaster.java`:
```java
package org.kevinkib.cardgames.sessionmanagement.presence.port;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;

/** Per-game broadcaster for lifecycle events. The contract names what happened, never the state shape. */
public interface GameLifecycleBroadcaster {

    boolean supports(Game game);

    void disconnected(Game game, Seat seat, long deadlineEpochMs);

    void reconnected(Game game, Seat seat);

    void forfeited(Game game, Seat seat, ForfeitReason reason);
}
```

- [ ] **Step 2: Create the resolver (content identical; new package)**

`presence/application/GameLifecycleBroadcasters.java`:
```java
package org.kevinkib.cardgames.sessionmanagement.presence.application;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;

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

- [ ] **Step 3: Delete the old interface + resolver**

```bash
git rm backend/src/main/java/org/kevinkib/cardgames/presentation/GameLifecycleBroadcaster.java \
       backend/src/main/java/org/kevinkib/cardgames/presentation/GameLifecycleBroadcasters.java
```

- [ ] **Step 4: Update references**

- `presentation/DisconnectForfeitService.java`: add `import …presence.application.GameLifecycleBroadcasters;` (field/param type unchanged name, new package).
- `bataillecorse/presentation/BatailleCorseLifecycleBroadcaster.java`: `implements GameLifecycleBroadcaster` now needs `import …presence.port.GameLifecycleBroadcaster;` (was covered by `presentation.*`). Add it.
- `bullshit/presentation/BullshitLifecycleBroadcaster.java`: replace `import …presentation.GameLifecycleBroadcaster;` → `import …presence.port.GameLifecycleBroadcaster;`.
- `config/AppConfig.java`: replace `import …presentation.GameLifecycleBroadcaster;` → `import …presence.port.GameLifecycleBroadcaster;` and `import …presentation.GameLifecycleBroadcasters;` → `import …presence.application.GameLifecycleBroadcasters;`. (Bean method signatures unchanged.)

- [ ] **Step 5: Update the service test import**

`presentation/DisconnectForfeitServiceTest.java`: add `import …presence.application.GameLifecycleBroadcasters;` (it constructs `new GameLifecycleBroadcasters(List.of(broadcaster))`).

- [ ] **Step 6: Compile + full suite**

Run: `mvn test` → BUILD SUCCESS, 265 tests.

- [ ] **Step 7: Commit**

```bash
git add -A backend/
git commit -m "refactor(session): relocate GameLifecycleBroadcaster port + resolver into presence"
```

---

### Task 6: Rename `DisconnectForfeitService` → `presence.application.PresenceService` and finalize wiring

**Files:**
- Create: `…/presence/application/PresenceService.java`
- Delete: `presentation/DisconnectForfeitService.java`
- Modify: `presentation/LifecycleController.java`, `presentation/WebSocketDisconnectListener.java`, `config/AppConfig.java`
- Move+rewrite test: `presentation/DisconnectForfeitServiceTest.java` → `…/presence/application/PresenceServiceTest.java`
- Modify test: `presentation/WebSocketDisconnectListenerTest.java`

- [ ] **Step 1: Create `PresenceService` (same logic, new package/name, ports only)**

`presence/application/PresenceService.java`:
```java
package org.kevinkib.cardgames.sessionmanagement.presence.application;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.application.InvalidGameIdException;
import org.kevinkib.cardgames.sessionmanagement.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ConnectionRegistry;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitLog;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitScheduler;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ScheduledForfeit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects multiplayer disconnects and runs the 60s auto-loss timer, plus the
 * shared forfeit path. Reconnect (a fresh presence for the same seat) cancels a
 * pending timer. Broadcasting is delegated to the per-game {@link GameLifecycleBroadcasters};
 * this service knows only the lifecycle, never a game's state shape.
 */
public class PresenceService {

    /** Grace before a dropped player auto-loses. */
    public static final Duration FORFEIT_GRACE = Duration.ofSeconds(60);

    private final SessionService sessionService;
    private final ConnectionRegistry registry;
    private final ForfeitScheduler scheduler;
    private final Clock clock;
    private final ForfeitLog forfeitLog;
    private final GameLifecycleBroadcasters broadcasters;

    private final Map<Seat, ScheduledForfeit> pendingForfeits = new ConcurrentHashMap<>();

    public PresenceService(SessionService sessionService,
                           ConnectionRegistry registry,
                           ForfeitScheduler scheduler,
                           Clock clock,
                           ForfeitLog forfeitLog,
                           GameLifecycleBroadcasters broadcasters) {
        this.sessionService = sessionService;
        this.registry = registry;
        this.scheduler = scheduler;
        this.clock = clock;
        this.forfeitLog = forfeitLog;
        this.broadcasters = broadcasters;
    }

    /** Records presence; if this seat had a pending forfeit, cancels it and announces the return. */
    public void onPresence(String connectionId, GameId gameId, PlayerId playerId) {
        Seat seat = new Seat(gameId, playerId);
        registry.bind(connectionId, seat);

        ScheduledForfeit pending = pendingForfeits.remove(seat);
        if (pending != null) {
            pending.cancel();
            Game game = findGame(seat.gameId());
            if (game != null) {
                broadcasters.broadcasterFor(game).reconnected(game, seat);
            }
        }
    }

    /** Attributes a dropped connection to a seat and, if the game is live, starts the auto-loss timer. */
    public void onDisconnect(String connectionId) {
        Optional<Seat> maybeSeat = registry.unbind(connectionId);
        if (maybeSeat.isEmpty()) {
            return;
        }
        Seat seat = maybeSeat.get();

        Game game = findGame(seat.gameId());
        if (game == null || game.isFinished()) {
            return;
        }

        Instant deadline = clock.instant().plus(FORFEIT_GRACE);
        ScheduledForfeit task = scheduler.schedule(deadline, () -> forfeit(seat, ForfeitReason.DISCONNECTED));
        pendingForfeits.put(seat, task);
        broadcasters.broadcasterFor(game).disconnected(game, seat, deadline.toEpochMilli());
    }

    /** Terminal path shared by the timer (DISCONNECTED) and explicit /app/forfeit (RESIGNED). Idempotent on a finished game. */
    public void forfeit(Seat seat, ForfeitReason reason) {
        pendingForfeits.remove(seat);

        Game game = findGame(seat.gameId());
        if (game == null || game.isFinished()) {
            return;
        }
        game.forfeit(seat.playerId());
        forfeitLog.record(seat, reason);
        sessionService.touch(seat.gameId()); // start the finished-grace clock
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

- [ ] **Step 2: Delete the old service**

```bash
git rm backend/src/main/java/org/kevinkib/cardgames/presentation/DisconnectForfeitService.java
```

- [ ] **Step 3: Update the inbound adapters**

- `presentation/LifecycleController.java`: replace `import` (same-package `DisconnectForfeitService`) by adding `import …presence.application.PresenceService;`; change field/param/type `DisconnectForfeitService disconnectForfeitService` → `PresenceService presenceService` and the two call sites (`presenceService.onPresence(...)`, `presenceService.forfeit(...)`). (`Seat`/`ForfeitReason` imports added in Task 1 stay.)
- `presentation/WebSocketDisconnectListener.java`: add `import …presence.application.PresenceService;`; change field/param/type `DisconnectForfeitService forfeitService` → `PresenceService forfeitService`.

- [ ] **Step 4: Update `AppConfig`**

- Replace `import …presentation.DisconnectForfeitService;` → `import …presence.application.PresenceService;`.
- Rename the bean:
```java
    @Bean
    public PresenceService presenceService(GameLifecycleBroadcasters gameLifecycleBroadcasters) {
        return new PresenceService(
                sessionService(), connectionRegistry(), forfeitScheduler(), clock(), forfeitLog(),
                gameLifecycleBroadcasters);
    }

    @Bean
    public WebSocketDisconnectListener webSocketDisconnectListener(PresenceService presenceService) {
        return new WebSocketDisconnectListener(presenceService);
    }
```

- [ ] **Step 5: Move + rewrite the service test**

Create `…/presence/application/PresenceServiceTest.java` from `DisconnectForfeitServiceTest` with: package `…presence.application`; class `PresenceServiceTest`; the `CapturingScheduler` already converted to `ForfeitScheduler` in Task 4; `forfeitReasonRegistry` already `InMemoryForfeitLog` in Task 3; `GameLifecycleBroadcasters` import from `…presence.application` (same package now, so no import needed); `Seat`/`ForfeitReason` from `…presence.domain`; `service = new PresenceService(...)`. All test methods/assertions stay byte-for-byte the same behavior. Imports to include: `…presence.domain.Seat`, `…presence.domain.ForfeitReason`, `…presence.port.ForfeitScheduler`, `…presence.port.ScheduledForfeit`, `…presence.infrastructure.InMemoryConnectionRegistry`, `…presence.infrastructure.InMemoryForfeitLog`, `…presence.port.ConnectionRegistry`, `…presence.port.ForfeitLog`, plus the existing BatailleCorse/SessionService imports. Then:
```bash
git rm backend/src/test/java/org/kevinkib/cardgames/presentation/DisconnectForfeitServiceTest.java
```

- [ ] **Step 6: Update `WebSocketDisconnectListenerTest`**

`presentation/WebSocketDisconnectListenerTest.java`: add `import …presence.application.PresenceService;`; change `RecordingService extends DisconnectForfeitService` → `extends PresenceService`. Its `super(null, null, null, null, null, null)` (6 args) still matches `PresenceService`'s 6-arg constructor — leave the six nulls.

- [ ] **Step 7: Compile + full suite (final gate)**

Run: `mvn test` → BUILD SUCCESS, 265 tests. Confirm the Spring context loads (the `ApplicationContextTest` exercises full wiring).

- [ ] **Step 8: Commit**

```bash
git add -A backend/
git commit -m "refactor(session): DisconnectForfeitService -> presence.application.PresenceService"
```

---

## Final verification

- [ ] `mvn test` from `backend/` — all 265 tests green.
- [ ] Grep confirms `presentation` no longer holds the moved symbols: `rg "class StompSessionSeatRegistry|class ForfeitReasonRegistry|class DisconnectForfeitService|record Seat|enum ForfeitReason" backend/src/main/java/org/kevinkib/cardgames/presentation` returns nothing.
- [ ] Grep confirms no dangling old imports: `rg "presentation\.(Seat|ForfeitReason|StompSessionSeatRegistry|ForfeitReasonRegistry|DisconnectForfeitService|GameLifecycleBroadcaster)" backend/src` returns nothing.

## Notes for the executor

- This is a pure relocation: **never change behavior**. If a test's assertions would need to change to pass, stop — something was moved wrong.
- The `presentation.*` wildcard imports in the two BatailleCorse files (`BatailleCorseLifecycleBroadcaster`, `BatailleCorseRestController`) are the main trap: once a symbol leaves `presentation`, the wildcard silently stops covering it and you must add an explicit import.
- Out of scope (do not do): renaming the `presentation` package; relocating `SessionRestController`/session DTOs; any frontend or game-domain change.

# Victory Screen Forfeit Reason + Human-Default Toggle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show *why* the winner won when a 2-player game ends by forfeit ("{opp} resigned." / "{opp} disconnected."), and default the create-game opponent toggle to Human.

**Architecture:** The card-game domain stays untouched — `concede(loser)` is transport-neutral. The forfeit *reason* (`RESIGNED` vs `DISCONNECTED`) is owned entirely by the session/presentation layer: a small `ForfeitReasonRegistry` (mirroring the existing `StompSessionSeatRegistry`) records it per seat, `DisconnectForfeitService` writes it on the two forfeit paths, and `BatailleCorseDto` merges it onto the relevant player so it travels in game **state** (not just the transient event). The frontend model exposes the opponent's reason; a pure `endGameMessage` function maps it to copy.

**Tech Stack:** Backend — Java 17 / Spring Boot, STOMP WebSocket, JUnit 5 + Hamcrest. Frontend — Vue 3 (`<script setup>` + TS), Pinia, Vitest, Vite.

---

## Conventions for this plan

- **Backend tests:** run from the `backend/` directory with the IntelliJ-bundled Maven. There is **no `mvnw` wrapper** in this repo — do not invent one. Use `mvn` (resolve the IntelliJ-bundled binary if `mvn` is not on PATH) and verify the output yourself.
  - Full suite: `mvn -q test`
  - Single class: `mvn -q -Dtest=ClassName test`
- **Frontend tests/build:** run from `frontend/`. Worktrees start **without `node_modules`** — run `npm install` once first.
  - Unit tests: `npm run test` (vitest run)
  - Single file: `npm run test -- src/model/endGameMessage.test.ts`
  - **Build is the real type-check gate:** `npm run build`. A bare `npx vue-tsc` can give a false pass — always finish a frontend task with `npm run build`.
- **New files:** `git add` each newly created file immediately after writing it.
- **Commits:** end every commit message with the trailer:
  `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`

---

# Part A — Backend

## Task A1: `ForfeitReason` enum + `ForfeitReasonRegistry`

The session-layer home for the reason. The enum lives in the WebSocket presentation package (next to `Seat`, `DisconnectForfeitService`, `StompSessionSeatRegistry`). The registry mirrors `StompSessionSeatRegistry` exactly: a `ConcurrentHashMap` keyed by `Seat`, with per-game lookup and per-game removal for cleanup.

**Files:**
- Create: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/ForfeitReason.java`
- Create: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/ForfeitReasonRegistry.java`
- Test: `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/ForfeitReasonRegistryTest.java`

- [ ] **Step 1: Create the enum**

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1;

/**
 * Why a seat left the game, as classified by the session/transport layer.
 * The card-game domain never sees this — to it, both are simply a concede.
 */
public enum ForfeitReason {
    RESIGNED,
    DISCONNECTED
}
```

- [ ] **Step 2: Write the failing registry test**

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.PlayerId;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ForfeitReasonRegistryTest {

    private final ForfeitReasonRegistry registry = new ForfeitReasonRegistry();

    @Test
    void givenReasonRecorded_thenReasonsBySeatContainsItKeyedBySeatIndex() {
        BatailleCorseId gameId = BatailleCorseId.generate();
        registry.record(new Seat(gameId, new PlayerId(1)), ForfeitReason.DISCONNECTED);

        Map<Integer, ForfeitReason> reasons = registry.reasonsBySeat(gameId);

        assertThat(reasons.get(1), is(ForfeitReason.DISCONNECTED));
        assertThat(reasons.size(), is(1));
    }

    @Test
    void givenReasonForAnotherGame_thenNotReturned() {
        BatailleCorseId gameId = BatailleCorseId.generate();
        BatailleCorseId otherGame = BatailleCorseId.generate();
        registry.record(new Seat(otherGame, new PlayerId(0)), ForfeitReason.RESIGNED);

        assertThat(registry.reasonsBySeat(gameId).isEmpty(), is(true));
    }

    @Test
    void givenRemoveGame_thenItsReasonsCleared() {
        BatailleCorseId gameId = BatailleCorseId.generate();
        registry.record(new Seat(gameId, new PlayerId(0)), ForfeitReason.RESIGNED);

        registry.removeGame(gameId);

        assertThat(registry.reasonsBySeat(gameId).isEmpty(), is(true));
    }
}
```

- [ ] **Step 3: Run the test, verify it fails**

Run: `mvn -q -Dtest=ForfeitReasonRegistryTest test`
Expected: compilation failure / FAIL — `ForfeitReasonRegistry` does not exist.

- [ ] **Step 4: Create the registry**

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-owned record of which seat forfeited a game and why. Reused across
 * games (and, in future, other game types) so the reason can be merged into
 * game state. Cleared per game on eviction, mirroring StompSessionSeatRegistry.
 */
public class ForfeitReasonRegistry {

    private final Map<Seat, ForfeitReason> reasonBySeat = new ConcurrentHashMap<>();

    public void record(Seat seat, ForfeitReason reason) {
        reasonBySeat.put(seat, reason);
    }

    /** Seat-index -> reason for the given game (empty if no seat forfeited). */
    public Map<Integer, ForfeitReason> reasonsBySeat(BatailleCorseId gameId) {
        Map<Integer, ForfeitReason> result = new HashMap<>();
        reasonBySeat.forEach((seat, reason) -> {
            if (seat.gameId().equals(gameId)) {
                result.put(seat.playerId().id(), reason);
            }
        });
        return result;
    }

    public void removeGame(BatailleCorseId gameId) {
        reasonBySeat.keySet().removeIf(seat -> seat.gameId().equals(gameId));
    }
}
```

- [ ] **Step 5: Run the test, verify it passes**

Run: `mvn -q -Dtest=ForfeitReasonRegistryTest test`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/ForfeitReason.java \
        backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/ForfeitReasonRegistry.java \
        backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/ForfeitReasonRegistryTest.java
git commit -m "feat(session): add ForfeitReason + ForfeitReasonRegistry

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task A2: `PlayerDto.forfeitReason`

Add a nullable `forfeitReason` string to the per-player DTO. Keep the existing 2-arg `from` (delegating with no reason) so the many current call sites are untouched.

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/PlayerDto.java`
- Test: `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/PlayerDtoTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1.dto;

import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.Player;
import org.kevinkib.bataillecorse.websocket.presentation.v1.ForfeitReason;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class PlayerDtoTest {

    private Player aPlayer() {
        BatailleCorse game = new BatailleCorse(BatailleCorseId.generate(), 2);
        return game.getPlayerByIndex(0);
    }

    @Test
    void givenNoReason_thenForfeitReasonIsNull() {
        PlayerDto dto = PlayerDto.from(aPlayer(), List.of());
        assertThat(dto.getForfeitReason(), is(nullValue()));
    }

    @Test
    void givenReason_thenForfeitReasonIsItsName() {
        PlayerDto dto = PlayerDto.from(aPlayer(), List.of(), ForfeitReason.DISCONNECTED);
        assertThat(dto.getForfeitReason(), is("DISCONNECTED"));
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `mvn -q -Dtest=PlayerDtoTest test`
Expected: compilation failure — `getForfeitReason` / 3-arg `from` do not exist.

- [ ] **Step 3: Modify `PlayerDto`**

Replace the file body with (adds the field, the `@JsonProperty`, the overloaded `from`, and the getter):

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.kevinkib.bataillecorse.core.domain.Action;
import org.kevinkib.bataillecorse.core.domain.Player;
import org.kevinkib.bataillecorse.websocket.presentation.v1.ForfeitReason;

import java.util.List;

public class PlayerDto {

    private final String id;
    private final Integer nbCards;
    private final List<String> availableActions;
    private final String forfeitReason;

    @JsonCreator
    public PlayerDto(@JsonProperty("id") String id,
                     @JsonProperty("nbCards") Integer nbCards,
                     @JsonProperty("availableActions") List<String> availableActions,
                     @JsonProperty("forfeitReason") String forfeitReason) {
        this.id = id;
        this.nbCards = nbCards;
        this.availableActions = availableActions;
        this.forfeitReason = forfeitReason;
    }

    public static PlayerDto from(Player player, List<Action> availableActions) {
        return from(player, availableActions, null);
    }

    public static PlayerDto from(Player player, List<Action> availableActions, ForfeitReason forfeitReason) {
        return new PlayerDto(
                player.id().toString(),
                player.getHandSize(),
                availableActions.stream().map(Action::toString).toList(),
                forfeitReason == null ? null : forfeitReason.name());
    }

    public String getId() {
        return id;
    }

    public Integer getNbCards() {
        return nbCards;
    }

    public List<String> getAvailableActions() {
        return availableActions;
    }

    public String getForfeitReason() {
        return forfeitReason;
    }

}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `mvn -q -Dtest=PlayerDtoTest test`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/PlayerDto.java \
        backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/PlayerDtoTest.java
git commit -m "feat(dto): add nullable forfeitReason to PlayerDto

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task A3: `BatailleCorseDto.from(game, forfeitReasons)`

Add an overload that merges seat-index → reason onto the matching players. Keep the no-reason `from(game)` delegating with an empty map (used by all the mid-game action broadcasts, which have no forfeit).

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/BatailleCorseDto.java`
- Test: `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/BatailleCorseDtoTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1.dto;

import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.websocket.presentation.v1.ForfeitReason;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class BatailleCorseDtoTest {

    private BatailleCorse twoPlayerGame() {
        return new BatailleCorse(BatailleCorseId.generate(), 2);
    }

    @Test
    void givenNoReasons_thenEveryPlayerForfeitReasonIsNull() {
        BatailleCorseDto dto = BatailleCorseDto.from(twoPlayerGame());

        assertThat(dto.getPlayers().get(0).getForfeitReason(), is(nullValue()));
        assertThat(dto.getPlayers().get(1).getForfeitReason(), is(nullValue()));
    }

    @Test
    void givenReasonForSeatOne_thenOnlyThatPlayerCarriesIt() {
        BatailleCorseDto dto = BatailleCorseDto.from(
                twoPlayerGame(), Map.of(1, ForfeitReason.RESIGNED));

        assertThat(dto.getPlayers().get(0).getForfeitReason(), is(nullValue()));
        assertThat(dto.getPlayers().get(1).getForfeitReason(), is("RESIGNED"));
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `mvn -q -Dtest=BatailleCorseDtoTest test`
Expected: compilation failure — the 2-arg `from` does not exist.

- [ ] **Step 3: Modify `BatailleCorseDto`**

Add the import and replace the single `from(BatailleCorse)` method with the two methods below. Leave the constructor, fields, and getters unchanged.

Add import near the top:

```java
import org.kevinkib.bataillecorse.websocket.presentation.v1.ForfeitReason;
import java.util.Map;
```

Replace the existing `from`:

```java
    public static BatailleCorseDto from(BatailleCorse batailleCorse) {
        return from(batailleCorse, Map.of());
    }

    public static BatailleCorseDto from(BatailleCorse batailleCorse,
                                        Map<Integer, ForfeitReason> forfeitReasons) {
        List<PlayerDto> players = batailleCorse.getPlayers().stream()
                .map(player -> PlayerDto.from(
                        player,
                        batailleCorse.getAvailableActions(player),
                        forfeitReasons.get(player.id())))
                .toList();

        PlayerIdDto winner = batailleCorse.isFinished()
                ? PlayerIdDto.from(batailleCorse.getWinner())
                : null;

        Player current = batailleCorse.getCurrentPlayer();
        PlayerDto currentPlayer = PlayerDto.from(current, batailleCorse.getAvailableActions(current));

        return new BatailleCorseDto(
                batailleCorse.getId().toString(),
                players,
                winner,
                PileDto.from(batailleCorse.getPile()),
                currentPlayer);
    }
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `mvn -q -Dtest=BatailleCorseDtoTest test`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/BatailleCorseDto.java \
        backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/BatailleCorseDtoTest.java
git commit -m "feat(dto): merge per-seat forfeit reasons into BatailleCorseDto

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task A4: Wire the two forfeit paths to record + broadcast their reason

`DisconnectForfeitService.forfeit(Seat)` becomes `forfeit(Seat, ForfeitReason)`: it records the reason in the registry and merges it into the terminal FORFEIT broadcast's state. The disconnect timer supplies `DISCONNECTED`; the `/app/forfeit` controller endpoint supplies `RESIGNED`. `AppConfig` gains the registry bean and injects it into `DisconnectForfeitService` and `GameCleanupService` (which clears it on eviction). The domain (`BatailleCorse.concede`) is **not** touched.

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/DisconnectForfeitService.java`
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/BatailleCorseWebSocketController.java:177-188`
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/GameCleanupService.java`
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/config/AppConfig.java`
- Test: `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/DisconnectForfeitServiceTest.java`

- [ ] **Step 1: Update the existing test to the new signature and assert the reason rides in state**

In `DisconnectForfeitServiceTest`:

(a) Add fields/imports and construct the registry in `setUp`:

```java
// add imports
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.PlayerDto;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.SuccessResponse;

// add field
private ForfeitReasonRegistry forfeitReasonRegistry;

// in setUp(), before constructing the service:
forfeitReasonRegistry = new ForfeitReasonRegistry();
service = new DisconnectForfeitService(
        sessionService, messaging, registry, scheduler, clock, forfeitReasonRegistry);
```

(b) Add a helper to read the forfeit reason carried in the last broadcast's state:

```java
private String forfeitReasonInLastStateForSeat(String seatId) {
    Response last = messaging.sent.get(messaging.sent.size() - 1);
    return last.getState().getPlayers().stream()
            .filter(p -> p.getId().equals(seatId))
            .map(PlayerDto::getForfeitReason)
            .findFirst()
            .orElse(null);
}
```

(c) Update the timer test to assert `DISCONNECTED` lands in state for the dropped seat:

```java
    @Test
    void givenPendingForfeit_whenTimerFires_thenGameConcededAndForfeitBroadcast() {
        service.onPresence("sess-0", gameId, new PlayerId(0));
        service.onDisconnect("sess-0");

        scheduler.lastTask.run(); // simulate the 60s elapsing

        BatailleCorse game = sessionService.getGame(gameId);
        assertThat(game.isFinished(), is(true));
        assertThat(game.getWinner().id(), is(new PlayerId(1)));
        assertThat(eventTypes(), contains("OPPONENT_DISCONNECTED", "FORFEIT"));
        assertThat(forfeitReasonInLastStateForSeat("0"), is("DISCONNECTED"));
    }
```

(d) Update the direct-forfeit test to the new signature and assert `RESIGNED`:

```java
    @Test
    void whenForfeitCalledDirectly_thenConcedesAndBroadcastsForfeitWithResigned() {
        service.forfeit(new Seat(gameId, new PlayerId(1)), ForfeitReason.RESIGNED);

        BatailleCorse game = sessionService.getGame(gameId);
        assertThat(game.getWinner().id(), is(new PlayerId(0)));
        assertThat(eventTypes(), contains("FORFEIT"));
        assertThat(forfeitReasonInLastStateForSeat("1"), is("RESIGNED"));
    }
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `mvn -q -Dtest=DisconnectForfeitServiceTest test`
Expected: compilation failure — constructor arity and `forfeit(Seat, ForfeitReason)` don't exist yet.

- [ ] **Step 3: Update `DisconnectForfeitService`**

Add the registry field + constructor param, schedule with `DISCONNECTED`, and change `forfeit` to take a reason, record it, and broadcast merged state.

Constructor — add the parameter and assignment:

```java
    private final ForfeitReasonRegistry forfeitReasonRegistry;

    public DisconnectForfeitService(SessionService sessionService,
                                    GameMessagingService messaging,
                                    StompSessionSeatRegistry registry,
                                    TaskScheduler scheduler,
                                    Clock clock,
                                    ForfeitReasonRegistry forfeitReasonRegistry) {
        this.sessionService = sessionService;
        this.messaging = messaging;
        this.registry = registry;
        this.scheduler = scheduler;
        this.clock = clock;
        this.forfeitReasonRegistry = forfeitReasonRegistry;
    }
```

In `onDisconnect`, change the scheduled call to pass the reason:

```java
        ScheduledFuture<?> task = scheduler.schedule(
                () -> forfeit(seat, ForfeitReason.DISCONNECTED), deadline);
```

Replace `forfeit`:

```java
    /** Terminal path shared by the timer ({@code DISCONNECTED}) and explicit
     *  /app/forfeit ({@code RESIGNED}). Idempotent on a finished game. */
    public void forfeit(Seat seat, ForfeitReason reason) {
        pendingForfeits.remove(seat);

        BatailleCorse game = findGame(seat.gameId());
        if (game == null || game.isFinished()) {
            return;
        }
        game.concede(seat.playerId());
        forfeitReasonRegistry.record(seat, reason);
        sessionService.touch(seat.gameId()); // start the finished-grace clock
        broadcast(seat.gameId(), new SuccessResponse(
                EventType.FORFEIT,
                new ForfeitEventData(seat.playerId().id()),
                "Player " + seat.playerId() + " forfeited.",
                BatailleCorseDto.from(game, forfeitReasonRegistry.reasonsBySeat(seat.gameId()))));
    }
```

- [ ] **Step 4: Update the `/forfeit` controller endpoint**

In `BatailleCorseWebSocketController.forfeit` (line ~184), pass the explicit reason:

```java
            disconnectForfeitService.forfeit(new Seat(gameId, playerId), ForfeitReason.RESIGNED);
```

(The `ForfeitReason` import is covered by the existing wildcard imports? No — add it explicitly:)

```java
import org.kevinkib.bataillecorse.websocket.presentation.v1.ForfeitReason;
```

(The controller is in package `...websocket.presentation.v1`, and `ForfeitReason` is in the same package — so **no import is needed**. Skip the import line if the package matches; only add it if your IDE flags it.)

- [ ] **Step 5: Update `GameCleanupService` to clear forfeit reasons on eviction**

Add the registry field + constructor param, and clear per evicted game in `sweep`:

```java
    private final ForfeitReasonRegistry forfeitReasonRegistry;

    public GameCleanupService(SessionRepository repository,
                              StompSessionSeatRegistry presenceRegistry,
                              ForfeitReasonRegistry forfeitReasonRegistry) {
        this.repository = repository;
        this.presenceRegistry = presenceRegistry;
        this.forfeitReasonRegistry = forfeitReasonRegistry;
    }
```

Add the import:

```java
import org.kevinkib.bataillecorse.websocket.presentation.v1.ForfeitReasonRegistry;
```

In `sweep`, after the existing `presenceRegistry` cleanup:

```java
        if (!evicted.isEmpty()) {
            log.info("Evicted {} stale game(s): {}", evicted.size(), evicted);
            evicted.forEach(presenceRegistry::removeGame);
            evicted.forEach(forfeitReasonRegistry::removeGame);
        }
```

- [ ] **Step 6: Wire the bean in `AppConfig`**

Add a `forfeitReasonRegistry` bean and pass it into the two services:

```java
    @Bean
    public ForfeitReasonRegistry forfeitReasonRegistry() {
        return new ForfeitReasonRegistry();
    }

    @Bean
    public DisconnectForfeitService disconnectForfeitService(GameMessagingService gameMessagingService) {
        return new DisconnectForfeitService(
                sessionService(), gameMessagingService, stompSessionSeatRegistry(),
                taskScheduler(), clock(), forfeitReasonRegistry());
    }

    @Bean
    public GameCleanupService gameCleanupService() {
        return new GameCleanupService(
                sessionRepository(), stompSessionSeatRegistry(), forfeitReasonRegistry());
    }
```

Add the import:

```java
import org.kevinkib.bataillecorse.websocket.presentation.v1.ForfeitReasonRegistry;
```

- [ ] **Step 7: Check other callers of the changed constructors/methods**

Run a search for compile breakers and fix any that surface (e.g. another test constructing `GameCleanupService` or calling `forfeit(seat)`):

Run: `mvn -q test-compile`
Expected: BUILD SUCCESS. If a test fails to compile, update its `new GameCleanupService(...)` / `new DisconnectForfeitService(...)` / `forfeit(seat)` usage to the new signatures (pass `new ForfeitReasonRegistry()` / a `ForfeitReason`).

- [ ] **Step 8: Run the full backend suite, verify green**

Run: `mvn -q test`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/DisconnectForfeitService.java \
        backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/BatailleCorseWebSocketController.java \
        backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/GameCleanupService.java \
        backend/src/main/java/org/kevinkib/bataillecorse/config/AppConfig.java \
        backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/DisconnectForfeitServiceTest.java
git commit -m "feat(session): classify forfeit reason (resign vs disconnect) into game state

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

# Part B — Frontend

## Task B1: `ForfeitReason` type + `Player.forfeitReason`

**Files:**
- Create: `frontend/src/model/ForfeitReason.ts`
- Modify: `frontend/src/model/Player.ts`
- Modify: `frontend/src/model/fixtures/index.ts:33-43`
- Test: `frontend/src/model/Player.test.ts`

- [ ] **Step 1: Create the reason type**

```typescript
export type ForfeitReason = 'RESIGNED' | 'DISCONNECTED';
```

- [ ] **Step 2: Write the failing Player test**

Append to `frontend/src/model/Player.test.ts` (create the file with this content if it does not already have a describe block — it exists; add this `it` inside a suitable describe or a new one):

```typescript
import { describe, it, expect } from 'vitest';
import Player from './Player';

describe('Player.fromJSON forfeitReason', () => {
  it('defaults forfeitReason to null when absent', () => {
    const player = Player.fromJSON({ id: '0', nbCards: 26, availableActions: [] });
    expect(player.forfeitReason).toBeNull();
  });

  it('reads forfeitReason when present', () => {
    const player = Player.fromJSON({
      id: '1', nbCards: 0, availableActions: [], forfeitReason: 'DISCONNECTED',
    });
    expect(player.forfeitReason).toBe('DISCONNECTED');
  });
});
```

> If `Player.test.ts` already imports `describe/it/expect` and `Player`, do not duplicate the imports — just add the new `describe` block.

- [ ] **Step 3: Run the test, verify it fails**

Run: `npm run test -- src/model/Player.test.ts`
Expected: FAIL — `forfeitReason` is `undefined`, not `null` / not accepted by `fromJSON`.

- [ ] **Step 4: Modify `Player.ts`**

```typescript
import type { ForfeitReason } from "./ForfeitReason";

export default class Player {
  constructor(
    public readonly id: string,
    public readonly nbCards: number,
    public readonly availableActions: string[],
    public readonly forfeitReason: ForfeitReason | null = null,
  ) {}

  /**
   * Returns whether this player has the given action available.
   * Reads server-provided state — not client-side authorization.
   * The server validates all action requests independently.
   */
  hasAvailableAction(action: string): boolean {
    return this.availableActions.includes(action);
  }

  static fromJSON(data: {
    id: string;
    nbCards: number;
    availableActions: string[];
    forfeitReason?: string | null;
  }): Player {
    return new Player(
      data.id,
      data.nbCards,
      data.availableActions,
      (data.forfeitReason as ForfeitReason) ?? null,
    );
  }
}
```

- [ ] **Step 5: Extend the `buildPlayer` fixture**

In `frontend/src/model/fixtures/index.ts`, update `buildPlayer` to accept and pass a reason:

```typescript
import type { ForfeitReason } from '../ForfeitReason';

export function buildPlayer(overrides: Partial<{
  id: string;
  nbCards: number;
  availableActions: string[];
  forfeitReason: ForfeitReason | null;
}> = {}): Player {
  return new Player(
    overrides.id ?? '0',
    overrides.nbCards ?? 26,
    overrides.availableActions ?? [],
    overrides.forfeitReason ?? null,
  );
}
```

- [ ] **Step 6: Run the test, verify it passes**

Run: `npm run test -- src/model/Player.test.ts`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/model/ForfeitReason.ts frontend/src/model/Player.ts \
        frontend/src/model/fixtures/index.ts frontend/src/model/Player.test.ts
git commit -m "feat(model): carry forfeitReason on Player

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task B2: `BatailleCorse.fromJSON` parses it + `opponentForfeitReason`

**Files:**
- Modify: `frontend/src/model/BatailleCorse.ts`
- Test: `frontend/src/model/BatailleCorse.test.ts`

- [ ] **Step 1: Write the failing test**

Append to `frontend/src/model/BatailleCorse.test.ts` (imports `buildGame`, `buildPlayer` already present):

```typescript
describe('BatailleCorse.opponentForfeitReason', () => {
  it('returns the other seat forfeit reason for the given player index', () => {
    const game = buildGame({
      players: [
        buildPlayer({ id: '0', forfeitReason: null }),
        buildPlayer({ id: '1', forfeitReason: 'DISCONNECTED' }),
      ],
    });
    // seat 0 is the winner; its opponent (seat 1) disconnected
    expect(game.opponentForfeitReason(0)).toBe('DISCONNECTED');
  });

  it('returns null when the opponent did not forfeit', () => {
    const game = buildGame({
      players: [buildPlayer({ id: '0' }), buildPlayer({ id: '1' })],
    });
    expect(game.opponentForfeitReason(0)).toBeNull();
  });

  it('parses forfeitReason from JSON onto players', () => {
    const game = BatailleCorse.fromJSON({
      currentPlayer: { id: '0', nbCards: 26, availableActions: [] },
      pile: {
        cards: [], grabbable: false, nbCardsSinceLastHonourCard: 0,
        playerThatAddedLastHonourCard: { id: '0' },
      },
      players: [
        { id: '0', nbCards: 26, availableActions: [] },
        { id: '1', nbCards: 0, availableActions: [], forfeitReason: 'RESIGNED' },
      ],
      winner: { id: '0' },
    });
    expect(game.players[1].forfeitReason).toBe('RESIGNED');
  });
});
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `npm run test -- src/model/BatailleCorse.test.ts`
Expected: FAIL — `opponentForfeitReason` is not a function.

- [ ] **Step 3: Modify `BatailleCorse.ts`**

Add the import, the method, and the `forfeitReason` field to the `fromJSON` players type:

```typescript
import type { ForfeitReason } from "./ForfeitReason";
```

Add the method inside the class (next to `isWinnerAt`):

```typescript
  /**
   * The forfeit reason of the seat opposite the given one, or null. 2-player:
   * the single other seat. Used by the winner's end screen to explain a forfeit.
   */
  opponentForfeitReason(playerIndex: number): ForfeitReason | null {
    const opponent = this.players.find((_, i) => i !== playerIndex);
    return opponent?.forfeitReason ?? null;
  }
```

Update the `fromJSON` `players` element type to include the optional field:

```typescript
    players: { id: string; nbCards: number; availableActions: string[]; forfeitReason?: string | null }[];
```

(The body — `data.players.map(p => Player.fromJSON(p))` — already forwards the field via `Player.fromJSON`; no change to the mapping line.)

- [ ] **Step 4: Run the test, verify it passes**

Run: `npm run test -- src/model/BatailleCorse.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/model/BatailleCorse.ts frontend/src/model/BatailleCorse.test.ts
git commit -m "feat(model): expose opponentForfeitReason on BatailleCorse

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task B3: `endGameMessage` — single source of truth for the subtitle

A pure function maps (didIWin, opponentLabel, opponentForfeitReason) → subtitle. This is the one place the end-screen copy lives. Testing the pure function (rather than mounting the heavy `GameScreen`) is the better test design here — the component only delegates to it.

**Files:**
- Create: `frontend/src/model/endGameMessage.ts`
- Test: `frontend/src/model/endGameMessage.test.ts`

- [ ] **Step 1: Write the failing test**

```typescript
import { describe, it, expect } from 'vitest';
import { endGameMessage } from './endGameMessage';

describe('endGameMessage', () => {
  it('normal card win', () => {
    expect(endGameMessage(true, 'Alice', null)).toBe('You beat Alice!');
  });

  it('win by opponent resignation', () => {
    expect(endGameMessage(true, 'Alice', 'RESIGNED')).toBe('Alice resigned.');
  });

  it('win by opponent disconnection', () => {
    expect(endGameMessage(true, 'Alice', 'DISCONNECTED')).toBe('Alice disconnected.');
  });

  it('defeat is always plain regardless of reason', () => {
    expect(endGameMessage(false, 'Alice', null)).toBe('Alice won.');
    expect(endGameMessage(false, 'Alice', 'RESIGNED')).toBe('Alice won.');
    expect(endGameMessage(false, 'Alice', 'DISCONNECTED')).toBe('Alice won.');
  });
});
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `npm run test -- src/model/endGameMessage.test.ts`
Expected: FAIL — module not found.

- [ ] **Step 3: Create `endGameMessage.ts`**

```typescript
import type { ForfeitReason } from './ForfeitReason';

/**
 * End-screen subtitle. Winner-perspective only: when the opponent forfeited we
 * say how; otherwise it was a normal card win. The loser always sees the plain
 * "{opponent} won." — telling a resigner they resigned adds nothing.
 */
export function endGameMessage(
  didIWin: boolean,
  opponentLabel: string,
  opponentForfeitReason: ForfeitReason | null,
): string {
  if (!didIWin) {
    return `${opponentLabel} won.`;
  }
  switch (opponentForfeitReason) {
    case 'RESIGNED':
      return `${opponentLabel} resigned.`;
    case 'DISCONNECTED':
      return `${opponentLabel} disconnected.`;
    default:
      return `You beat ${opponentLabel}!`;
  }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `npm run test -- src/model/endGameMessage.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/model/endGameMessage.ts frontend/src/model/endGameMessage.test.ts
git commit -m "feat(model): endGameMessage subtitle source of truth

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task B4: Wire the subtitle into `GameScreen.vue`

**Files:**
- Modify: `frontend/src/view/alpha/GameScreen.vue:110` (template) and the `<script setup>` computeds region (~line 286-292).

- [ ] **Step 1: Add the import and computed**

In the `<script setup>` imports, add:

```typescript
import { endGameMessage } from '../../model/endGameMessage';
```

Near `didIWin` (after line ~292), add:

```typescript
const endSubtitle = computed(() =>
  endGameMessage(
    didIWin.value,
    opponentLabel.value,
    batailleCorse.value?.opponentForfeitReason(myPlayerIndex.value) ?? null,
  ));
```

- [ ] **Step 2: Use it in the template**

Replace the end-overlay subtitle (current line 109-111):

```vue
        <p class="end-sub">
          {{ didIWin ? `You beat ${opponentLabel}!` : `${opponentLabel} won.` }}
        </p>
```

with:

```vue
        <p class="end-sub">{{ endSubtitle }}</p>
```

- [ ] **Step 3: Verify type-check + build (the real gate)**

Run: `npm run build`
Expected: build succeeds with no type errors.

- [ ] **Step 4: Run the existing GameScreen tests to confirm no regression**

Run: `npm run test -- src/view/alpha/GameScreen.test.ts`
Expected: PASS (existing disconnect-countdown / leave-guard tests still green).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/view/alpha/GameScreen.vue
git commit -m "feat(ui): show forfeit reason on the victory screen

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task B5: Default the opponent toggle to Human

A one-line default flip. No existing test depends on it (there is no `StartGame.test.ts`), and a mounted-component test for a single default literal would be brittle for no real coverage gain — the build + manual smoke is the right verification here.

**Files:**
- Modify: `frontend/src/view/alpha/StartGame.vue:158-159`

- [ ] **Step 1: Flip the default and fix the comment**

Replace:

```typescript
// New Game toggle: true = solo vs computer, false = 2-player vs human
const vsComputer = ref(true);
```

with:

```typescript
// New Game toggle: false = 2-player vs human (default), true = solo vs computer
const vsComputer = ref(false);
```

- [ ] **Step 2: Verify build**

Run: `npm run build`
Expected: build succeeds.

- [ ] **Step 3: Manual smoke (optional but recommended)**

Run the app, open the create-game screen, and confirm: **Human** is highlighted by default, the difficulty slider is hidden, and the submit button reads **"Create Game"**.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/view/alpha/StartGame.vue
git commit -m "feat(ui): default new-game opponent to Human

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

# Final verification

- [ ] **Backend:** from `backend/`, run `mvn -q test` → BUILD SUCCESS.
- [ ] **Frontend unit:** from `frontend/`, run `npm run test` → all suites pass.
- [ ] **Frontend build:** from `frontend/`, run `npm run build` → succeeds.
- [ ] **Manual end-to-end (multiplayer):** open two clients, start a 2-player game.
  - Have one player use the leave-guard / forfeit → the other's screen reads **"{opp} resigned."**
  - Have one player drop their connection and wait out the 60s timer → the other's screen reads **"{opp} disconnected."**
  - Play a normal game to a card win → winner still reads **"You beat {opp}!"**, loser reads **"{opp} won."**

---

# Self-review notes (author)

**Spec coverage:**
- Winner-only forfeit messaging (resign vs disconnect) → A1–A4 (reason into state) + B1–B4 (display). ✓
- Domain stays transport-neutral (`concede` unchanged) → explicitly untouched in A4; `BatailleCorseConcedeTest` needs no change. ✓
- Reason owned by session hexagon → `ForfeitReason`/`ForfeitReasonRegistry` in the WebSocket presentation package (A1); domain has no knowledge. ✓
- Reason in state, not only the event → merged into `BatailleCorseDto` (A3) and broadcast in FORFEIT state (A4). ✓
- Cleanup symmetry → `GameCleanupService` clears the registry on eviction (A4). ✓
- Human default → B5. ✓

**Known boundary (intentionally not built):** `DisconnectForfeitService` still constructs `BatailleCorseDto` directly — the future "game presentation port" seam — and the 60s-then-forfeit reaction policy stays hardcoded. Both flagged in the spec, out of scope here.

**Type consistency:** `ForfeitReason` (Java enum: `RESIGNED`/`DISCONNECTED`) ↔ `forfeitReason` string on `PlayerDto` (`enum.name()`) ↔ TS `ForfeitReason = 'RESIGNED' | 'DISCONNECTED'` ↔ `Player.forfeitReason` ↔ `BatailleCorse.opponentForfeitReason` ↔ `endGameMessage`. Names line up end to end.

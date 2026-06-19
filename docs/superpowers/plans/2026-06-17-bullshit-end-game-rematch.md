# Bullshit End-of-Game + Rematch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an end-of-game overlay and a Bullshit rematch flow (unanimous among connected, non-forfeited seats) without `core` ever depending on `presence`.

**Architecture:** The Bullshit WS controller orchestrates: it asks the presence side (via a narrow `SeatPresence` port) for the eligible seat set and passes it into `core`'s rematch coordinator. `core` stays presence-ignorant (preserving #63/#64/#65). The fresh game is broadcast per-seat through the existing `BullshitStateBroadcaster`. Frontend reuses the game-agnostic `EndGameOverlay` + `RematchButton`.

**Tech Stack:** Java 17, Spring Boot, JUnit 5 + Hamcrest (no Mockito on domain). Vue 3 `<script setup>` + TypeScript, Pinia, Vitest. Maven from `backend/` (no `./mvnw`): `"C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" test`. Frontend from `frontend/` (worktree lacks `node_modules` — run `npm ci` once first): unit `npx vitest run <path>`, real gate `npm run build`.

**Source spec:** `docs/superpowers/specs/2026-06-17-bullshit-end-game-rematch-design.md`

---

## File Structure

**Backend — new**
- `sessionmanagement/presence/application/SeatPresence.java` — narrow query port: `Set<PlayerId> activeSeats(GameId)`.
- `sessionmanagement/core/application/RematchTally.java` — `(boolean unanimous, int ready, int eligible)`.
- `bullshit/presentation/dto/event/BullshitRematchEventData.java` — `(RematchStatus status, int ready, int eligible)`.

**Backend — modified**
- `sessionmanagement/presence/port/ConnectionRegistry.java` + `infrastructure/InMemoryConnectionRegistry.java` — add `seatsFor`.
- `sessionmanagement/presence/application/PresenceService.java` — implements `SeatPresence`.
- `sessionmanagement/core/domain/SessionGame.java` — `hasRequestedRematch(PlayerId)`, `isRematchUnanimousAmong(Set<PlayerId>)`.
- `sessionmanagement/core/application/SessionService.java` — `requestRematch(GameId, PlayerId, Set<PlayerId>): RematchTally`.
- `bullshit/presentation/BullshitWebSocketController.java` — `SeatPresence` dependency + `/bullshit/rematch`.

**Frontend — new**
- `model/bullshit/BullshitRematch.ts` — `bullshitRematchButton(...)` returning the shared `RematchButton`.

**Frontend — modified**
- `application/BullshitSession.ts` — `rematch()`.
- `state/Bullshit.store.ts` — rematch progress state, `rematch()` action, `rematchButton` computed.
- `view/bullshit/BullshitGameScreen.vue` — use `EndGameOverlay` for the finished phase.

---

## PART A — Backend

### Task 1: `ConnectionRegistry.seatsFor(GameId)`

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/presence/port/ConnectionRegistry.java`
- Modify: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/presence/infrastructure/InMemoryConnectionRegistry.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/presence/infrastructure/InMemoryConnectionRegistryTest.java`

- [ ] **Step 1: Write the failing test**

Add to `InMemoryConnectionRegistryTest` (it already has a `private final InMemoryConnectionRegistry registry = new InMemoryConnectionRegistry();` field and imports `GameId`, `PlayerId`, `Seat`):

```java
@Test
void givenSeatsAcrossTwoGames_whenSeatsFor_thenOnlyThatGamesPlayerIds() {
    GameId g1 = GameId.generate();
    GameId g2 = GameId.generate();
    registry.bind("c0", new Seat(g1, new PlayerId(0)));
    registry.bind("c1", new Seat(g1, new PlayerId(1)));
    registry.bind("c2", new Seat(g2, new PlayerId(0)));

    assertThat(registry.seatsFor(g1), containsInAnyOrder(new PlayerId(0), new PlayerId(1)));
}
```

Add imports if missing: `import java.util.Set;` is not needed for the assertion, but add `import static org.hamcrest.Matchers.containsInAnyOrder;`.

- [ ] **Step 2: Run to verify it fails**

Run: `cd backend && "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q test -Dtest=InMemoryConnectionRegistryTest`
Expected: compile failure — `seatsFor` not defined.

- [ ] **Step 3: Add the port method**

In `ConnectionRegistry.java`, add the import `import org.kevinkib.cardgames.game.PlayerId;` and `import java.util.Set;`, then add to the interface:

```java
    /** Player ids of every seat currently bound to a live connection for this game. */
    Set<PlayerId> seatsFor(GameId gameId);
```

- [ ] **Step 4: Implement in `InMemoryConnectionRegistry`**

Add imports `import org.kevinkib.cardgames.game.PlayerId;`, `import java.util.Set;`, `import java.util.stream.Collectors;`, then:

```java
    @Override
    public Set<PlayerId> seatsFor(GameId gameId) {
        return seatByConnection.values().stream()
                .filter(seat -> seat.gameId().equals(gameId))
                .map(Seat::playerId)
                .collect(Collectors.toSet());
    }
```

- [ ] **Step 5: Run to verify it passes**

Run: `cd backend && "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q test -Dtest=InMemoryConnectionRegistryTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A backend/
git commit -m "feat(presence): ConnectionRegistry.seatsFor(gameId)"
```

---

### Task 2: `SeatPresence` port + `PresenceService.activeSeats`

**Files:**
- Create: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/presence/application/SeatPresence.java`
- Modify: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/presence/application/PresenceService.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/presence/application/PresenceServiceTest.java`

- [ ] **Step 1: Create the port**

```java
package org.kevinkib.cardgames.sessionmanagement.presence.application;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.Set;

/** Published query: which seats are eligible to act now (connected and not forfeited). */
public interface SeatPresence {
    Set<PlayerId> activeSeats(GameId gameId);
}
```

- [ ] **Step 2: Write the failing test**

In `PresenceServiceTest`, re-add `import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;` (removed during the #65 cleanup) and `import static org.hamcrest.Matchers.contains;`. `ForfeitReason` (presence.port) and `PlayerId`/`GameId` are already imported. The test already has `service` (a `PresenceService`), `registry`, `forfeitLog`, and `gameId` fields. Add:

```java
@Test
void givenConnectedAndForfeitedSeats_whenActiveSeats_thenExcludesForfeitedAndDisconnected() {
    registry.bind("c0", new Seat(gameId, new PlayerId(0)));
    registry.bind("c1", new Seat(gameId, new PlayerId(1)));
    forfeitLog.record(new Seat(gameId, new PlayerId(0)), ForfeitReason.RESIGNED);
    // seat 2 is never bound -> disconnected -> excluded

    assertThat(service.activeSeats(gameId), contains(new PlayerId(1)));
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `cd backend && "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q test -Dtest=PresenceServiceTest`
Expected: compile failure — `activeSeats` not defined.

- [ ] **Step 4: Implement on `PresenceService`**

Change the class declaration to `public class PresenceService implements SeatPresence {`. Add imports `import java.util.Set;`, `import java.util.stream.Collectors;`. Add the method:

```java
    @Override
    public Set<PlayerId> activeSeats(GameId gameId) {
        Set<Integer> forfeited = forfeitLog.reasonsBySeat(gameId).keySet();
        return registry.seatsFor(gameId).stream()
                .filter(seat -> !forfeited.contains(seat.id()))
                .collect(Collectors.toSet());
    }
```

- [ ] **Step 5: Run to verify it passes**

Run: `cd backend && "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q test -Dtest=PresenceServiceTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A backend/
git commit -m "feat(presence): SeatPresence.activeSeats (connected minus forfeited)"
```

---

### Task 3: Core unanimity-among-eligible + `RematchTally`

**Files:**
- Create: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/core/application/RematchTally.java`
- Modify: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/core/domain/SessionGame.java`
- Modify: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/core/application/SessionService.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/core/domain/SessionGameTest.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/core/application/SessionServiceTest.java`

- [ ] **Step 1: Write the failing domain tests**

In `SessionGameTest`'s `RematchTest` nested class (add `import java.util.Set;` and, if absent, `import org.kevinkib.cardgames.game.GameId;`/`PlayerId`):

```java
@Test
void givenSubsetRequested_whenUnanimousAmongThatSubset_thenTrue() {
    SessionGame session = SessionGame.create(GameId.generate(), 3, "bullshit");
    session.requestRematch(new PlayerId(0));
    session.requestRematch(new PlayerId(2));

    assertThat(session.isRematchUnanimousAmong(Set.of(new PlayerId(0), new PlayerId(2))), is(true));
}

@Test
void givenAnEligibleSeatHasNotRequested_thenNotUnanimousAmong() {
    SessionGame session = SessionGame.create(GameId.generate(), 3, "bullshit");
    session.requestRematch(new PlayerId(0));

    assertThat(session.isRematchUnanimousAmong(Set.of(new PlayerId(0), new PlayerId(1))), is(false));
}

@Test
void givenEmptyEligible_thenNotUnanimousAmong() {
    SessionGame session = SessionGame.create(GameId.generate(), 2, "bullshit");

    assertThat(session.isRematchUnanimousAmong(Set.of()), is(false));
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd backend && "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q test -Dtest=SessionGameTest`
Expected: compile failure — `isRematchUnanimousAmong` not defined.

- [ ] **Step 3: Implement on `SessionGame`**

Add `import java.util.Set;` to `SessionGame.java`, then add:

```java
    public boolean hasRequestedRematch(PlayerId playerId) {
        SessionPlayer seat = players.get(playerId);
        return seat != null && seat.hasRequestedRematch();
    }

    public boolean isRematchUnanimousAmong(Set<PlayerId> eligible) {
        return !eligible.isEmpty() && eligible.stream().allMatch(this::hasRequestedRematch);
    }
```

- [ ] **Step 4: Run to verify they pass**

Run: `cd backend && "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q test -Dtest=SessionGameTest`
Expected: PASS.

- [ ] **Step 5: Create `RematchTally`**

```java
package org.kevinkib.cardgames.sessionmanagement.core.application;

/** Outcome of recording a rematch request among an eligible seat set. */
public record RematchTally(boolean unanimous, int ready, int eligible) {
}
```

- [ ] **Step 6: Write the failing service test**

In `SessionServiceTest`'s `RematchTest` (it sets up a 2-seat game; reuse its game-id local — call it `id` below; adapt to the actual variable name in that class). Add `import java.util.Set;` and `import org.kevinkib.cardgames.sessionmanagement.core.application.RematchTally;` if not already in-package (same package — no import needed):

```java
@Test
void givenOnlyOneSeatEligible_whenThatSeatRequests_thenUnanimousAmongEligible() {
    RematchTally tally = service.requestRematch(id, new PlayerId(0), Set.of(new PlayerId(0)));

    assertThat(tally.unanimous(), is(true));   // seat 1 not eligible, so seat 0 alone suffices
    assertThat(tally.ready(), is(1));
    assertThat(tally.eligible(), is(1));
}

@Test
void givenTwoEligibleAndOneRequested_whenRequest_thenPendingWithReadyOne() {
    RematchTally tally = service.requestRematch(id, new PlayerId(0),
            Set.of(new PlayerId(0), new PlayerId(1)));

    assertThat(tally.unanimous(), is(false));
    assertThat(tally.ready(), is(1));
    assertThat(tally.eligible(), is(2));
}
```

If `RematchTest` has no shared 2-seat game with id `id`, create one at the top of each test using the same factory call the other tests in that class use (read the class first).

- [ ] **Step 7: Run to verify it fails**

Run: `cd backend && "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q test -Dtest=SessionServiceTest`
Expected: compile failure — no `requestRematch(GameId, PlayerId, Set)` overload.

- [ ] **Step 8: Add the `SessionService` overload**

Add `import java.util.Set;`. Below the existing `requestRematch(GameId, PlayerId)`:

```java
    /** Records this seat's request and tallies it against the eligible (connected, non-forfeited) seats. */
    public RematchTally requestRematch(GameId id, PlayerId playerId, Set<PlayerId> eligibleSeats) {
        SessionGame session = repository.loadSessionGame(id);
        session.requestRematch(playerId);
        int ready = (int) eligibleSeats.stream().filter(session::hasRequestedRematch).count();
        return new RematchTally(session.isRematchUnanimousAmong(eligibleSeats), ready, eligibleSeats.size());
    }
```

- [ ] **Step 9: Run to verify it passes**

Run: `cd backend && "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q test -Dtest=SessionServiceTest`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add -A backend/
git commit -m "feat(session): rematch unanimity among an eligible seat set (RematchTally)"
```

---

### Task 4: `/bullshit/rematch` endpoint + per-seat broadcast

**Files:**
- Create: `backend/src/main/java/org/kevinkib/cardgames/bullshit/presentation/dto/event/BullshitRematchEventData.java`
- Modify: `backend/src/main/java/org/kevinkib/cardgames/bullshit/presentation/BullshitWebSocketController.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/bullshit/presentation/BullshitWebSocketControllerTest.java`

- [ ] **Step 1: Create the event data**

```java
package org.kevinkib.cardgames.bullshit.presentation.dto.event;

import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.presentation.dto.event.RematchStatus;

public record BullshitRematchEventData(RematchStatus status, int ready, int eligible) implements EventData {
}
```

(If `BullshitCreateEventData` does NOT implement `EventData`, drop the `implements EventData` clause — match whatever the sibling event-data records in that package do. Check `bullshit/presentation/dto/event/BullshitCreateEventData.java` first.)

- [ ] **Step 2: Write the failing controller test**

In `BullshitWebSocketControllerTest`: add `import org.kevinkib.cardgames.sessionmanagement.presence.application.SeatPresence;`, `import org.kevinkib.cardgames.bullshit.presentation.dto.event.BullshitRematchEventData;`, `import org.kevinkib.cardgames.presentation.dto.event.RematchStatus;`, `import java.util.Set;`.

In `setUp()`, change the controller construction to pass a `SeatPresence` stub returning both seats:

```java
SeatPresence seatPresence = gid -> Set.of(new PlayerId(0), new PlayerId(1));
controller = new BullshitWebSocketController(
        sessionService, new BullshitStateBroadcaster(messaging), messaging, seatPresence);
```

(The `RecordingMessaging` in this test records `seats` and `payloads`; reuse it.) Add the tests:

```java
@Test
void givenFirstSeatRequestsRematch_whenRematch_thenBroadcastsPendingToAllSeats() {
    Bullshit game = (Bullshit) sessionService.createGame("bullshit", 2, GameMode.SOLO);
    GameId id = game.getId();
    String t0 = sessionService.tokenForSeat(id, new PlayerId(0));

    controller.rematch(new GameActionPayload(id.uuid().toString(), t0));

    assertThat(messaging.seats.size(), is(2)); // fanned to both seats
    Response r = messaging.payloads.get(0);
    assertThat(r.getEventType(), is("REMATCH"));
    BullshitRematchEventData data = (BullshitRematchEventData) r.getEventData();
    assertThat(data.status(), is(RematchStatus.PENDING));
    assertThat(data.ready(), is(1));
    assertThat(data.eligible(), is(2));
}

@Test
void givenAllEligibleSeatsRequest_whenLastRematch_thenBroadcastsStartedWithFreshState() {
    Bullshit game = (Bullshit) sessionService.createGame("bullshit", 2, GameMode.SOLO);
    GameId id = game.getId();
    String t0 = sessionService.tokenForSeat(id, new PlayerId(0));
    String t1 = sessionService.tokenForSeat(id, new PlayerId(1));
    controller.rematch(new GameActionPayload(id.uuid().toString(), t0));
    messaging.clear();

    controller.rematch(new GameActionPayload(id.uuid().toString(), t1));

    Response r = messaging.payloads.get(0);
    assertThat(r.getEventType(), is("REMATCH"));
    assertThat(((BullshitRematchEventData) r.getEventData()).status(), is(RematchStatus.STARTED));
    assertThat(r.getState(), instanceOf(BullshitDto.class));
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `cd backend && "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q test -Dtest=BullshitWebSocketControllerTest`
Expected: compile failure — 4-arg constructor / `rematch` not defined.

- [ ] **Step 4: Add the dependency + endpoint**

In `BullshitWebSocketController.java`, add imports:

```java
import org.kevinkib.cardgames.bullshit.presentation.dto.event.BullshitRematchEventData;
import org.kevinkib.cardgames.presentation.dto.event.RematchStatus;
import org.kevinkib.cardgames.sessionmanagement.core.application.RematchTally;
import org.kevinkib.cardgames.sessionmanagement.presence.application.SeatPresence;
import java.util.Set;
```

Add a field and constructor param:

```java
    private final SeatPresence seatPresence;

    public BullshitWebSocketController(SessionService sessionService,
                                       BullshitStateBroadcaster broadcaster,
                                       GameMessagingService messaging,
                                       SeatPresence seatPresence) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
        this.messaging = messaging;
        this.seatPresence = seatPresence;
    }
```

Add the handler (mirror the `start` handler's token-resolution + try/catch style):

```java
    @MessageMapping("/bullshit/rematch")
    public void rematch(@Payload GameActionPayload payload) {
        GameId gameId = new GameId(payload.gameId());
        PlayerId actor = sessionService.findPlayerIdByToken(gameId, payload.token()).orElse(null);
        if (actor == null) {
            return;
        }
        try {
            Set<PlayerId> eligible = seatPresence.activeSeats(gameId);
            RematchTally tally = sessionService.requestRematch(gameId, actor, eligible);
            if (tally.unanimous()) {
                Bullshit fresh = (Bullshit) sessionService.rematch(gameId);
                sessionService.touch(gameId);
                broadcaster.broadcast(fresh, LifecycleEventType.REMATCH.toString(),
                        new BullshitRematchEventData(RematchStatus.STARTED, tally.ready(), tally.eligible()),
                        "Rematch started.");
            } else {
                Bullshit current = sessionService.getGame(gameId, Bullshit.class);
                broadcaster.broadcast(current, LifecycleEventType.REMATCH.toString(),
                        new BullshitRematchEventData(RematchStatus.PENDING, tally.ready(), tally.eligible()),
                        "Rematch requested.");
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
```

(`LifecycleEventType` is already imported in this controller. `getGame(GameId, Class)` already exists and is used by `discard`.)

- [ ] **Step 5: Run to verify it passes**

Run: `cd backend && "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q test -Dtest=BullshitWebSocketControllerTest`
Expected: PASS.

- [ ] **Step 6: Full backend suite (confirms Spring context still wires the controller — `SeatPresence` resolves to the `PresenceService` bean)**

Run: `cd backend && "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" test`
Expected: BUILD SUCCESS, all tests green (265 + the new ones). If `ApplicationContextTest` fails because two `SeatPresence` beans exist or none, ensure `PresenceService` is the only `SeatPresence` implementation wired as a bean (it is — `presenceService()` in `AppConfig`).

- [ ] **Step 7: Commit**

```bash
git add -A backend/
git commit -m "feat(bullshit): /bullshit/rematch endpoint broadcasting per-seat REMATCH"
```

---

## PART B — Frontend

> Run `npm ci` from `frontend/` once before starting (worktree has no `node_modules`).

### Task 5: `BullshitSession.rematch()`

**Files:**
- Modify: `frontend/src/application/BullshitSession.ts`
- Test: `frontend/src/application/BullshitSession.test.ts`

- [ ] **Step 1: Write the failing test**

Read `BullshitSession.test.ts` for its fake `BullshitWebSocketPort` (it captures `publish` calls). Add a test mirroring the existing `discard`/`startGame` tests:

```ts
it('rematch publishes to /app/bullshit/rematch with gameId and token', () => {
  const published: { destination: string; body?: string }[] = [];
  const port = makePort({ onPublish: (destination, body) => published.push({ destination, body }) });
  const session = new BullshitSession(port, { onEvent: () => {} });
  session.restore('g1', 0, 'tok-0');

  session.rematch();

  const frame = published.find(p => p.destination === '/app/bullshit/rematch');
  expect(frame).toBeDefined();
  expect(JSON.parse(frame!.body!)).toEqual({ gameId: 'g1', token: 'tok-0' });
});
```

Adapt `makePort(...)` to however the existing tests build the fake port (read the file — reuse its helper).

- [ ] **Step 2: Run to verify it fails**

Run: `cd frontend && npx vitest run src/application/BullshitSession.test.ts`
Expected: FAIL — `session.rematch is not a function`.

- [ ] **Step 3: Implement**

In `BullshitSession.ts`, beside `startGame()`:

```ts
  rematch(): void {
    this.webSocket.publish('/app/bullshit/rematch', JSON.stringify({ gameId: this.gameId, token: this.myToken }));
  }
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd frontend && npx vitest run src/application/BullshitSession.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A frontend/
git commit -m "feat(frontend): BullshitSession.rematch()"
```

---

### Task 6: Rematch button model + store wiring

**Files:**
- Create: `frontend/src/model/bullshit/BullshitRematch.ts`
- Test: `frontend/src/model/bullshit/BullshitRematch.test.ts`
- Modify: `frontend/src/state/Bullshit.store.ts`
- Test: `frontend/src/state/Bullshit.store.test.ts`

- [ ] **Step 1: Write the failing model test**

```ts
import { describe, it, expect } from 'vitest';
import { bullshitRematchButton } from './BullshitRematch';

describe('bullshitRematchButton', () => {
  it('idle before I request', () => {
    expect(bullshitRematchButton({ iRequested: false, ready: 0, eligible: 0 }))
      .toEqual({ label: 'Play again', disabled: false });
  });

  it('waiting with progress after I request', () => {
    expect(bullshitRematchButton({ iRequested: true, ready: 1, eligible: 3 }))
      .toEqual({ label: 'Waiting… 1/3 ready', disabled: true });
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd frontend && npx vitest run src/model/bullshit/BullshitRematch.test.ts`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the model**

```ts
import type { RematchButton } from '../RematchButton';

export interface BullshitRematchProgress {
  iRequested: boolean;
  ready: number;
  eligible: number;
}

/** Bullshit's Play-Again button: idle until I click, then a waiting label with live progress. */
export function bullshitRematchButton(p: BullshitRematchProgress): RematchButton {
  if (!p.iRequested) return { label: 'Play again', disabled: false };
  return { label: `Waiting… ${p.ready}/${p.eligible} ready`, disabled: true };
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd frontend && npx vitest run src/model/bullshit/BullshitRematch.test.ts`
Expected: PASS.

- [ ] **Step 5: Write the failing store tests**

In `Bullshit.store.test.ts` (it uses `setActivePinia(createPinia())` per its existing setup — reuse it). Add:

```ts
it('tracks rematch progress from a PENDING REMATCH event', () => {
  const store = useBullshitStore();
  store.applyEvent({ type: 'event', eventType: 'REMATCH',
    eventData: { status: 'PENDING', ready: 1, eligible: 3 }, message: '' });

  expect(store.rematchReady).toBe(1);
  expect(store.rematchEligible).toBe(3);
});

it('rematch() marks me as requested so the button shows waiting', () => {
  const store = useBullshitStore();
  vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
  store.applyEvent({ type: 'event', eventType: 'REMATCH',
    eventData: { status: 'PENDING', ready: 1, eligible: 2 }, message: '' });

  store.rematch();

  expect(store.rematchButton).toEqual({ label: 'Waiting… 1/2 ready', disabled: true });
});
```

Add imports at the top of the test if missing: `import { vi } from 'vitest';` and `import webSocketService from '../service/WebSocketService';`.

- [ ] **Step 6: Run to verify it fails**

Run: `cd frontend && npx vitest run src/state/Bullshit.store.test.ts`
Expected: FAIL — `rematchReady`/`rematch`/`rematchButton` undefined.

- [ ] **Step 7: Wire the store**

In `Bullshit.store.ts`: add the import `import { bullshitRematchButton } from '../model/bullshit/BullshitRematch';`. Add refs near the other `ref(...)` declarations:

```ts
  const rematchRequested = ref(false);
  const rematchReady = ref(0);
  const rematchEligible = ref(0);
```

In `applyEvent`, extend the `'event'` case so it handles `REMATCH` (keep the existing CALL_BULLSHIT/reveal lines):

```ts
      case 'event':
        if (event.eventType === 'CALL_BULLSHIT') reveal.value = event.eventData as CallBullshitEventData;
        else reveal.value = null;
        if (event.eventType === 'REMATCH') {
          const d = event.eventData as { status: string; ready: number; eligible: number };
          rematchReady.value = d.ready;
          rematchEligible.value = d.eligible;
          if (d.status === 'STARTED') rematchRequested.value = false;
        }
        break;
```

Add the computed + action:

```ts
  const rematchButton = computed(() =>
    bullshitRematchButton({ iRequested: rematchRequested.value, ready: rematchReady.value, eligible: rematchEligible.value }));

  function requestRematch() {
    rematchRequested.value = true;
    session.rematch();
  }
```

Add to the store's returned object: `rematchRequested, rematchReady, rematchEligible, rematchButton,` and `rematch: requestRematch,`.

- [ ] **Step 8: Run to verify it passes**

Run: `cd frontend && npx vitest run src/state/Bullshit.store.test.ts`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add -A frontend/
git commit -m "feat(frontend): Bullshit rematch button model + store wiring"
```

---

### Task 7: End-game overlay on the Bullshit screen

**Files:**
- Modify: `frontend/src/view/bullshit/BullshitGameScreen.vue`
- Test: `frontend/src/view/bullshit/BullshitGameScreen.test.ts`

- [ ] **Step 1: Write the failing test**

Read `BullshitGameScreen.test.ts` for its mount harness (pinia + router + PrimeVue plugins, and any `data-test` query helpers). Add a test that drives the store into the finished phase and asserts the overlay renders and emits wire to `store.rematch`:

```ts
it('renders the end-game overlay when finished and play-again calls rematch', async () => {
  const store = useBullshitStore();
  store.applyEvent({ type: 'seat-change', seat: 0 });
  store.applyEvent({ type: 'state-update', state: {
    started: true,
    outcome: { status: 'FINISHED', winnerId: '0' },
    players: [{ id: '0', handCount: 0, isCurrentPlayer: false }, { id: '1', handCount: 3, isCurrentPlayer: false }],
    currentTarget: { label: 'Aces' },
    table: { state: 'EMPTY' },
    discardPileSize: 0,
    myHand: [],
    availableActions: [],
  } as any });

  const wrapper = mountScreen('g1');                 // reuse the file's existing mount helper
  const overlay = wrapper.findComponent(EndGameOverlay);
  expect(overlay.exists()).toBe(true);

  const spy = vi.spyOn(store, 'rematch');
  await overlay.vm.$emit('playAgain');
  expect(spy).toHaveBeenCalled();
});
```

Add imports: `import EndGameOverlay from '../../components/EndGameOverlay.vue';`, `import { vi } from 'vitest';`. If the test file has no `mountScreen` helper, mount inline the same way its other tests do (stub `RouterLink`, register PrimeVue). Match the real `BullshitState` shape in `model/bullshit/BullshitState.ts` if the fields above differ.

- [ ] **Step 2: Run to verify it fails**

Run: `cd frontend && npx vitest run src/view/bullshit/BullshitGameScreen.test.ts`
Expected: FAIL — no `EndGameOverlay` rendered (current finished block is a plain `<div>`).

- [ ] **Step 3: Implement**

In `BullshitGameScreen.vue` `<script setup>`, add `import EndGameOverlay from '../../components/EndGameOverlay.vue';`. Replace the finished block (currently):

```html
    <div v-else-if="store.phase === 'finished'" data-test="end" class="panel">
      <h2>{{ store.iWon ? 'You win!' : 'You lose' }}</h2>
    </div>
```

with:

```html
    <EndGameOverlay
      v-else-if="store.phase === 'finished'"
      data-test="end"
      :did-i-win="store.iWon"
      :subtitle="store.iWon ? 'You emptied your hand first.' : 'Another player emptied their hand first.'"
      :rematch-button="store.rematchButton"
      @play-again="store.rematch()"
    />
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd frontend && npx vitest run src/view/bullshit/BullshitGameScreen.test.ts`
Expected: PASS.

- [ ] **Step 5: Frontend gate — full unit run + build**

Run: `cd frontend && npx vitest run`
Expected: all suites pass.
Run: `cd frontend && npm run build`
Expected: build succeeds (the real type-check gate; a bare `vue-tsc` can give a false pass).

- [ ] **Step 6: Commit**

```bash
git add -A frontend/
git commit -m "feat(frontend): end-game overlay + rematch on the Bullshit screen"
```

---

## Final verification

- [ ] Backend full suite green: `cd backend && "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" test`
- [ ] Frontend build green: `cd frontend && npm run build`
- [ ] Manual smoke (optional): two browsers join a Bullshit game, finish it, both click Play Again → fresh game starts for both; a third tab that closed before the end does not block.

---

## Notes for the executor

- **Pure-feature, behavior-additive.** Do not alter BatailleCorse's rematch (`requestRematch(GameId, PlayerId)` and its `/rematch`) — the new core method is an additional overload.
- **`core` must not import `presence`.** The controller passes the eligible set in; never make `SessionService`/`SessionGame` reference `SeatPresence`/presence.
- **v1 limitation (intended):** unanimity is evaluated on each click. If an awaited player disconnects after others have clicked, the rematch fires on the next click, not instantly. Do not add presence-change listeners to "fix" this now.
- When a test references an existing helper/harness you don't see quoted here (fake WS port, store pinia setup, screen mount), open the test file and reuse its existing pattern rather than inventing a new one.

# Rematch ("Play Again") Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Play Again" button to the end-of-game overlay that restarts the game in place — for both solo (vs computer) and multiplayer (vs human) games.

**Architecture:** One backend rule governs both modes: reset the game in place once **every seat** has requested a rematch. Multiplayer requires both humans to consent; solo satisfies the rule instantly because the client holds both seat tokens. The reset re-deals a fresh deck into the **same game id**, preserving seats, tokens, and names. The frontend's end overlay drives a "Play Again" button whose label reflects the handshake state.

**Tech Stack:** Java / Spring (STOMP WebSocket), Vue 3 `<script setup>` + Pinia + TypeScript, Vitest, Cypress. Backend tests run via the IntelliJ-bundled `mvn` (no `mvnw` wrapper). Frontend gate is `npm run build` + `npx vitest run`.

**Spec:** `docs/superpowers/specs/2026-06-12-rematch-play-again-design.md`

---

## File Structure

**Backend — create:**
- `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/event/RematchStatus.java` — `PENDING | STARTED` enum.
- `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/event/RematchEventData.java` — event payload.
- `backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/domain/SessionGameTest.java` — domain unanimity tests.

**Backend — modify:**
- `.../sessionmanagement/domain/SessionPlayer.java` — add `rematchRequested` flag.
- `.../sessionmanagement/domain/SessionGame.java` — add `requestRematch` / `isRematchUnanimous` / `clearRematch`.
- `.../sessionmanagement/application/SessionService.java` — add `rematch(BatailleCorseId)`.
- `.../sessionmanagement/application/SessionServiceTest.java` (test) — `rematch()` behaviour.
- `.../websocket/presentation/v1/dto/event/EventType.java` — add `REMATCH`.
- `.../websocket/presentation/v1/BatailleCorseWebSocketController.java` — add `@MessageMapping("/rematch")`.
- `.../websocket/presentation/v1/BatailleCorseWebSocketControllerTest.java` (test) — endpoint behaviour.

**Frontend — modify:**
- `frontend/src/model/Response.ts` — add `REMATCH` to the event union.
- `frontend/src/model/event/RematchEventData.ts` — create the event-data type.
- `frontend/src/application/GameEvent.ts` — add `rematch` GameEvent variants.
- `frontend/src/application/GameSession.ts` — add `rematch()` + `REMATCH` handling.
- `frontend/src/application/GameSession.test.ts` (test) — solo + multiplayer rematch.
- `frontend/src/state/BatailleCorse.store.ts` — add `rematchState` + `rematch` action.
- `frontend/src/composables/useEndScreen.ts` — dismiss overlay when state is no longer over.
- `frontend/src/composables/useEndScreen.test.ts` (test) — dismiss-on-reset.
- `frontend/src/view/alpha/GameScreen.vue` — "Play Again" button + wiring.
- `frontend/cypress/specs/rematch.cy.ts` — create: solo "Play Again" loop.

---

## Task 1: Backend domain — rematch request flag on `SessionPlayer`

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/domain/SessionPlayer.java`

This task has no standalone test; it is exercised by `SessionGameTest` in Task 2. It is a tiny field addition mirroring the existing `claimed` flag.

- [ ] **Step 1: Add the flag and accessors**

Add a `rematchRequested` boolean (default `false`) with mutators and a getter, mirroring `claimed`.

```java
package org.kevinkib.bataillecorse.sessionmanagement.domain;

import org.kevinkib.bataillecorse.core.domain.PlayerId;

public class SessionPlayer {

    private final PlayerId id;
    private final SessionToken token;
    private boolean claimed;
    private String name;
    private boolean rematchRequested;

    public SessionPlayer(PlayerId id, SessionToken token) {
        this.id = id;
        this.token = token;
        this.claimed = false;
        this.name = null;
        this.rematchRequested = false;
    }

    public void claim(String name) {
        this.claimed = true;
        this.name = name;
    }

    public void requestRematch() {
        this.rematchRequested = true;
    }

    public void clearRematch() {
        this.rematchRequested = false;
    }

    public boolean hasRequestedRematch() {
        return rematchRequested;
    }

    public PlayerId id() {
        return id;
    }

    public SessionToken token() {
        return token;
    }

    public boolean isClaimed() {
        return claimed;
    }

    public String name() {
        return name;
    }
}
```

- [ ] **Step 2: Compile**

Run: `mvn -q -pl backend compile` (from repo root; if that fails, run `mvn -q compile` inside `backend/`)
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/domain/SessionPlayer.java
git commit -m "feat(session): add rematch-requested flag to SessionPlayer"
```

---

## Task 2: Backend domain — the unanimity rule on `SessionGame`

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/domain/SessionGame.java`
- Test: `backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/domain/SessionGameTest.java`

- [ ] **Step 1: Write the failing test**

Create `SessionGameTest.java`. It builds a real `SessionGame` from a real 2-player `BatailleCorse` (no Mockito on domain classes, per project rules).

```java
package org.kevinkib.bataillecorse.sessionmanagement.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.PlayerId;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class SessionGameTest {

    private SessionGame session;

    @BeforeEach
    void setUp() {
        BatailleCorse game = new BatailleCorse(BatailleCorseId.generate(), 2);
        session = SessionGame.create(game.getId(), game.getPlayers());
    }

    @Test
    void givenNoSeatRequested_whenIsRematchUnanimous_thenFalse() {
        assertThat(session.isRematchUnanimous(), is(false));
    }

    @Test
    void givenOneOfTwoSeatsRequested_whenIsRematchUnanimous_thenFalse() {
        session.requestRematch(new PlayerId(0));

        assertThat(session.isRematchUnanimous(), is(false));
    }

    @Test
    void givenAllSeatsRequested_whenIsRematchUnanimous_thenTrue() {
        session.requestRematch(new PlayerId(0));
        session.requestRematch(new PlayerId(1));

        assertThat(session.isRematchUnanimous(), is(true));
    }

    @Test
    void givenUnanimousRematch_whenClearRematch_thenNoLongerUnanimous() {
        session.requestRematch(new PlayerId(0));
        session.requestRematch(new PlayerId(1));

        session.clearRematch();

        assertThat(session.isRematchUnanimous(), is(false));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -pl backend test -Dtest=SessionGameTest`
Expected: COMPILE FAILURE — `requestRematch` / `isRematchUnanimous` / `clearRematch` do not exist on `SessionGame`.

- [ ] **Step 3: Implement the rule on `SessionGame`**

Add the three methods. `isRematchUnanimous` requires the game to have seats and every seat to have requested.

```java
    public void requestRematch(PlayerId playerId) {
        SessionPlayer seat = players.get(playerId);
        if (seat == null) {
            throw new IllegalArgumentException("Unknown seat " + playerId.id());
        }
        seat.requestRematch();
    }

    public boolean isRematchUnanimous() {
        return !players.isEmpty()
                && players.values().stream().allMatch(SessionPlayer::hasRequestedRematch);
    }

    public void clearRematch() {
        players.values().forEach(SessionPlayer::clearRematch);
    }
```

Place them alongside the existing `claim` / `isClaimed` methods inside the `SessionGame` record body.

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -pl backend test -Dtest=SessionGameTest`
Expected: PASS (4 tests green).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/domain/SessionGame.java \
        backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/domain/SessionGameTest.java
git commit -m "feat(session): add rematch unanimity rule to SessionGame"
```

---

## Task 3: Backend application — reset in place via `SessionService.rematch`

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/SessionService.java`
- Test: `backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/application/SessionServiceTest.java`

- [ ] **Step 1: Write the failing test**

Add a nested class to the existing `SessionServiceTest`. It asserts the reset keeps the same id, the same claimed seats with the same names, and the same tokens, while producing a fresh (not finished) game. Add these imports near the top of the file if not already present: `org.kevinkib.bataillecorse.core.domain.BatailleCorseId` is not needed (use `game.getId()`).

```java
    @Nested
    class RematchTest {

        @Test
        void givenSoloGame_whenRematch_thenSameIdAndSeatsPreserved() {
            BatailleCorse game = service.createGame(2, GameMode.SOLO, "Alice");
            SessionToken seat0TokenBefore = service.loadTokenByPlayerId(game.getId(), new PlayerId(0));

            BatailleCorse fresh = service.rematch(game.getId());

            assertThat(fresh.getId(), is(game.getId()));
            assertThat(fresh.isFinished(), is(false));
            assertThat(service.isSeatClaimed(game.getId(), new PlayerId(0)), is(true));
            assertThat(service.isSeatClaimed(game.getId(), new PlayerId(1)), is(true));
            assertThat(service.getSeats(game.getId()).get(0).name(), is("Alice"));
            assertThat(service.loadTokenByPlayerId(game.getId(), new PlayerId(0)), is(seat0TokenBefore));
        }

        @Test
        void givenRequestedRematch_whenRematch_thenRequestFlagsCleared() {
            BatailleCorse game = service.createGame(2, GameMode.SOLO);
            service.getGameSession(game.getId()).requestRematch(new PlayerId(0));
            service.getGameSession(game.getId()).requestRematch(new PlayerId(1));

            service.rematch(game.getId());

            assertThat(service.getGameSession(game.getId()).isRematchUnanimous(), is(false));
        }
    }
```

This test references `service.getGameSession(...)`. That accessor does not exist yet — add it in Step 3 (it returns the `SessionGame` so the handshake state can be inspected/mutated; the controller in Task 5 also needs it).

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -pl backend test -Dtest=SessionServiceTest`
Expected: COMPILE FAILURE — `rematch` and `getGameSession` do not exist on `SessionService`.

- [ ] **Step 3: Implement `rematch` and `getGameSession`**

In `SessionService`, add (use the existing `NB_PLAYERS`-style constant — `BatailleCorse` is constructed with `nbPlayers`, and the repository already keys games by id):

```java
    public SessionGame getGameSession(BatailleCorseId id) {
        return repository.loadSessionGame(id);
    }

    public BatailleCorse rematch(BatailleCorseId id) {
        SessionGame session = repository.loadSessionGame(id);
        BatailleCorse fresh = new BatailleCorse(id, session.seats().size());
        session.clearRematch();
        repository.save(fresh, session);
        return fresh;
    }
```

Add the import for `SessionGame` if not already present (it is already imported in `SessionService`).

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -pl backend test -Dtest=SessionServiceTest`
Expected: PASS (all existing tests plus the two new ones).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/SessionService.java \
        backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/application/SessionServiceTest.java
git commit -m "feat(session): reset game in place via SessionService.rematch"
```

---

## Task 4: Backend transport — `REMATCH` event type + data DTOs

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/event/EventType.java`
- Create: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/event/RematchStatus.java`
- Create: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/event/RematchEventData.java`

This task is plumbing types only; it is exercised by the controller test in Task 5.

- [ ] **Step 1: Add `REMATCH` to `EventType`**

```java
public enum EventType {

    CREATE,
    SEND,
    SLAP,
    GRAB,
    JOIN,
    OPPONENT_DISCONNECTED,
    OPPONENT_RECONNECTED,
    FORFEIT,
    REMATCH;

    @Override
    public String toString() {
        return name();
    }
}
```

- [ ] **Step 2: Create `RematchStatus`**

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event;

public enum RematchStatus {
    PENDING,
    STARTED;
}
```

- [ ] **Step 3: Create `RematchEventData`**

`requestedBy` is the seat that just requested (null is acceptable for `STARTED`, where no single seat is meaningful — but we always pass the triggering seat for symmetry).

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event;

import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.PlayerIdDto;

public record RematchEventData(RematchStatus status, PlayerIdDto requestedBy) implements EventData {
}
```

- [ ] **Step 4: Compile**

Run: `mvn -q -pl backend compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/event/EventType.java \
        backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/event/RematchStatus.java \
        backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/event/RematchEventData.java
git commit -m "feat(transport): add REMATCH event type and data DTOs"
```

---

## Task 5: Backend transport — the `/app/rematch` endpoint

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/BatailleCorseWebSocketController.java`
- Test: `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/BatailleCorseWebSocketControllerTest.java`

- [ ] **Step 1: Write the failing tests**

Add a nested class to the existing controller test. It verifies: (a) a single multiplayer request broadcasts a `PENDING` success; (b) once both seats have requested (solo case — both tokens held locally), the broadcast is `STARTED`.

```java
    @Nested
    class RematchTest {

        @Test
        void givenSoloBothSeatsRequest_whenSecondRematch_thenBroadcastsStarted() {
            var game = sessionService.createGame(2, org.kevinkib.bataillecorse.sessionmanagement.domain.GameMode.SOLO);
            String gameId = game.getId().uuid().toString();
            SessionToken token0 = sessionService.loadTokenByPlayerId(game.getId(), new PlayerId(0));
            SessionToken token1 = sessionService.loadTokenByPlayerId(game.getId(), new PlayerId(1));

            controller.rematch(new GameActionPayload(gameId, token0.uuid().toString())); // PENDING
            clearInvocations(template);

            controller.rematch(new GameActionPayload(gameId, token1.uuid().toString())); // STARTED

            verify(template).convertAndSend(
                    eq("/topic/game/" + gameId),
                    (Object) argThat(r -> ((Response) r).isSuccess()
                            && ((org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.RematchEventData)
                                    ((Response) r).getEventData()).status()
                               == org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.RematchStatus.STARTED)
            );
        }

        @Test
        void givenMultiplayerSingleRequest_whenRematch_thenBroadcastsPending() {
            var game = sessionService.createGame(2, org.kevinkib.bataillecorse.sessionmanagement.domain.GameMode.MULTIPLAYER);
            sessionService.joinGame(game.getId()); // claim seat 1 so the game has two humans
            String gameId = game.getId().uuid().toString();
            SessionToken token0 = sessionService.loadTokenByPlayerId(game.getId(), new PlayerId(0));

            controller.rematch(new GameActionPayload(gameId, token0.uuid().toString()));

            verify(template).convertAndSend(
                    eq("/topic/game/" + gameId),
                    (Object) argThat(r -> ((Response) r).isSuccess()
                            && ((org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.RematchEventData)
                                    ((Response) r).getEventData()).status()
                               == org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.RematchStatus.PENDING)
            );
        }

        @Test
        void givenInvalidToken_whenRematch_thenDoesNotBroadcast() {
            var game = sessionService.createGame(2);
            String gameId = game.getId().uuid().toString();

            controller.rematch(new GameActionPayload(gameId, SessionToken.generate().uuid().toString()));

            verify(template, org.mockito.Mockito.never()).convertAndSend(
                    eq("/topic/game/" + gameId), (Object) org.mockito.ArgumentMatchers.any());
        }
    }
```

(Mockito is used here only on the Spring `SimpMessagingTemplate` infrastructure double — never on domain classes — consistent with the existing tests in this file.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q -pl backend test -Dtest=BatailleCorseWebSocketControllerTest`
Expected: COMPILE FAILURE — `controller.rematch(...)` does not exist.

- [ ] **Step 3: Implement the endpoint**

Add to `BatailleCorseWebSocketController`. On an invalid token, log and return without broadcasting (matches the forfeit handler's tolerance). On a valid token: record the request; if unanimous, reset in place and broadcast `STARTED` with the fresh state; otherwise broadcast `PENDING` with the current state.

```java
    @MessageMapping("/rematch")
    public void rematch(GameActionPayload payload) {
        BatailleCorseId gameId = new BatailleCorseId(payload.gameId());
        try {
            PlayerId playerId = sessionService
                    .findPlayerIdByToken(gameId, new SessionToken(payload.token()))
                    .orElseThrow(InvalidTokenException::new);

            sessionService.getGameSession(gameId).requestRematch(playerId);

            Response response;
            if (sessionService.getGameSession(gameId).isRematchUnanimous()) {
                BatailleCorse fresh = sessionService.rematch(gameId);
                response = new SuccessResponse(
                        EventType.REMATCH,
                        new RematchEventData(RematchStatus.STARTED, new PlayerIdDto(String.valueOf(playerId.id()))),
                        "Rematch started.",
                        BatailleCorseDto.from(fresh));
            } else {
                BatailleCorse current = sessionService.getGame(gameId);
                response = new SuccessResponse(
                        EventType.REMATCH,
                        new RematchEventData(RematchStatus.PENDING, new PlayerIdDto(String.valueOf(playerId.id()))),
                        "Rematch requested.",
                        BatailleCorseDto.from(current));
            }

            gameMessagingService.sendToGame(payload.gameId(), response);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
```

Add the needed imports to the controller (the package already wildcard-imports `dto.event.*` and `dto.*`, so `RematchEventData`, `RematchStatus`, `PlayerIdDto`, and `BatailleCorseDto` resolve; `InvalidTokenException`, `SessionToken`, `BatailleCorseId`, `PlayerId`, `BatailleCorse`, and `SuccessResponse` are already imported).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl backend test -Dtest=BatailleCorseWebSocketControllerTest`
Expected: PASS (existing send/slap/grab tests plus the three new rematch tests).

- [ ] **Step 5: Run the full backend suite**

Run: `mvn -q -pl backend test`
Expected: BUILD SUCCESS, all green.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/BatailleCorseWebSocketController.java \
        backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/BatailleCorseWebSocketControllerTest.java
git commit -m "feat(transport): add /app/rematch endpoint with both-accept handshake"
```

---

## Task 6: Frontend model — `REMATCH` response type + event data

**Files:**
- Modify: `frontend/src/model/Response.ts`
- Create: `frontend/src/model/event/RematchEventData.ts`

- [ ] **Step 1: Add `REMATCH` to the `Response` event union**

```ts
import BatailleCorse from "./BatailleCorse";
import EventData from "./event/EventData";

export default interface Response {
  success: boolean,
  eventType: "CREATE" | "SEND" | "SLAP" | "GRAB" | "JOIN" | "OPPONENT_DISCONNECTED" | "OPPONENT_RECONNECTED" | "FORFEIT" | "REMATCH",
  eventData: EventData,
  message: string,
  state: BatailleCorse,
}
```

- [ ] **Step 2: Create the `RematchEventData` type**

Mirror the existing `SendEventData.ts` shape (look at the sibling files in `frontend/src/model/event/` to match the export style — they use a default-exported interface).

```ts
export default interface RematchEventData {
  status: 'PENDING' | 'STARTED';
  requestedBy: { id: string };
}
```

- [ ] **Step 3: Type-check via build**

Run: `cd frontend && npm run build`
Expected: build succeeds (worktrees lack `node_modules`; run `npm ci` first if the build reports missing modules — see `reference_frontend_verification`).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/model/Response.ts frontend/src/model/event/RematchEventData.ts
git commit -m "feat(model): add REMATCH response type and event data"
```

---

## Task 7: Frontend — `GameSession.rematch()` + `REMATCH` handling

**Files:**
- Modify: `frontend/src/application/GameEvent.ts`
- Modify: `frontend/src/application/GameSession.ts`
- Test: `frontend/src/application/GameSession.test.ts`

- [ ] **Step 1: Add the `rematch` GameEvent variants**

Append to the union in `GameEvent.ts`:

```ts
  | { type: 'rematch'; status: 'pending'; requestedBy: number }
  | { type: 'rematch'; status: 'started' };
```

(Place these before the closing `;` of the existing union — i.e. replace the final `... seat: number };` line's terminating `;` so the union continues, then add the two variants ending with `;`.)

- [ ] **Step 2: Write the failing tests**

Add a `describe('REMATCH', ...)` block to `GameSession.test.ts`. First add a fixture helper at the top of the file (after the existing imports) for building a rematch response — keep it local to the test to avoid touching shared fixtures:

```ts
function buildRematchResponse(status: 'PENDING' | 'STARTED', requestedById: string) {
  return buildResponse({
    eventType: 'REMATCH',
    eventData: { status, requestedBy: { id: requestedById } },
  });
}
```

Then the tests:

```ts
  describe('REMATCH', () => {
    it('solo rematch() publishes /app/rematch for BOTH seat tokens', async () => {
      const { session, published } = makeSession();
      session.create('solo');
      await session.onResponse(buildCreateResponse('game-1', { 0: 'tok-a', 1: 'tok-b' }));

      session.rematch();

      const rematchPublishes = published.filter(p => p.dest === '/app/rematch');
      expect(rematchPublishes).toHaveLength(2);
      const tokens = rematchPublishes.map(p => JSON.parse(p.body!).token).sort();
      expect(tokens).toEqual(['tok-a', 'tok-b']);
    });

    it('multiplayer rematch() publishes only for the local seat and emits pending', async () => {
      const { session, published, events } = makeSession();
      session.create('multiplayer', 'Alice');
      await session.onResponse(buildCreateResponse('game-1', { 0: 'tok-a' }));

      session.rematch();

      const rematchPublishes = published.filter(p => p.dest === '/app/rematch');
      expect(rematchPublishes).toHaveLength(1);
      expect(JSON.parse(rematchPublishes[0].body!).token).toBe('tok-a');
      expect(events).toContainEqual({ type: 'rematch', status: 'pending', requestedBy: 0 });
    });

    it('REMATCH pending from opponent emits a pending event with their seat', async () => {
      const { session, events } = makeSession();
      session.create('multiplayer', 'Alice');
      await session.onResponse(buildCreateResponse('game-1', { 0: 'tok-a' }));

      await session.onResponse(buildRematchResponse('PENDING', '1'));

      expect(events).toContainEqual({ type: 'rematch', status: 'pending', requestedBy: 1 });
    });

    it('REMATCH started emits a started event', async () => {
      const { session, events } = makeSession();
      session.create('solo');
      await session.onResponse(buildCreateResponse('game-1', { 0: 'tok-a', 1: 'tok-b' }));

      await session.onResponse(buildRematchResponse('STARTED', '0'));

      expect(events).toContainEqual({ type: 'rematch', status: 'started' });
    });
  });
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd frontend && npx vitest run src/application/GameSession.test.ts`
Expected: FAIL — `session.rematch` is not a function / no `rematch` events emitted.

- [ ] **Step 4: Implement `rematch()` and `REMATCH` handling**

In `GameSession.ts`, import the type at the top:

```ts
import type RematchEventData from '../model/event/RematchEventData';
```

Add the public method (near `forfeit`):

```ts
  /**
   * Requests a rematch. Solo holds both seat tokens, so it requests for every
   * seat at once and the server's unanimity rule fires immediately. Multiplayer
   * requests only the local seat and optimistically shows "waiting".
   */
  rematch(): void {
    const seats = this.mode === 'solo'
      ? Object.keys(this.playerTokens).map(Number)
      : [this.myPlayerIndex];
    for (const seat of seats) {
      this.webSocket.publish('/app/rematch', JSON.stringify({
        gameId: this.gameId,
        token: this.playerTokens[seat],
      }));
    }
    if (this.mode === 'multiplayer') {
      this.callbacks.onEvent({ type: 'rematch', status: 'pending', requestedBy: this.myPlayerIndex });
    }
  }
```

Add the `REMATCH` branch inside `processEvent`, **before** the common state-update tail (so the AI is re-initialised before `ai.play` runs on the fresh state). Place it after the `OPPONENT_RECONNECTED` block:

```ts
    if (response.eventType === 'REMATCH') {
      const data = response.eventData as unknown as RematchEventData;
      if (data.status === 'STARTED') {
        this.cancelAll();
        if (this.mode === 'solo') this.ai = this.aiFactory();
        this.callbacks.onEvent({ type: 'rematch', status: 'started' });
      } else if (this.mode !== 'solo') {
        const requestedBy = Number(data.requestedBy?.id);
        if (!isNaN(requestedBy)) {
          this.callbacks.onEvent({ type: 'rematch', status: 'pending', requestedBy });
        }
      }
    }
```

The common tail then runs `BatailleCorse.fromJSON(response.state...)` → `state-update`, which carries the fresh dealt board to the store and view.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd frontend && npx vitest run src/application/GameSession.test.ts`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/application/GameEvent.ts frontend/src/application/GameSession.ts frontend/src/application/GameSession.test.ts
git commit -m "feat(session): add rematch request + REMATCH handling to GameSession"
```

---

## Task 8: Frontend store — `rematchState` + `rematch` action

**Files:**
- Modify: `frontend/src/state/BatailleCorse.store.ts`

This is wiring exercised by the view (Task 10) and indirectly by the GameSession tests; no dedicated store test exists in the codebase for this store, so none is added.

- [ ] **Step 1: Add the `rematchState` ref**

After the `opponentConnection` ref declaration:

```ts
  const rematchState = ref<'idle' | 'requested-by-me' | 'requested-by-opponent'>('idle');
```

- [ ] **Step 2: Handle the `rematch` event in `onEvent`**

Add a case to the `switch (event.type)` block:

```ts
          case 'rematch':
            if (event.status === 'started') {
              rematchState.value = 'idle';
            } else {
              rematchState.value = event.requestedBy === myPlayerIndex.value
                ? 'requested-by-me'
                : 'requested-by-opponent';
            }
            break;
```

- [ ] **Step 3: Reset `rematchState` on a fresh game and expose the action**

In the returned object, add `rematchState` to the exposed refs and add a `rematch` action. Also reset `rematchState` to `'idle'` inside the `create` action wrapper so a brand-new game never inherits a stale handshake state:

```ts
    rematchState,
    create:               (gameMode: 'solo' | 'multiplayer', name?: string) => { rematchState.value = 'idle'; session.create(gameMode, name); },
    ...
    rematch:              () => session.rematch(),
```

(Keep the rest of the returned object unchanged; only the `create` line is modified and the two new entries added.)

- [ ] **Step 4: Type-check via build**

Run: `cd frontend && npm run build`
Expected: build succeeds.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/state/BatailleCorse.store.ts
git commit -m "feat(store): mirror rematch handshake state and expose rematch action"
```

---

## Task 9: Frontend — dismiss the end overlay on reset (`useEndScreen`)

**Files:**
- Modify: `frontend/src/composables/useEndScreen.ts`
- Test: `frontend/src/composables/useEndScreen.test.ts`

- [ ] **Step 1: Write the failing test**

Add to `useEndScreen.test.ts`:

```ts
  it('givenOverlayShown_whenStateBecomesNotOver_thenOverlayHides', async () => {
    const over = ref(true);
    const animating = ref(false);
    const { showEndOverlay } = useEndScreen(() => over.value, () => animating.value);

    over.value = true;
    await nextTick();
    vi.advanceTimersByTime(END_SCREEN_DELAY_MS);
    expect(showEndOverlay.value).toBe(true);

    // A rematch deals a fresh, not-over game.
    over.value = false;
    await nextTick();
    expect(showEndOverlay.value).toBe(false);
  });
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend && npx vitest run src/composables/useEndScreen.test.ts`
Expected: FAIL — overlay stays `true` because nothing sets it back to `false`.

- [ ] **Step 3: Implement the dismiss**

In `useEndScreen.ts`, update the watch so that when the game is no longer over it cancels any pending reveal and hides the overlay:

```ts
  // Reveal once the game is over AND the board has settled (no pile animation).
  // When the game is no longer over (e.g. a rematch dealt a fresh board), cancel
  // any pending reveal and hide the overlay.
  const stopWatch = watch(
    [() => isOver(), () => isAnimating()],
    ([over, animating]) => {
      if (over && !animating) {
        scheduleReveal();
      } else if (!over) {
        if (timeoutId !== null) { clearTimeout(timeoutId); timeoutId = null; }
        showEndOverlay.value = false;
      }
    },
  );
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd frontend && npx vitest run src/composables/useEndScreen.test.ts`
Expected: PASS (all existing tests plus the new one).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/composables/useEndScreen.ts frontend/src/composables/useEndScreen.test.ts
git commit -m "feat(end-screen): dismiss overlay when a fresh game is dealt"
```

---

## Task 10: Frontend — the "Play Again" button (`GameScreen.vue`)

**Files:**
- Modify: `frontend/src/view/alpha/GameScreen.vue`

The end overlay (`GameScreen.vue:105-116`) currently has only a "Back to home" link. Add a "Play Again" button above it whose label/disabled state reflects `rematchState`.

- [ ] **Step 1: Expose `rematchState` and add the click handler in `<script setup>`**

In the `storeToRefs(batailleCorseStore)` destructuring (around line 175), add `rematchState`:

```ts
const { state: batailleCorse, mode, myPlayerIndex, waiting, myName, opponentName, opponentConnection,
        lastSend, lastGrab, lastSlap, lastSuccessfulSlap, lastErroneousSlap, rematchState } = storeToRefs(batailleCorseStore);
```

Add a computed describing the button, plus a handler (place near the other end-of-game computeds around line 310, after `didIWin`):

```ts
const rematchButton = computed(() => {
  if (isSolo.value) return { label: 'Play Again', disabled: false };
  switch (rematchState.value) {
    case 'requested-by-me':       return { label: 'Waiting for opponent…', disabled: true };
    case 'requested-by-opponent': return { label: 'Accept Rematch', disabled: false };
    default:                      return { label: 'Play Again', disabled: false };
  }
});

function onPlayAgain() {
  batailleCorseStore.rematch();
}
```

- [ ] **Step 2: Add the button to the end overlay template**

Replace the end-card action area (the `RouterLink` block at `GameScreen.vue:112-114`) so "Play Again" sits above "Back to home":

```html
        <div class="end-actions">
          <Button
            class="end-replay-button"
            :label="rematchButton.label"
            :disabled="rematchButton.disabled"
            icon="pi pi-replay"
            severity="success"
            rounded
            data-cy="play-again"
            @click="onPlayAgain"
          />
          <RouterLink to="/" class="end-home-button">
            <Button label="Back to home" icon="pi pi-home" severity="secondary" text rounded />
          </RouterLink>
        </div>
```

- [ ] **Step 3: Add minimal styles**

Add to the component's `<style scoped>` (near the existing `.end-overlay` / `.end-home-button` rules around line 881):

```css
.end-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}
```

- [ ] **Step 4: Verify build and the existing GameScreen test**

Run: `cd frontend && npm run build && npx vitest run src/view/alpha/GameScreen.test.ts`
Expected: build succeeds; `GameScreen.test.ts` still passes (if it asserts on overlay contents, adjust those assertions to account for the new button — keep the existing victory/defeat assertions intact).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/view/alpha/GameScreen.vue
git commit -m "feat(ui): add Play Again button to end-of-game overlay"
```

---

## Task 11: E2E — solo "Play Again" loop (Cypress)

**Files:**
- Create: `frontend/cypress/specs/rematch.cy.ts`

Mirror an existing spec (e.g. `frontend/cypress/specs/create-game.cy.ts`) for app bootstrapping, selectors, and how a solo game is driven to completion. Read it first to reuse its helpers/commands rather than inventing new ones.

- [ ] **Step 1: Write the spec**

The exact steps to *finish* a solo game depend on existing helpers; reuse whatever `create-game.cy.ts` / `send-card.cy.ts` use. The spec must:

```ts
describe('rematch (play again)', () => {
  it('solo: Play Again deals a fresh game and clears the end overlay', () => {
    // 1. Start a solo game vs computer (reuse the create-game helper/flow).
    // 2. Drive it to completion until [data-cy="end-overlay"] is visible.
    // 3. Click [data-cy="play-again"].
    // 4. Assert the end overlay is gone and the board is playable again:
    cy.get('[data-cy="play-again"]').click();
    cy.get('[data-cy="end-overlay"]').should('not.exist');
    // 5. Assert a fresh board (e.g. both card counters back to full / pile empty),
    //    using the same selectors the other specs use to read board state.
  });
});
```

Fill in steps 1–2 and 5 with the concrete selectors/commands found in the sibling specs. Do not leave pseudocode in the committed file — every line must be runnable Cypress.

- [ ] **Step 2: Run the spec**

Run: `cd frontend && npx cypress run --spec cypress/specs/rematch.cy.ts`
Expected: PASS. (If the suite needs the dev server / docker stack running, start it the way the other specs document.)

- [ ] **Step 3: Commit**

```bash
git add frontend/cypress/specs/rematch.cy.ts
git commit -m "test(e2e): cover solo Play Again loop"
```

---

## Task 12: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Backend suite**

Run: `mvn -q -pl backend test`
Expected: BUILD SUCCESS, all green.

- [ ] **Step 2: Frontend unit suite + build**

Run: `cd frontend && npx vitest run && npm run build`
Expected: all tests pass; build succeeds (this is the real type-check gate — see `reference_frontend_verification`).

- [ ] **Step 3: Manual smoke (optional but recommended)**

- Solo: finish a game → "Play Again" → fresh board, no overlay, AI resumes.
- Multiplayer (two browser tabs): finish → tab A "Play Again" shows "Waiting for opponent…", tab B shows "Accept Rematch" → tab B accepts → both get a fresh board in the same room.

- [ ] **Step 4: Final commit (if any verification fixes were needed)**

```bash
git add -A
git commit -m "chore: rematch feature verification fixes"
```

---

## Notes / edge cases (from the spec)

- **Opponent left / forfeited:** their seat can't request, so a multiplayer proposal stays pending and the proposer keeps seeing "Waiting for opponent…". No timeout is built in v1. This is expected behaviour, not a bug.
- **Both click simultaneously:** the flag-based rule is idempotent; whichever request arrives second triggers `STARTED`.
- **Stale animation refs:** the fresh `STARTED` state flows through the normal `state-update` path; `lastSend`/`lastGrab`/`lastSlap` are never set during a rematch, so no stale move replays. Confirm during the Task 12 manual smoke.

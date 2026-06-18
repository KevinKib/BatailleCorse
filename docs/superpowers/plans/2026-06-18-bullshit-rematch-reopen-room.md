# Bullshit Rematch via Reopen-the-Room Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Bullshit's bespoke rematch tally with "reopen the room": a rematch clears the finished game, resets the room to an empty lobby, and players re-join via the normal `joinRoom`/`startGame` flow (first to click Play Again = seat 0 / host).

**Architecture:** One new atomic facade op, `SessionService.playAgain` (reopen-if-a-game-is-present, then claim a seat), behind `POST /api/bullshit/game/{id}/play-again`. Everything else reuses the existing lobby flow. This deletes the bespoke Bullshit rematch path (tally/outcome/staying/`leftRematch`/`BullshitRematchEventData`/the rematch WS endpoints). BatailleCorse's instant 2-player rematch is untouched. The frontend has no separate lobby route — `/games/bullshit/room/:id` renders lobby/playing/finished by `store.phase` — so `playAgain` mirrors `join` (re-bind + re-hydrate) and the screen flips back to the lobby reactively; no navigation.

**Tech Stack:** Java 17, Spring Boot, JUnit 5 + Hamcrest. Vue 3 `<script setup>` + TS, Pinia, Vitest. Maven from `backend/` (no `./mvnw`): `"C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" test` (add `-q -Dtest=<Class>` for one class). Frontend from `frontend/` (`node_modules` already installed): unit `npx vitest run <path>`, gate `npm run build`.

**Source spec:** `docs/superpowers/specs/2026-06-18-bullshit-rematch-reopen-room-design.md`

---

## File Structure

**Backend — modified**
- `sessionmanagement/core/application/SessionService.java` — add `playAgain`; later remove `joinRematch`/`leaveRematch`/`settleRematch`.
- `bullshit/presentation/BullshitRestController.java` — add `POST .../play-again`.
- `bullshit/presentation/BullshitWebSocketController.java` — remove `rematch`/`leaveRematch`/`settleRematch`.
- `sessionmanagement/core/domain/SessionGame.java` — remove `leaveRematch`/`rematchStayingCount`/`rematchReadyCount`/`isRematchReady`/`staying`.
- `sessionmanagement/core/domain/SessionPlayer.java` — remove the `leftRematch` flag and its methods.

**Backend — deleted**
- `sessionmanagement/core/application/RematchOutcome.java`
- `bullshit/presentation/dto/event/BullshitRematchEventData.java`

**Frontend — modified**
- `application/BullshitSession.ts` — replace `rematch`/`leaveRematch` with `playAgain`.
- `state/Bullshit.store.ts` — replace rematch-progress state/actions with a `playAgain` action.
- `view/bullshit/BullshitGameScreen.vue` — Play Again → `store.playAgain()`; drop `@leave`; static rematch button.
- `components/EndGameOverlay.vue` — remove the `leave` emit (keep `rematchButton`/`playAgain`; BatailleCorse still uses them).

---

## PART A — Backend

### Task 1: `SessionService.playAgain` (reopen + claim, atomic)

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/core/application/SessionService.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/core/application/SessionServiceTest.java`

- [ ] **Step 1: Write the failing test.** Add to `SessionServiceTest` (self-contained — builds its own service so it doesn't depend on the class's `@BeforeEach` factory set). Add any missing imports: `org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated`, `org.kevinkib.cardgames.game.Game`, `org.kevinkib.cardgames.bullshit.domain.BullshitFactory`, `org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository`, `java.time.Clock`, `java.util.List`.

```java
@Test
void playAgain_reopensRoom_andNextGameHasOnlyReturningPlayers() {
    var service = new SessionService(
            new InMemorySessionRepository(Clock.systemUTC()),
            new GameFactories(List.of(new BullshitFactory())));
    RoomCreated room = service.createRoom("bullshit", "Alice");
    GameId id = new GameId(room.gameId());
    service.joinRoom(id, "Bob");
    service.joinRoom(id, "Cara");                  // 3 joined of a 6-seat room
    service.startGame(id, room.hostToken());       // game dealt to 3

    JoinResult first = service.playAgain(id, "Alice");  // reopens -> seat 0 (host)
    JoinResult second = service.playAgain(id, "Bob");   // joins reopened lobby -> seat 1
    Game fresh = service.startGame(id, first.token());

    assertThat(first.playerId(), is(new PlayerId(0)));
    assertThat(second.playerId(), is(new PlayerId(1)));
    assertThat(fresh.getPlayerIds().size(), is(2));     // only the two who came back, not 3 or 6
}
```

- [ ] **Step 2: Run to verify it fails:** `cd backend && "...mvn.cmd" -q test -Dtest=SessionServiceTest` → compile failure (`playAgain` undefined).

- [ ] **Step 3: Implement `playAgain`.** In `SessionService.java`, add (next to `joinRoom`):

```java
    /**
     * Re-join the room for a rematch. If a game is present (finished or not), the room is first
     * reopened — the game is dropped and the lobby reset to empty — then a seat is claimed; the
     * first caller takes seat 0 and becomes host. Synchronized so a burst of simultaneous
     * Play-Again calls reopen exactly once before any of them claims a seat.
     */
    public synchronized JoinResult playAgain(GameId id, String name) {
        String type = repository.loadSessionGame(id).gameType(); // throws IllegalArgumentException if unknown
        if (repository.findGame(id).isPresent()) {
            repository.remove(id);
            repository.saveLobby(SessionGame.create(id, gameFactories.maxPlayers(type), type));
        }
        return joinRoom(id, name);
    }
```

(`repository.remove(id)` drops both the game and the old session; `saveLobby(...)` re-adds a fresh empty lobby. `joinRoom` then claims the lowest free seat — seat 0 first — and returns the `JoinResult`. `joinRoom` already throws `RoomFullException` if somehow full.)

- [ ] **Step 4: Run to verify it passes:** `-Dtest=SessionServiceTest` → PASS.

- [ ] **Step 5: Commit:**
```bash
cd "C:\Users\kevin\Documents\GitHub\IntelliJ\BatailleCorse\.claude\worktrees\sharp-leakey-22c0d5"
git add -A backend/
git commit -m "feat(session): playAgain reopens the room then re-joins (rematch via lobby)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: REST endpoint `POST /api/bullshit/game/{id}/play-again`

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/cardgames/bullshit/presentation/BullshitRestController.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/bullshit/presentation/BullshitRestControllerTest.java`

- [ ] **Step 1: Write the failing test.** Open `BullshitRestControllerTest` and mirror its existing `joinGame` test's setup (how it builds the controller + `sessionService`). Add a test that: creates a room (`sessionService.createRoom("bullshit","Alice")`), joins two more (`sessionService.joinRoom`), starts (`sessionService.startGame(id, hostToken)`), then calls `controller.playAgain(room.gameId(), new JoinGamePayload(null))` and asserts the response is `200 OK` with a body whose `playerId()` is `0` (reopened → first seat). Use the same `JoinGamePayload`/`JoinResponseDto` types the join test uses. Concrete body:

```java
@Test
void playAgain_onFinishedRoom_reopensAndReturnsHostSeat() {
    RoomCreated room = sessionService.createRoom("bullshit", "Alice");
    GameId id = new GameId(room.gameId());
    sessionService.joinRoom(id, "Bob");
    sessionService.startGame(id, room.hostToken());

    ResponseEntity<JoinResponseDto> response = controller.playAgain(room.gameId(), new JoinGamePayload(null));

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().playerId(), is(0));
}
```

Add imports as needed (`RoomCreated`, `GameId`, `HttpStatus`, `ResponseEntity`, `JoinResponseDto`, `JoinGamePayload`). If `JoinGamePayload`'s constructor differs, match the join test's usage.

- [ ] **Step 2: Run to verify it fails:** `-Dtest=BullshitRestControllerTest` → compile failure (`playAgain` undefined on controller).

- [ ] **Step 3: Implement the endpoint.** In `BullshitRestController.java`, add (next to `joinGame`), reusing its imports (`JoinResult`, `RoomFullException`, `LobbyBroadcaster`, `EmptyEventData`, `LifecycleEventType`, `JoinResponseDto`, `JoinGamePayload`):

```java
    @PostMapping("/game/{id}/play-again")
    public ResponseEntity<JoinResponseDto> playAgain(@PathVariable String id,
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
            JoinResult result = sessionService.playAgain(gameId, name);
            sessionService.touch(gameId);
            lobbyBroadcaster.broadcast(gameId, LifecycleEventType.JOIN.toString(), new EmptyEventData(),
                    "Player " + (result.playerId().id() + 1) + " joined.");
            return ResponseEntity.ok(new JoinResponseDto(result.playerId().id(), result.token()));
        } catch (RoomFullException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
```

- [ ] **Step 4: Run to verify it passes:** `-Dtest=BullshitRestControllerTest` → PASS.

- [ ] **Step 5: Commit:**
```bash
git add -A backend/
git commit -m "feat(bullshit): POST /game/{id}/play-again reopens the room and re-joins

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Remove the bespoke rematch path (backend)

Deletes the old Bullshit rematch tally. **Do not touch** BatailleCorse's path: keep `SessionService.requestRematch(GameId, PlayerId): boolean`, `SessionService.rematch(GameId)`, `SessionGame.requestRematch`/`isRematchUnanimous`/`clearRematch`/`seatOrThrow`/`claimedCount`, `SessionPlayer.requestRematch`/`hasRequestedRematch`/`clearRematch`, and the shared `RematchStatus` enum.

**Files:**
- Modify: `SessionService.java`, `SessionGame.java`, `SessionPlayer.java`, `BullshitWebSocketController.java`
- Delete: `core/application/RematchOutcome.java`, `bullshit/presentation/dto/event/BullshitRematchEventData.java`
- Tests: `SessionServiceTest.java`, `SessionGameTest.java`, `BullshitWebSocketControllerTest.java`

- [ ] **Step 1: Remove from `SessionService.java`** the methods `joinRematch`, `leaveRematch`, and the private `settleRematch` (the three added for the tally). Remove the `import ...RematchOutcome;`. Keep `rematch(GameId)` and `requestRematch(GameId, PlayerId)`.

- [ ] **Step 2: Remove from `SessionGame.java`** the methods `leaveRematch(PlayerId)`, `rematchStayingCount()`, `rematchReadyCount()`, `isRematchReady()`, and the private `staying()` helper. Remove the now-unused `import java.util.stream.Stream;` (verify no other use). Keep `requestRematch`, `isRematchUnanimous`, `clearRematch`, `seatOrThrow`, `claimedCount`.

- [ ] **Step 3: Revert `SessionPlayer.java`** to the no-`leftRematch` state:
```java
    private boolean rematchRequested;
    // ... constructor sets this.rematchRequested = false; (remove the leftRematch field + its init)

    public void requestRematch() {
        this.rematchRequested = true;
    }

    public void clearRematch() {
        this.rematchRequested = false;
    }

    public boolean hasRequestedRematch() {
        return rematchRequested;
    }
```
Remove the `leftRematch` field, `leaveRematch()`, and `hasLeftRematch()`.

- [ ] **Step 4: Remove from `BullshitWebSocketController.java`** the `@MessageMapping("/bullshit/rematch")` and `@MessageMapping("/bullshit/leaveRematch")` handlers and the private `settleRematch` helper. Remove the now-unused imports: `BullshitRematchEventData`, `RematchStatus`, `RematchOutcome`. (Leave `create`/`start`/`discard`/`callBullshit` intact.)

- [ ] **Step 5: Delete the dead files:**
```bash
cd "C:\Users\kevin\Documents\GitHub\IntelliJ\BatailleCorse\.claude\worktrees\sharp-leakey-22c0d5\backend"
git rm src/main/java/org/kevinkib/cardgames/sessionmanagement/core/application/RematchOutcome.java
git rm src/main/java/org/kevinkib/cardgames/bullshit/presentation/dto/event/BullshitRematchEventData.java
```

- [ ] **Step 6: Remove the dead tests.**
  - `SessionGameTest` (`RematchTest` nested class): remove the tests that call `isRematchReady`/`rematchStayingCount`/`rematchReadyCount`/`leaveRematch` — i.e. `givenLeaverAndRemainingAllRequested_whenReady_thenTrue`, `givenAStayingSeatHasNotRequested_thenNotReady`, `givenEveryoneLeft_thenNotReady`, `givenLeaverThenRejoins_whenRequest_thenCountedAgain`, `givenRoomWithUnclaimedSeats_whenRematch_thenOnlyClaimedSeatsCount`. Keep the original `isRematchUnanimous`/`clearRematch` tests. Remove `import java.util.Set;` if now unused.
  - `SessionServiceTest` (`RematchTest`): remove the tests calling `joinRematch`/`leaveRematch` (`givenOneSeatLeft_whenTheOtherJoins_thenRematchStartsAmongStayers`, `givenTwoStayers_whenOneJoins_thenPendingWithReadyOne`). Keep `requestRematch(GameId, PlayerId)` and `rematch` tests if present. Keep the `playAgain` test from Task 1.
  - `BullshitWebSocketControllerTest`: remove the rematch tests (`givenFirstSeatRequestsRematch_*`, `givenAllEligibleSeatsRequest_*`, `givenThreeSeats_*`, `givenOneSeatLeaves_*`, `givenThreePlayersJoinedASixSeatRoom_*`, `givenThreePlayerRoom_whenRematchStarts_*`) and the now-unused imports (`BullshitRematchEventData`, `RematchStatus`). Keep create/start/discard/callBullshit tests.

- [ ] **Step 7: Full backend suite:** `cd backend && "...mvn.cmd" test` → BUILD SUCCESS, all green. Fix any stragglers (a leftover reference will fail compilation — remove it).

- [ ] **Step 8: Commit:**
```bash
git add -A backend/
git commit -m "refactor(bullshit): remove the bespoke rematch tally (superseded by play-again)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## PART B — Frontend

> Run from `frontend/`. `node_modules` is already installed.

### Task 4: `BullshitSession.playAgain` (replace rematch/leaveRematch)

**Files:**
- Modify: `frontend/src/application/BullshitSession.ts`
- Test: `frontend/src/application/BullshitSession.test.ts`

- [ ] **Step 1: Write the failing test.** `playAgain` calls a REST endpoint, like `join`. Open `BullshitSession.test.ts` and mirror however it tests `join` (it stubs `fetch`). Add a test: bind via `restore('g1', 2, 'old-tok')`, stub `fetch` to resolve `{ ok: true, json: async () => ({ playerId: 0, token: 'new-tok' }) }`, call `await session.playAgain('Alice')`, and assert `fetch` was called with `/api/bullshit/game/g1/play-again` and that a `seat-change` event with seat `0` was emitted (re-bind). Reuse the file's existing fetch-stub helper and event capture. If the join test asserts `localStorage`, mirror that too.

- [ ] **Step 2: Run to verify it fails:** `cd frontend && npx vitest run src/application/BullshitSession.test.ts` → FAIL (`playAgain` not a function).

- [ ] **Step 3: Implement.** In `BullshitSession.ts`, remove `rematch()` and `leaveRematch()`. Add (next to `join`):

```ts
  async playAgain(name?: string): Promise<void> {
    if (!this.gameId) return;
    const res = await fetch(`/api/bullshit/game/${this.gameId}/play-again`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: name ?? null }),
    });
    if (!res.ok) throw new Error(`Play again failed: ${res.status}`);
    const body = await res.json() as { playerId: number; token: string };
    this.bind(this.gameId, body.playerId, body.token);
    localStorage.setItem(`bullshit:tokens:${this.gameId}`, JSON.stringify({ [body.playerId]: body.token }));
    await this.hydrate();
  }
```

(`bind` re-subscribes to the new seat topic — `WebSocketService.subscribeToSeat` unsubscribes the previous one — and emits `game-id-change`/`seat-change`. `hydrate` then pulls the fresh lobby view so the store's `phase` flips to `lobby`.)

- [ ] **Step 4: Run to verify it passes:** same command → PASS.

- [ ] **Step 5: Commit:**
```bash
cd "C:\Users\kevin\Documents\GitHub\IntelliJ\BatailleCorse\.claude\worktrees\sharp-leakey-22c0d5"
git add -A frontend/
git commit -m "feat(frontend): BullshitSession.playAgain (replaces rematch/leaveRematch)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: Store — `playAgain` action (remove rematch progress)

**Files:**
- Modify: `frontend/src/state/Bullshit.store.ts`
- Test: `frontend/src/state/Bullshit.store.test.ts`

- [ ] **Step 1: Write the failing test.** In `Bullshit.store.test.ts`, add (reuse the existing pinia setup + the `vi.spyOn(webSocketService, 'publish')` pattern — but `playAgain` uses `fetch`, so stub `fetch` instead). Simplest: spy on the session via the store boundary — assert calling `store.playAgain()` invokes a `fetch` to `.../play-again`. Mirror how the store test for `join`/network is done if present; otherwise:

```ts
it('playAgain() posts to the play-again endpoint', async () => {
  const store = useBullshitStore();
  store.applyEvent({ type: 'game-id-change', gameId: 'g1' });
  const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(JSON.stringify({ playerId: 0, token: 't' }), { status: 200 }));

  await store.playAgain();

  expect(fetchSpy).toHaveBeenCalledWith(
    expect.stringContaining('/api/bullshit/game/g1/play-again'),
    expect.objectContaining({ method: 'POST' }));
  fetchSpy.mockRestore();
});
```

Also remove the existing rematch-progress store tests (`tracks rematch progress from a PENDING REMATCH event`, `rematch() marks me as requested...`, `leaveRematch() publishes the leave frame`).

- [ ] **Step 2: Run to verify it fails:** `npx vitest run src/state/Bullshit.store.test.ts` → FAIL (`playAgain` undefined).

- [ ] **Step 3: Implement.** In `Bullshit.store.ts`:
  - Remove the refs `rematchRequested`, `rematchReady`, `rematchEligible`; the `rematchButton` computed; the `requestRematch`/`leaveRematch` functions; the `REMATCH` handling block inside the `'event'` case (keep the `CALL_BULLSHIT`/reveal lines); the `import { bullshitRematchButton } ...`; and the returned keys `rematchRequested, rematchReady, rematchEligible, rematchButton, rematch, leaveRematch`.
  - Add a `playAgain` action and expose it:
```ts
  function playAgain() {
    return session.playAgain();
  }
```
  add `playAgain,` to the returned object.
  - Delete `frontend/src/model/bullshit/BullshitRematch.ts` and `BullshitRematch.test.ts` (`git rm`) — the static button no longer needs the model.

- [ ] **Step 4: Run to verify it passes:** `npx vitest run src/state/Bullshit.store.test.ts` → PASS.

- [ ] **Step 5: Commit:**
```bash
git add -A frontend/
git commit -m "feat(frontend): store playAgain action; drop rematch-progress state

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: Screen + overlay wiring

**Files:**
- Modify: `frontend/src/view/bullshit/BullshitGameScreen.vue`
- Modify: `frontend/src/components/EndGameOverlay.vue`
- Test: `frontend/src/view/bullshit/BullshitGameScreen.test.ts`

- [ ] **Step 1: Write the failing test.** In `BullshitGameScreen.test.ts`, update the finished-phase test: drive the store to `finished` (as the existing test does), mount, find `EndGameOverlay`, emit `playAgain`, and assert `store.playAgain` is called (`vi.spyOn(store, 'playAgain')`). Remove any assertion about a rematch button label/`@leave`.

- [ ] **Step 2: Run to verify it fails:** `npx vitest run src/view/bullshit/BullshitGameScreen.test.ts` → FAIL (screen still wires `@play-again="store.rematch()"` / `@leave`).

- [ ] **Step 3: Implement.**
  - `EndGameOverlay.vue`: remove the `leave` emit from the `Emits` interface and the `@click="emit('leave')"` on the "Back to home" `RouterLink` (added earlier for the tally). Keep `rematchButton` prop, the `playAgain` emit, and the RouterLink navigation. (BatailleCorse still passes `rematchButton` and listens to `playAgain`.)
  - `BullshitGameScreen.vue`: change the `EndGameOverlay` usage to pass a static button and the new handler, and drop `@leave`:
```html
    <EndGameOverlay
      v-else-if="store.phase === 'finished'"
      data-test="end"
      :did-i-win="store.iWon"
      :subtitle="store.iWon ? 'You emptied your hand first.' : 'Another player emptied their hand first.'"
      :rematch-button="{ label: 'Play again', disabled: false }"
      @play-again="store.playAgain()"
    />
```

- [ ] **Step 4: Run to verify it passes:** `npx vitest run src/view/bullshit/BullshitGameScreen.test.ts` → PASS.

- [ ] **Step 5: Frontend gate:**
  - `cd frontend && npx vitest run` → all suites pass.
  - `cd frontend && npm run build` → succeeds (type-check gate).

- [ ] **Step 6: Commit:**
```bash
git add -A frontend/
git commit -m "feat(frontend): end screen Play Again reopens the room (lobby rematch)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Final verification

- [ ] Backend full suite green: `cd backend && "...mvn.cmd" test`.
- [ ] Frontend build green: `cd frontend && npm run build`.
- [ ] Grep clean: no remaining references to `joinRematch`, `leaveRematch`, `RematchOutcome`, `BullshitRematchEventData`, `isRematchReady`, `rematchStayingCount`, `leftRematch`, `/bullshit/rematch`, `/bullshit/leaveRematch` in `backend/src` or `frontend/src` (BatailleCorse's `requestRematch`/`isRematchUnanimous`/`rematch`/`RematchStatus`/`RematchEventData` must remain).
- [ ] Manual smoke (optional): 3-player room, finish, two click Play Again + one Back to Home → those two land in the lobby, host Starts → fresh **2-player** game.

---

## Notes for the executor

- **Do not touch BatailleCorse's rematch.** Only the Bullshit-specific tally is removed. `RematchStatus` (shared) and `RematchEventData` (BC) stay.
- **`playAgain` must stay `synchronized`** (per the spec's concurrency note): the reopen is a check-then-act and STOMP/REST handlers run concurrently.
- The frontend has **no separate lobby route**; do not add navigation in `playAgain` — re-bind + re-hydrate flips `store.phase` to `lobby` on the same `/games/bullshit/room/:id` screen.
- If a test references a harness you can't see quoted here (fetch stub, pinia setup, mount helper), open the test file and reuse its existing pattern rather than inventing one.

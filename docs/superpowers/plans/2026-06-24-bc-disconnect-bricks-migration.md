# BC Disconnect-Bricks Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace BatailleCorse's bespoke single-opponent disconnect plumbing with the game-agnostic presence bricks from PR #72, expressed as a 1-entry seat→deadline map, with zero change to BC's full-screen `DisconnectOverlay` UX.

**Architecture:** `GameSession` forwards the two raw lifecycle events (`OPPONENT_DISCONNECTED`/`OPPONENT_RECONNECTED`) as a generic `presence-event`. The BC store delegates those to `useSeatPresence.applyPresenceEvent` and narrows `presentSeats()` to `multiplayer ? [opponentSeat] : []` — which folds the old is-me + is-multiplayer guards into the brick's existing filter. `GameScreen` drives `useSeatDisconnectCountdown` off the resulting `liveDisconnections` map and derives the single opponent's `secondsRemaining`, feeding the unchanged overlay. Forfeit is out of scope.

**Tech Stack:** Vue 3 (`<script setup>`, Composition API), Pinia, TypeScript, Vitest, `@vue/test-utils`. Build/gate: `npm run build` from `frontend/`.

**Spec:** `docs/superpowers/specs/2026-06-23-bc-disconnect-bricks-migration-design.md`

---

## File Structure

- `frontend/src/model/SeatLifecycleEvents.ts` — **Modify.** Gains the `SEAT_LIFECYCLE_EVENT` event-type constant (relocated from the composable so the framework-free `application/` layer can import it without pulling in Vue).
- `frontend/src/composables/useSeatPresence.ts` — **Modify (backward-compatible only).** Imports `SEAT_LIFECYCLE_EVENT` from the model and re-exports it so existing importers (its own test) keep working. No behavior change.
- `frontend/src/application/GameEvent.ts` — **Modify.** Replace the two `opponent-connection` variants with one `presence-event` variant.
- `frontend/src/application/GameSession.ts` — **Modify (~350-365).** Forward the two lifecycle events as `presence-event`, verbatim, using the constant. No `Number()` coercion.
- `frontend/src/state/BatailleCorse.store.ts` — **Modify.** Wire `useSeatPresence`, expose `liveDisconnections` + public `applyEvent`, drop `opponentConnection`, reset presence on rematch-started + create.
- `frontend/src/view/alpha/GameScreen.vue` — **Modify.** Swap `useDisconnectCountdown` → `useSeatDisconnectCountdown`; derive `disconnectedSeat`/`opponentDisconnected`/`secondsRemaining`.
- `frontend/src/composables/useDisconnectCountdown.ts` — **Delete.**
- `frontend/src/composables/useDisconnectCountdown.test.ts` — **Delete.**
- `frontend/src/state/BatailleCorse.store.test.ts` — **Create.** New: covers the `presentSeats` narrowing + reset.
- `frontend/src/application/GameSession.test.ts` — **Modify.** Replace the two `opponent-connection` assertions with `presence-event` assertions.
- `frontend/src/view/alpha/GameScreen.test.ts` — **Modify.** Drive disconnect via `store.applyEvent(...)` instead of poking `store.opponentConnection`.

> **Why expose a public `applyEvent` on the BC store:** today the session→store callback switch is an inline anonymous function, so tests had to poke the `opponentConnection` ref directly. That ref is being deleted. Extracting the switch into a named `applyEvent(event: GameEvent)` and returning it (exactly as `Bullshit.store.ts` already does) gives tests a public seam to drive presence events, with no production behavior change.

---

## Task 1: Relocate `SEAT_LIFECYCLE_EVENT` to the model (framework-free), re-export from the composable

**Files:**
- Modify: `frontend/src/model/SeatLifecycleEvents.ts`
- Modify: `frontend/src/composables/useSeatPresence.ts:9-14`

This is a pure move + re-export so the `application/` layer can import the constant without depending on a Vue composable. No behavior changes; the existing `useSeatPresence.test.ts` (which imports `SEAT_LIFECYCLE_EVENT` from `useSeatPresence`) stays green via the re-export.

- [ ] **Step 1: Add the constant to the model file**

Append to `frontend/src/model/SeatLifecycleEvents.ts` (after the existing interfaces):

```ts
/** The three game-agnostic per-seat lifecycle event types (single source of truth). */
export const SEAT_LIFECYCLE_EVENT = {
  OPPONENT_DISCONNECTED: 'OPPONENT_DISCONNECTED',
  OPPONENT_RECONNECTED: 'OPPONENT_RECONNECTED',
  FORFEIT: 'FORFEIT',
} as const;
```

- [ ] **Step 2: Re-export from the composable instead of redefining**

In `frontend/src/composables/useSeatPresence.ts`, change the import block at the top (lines 3-7) to also pull the constant from the model, and delete the local `SEAT_LIFECYCLE_EVENT` definition (lines 9-14), replacing it with a re-export.

Replace:

```ts
import type {
  OpponentDisconnectedEventData,
  OpponentReconnectedEventData,
  ForfeitEventData,
} from '../model/SeatLifecycleEvents';

/** The three game-agnostic per-seat lifecycle event types (single source of truth). */
export const SEAT_LIFECYCLE_EVENT = {
  OPPONENT_DISCONNECTED: 'OPPONENT_DISCONNECTED',
  OPPONENT_RECONNECTED: 'OPPONENT_RECONNECTED',
  FORFEIT: 'FORFEIT',
} as const;
```

with:

```ts
import type {
  OpponentDisconnectedEventData,
  OpponentReconnectedEventData,
  ForfeitEventData,
} from '../model/SeatLifecycleEvents';
import { SEAT_LIFECYCLE_EVENT } from '../model/SeatLifecycleEvents';

// Re-exported for existing importers; the canonical definition lives in the model
// so the framework-free application layer can import it without pulling in Vue.
export { SEAT_LIFECYCLE_EVENT };
```

- [ ] **Step 3: Verify the existing presence tests still pass**

Run: `cd frontend && npx vitest run src/composables/useSeatPresence.test.ts`
Expected: PASS (all existing tests green — the constant resolves identically through the re-export).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/model/SeatLifecycleEvents.ts frontend/src/composables/useSeatPresence.ts
git commit -m "refactor(presence): #71 relocate SEAT_LIFECYCLE_EVENT to the model layer"
```

---

## Task 2: Core wiring — `presence-event` through GameEvent, GameSession, and the BC store

**Files:**
- Modify: `frontend/src/application/GameEvent.ts:17-18`
- Modify: `frontend/src/application/GameSession.ts:350-365`
- Modify: `frontend/src/state/BatailleCorse.store.ts`
- Modify: `frontend/src/application/GameSession.test.ts:499-523`
- Create: `frontend/src/state/BatailleCorse.store.test.ts`

These edits are tightly coupled through the `GameEvent` union type — changing the type alone breaks the store's compile, so they land together in one task. Tests are written first, then the implementation that makes them pass.

- [ ] **Step 1: Rewrite the two `opponent-connection` tests in GameSession.test.ts as `presence-event`**

In `frontend/src/application/GameSession.test.ts`, replace the two tests at lines 499-523 (`emits opponent-connection on OPPONENT_DISCONNECTED` and `...on OPPONENT_RECONNECTED`) with:

```ts
    it('emits presence-event on OPPONENT_DISCONNECTED, forwarding eventData verbatim', async () => {
      const { session, events } = makeSession();
      session.create('multiplayer', 'Bob');
      await session.onResponse(buildCreateResponse('game-9', { 0: 'tok' }));
      await session.onResponse(buildResponse({
        eventType: 'OPPONENT_DISCONNECTED',
        eventData: { disconnectedSeat: 1, deadlineEpochMs: 1234 },
      }));
      expect(events).toContainEqual({
        type: 'presence-event',
        eventType: 'OPPONENT_DISCONNECTED',
        eventData: { disconnectedSeat: 1, deadlineEpochMs: 1234 },
      });
    });

    it('emits presence-event on OPPONENT_RECONNECTED, forwarding eventData verbatim', async () => {
      const { session, events } = makeSession();
      session.create('multiplayer', 'Bob');
      await session.onResponse(buildCreateResponse('game-10', { 0: 'tok' }));
      await session.onResponse(buildResponse({
        eventType: 'OPPONENT_RECONNECTED',
        eventData: { reconnectedSeat: 1 },
      }));
      expect(events).toContainEqual({
        type: 'presence-event',
        eventType: 'OPPONENT_RECONNECTED',
        eventData: { reconnectedSeat: 1 },
      });
    });
```

- [ ] **Step 2: Create the BC store test**

Create `frontend/src/state/BatailleCorse.store.test.ts`:

```ts
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';

vi.mock('../service/WebSocketService', () => ({
  default: {
    subscribeToGame: vi.fn(),
    unsubscribeFromGame: vi.fn(),
    setPresence: vi.fn(),
    clearPresence: vi.fn(),
    publish: vi.fn(),
  },
}));

import { useBatailleCorseStore } from './BatailleCorse.store';

describe('BatailleCorse store — opponent presence', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); });

  function disconnect(store: ReturnType<typeof useBatailleCorseStore>, seat: number, deadline = 1_060_000) {
    store.applyEvent({ type: 'presence-event', eventType: 'OPPONENT_DISCONNECTED',
      eventData: { disconnectedSeat: seat, deadlineEpochMs: deadline } });
  }

  it('exposes a multiplayer opponent disconnect via liveDisconnections', () => {
    const store = useBatailleCorseStore();
    store.mode = 'multiplayer';
    store.myPlayerIndex = 0;
    disconnect(store, 1);
    expect(store.liveDisconnections).toEqual({ 1: 1_060_000 });
  });

  it('clears the disconnect on reconnect', () => {
    const store = useBatailleCorseStore();
    store.mode = 'multiplayer';
    store.myPlayerIndex = 0;
    disconnect(store, 1);
    store.applyEvent({ type: 'presence-event', eventType: 'OPPONENT_RECONNECTED',
      eventData: { reconnectedSeat: 1 } });
    expect(store.liveDisconnections).toEqual({});
  });

  it('filters out disconnects in solo mode', () => {
    const store = useBatailleCorseStore();
    store.mode = 'solo';
    store.myPlayerIndex = 0;
    disconnect(store, 1);
    expect(store.liveDisconnections).toEqual({});
  });

  it('filters out a disconnect carrying my own seat', () => {
    const store = useBatailleCorseStore();
    store.mode = 'multiplayer';
    store.myPlayerIndex = 0;
    disconnect(store, 0); // my own seat — not the opponent
    expect(store.liveDisconnections).toEqual({});
  });

  it('resets presence when a rematch starts', () => {
    const store = useBatailleCorseStore();
    store.mode = 'multiplayer';
    store.myPlayerIndex = 0;
    disconnect(store, 1);
    expect(store.liveDisconnections).toEqual({ 1: 1_060_000 });

    store.applyEvent({ type: 'rematch', status: 'started' });
    expect(store.liveDisconnections).toEqual({});
  });
});
```

- [ ] **Step 3: Run the new/updated tests to verify they fail**

Run: `cd frontend && npx vitest run src/state/BatailleCorse.store.test.ts src/application/GameSession.test.ts`
Expected: FAIL — `BatailleCorse.store.test.ts` fails to compile/run (no `applyEvent`/`liveDisconnections` on the store; `presence-event` not in `GameEvent`), and the two rewritten GameSession tests fail (store/session still emit `opponent-connection`).

- [ ] **Step 4: Replace the `opponent-connection` variants in GameEvent.ts**

In `frontend/src/application/GameEvent.ts`, replace lines 17-18:

```ts
  | { type: 'opponent-connection'; status: 'disconnected'; seat: number; deadlineEpochMs: number }
  | { type: 'opponent-connection'; status: 'connected'; seat: number }
```

with:

```ts
  | { type: 'presence-event'; eventType: string; eventData: unknown }
```

- [ ] **Step 5: Forward the lifecycle events verbatim in GameSession.ts**

In `frontend/src/application/GameSession.ts`, add the import after line 11 (`import type SessionSeat ...`):

```ts
import { SEAT_LIFECYCLE_EVENT } from '../model/SeatLifecycleEvents';
```

Then replace the bespoke mapping block at lines 350-365:

```ts
    if (response.eventType === 'OPPONENT_DISCONNECTED') {
      const data = response.eventData as unknown as { disconnectedSeat: number; deadlineEpochMs: number };
      this.callbacks.onEvent({
        type: 'opponent-connection',
        status: 'disconnected',
        seat: Number(data.disconnectedSeat),
        deadlineEpochMs: Number(data.deadlineEpochMs),
      });
    } else if (response.eventType === 'OPPONENT_RECONNECTED') {
      const data = response.eventData as unknown as { reconnectedSeat: number };
      this.callbacks.onEvent({
        type: 'opponent-connection',
        status: 'connected',
        seat: Number(data.reconnectedSeat),
      });
    }
```

with:

```ts
    // Forward the game-agnostic seat-lifecycle events verbatim; the store reduces
    // them via useSeatPresence. No Number() coercion — the brick is coercion-tolerant
    // (liveDisconnections Number()-coerces its keys; secondsRemainingFor does numeric
    // arithmetic on the deadline), so reshaping here would be redundant.
    if (
      response.eventType === SEAT_LIFECYCLE_EVENT.OPPONENT_DISCONNECTED ||
      response.eventType === SEAT_LIFECYCLE_EVENT.OPPONENT_RECONNECTED
    ) {
      this.callbacks.onEvent({
        type: 'presence-event',
        eventType: response.eventType,
        eventData: response.eventData,
      });
    }
```

- [ ] **Step 6: Rewire the BC store**

In `frontend/src/state/BatailleCorse.store.ts`:

(a) Add the import after line 11 (`import GameSession ...`) / near the other imports:

```ts
import { useSeatPresence } from '../composables/useSeatPresence';
```

(b) Delete the `opponentConnection` ref declaration (lines 30-34):

```ts
  const opponentConnection = ref<
    | { status: 'disconnected'; seat: number; deadlineEpochMs: number }
    | { status: 'connected'; seat: number }
    | null
  >(null);
```

(c) Immediately after `const settingsStore = useSettingsStore();` (line 42), add the presence wiring. The narrowing of `presentSeats` to the opponent seat (and `[]` in solo) is what reproduces the old is-me + is-multiplayer guards through the shared brick's filter:

```ts
  // BC has exactly one opponent. Express that as the brick's seat map: in multiplayer
  // the only "present" seat that can disconnect is the opponent (1 - myPlayerIndex);
  // in solo there is none. This single narrowing folds the old is-me and
  // is-multiplayer guards into useSeatPresence's liveDisconnections filter.
  const presence = useSeatPresence({
    presentSeats: () => (mode.value === 'multiplayer' ? [1 - myPlayerIndex.value] : []),
  });
```

(d) Extract the inline `onEvent` switch into a named `applyEvent`, delegate presence, reset on rematch-started, and remove the `opponent-connection` case. Replace the `onEvent(event: GameEvent) { switch ... }` member (lines 47-72) — define `applyEvent` as a standalone function above the `session` construction and pass it as the callback:

```ts
  function applyEvent(event: GameEvent) {
    switch (event.type) {
      case 'state-update':    state.value = event.state; break;
      case 'game-id-change':  gameId.value = event.gameId; break;
      case 'send':            lastSend.value = event; break;
      case 'grab':            lastGrab.value = event; break;
      case 'slap':            lastSlap.value = { seq: ++slapSeq }; break;
      case 'successful-slap': lastSuccessfulSlap.value = event; break;
      case 'erroneous-slap':  lastErroneousSlap.value = event; break;
      case 'mode-change':          mode.value = event.mode; break;
      case 'my-index-change':      myPlayerIndex.value = event.playerIndex; break;
      case 'waiting-change':       waiting.value = event.waiting; break;
      case 'my-name-change':       myName.value = event.name; break;
      case 'opponent-name-change': opponentName.value = event.name; break;
      case 'presence-event':       presence.applyPresenceEvent(event.eventType, event.eventData); break;
      case 'rematch':
        if (event.status === 'started') {
          rematchState.value = 'idle';
          presence.reset();
        } else {
          rematchState.value = event.requestedBy === myPlayerIndex.value
            ? 'requested-by-me'
            : 'requested-by-opponent';
        }
        break;
    }
  }

  const session = new GameSession(
    webSocketService,
    {
      onEvent: applyEvent,
      awaitAnimation: () => new Promise<void>(resolve => { animationResolve = resolve; }),
    },
    () => new AI(1, DIFFICULTY[settingsStore.difficulty].reactionTime),
  );
```

> Note: `slapSeq` and `animationResolve` are declared with `let` above this block already (lines 37-40); `applyEvent` closes over them, so no ordering change is needed beyond defining `applyEvent` before `session`.

(e) In the returned object, remove `opponentConnection,` and add `liveDisconnections` + `applyEvent`. Also reset presence in `create`:

Replace `opponentConnection,` (line 90) — delete it. Add to the returned object (near `rematchState,`):

```ts
    liveDisconnections: presence.liveDisconnections,
    applyEvent,
```

And change the `create` entry (line 98) to reset presence:

```ts
    create:               (gameMode: 'solo' | 'multiplayer', name?: string) => { rematchState.value = 'idle'; presence.reset(); session.create(gameMode, name); },
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `cd frontend && npx vitest run src/state/BatailleCorse.store.test.ts src/application/GameSession.test.ts`
Expected: PASS (all tests in both files green).

- [ ] **Step 8: Commit**

```bash
git add frontend/src/application/GameEvent.ts frontend/src/application/GameSession.ts frontend/src/state/BatailleCorse.store.ts frontend/src/application/GameSession.test.ts frontend/src/state/BatailleCorse.store.test.ts
git commit -m "feat(presence): #71 reduce BC disconnect events through useSeatPresence"
```

---

## Task 3: Rewire GameScreen onto `useSeatDisconnectCountdown`

**Files:**
- Modify: `frontend/src/view/alpha/GameScreen.vue` (imports, `storeToRefs`, countdown wiring at ~241-246)
- Modify: `frontend/src/view/alpha/GameScreen.test.ts:52-78`

- [ ] **Step 1: Update the GameScreen test to drive the disconnect via `applyEvent`**

In `frontend/src/view/alpha/GameScreen.test.ts`, replace the entire `describe('opponent-disconnect countdown', ...)` block (lines 52-78) with:

```ts
  describe('opponent-disconnect countdown', () => {
    it('shows the countdown banner while the opponent is disconnected', async () => {
      const wrapper = await mountGameScreen();
      const store = useBatailleCorseStore();
      store.mode = 'multiplayer';
      store.myPlayerIndex = 0;
      store.state = buildGame();
      store.applyEvent({ type: 'presence-event', eventType: 'OPPONENT_DISCONNECTED',
        eventData: { disconnectedSeat: 1, deadlineEpochMs: Date.now() + 60_000 } });
      await nextTick();

      const overlay = wrapper.findComponent(DisconnectOverlay);
      expect(overlay.exists()).toBe(true);
      expect(overlay.props('secondsRemaining')).toBeGreaterThan(0);
    });

    it('hides the banner once the opponent reconnects', async () => {
      const wrapper = await mountGameScreen();
      const store = useBatailleCorseStore();
      store.mode = 'multiplayer';
      store.myPlayerIndex = 0;
      store.state = buildGame();
      store.applyEvent({ type: 'presence-event', eventType: 'OPPONENT_DISCONNECTED',
        eventData: { disconnectedSeat: 1, deadlineEpochMs: Date.now() + 60_000 } });
      await nextTick();
      expect(wrapper.findComponent(DisconnectOverlay).exists()).toBe(true);

      store.applyEvent({ type: 'presence-event', eventType: 'OPPONENT_RECONNECTED',
        eventData: { reconnectedSeat: 1 } });
      await nextTick();
      expect(wrapper.findComponent(DisconnectOverlay).exists()).toBe(false);
    });
  });
```

- [ ] **Step 2: Run the GameScreen test to verify it fails**

Run: `cd frontend && npx vitest run src/view/alpha/GameScreen.test.ts`
Expected: FAIL — `store.applyEvent` is now defined (Task 2), but `GameScreen.vue` still imports/uses `useDisconnectCountdown` and reads `opponentConnection` via `storeToRefs`, which no longer exists → mount/compile error or overlay never shows.

- [ ] **Step 3: Swap the composable import in GameScreen.vue**

In `frontend/src/view/alpha/GameScreen.vue`, replace line 157:

```ts
import { useDisconnectCountdown } from '../../composables/useDisconnectCountdown';
```

with:

```ts
import { useSeatDisconnectCountdown } from '../../composables/useSeatDisconnectCountdown';
```

- [ ] **Step 4: Replace `opponentConnection` with `liveDisconnections` in `storeToRefs`**

In `frontend/src/view/alpha/GameScreen.vue`, lines 166-167, change the destructure from:

```ts
const { state: batailleCorse, mode, myPlayerIndex, waiting, myName, opponentName, opponentConnection,
        rematchState } = storeToRefs(batailleCorseStore);
```

to:

```ts
const { state: batailleCorse, mode, myPlayerIndex, waiting, myName, opponentName, liveDisconnections,
        rematchState } = storeToRefs(batailleCorseStore);
```

- [ ] **Step 5: Replace the countdown wiring**

In `frontend/src/view/alpha/GameScreen.vue`, replace the `useDisconnectCountdown` block at lines 241-246:

```ts
const { opponentDisconnected, secondsRemaining, cancel: cancelDisconnectCountdown } = useDisconnectCountdown({
  mode: () => mode.value,
  opponentConnection: () => opponentConnection.value,
  myPlayerIndex: () => myPlayerIndex.value,
  isGameOver: () => isGameOver.value,
});
```

with:

```ts
// BC has a single opponent, so the disconnections map holds at most one entry.
// liveDisconnections already excludes solo / self via the store's presentSeats narrowing.
const { secondsRemainingFor, cancel: cancelDisconnectCountdown } = useSeatDisconnectCountdown({
  disconnections: () => liveDisconnections.value,
  isGameOver: () => isGameOver.value,
});

const disconnectedSeat = computed(() => {
  const seats = Object.keys(liveDisconnections.value);
  return seats.length ? Number(seats[0]) : null;
});
const opponentDisconnected = computed(() => disconnectedSeat.value !== null && !isGameOver.value);
const secondsRemaining = computed(() =>
  disconnectedSeat.value === null
    ? 0
    : secondsRemainingFor(liveDisconnections.value[disconnectedSeat.value]));
```

> The template (`<DisconnectOverlay v-if="opponentDisconnected" :opponent-label="opponentLabel" :seconds-remaining="secondsRemaining"/>`, lines 87-91) and the `cancelDisconnectCountdown()` call in `onBeforeUnmount` (line 273) are unchanged — `opponentDisconnected`, `secondsRemaining`, and `cancelDisconnectCountdown` keep the same names and meanings.

- [ ] **Step 6: Run the GameScreen test to verify it passes**

Run: `cd frontend && npx vitest run src/view/alpha/GameScreen.test.ts`
Expected: PASS (overlay shows on disconnect with `secondsRemaining > 0`, hides on reconnect).

- [ ] **Step 7: Commit**

```bash
git add frontend/src/view/alpha/GameScreen.vue frontend/src/view/alpha/GameScreen.test.ts
git commit -m "feat(presence): #71 drive BC DisconnectOverlay off useSeatDisconnectCountdown"
```

---

## Task 4: Delete the now-orphaned `useDisconnectCountdown`

**Files:**
- Delete: `frontend/src/composables/useDisconnectCountdown.ts`
- Delete: `frontend/src/composables/useDisconnectCountdown.test.ts`

- [ ] **Step 1: Confirm there are no remaining importers**

Run: `cd frontend && grep -rn "useDisconnectCountdown" src/`
Expected: no matches (after Task 3, GameScreen no longer imports it; only the two files about to be deleted would match — confirm only those two appear, then delete).

- [ ] **Step 2: Delete both files**

```bash
git rm frontend/src/composables/useDisconnectCountdown.ts frontend/src/composables/useDisconnectCountdown.test.ts
```

- [ ] **Step 3: Verify the suite still passes without them**

Run: `cd frontend && npx vitest run`
Expected: PASS (no test references the deleted composable).

- [ ] **Step 4: Commit**

```bash
git commit -m "chore(presence): #71 remove orphaned useDisconnectCountdown"
```

---

## Task 5: Full verification and PR

**Files:** none (verification + integration only).

- [ ] **Step 1: Run the full frontend test suite**

Run: `cd frontend && npx vitest run`
Expected: PASS (entire suite green).

- [ ] **Step 2: Run the build gate**

Run: `cd frontend && npm run build`
Expected: build succeeds with no TypeScript errors. (This is the authoritative type-check gate — a bare `vue-tsc` can give a false pass in a worktree without `node_modules`.)

- [ ] **Step 3: Push and open the PR**

Open a PR targeting `main`. The description should: state that #71 collapses BC's bespoke single-opponent disconnect UI onto the shared presence bricks with no UX change; call out the `presentSeats() = multiplayer ? [opponentSeat] : []` narrowing as the one non-obvious move; note that forfeit stays out of scope (follow-up); and include `Closes #71`.

---

## Self-Review

**Spec coverage:**
- Forward lifecycle events generically → Task 2 (GameSession). ✅
- Drop `opponentConnection` ref + `opponent-connection` case → Task 2 (store). ✅
- `useSeatPresence` with narrowed `presentSeats` (folds is-me + is-multiplayer) → Task 2 (store). ✅
- Expose `liveDisconnections` → Task 2 (store). ✅
- Reset presence on rematch-started + create → Task 2 (store). ✅
- Swap to `useSeatDisconnectCountdown`, derive `secondsRemaining`, feed unchanged overlay → Task 3 (GameScreen). ✅
- Delete `useDisconnectCountdown` + test after grep → Task 4. ✅
- No `Number()` re-coercion when forwarding → Task 2 Step 5 (explicit comment + verbatim forward). ✅
- Tests: store narrowing/reset, GameSession `presence-event`, overlay show/hide → Tasks 2-3. ✅
- Build gate `npm run build` → Task 5. ✅
- Bricks reused; the one tweak (relocate constant + re-export) is backward-compatible and changes no Bullshit behavior → Task 1. ✅

**Type consistency:** `applyEvent(event: GameEvent)`, `presence-event` variant `{eventType: string; eventData: unknown}`, `liveDisconnections: Record<number, number>`, `secondsRemainingFor(deadline: number): number` — names and signatures match across Tasks 2-3 and the existing bricks.

**Placeholder scan:** none — every code step shows complete code.

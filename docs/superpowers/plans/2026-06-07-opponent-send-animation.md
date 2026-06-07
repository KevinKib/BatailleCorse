# Opponent Send Animation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the opponent's card-send animate in 2-player multiplayer, while keeping the local player's instant (optimistic) send animation.

**Architecture:** A server `SEND` response is currently the only branch in `GameSession.processEvent` that is *not* handled, so the opponent's send only produces a `state-update` and never the `send` GameEvent the animation watches. We add a `SEND` branch that emits a `send` event, gated by a single named predicate `emitsSendOptimistically()` so it fires only for sends this client did NOT already emit optimistically. All dedup logic stays inside `GameSession`; the animation composable and the Vue component are untouched.

**Tech Stack:** TypeScript, Vue 3, Vitest. Frontend lives in `frontend/`.

---

## Prerequisites (run once in the worktree)

This worktree may not have dependencies installed.

- [ ] **Install frontend dependencies**

Run (from the repo root):
```bash
cd frontend && npm ci
```
Expected: completes with no errors; `node_modules/` now exists.

---

## File Structure

- `frontend/src/application/GameSession.ts` — add `SendEventData` import, a private `emitsSendOptimistically()` predicate, and a `SEND` branch in `processEvent`. This is the only production file changed.
- `frontend/src/application/GameSession.test.ts` — add tests under a new `describe('SEND response', ...)` block.

No changes to `useCardAnimation.ts`, `GameScreen.vue`, `GameEvent.ts`, or the backend.

---

### Task 1: Emit a `send` event for the opponent's server SEND (with dedup)

**Files:**
- Modify: `frontend/src/application/GameSession.ts`
- Test: `frontend/src/application/GameSession.test.ts`

Context for the test setup (already-existing helpers, do not redefine):
- `makeSession()` returns `{ session, events, published, subscribed }`. `events` is the array of all emitted `GameEvent`s.
- `session.restoreSession({ 1: 'tok-b' })` puts the session in **multiplayer** with `myPlayerIndex = 1` (one token => multiplayer, that seat is mine). `session.restoreSession({ 0: 'a', 1: 'b' })` puts it in **solo** with `myPlayerIndex = 0`.
- `session.hydrate(id, state)` seeds `this.state` (used to snapshot `topCard`).
- `buildResponse({ eventType, eventData, state })`, `buildGame`, `buildPile`, `buildCard` come from `../model/fixtures`.

- [ ] **Step 1: Write the failing test (opponent send emits a `send` event with the pre-send topCard)**

Add this block inside the top-level `describe('GameSession', ...)` in `frontend/src/application/GameSession.test.ts`, after the `describe('send()', ...)` block:

```ts
  // --- SEND server response (opponent / echo dedup) -------------------------

  describe('SEND response', () => {
    it('emits a send event for the opponent in multiplayer, with the pre-send topCard', async () => {
      const { session, events } = makeSession();
      const topCard = buildCard({ rank: 'K', suit: 'spade' });
      session.hydrate('game-1', buildGame({ pile: buildPile({ cards: [topCard] }) }));
      session.restoreSession({ 1: 'tok-b' }); // multiplayer, my seat = 1, opponent = 0

      await session.onResponse(buildResponse({
        eventType: 'SEND',
        eventData: { player: { id: '0' } },
        state: buildGame(),
      }));

      const sendEvent = events.find(e => e.type === 'send') as
        Extract<GameEvent, { type: 'send' }> | undefined;
      expect(sendEvent).toBeDefined();
      expect(sendEvent!.playerIndex).toBe(0);
      expect(sendEvent!.topCard).toEqual(topCard);
    });
  });
```

- [ ] **Step 2: Run the test to verify it fails**

Run (from `frontend/`):
```bash
npx vitest run src/application/GameSession.test.ts -t "emits a send event for the opponent"
```
Expected: FAIL — `sendEvent` is `undefined` (no SEND branch exists yet).

- [ ] **Step 3: Add the `SendEventData` import**

In `frontend/src/application/GameSession.ts`, add the import next to the existing event-data imports (after the `SlapEventData` import line):

```ts
import type SendEventData from '../model/event/SendEventData';
```

- [ ] **Step 4: Add the SEND branch in `processEvent` (no dedup yet — minimal)**

In `frontend/src/application/GameSession.ts`, inside `processEvent`, add this block immediately **before** the line `const newState = BatailleCorse.fromJSON(...)` (i.e. before the `state-update` is emitted), after the `SLAP` block:

```ts
    if (response.eventType === 'SEND') {
      const senderIndex = Number((response.eventData as SendEventData).player?.id);
      if (!isNaN(senderIndex)) {
        const topCard = this.state?.pile.cards.at(0);
        this.callbacks.onEvent({
          type: 'send',
          playerIndex: senderIndex,
          seq: ++this.sendSeq,
          topCard,
        });
      }
    }
```

- [ ] **Step 5: Run the test to verify it passes**

Run (from `frontend/`):
```bash
npx vitest run src/application/GameSession.test.ts -t "emits a send event for the opponent"
```
Expected: PASS.

- [ ] **Step 6: Write the failing dedup tests (own send must not double; solo must not re-emit)**

Add these two tests inside the `describe('SEND response', ...)` block:

```ts
    it('does NOT emit a second send event for the local player in multiplayer (optimistic already fired)', async () => {
      const { session, events } = makeSession();
      session.hydrate('game-1', buildGame({ pile: buildPile({ cards: [buildCard()] }) }));
      session.restoreSession({ 1: 'tok-b' }); // multiplayer, my seat = 1

      session.send(1); // optimistic emit for my own send

      await session.onResponse(buildResponse({
        eventType: 'SEND',
        eventData: { player: { id: '1' } }, // server echo of my own send
        state: buildGame(),
      }));

      const mySendEvents = events.filter(
        e => e.type === 'send' && e.playerIndex === 1,
      );
      expect(mySendEvents).toHaveLength(1); // only the optimistic one
    });

    it('does NOT emit a send event from the SEND response in solo (AI already emits via send(1))', async () => {
      const { session, events } = makeSession();
      session.hydrate('game-1', buildGame({ pile: buildPile({ cards: [buildCard()] }) }));
      session.restoreSession({ 0: 'tok-a', 1: 'tok-b' }); // solo, my seat = 0

      await session.onResponse(buildResponse({
        eventType: 'SEND',
        eventData: { player: { id: '1' } },
        state: buildGame(), // empty pile + no available actions => AI stays idle
      }));

      expect(events.find(e => e.type === 'send')).toBeUndefined();
    });
```

- [ ] **Step 7: Run the dedup tests to verify they fail**

Run (from `frontend/`):
```bash
npx vitest run src/application/GameSession.test.ts -t "SEND response"
```
Expected: the two new tests FAIL — the multiplayer one emits 2 send events (optimistic + echo), the solo one emits 1 (echo). The opponent test still PASSES.

- [ ] **Step 8: Add the `emitsSendOptimistically` predicate**

In `frontend/src/application/GameSession.ts`, add this private method to the class (place it just below the `emitPerspective()` method for locality with the other perspective logic):

```ts
  /**
   * True when this client already emits the `send` GameEvent optimistically for
   * the given seat, so a server SEND echo must NOT emit a duplicate. Solo drives
   * both seats locally (user = 0, AI = 1 via send(1)); multiplayer drives only the
   * local player's seat.
   */
  private emitsSendOptimistically(playerIndex: number): boolean {
    return this.mode === 'solo' || playerIndex === this.myPlayerIndex;
  }
```

- [ ] **Step 9: Apply the predicate as a guard in the SEND branch**

In `frontend/src/application/GameSession.ts`, change the SEND branch condition from:

```ts
      if (!isNaN(senderIndex)) {
```
to:
```ts
      if (!isNaN(senderIndex) && !this.emitsSendOptimistically(senderIndex)) {
```

- [ ] **Step 10: Run the SEND-response tests to verify they pass**

Run (from `frontend/`):
```bash
npx vitest run src/application/GameSession.test.ts -t "SEND response"
```
Expected: all three SEND-response tests PASS.

- [ ] **Step 11: Commit**

```bash
git add frontend/src/application/GameSession.ts frontend/src/application/GameSession.test.ts
git commit -m "fix: animate the opponent's send in multiplayer

Add a SEND branch to GameSession.processEvent that emits a send event for
sends this client did not already emit optimistically, fixing the missing
opponent send animation. Dedup is isolated in emitsSendOptimistically()."
```

---

### Task 2: Respect `skipAnimation` (suppress opponent send during catch-up)

**Files:**
- Modify: `frontend/src/application/GameSession.ts`
- Test: `frontend/src/application/GameSession.test.ts`

Context: `processEvent` computes `const skipAnimation = this.eventQueue.length >= this.CATCHUP_THRESHOLD;` (threshold is 3). GRAB/SLAP already suppress their animations when `skipAnimation` is true. The opponent send must do the same so a backlog (e.g. several sends queued behind a grab/slap animation) doesn't stack many ghost-card animations.

To build a backlog deterministically, a GRAB response is enqueued first: it blocks on `awaitAnimation()`, during which several SEND responses pile up in the queue. We control `awaitAnimation` with a gate promise (the shared `makeSession` resolves it immediately, so this test builds its own harness).

- [ ] **Step 1: Write the failing catch-up test**

Add this test inside the `describe('SEND response', ...)` block:

```ts
    it('skips opponent send animations while catching up (queue backed up)', async () => {
      const events: GameEvent[] = [];
      let releaseAnimation!: () => void;
      const animationGate = new Promise<void>(res => { releaseAnimation = res; });

      const webSocket: WebSocketPort = {
        publish: () => {},
        subscribeToGame: () => {},
      };
      const callbacks: GameSessionCallbacks = {
        onEvent: (e) => events.push(e),
        awaitAnimation: () => animationGate,
      };
      const session = new GameSession(webSocket, callbacks, () => new AI(1, 0));
      session.hydrate('game-1', buildGame());
      session.restoreSession({ 1: 'tok-b' }); // multiplayer, my seat = 1, opponent = 0

      // A GRAB by the opponent blocks on awaitAnimation, letting the queue build.
      const drain = session.onResponse(buildGrabResponse('0', buildGame()));
      // Four opponent SEND responses pile up behind the blocked grab animation.
      for (let i = 0; i < 4; i++) {
        session.onResponse(buildResponse({
          eventType: 'SEND',
          eventData: { player: { id: '0' } },
          state: buildGame(),
        }));
      }
      releaseAnimation();
      await drain;

      const sendEvents = events.filter(e => e.type === 'send');
      // With threshold 3: the first SEND (3 still queued) is skipped, the rest emit.
      expect(sendEvents).toHaveLength(3);
    });
```

This test uses `WebSocketPort` and `GameSessionCallbacks`, which are already imported at the top of the test file (`import GameSession, { type WebSocketPort, type GameSessionCallbacks } from './GameSession';`), and `buildGrabResponse`, already imported from `../model/fixtures`.

- [ ] **Step 2: Run the catch-up test to verify it fails**

Run (from `frontend/`):
```bash
npx vitest run src/application/GameSession.test.ts -t "skips opponent send animations while catching up"
```
Expected: FAIL — all 4 SEND responses emit (`sendEvents` has length 4) because `skipAnimation` is not yet respected.

- [ ] **Step 3: Add the `!skipAnimation` guard**

In `frontend/src/application/GameSession.ts`, change the SEND branch condition from:

```ts
      if (!isNaN(senderIndex) && !this.emitsSendOptimistically(senderIndex)) {
```
to:
```ts
      if (!isNaN(senderIndex) && !skipAnimation && !this.emitsSendOptimistically(senderIndex)) {
```

- [ ] **Step 4: Run the catch-up test to verify it passes**

Run (from `frontend/`):
```bash
npx vitest run src/application/GameSession.test.ts -t "skips opponent send animations while catching up"
```
Expected: PASS (`sendEvents` has length 3).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/application/GameSession.ts frontend/src/application/GameSession.test.ts
git commit -m "fix: suppress opponent send animation during catch-up

Respect the existing skipAnimation flag for SEND responses, matching how
GRAB/SLAP behave, so a backlog of queued sends does not stack ghost cards."
```

---

### Task 3: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Run the entire frontend test suite**

Run (from `frontend/`):
```bash
npm test
```
Expected: all tests PASS (including the existing `GameSession`, `useEndScreen`, model, and AI suites).

- [ ] **Step 2: Run the production build (the real type/compile gate)**

Run (from `frontend/`):
```bash
npm run build
```
Expected: `vite build` completes with no TypeScript errors and emits the `dist/` bundle.

- [ ] **Step 3: Confirm no unintended files changed**

Run (from the repo root):
```bash
git status
```
Expected: only `frontend/src/application/GameSession.ts` and `frontend/src/application/GameSession.test.ts` were modified (plus the plan/spec docs). No changes to `useCardAnimation.ts`, `GameScreen.vue`, or backend files.

---

## Notes for the implementer

- **Keep the optimistic path.** Do NOT remove or alter `GameSession.send()`'s optimistic `send` emission — instant local feedback depends on it. The new branch only handles the *server echo*.
- **Isolation is the point.** All new logic lives in `GameSession`. If you find yourself editing `useCardAnimation.ts` or `GameScreen.vue`, stop — the component already animates correctly from a `send` event regardless of its origin.
- **`topCard` semantics.** At the SEND branch, `this.state` is still the *pre-send* pile (the `state-update` to the new state happens later in `processEvent`), so `this.state?.pile.cards.at(0)` is the correct card to snapshot — identical to what `send()` does.

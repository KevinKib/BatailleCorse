# Frontend Game Session Design

## Goal

Extract all business logic out of the Pinia store into a testable plain TypeScript `GameSession` class, enrich the domain model with behaviour methods, and decouple `AI` from the store — without changing the public API that Vue components consume.

## Problem

The current `BatailleCorse.store.ts` conflates four responsibilities:

1. **Reactive state** — Vue `ref`s watched by components for rendering and animation
2. **Event processing** — queue, drain loop, animation gating
3. **Game flow** — auto-grab timer, AI orchestration
4. **Transport coupling** — `WebSocketService` imports the store and calls `onResponse` directly; `AI` imports the store in its constructor

Business logic is untestable without mounting Vue components or bootstrapping Pinia. The `AI` class contains slap-worthiness rules (game domain knowledge) and has a circular dependency with the store.

## Server authority

The server is the sole authority on game validity. No client-side logic constitutes a security boundary or replaces server validation. Every action the client sends (send, slap, grab) is validated server-side regardless of what the client computed locally. Client-side checks are UI hints and AI heuristics only.

## Architecture

Three layers after the refactor:

```
Domain          BatailleCorse, Pile, Player, AI  — pure TypeScript, no framework
Application     GameSession                       — pure TypeScript, no framework
Presentation    BatailleCorse.store.ts            — thin Pinia bridge, Vue refs only
```

`WebSocketService` remains a module-level singleton. It continues calling `useBatailleCorseStore().onResponse()` — the store delegates immediately to `session.onResponse()`. No change required in `WebSocketService`.

Vue components (`GameScreen.vue`, `StartGame.vue`) keep their existing store API unchanged.

---

## Section 1 — Domain enrichment

### `Pile` (interface → class)

`Pile` gains one behaviour method: `getAutoGrabPlayer()`. This reads pure pile state (`grabbable`, `playerThatAddedLastHonourCard`) that the pile already owns, so it belongs here.

`isSlapWorthy()` is intentionally **not** added to `Pile`. Slap conditions are game-variant rules, not pile knowledge — the pile is state, not a rule evaluator. The conditions will be game-dependent in future variants and must remain separate from the pile data structure. See the AI section for where they live now.

```typescript
class Pile {
  constructor(
    public readonly cards: Card[],
    public readonly grabbable: boolean,
    public readonly nbCardsSinceLastHonourCard: number,
    public readonly playerThatAddedLastHonourCard: PlayerId,
  ) {}

  getAutoGrabPlayer(): number | null {
    if (!this.grabbable) return null;
    const id = Number(this.playerThatAddedLastHonourCard?.id);
    return isNaN(id) ? null : id;
  }

  static fromJSON(data: unknown): Pile { ... }
}
```

### `Player` (interface → class)

`availableActions` is server-provided state — the server decides what each player may do. The client reads it only as a UI hint (disabling buttons) and to guide the AI. The server validates every action independently.

```typescript
class Player {
  constructor(
    public readonly id: string,
    public readonly nbCards: number,
    public readonly availableActions: string[],
  ) {}

  // Reads server-provided state — not client-side authorization.
  hasAvailableAction(action: string): boolean {
    return this.availableActions.includes(action);
  }

  static fromJSON(data: unknown): Player { ... }
}
```

### `BatailleCorse` (class, add deserialization)

`BatailleCorse` is already a class. It gains a `static fromJSON()` factory so server responses produce real domain objects with behaviour rather than raw casts.

```typescript
class BatailleCorse {
  constructor(
    public readonly currentPlayer: Player,
    public readonly pile: Pile,
    public readonly players: Player[],
    public readonly winner: PlayerId | null,
  ) {}

  static fromJSON(data: unknown): BatailleCorse {
    return new BatailleCorse(
      Player.fromJSON(data.currentPlayer),
      Pile.fromJSON(data.pile),
      data.players.map((p: unknown) => Player.fromJSON(p)),
      data.winner ?? null,
    );
  }
}
```

The `Response` interface keeps `state: BatailleCorse` typed as before, but every `processEvent` call runs `BatailleCorse.fromJSON(response.state)` to materialise a real instance.

---

## Section 2 — AI decoupling

`AI` loses its `useBatailleCorseStore()` import. State and actions are injected at call time via `play()`.

**Slap heuristic:** The AI has a private heuristic for deciding when to attempt a slap. This is an AI decision aid — a best-effort approximation — not a validity check. The server validates every slap attempt independently and will penalise the AI for erroneous slaps. The exact conditions are an internal implementation detail of `AI` and are intentionally not prescribed in this spec; they must not be treated as authoritative game rules and will need to become game-variant-configurable in the future.

```typescript
class AI {
  private timeoutId: ReturnType<typeof setTimeout> | undefined;

  constructor(
    private readonly playerIndex: number,
    private readonly reactionTime: number,
  ) {}

  play(state: BatailleCorse, actions: { send(): void; slap(): void }): void {
    clearTimeout(this.timeoutId);
    const delay = this.reactionTime + Math.floor(Math.random() * 200) - 100;
    this.timeoutId = setTimeout(() => {
      if (this.shouldAttemptSlap(state.pile)) {
        actions.slap();  // server validates; AI accepts any resulting penalty
      } else if (state.players[this.playerIndex]?.hasAvailableAction('SEND')) {
        actions.send();
      }
    }, delay);
  }

  cancel(): void {
    clearTimeout(this.timeoutId);
    this.timeoutId = undefined;
  }

  // Internal heuristic — not authoritative game rules.
  // Server validates all slap attempts independently.
  private shouldAttemptSlap(pile: Pile): boolean { ... }
}
```

`AI` is now testable with real domain objects and two spy functions — no store, no Pinia.

---

## Section 3 — `GameSession` (application layer)

### Callback interface

```typescript
type GameEvent =
  | { type: 'state-update'; state: BatailleCorse }
  | { type: 'game-id-change'; gameId: string }
  | { type: 'send'; playerIndex: number; seq: number; topCard: Card | undefined }
  | { type: 'grab'; winnerPlayerIndex: number; seq: number; pileCards: Card[] }
  | { type: 'slap' }
  | { type: 'successful-slap'; winnerPlayerIndex: number; seq: number; pileCards: Card[] }
  | { type: 'erroneous-slap'; playerIndex: number; seq: number };

interface GameSessionCallbacks {
  onEvent(event: GameEvent): void;
  awaitAnimation(): Promise<void>;
}
```

### WebSocket port

```typescript
interface WebSocketPort {
  publish(destination: string, body?: string): void;
  subscribeToGame(gameId: string): void;
}
```

`WebSocketService` satisfies this interface structurally — no change needed.

### `GameSession` responsibilities

- Owns `gameId`, `playerTokens`, `pendingCreate`
- Owns the event queue and drain loop (identical logic to current store)
- Owns the auto-grab timer, using `pile.getAutoGrabPlayer()` instead of inspecting pile fields directly
- Holds an `AI` instance; calls `ai.play(newState, { send, slap })` after each state update
- Recreates the AI on `create()` via an injected factory
- Emits `GameEvent` values through `callbacks.onEvent()`; waits for animation via `callbacks.awaitAnimation()`
- Exposes: `create`, `hydrate`, `restoreTokens`, `send`, `slap`, `grab`, `onResponse`, `cancelAll`
- `notifyAnimationComplete` is **not** on `GameSession` — the store owns `animationResolve` and resolves it directly when the component calls `notifyAnimationComplete()`
- `cancelAll()` cancels both the auto-grab timer and the AI timer; the store calls this from its `cancelAutoGrab()` wrapper (invoked on component unmount)

### AI factory

`GameSession` receives `aiFactory: () => AI` at construction time and calls it inside `create()`. This keeps difficulty/settings out of `GameSession` while ensuring a fresh AI for each game:

```typescript
// Store wires it up:
const session = new GameSession(
  webSocketService,
  callbacks,
  () => new AI(1, DIFFICULTY[settingsStore.difficulty].reactionTime),
);

// Inside GameSession.create():
create(playerName?: string): void {
  this.ai = this.aiFactory();
  this.pendingCreate = true;
  this.webSocket.publish('/app/create', playerName ? JSON.stringify({ playerName }) : undefined);
}
```

---

## Section 4 — Thin store (presentation layer)

The store holds only Vue reactive state and wires it to `GameSession`:

```typescript
export const useBatailleCorseStore = defineStore('bataille-corse-store', () => {
  const state = ref<BatailleCorse>();
  const gameId = ref<string | null>(null);
  const lastSend = ref<SendEvent | null>(null);
  const lastGrab = ref<GrabEvent | null>(null);
  const lastSlap = ref<{ seq: number } | null>(null);
  const lastSuccessfulSlap = ref<SuccessfulSlapEvent | null>(null);
  const lastErroneousSlap = ref<ErroneousSlapEvent | null>(null);

  let animationResolve: (() => void) | null = null;
  // slapSeq lives in the store (not GameSession) because the slap flash animation fires
  // optimistically at the moment the user presses the button, before the server responds.
  let slapSeq = 0;

  const settingsStore = useSettingsStore();

  const session = new GameSession(
    webSocketService,
    {
      onEvent(event) {
        switch (event.type) {
          case 'state-update':      state.value = event.state; break;
          case 'game-id-change':    gameId.value = event.gameId; break;
          case 'send':              lastSend.value = event; break;
          case 'grab':              lastGrab.value = event; break;
          case 'slap':              lastSlap.value = { seq: ++slapSeq }; break;
          case 'successful-slap':   lastSuccessfulSlap.value = event; break;
          case 'erroneous-slap':    lastErroneousSlap.value = event; break;
        }
      },
      awaitAnimation: () => new Promise(resolve => { animationResolve = resolve; }),
    },
    () => new AI(1, DIFFICULTY[settingsStore.difficulty].reactionTime),
  );

  function notifyAnimationComplete() {
    animationResolve?.();
    animationResolve = null;
  }

  return {
    state, gameId, lastSend, lastGrab, lastSlap, lastSuccessfulSlap, lastErroneousSlap,
    create:                   (name?: string) => session.create(name),
    hydrate:                  (id: string, s: BatailleCorse) => session.hydrate(id, s),
    restoreTokens:            (tokens: Record<number, string>) => session.restoreTokens(tokens),
    send:                     (playerIndex: number) => session.send(playerIndex),
    slap:                     (playerIndex: number) => session.slap(playerIndex),
    grab:                     (playerIndex: number) => session.grab(playerIndex),
    onResponse:               (r: Response) => session.onResponse(r),
    notifyAnimationComplete,
    cancelAutoGrab:           () => session.cancelAll(),
  };
});
```

No component changes required.

---

## Section 5 — Testing

### What tests cover

Workflow tests on `GameSession` verify cross-cutting behaviour that is currently untestable:

- CREATE event → game ID emitted, tokens stored in localStorage, subscribeToGame called
- GRAB event → grab event emitted with correct pile snapshot, state updated
- SLAP event (successful) → successful-slap event emitted
- SLAP event (erroneous) → erroneous-slap event emitted
- Auto-grab timer fires after 1500 ms when pile is grabbable → grab published to WebSocket
- Auto-grab cancelled when next event arrives before timer fires
- Animation catchup: events beyond threshold skip `awaitAnimation`

Unit tests on `AI.play()` verify that the AI attempts a slap when its internal heuristic fires and sends when `hasAvailableAction('SEND')` is true — using real `Pile` and `Player` objects built from fixture data.

### Test shape

```typescript
const events: GameEvent[] = [];
const published: { dest: string; body?: string }[] = [];

const session = new GameSession(
  { publish: (dest, body) => published.push({ dest, body }), subscribeToGame: () => {} },
  { onEvent: e => events.push(e), awaitAnimation: () => Promise.resolve() },
  () => new AI(1, 0),
);

session.onResponse(Fixtures.createResponse({ gameId: 'g1', tokens: { 0: 'tok-a', 1: 'tok-b' } }));

expect(events).toContainEqual({ type: 'game-id-change', gameId: 'g1' });
expect(published).toContainEqual(expect.objectContaining({ dest: '/app/create' }));
```

No `vi.fn()` explosion. The WebSocket port is a plain spy object. Domain objects come from fixtures.

### Test infrastructure needed

- `frontend/src/model/fixtures/` — factory functions returning real domain objects (`buildPile`, `buildPlayer`, `buildBatailleCorse`, `buildResponse`)
- Vitest fake timers for auto-grab and AI timeout tests

---

## Files changed

| File | Change |
|------|--------|
| `frontend/src/model/Pile.ts` | interface → class, add `getAutoGrabPlayer()`, `fromJSON()` |
| `frontend/src/model/Player.ts` | interface → class, add `hasAvailableAction()`, `fromJSON()` |
| `frontend/src/model/BatailleCorse.ts` | add constructor, `fromJSON()` |
| `frontend/src/model/ai/AI.ts` | remove store import, inject state+actions at `play()` call time, add `cancel()` |
| `frontend/src/application/GameSession.ts` | new file — application layer |
| `frontend/src/application/GameEvent.ts` | new file — discriminated union |
| `frontend/src/state/BatailleCorse.store.ts` | thin bridge only |
| `frontend/src/model/fixtures/` | new directory — test fixture factories |
| `frontend/src/application/GameSession.test.ts` | new file — workflow tests |
| `frontend/src/model/ai/AI.test.ts` | new file — slap condition and play() workflow tests |

# Frontend Game Session Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract all business logic from the Pinia store into a testable plain TypeScript `GameSession` class, enrich domain models with behaviour methods, and decouple `AI` from the store — without changing the public API that Vue components consume.

**Architecture:** Domain classes (`Pile`, `Player`, `BatailleCorse`) gain behaviour methods and `fromJSON()` factories. A new `GameSession` class owns the event queue, auto-grab timer, and AI orchestration with no Vue/Pinia imports. The store becomes a thin bridge that holds Vue `ref`s and wires them to `GameSession` via a two-callback interface.

**Tech Stack:** Vue 3, Pinia, TypeScript, Vite, Vitest, happy-dom (for `localStorage` in tests)

---

## File map

| File | Action | Responsibility |
|------|--------|----------------|
| `frontend/package.json` | Modify | Add vitest + happy-dom dev deps and test scripts |
| `frontend/vite.config.mjs` | Modify | Add `test` block pointing at happy-dom |
| `frontend/src/model/Pile.ts` | Modify | interface → class; add `getAutoGrabPlayer()`, `fromJSON()` |
| `frontend/src/model/Pile.test.ts` | Create | Unit tests for `getAutoGrabPlayer()` and `fromJSON()` |
| `frontend/src/model/Player.ts` | Modify | interface → class; add `hasAvailableAction()`, `fromJSON()` |
| `frontend/src/model/Player.test.ts` | Create | Unit tests for `hasAvailableAction()` and `fromJSON()` |
| `frontend/src/model/BatailleCorse.ts` | Modify | Add constructor and `static fromJSON()` |
| `frontend/src/model/BatailleCorse.test.ts` | Create | Unit test that `fromJSON` produces real `Pile`/`Player` instances |
| `frontend/src/model/fixtures/index.ts` | Create | Factory functions for real domain objects used in tests |
| `frontend/src/application/GameEvent.ts` | Create | Discriminated union of all game events |
| `frontend/src/model/ai/AI.ts` | Modify | Remove store import; `play(state, actions)` + `cancel()` |
| `frontend/src/model/ai/AI.test.ts` | Create | Tests for play() sending/slapping and cancel() |
| `frontend/src/application/GameSession.ts` | Create | Application layer — event queue, auto-grab, AI orchestration |
| `frontend/src/application/GameSession.test.ts` | Create | Workflow tests: CREATE, GRAB, SLAP, auto-grab timer |
| `frontend/src/state/BatailleCorse.store.ts` | Modify | Thin bridge: Vue refs + GameSession instantiation only |

---

## Task 1: Install and configure Vitest

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/vite.config.mjs`

- [ ] **Step 1: Install Vitest and happy-dom**

Run from the `frontend/` directory (inside the dev container or locally if node_modules is available):

```bash
npm install --save-dev vitest happy-dom
```

Expected: packages appear in `package.json` devDependencies and `node_modules/vitest/` exists.

- [ ] **Step 2: Add test scripts to `package.json`**

Open `frontend/package.json` and add two scripts:

```json
{
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "serve": "vite preview",
    "test": "vitest run",
    "test:watch": "vitest",
    "cy:open": "cypress open",
    "cy:run": "cypress run"
  }
}
```

- [ ] **Step 3: Add test configuration to `vite.config.mjs`**

Replace the entire file content:

```javascript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import svgLoader from 'vite-svg-loader'

export default defineConfig({
  plugins: [vue(), tailwindcss(), svgLoader()],
  test: {
    environment: 'happy-dom',
  },
  server: {
    host: true,
    port: 5173,
    hmr: {
      host: 'localhost',
      port: 5173,
    },
    proxy: {
      "/connect": {
        target: "http://backend:8080",
        ws: true,
        changeOrigin: true
      },
      "/api": {
        target: "http://backend:8080",
        changeOrigin: true
      }
    }
  },
  base: './',
})
```

- [ ] **Step 4: Verify Vitest works**

Run:
```bash
npx vitest run
```

Expected output:
```
No test files found, exiting with code 0
```

(No tests yet — that's correct. The important thing is no "command not found" error.)

- [ ] **Step 5: Commit**

```bash
git add frontend/package.json frontend/vite.config.mjs frontend/package-lock.json
git commit -m "chore: install and configure vitest with happy-dom"
```

---

## Task 2: `Pile` — interface to class

**Files:**
- Modify: `frontend/src/model/Pile.ts`
- Create: `frontend/src/model/Pile.test.ts`

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/model/Pile.test.ts`:

```typescript
import { describe, it, expect } from 'vitest';
import Pile from './Pile';

describe('Pile', () => {
  describe('getAutoGrabPlayer', () => {
    it('returns null when pile is not grabbable', () => {
      const pile = new Pile([], false, 0, { id: '0' });
      expect(pile.getAutoGrabPlayer()).toBeNull();
    });

    it('returns the player index when pile is grabbable', () => {
      const pile = new Pile([], true, 3, { id: '1' });
      expect(pile.getAutoGrabPlayer()).toBe(1);
    });

    it('returns null when playerThatAddedLastHonourCard id is not numeric', () => {
      const pile = new Pile([], true, 0, { id: 'not-a-number' });
      expect(pile.getAutoGrabPlayer()).toBeNull();
    });
  });

  describe('fromJSON', () => {
    it('constructs a Pile instance with all fields accessible', () => {
      const data = {
        cards: [{ rank: 'A', suit: 'spade', name: 'Ace of spades' }],
        grabbable: true,
        nbCardsSinceLastHonourCard: 2,
        playerThatAddedLastHonourCard: { id: '0' },
      };
      const pile = Pile.fromJSON(data);
      expect(pile).toBeInstanceOf(Pile);
      expect(pile.grabbable).toBe(true);
      expect(pile.cards).toHaveLength(1);
      expect(pile.getAutoGrabPlayer()).toBe(0);
    });
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
npx vitest run src/model/Pile.test.ts
```

Expected: FAIL — `Pile is not a constructor` or similar, because `Pile` is still an interface.

- [ ] **Step 3: Replace `Pile` interface with a class**

Replace the entire contents of `frontend/src/model/Pile.ts`:

```typescript
import type Card from "./Card";
import type PlayerId from "./PlayerId";

export default class Pile {
  constructor(
    public readonly cards: Card[],
    public readonly grabbable: boolean,
    public readonly nbCardsSinceLastHonourCard: number,
    public readonly playerThatAddedLastHonourCard: PlayerId,
  ) {}

  /**
   * Returns the index of the player who should auto-grab the pile,
   * or null if the pile is not in a grabbable state.
   * Reads server-provided state — not client-side authorization.
   */
  getAutoGrabPlayer(): number | null {
    if (!this.grabbable) return null;
    const id = Number(this.playerThatAddedLastHonourCard?.id);
    return isNaN(id) ? null : id;
  }

  static fromJSON(data: {
    cards: Card[];
    grabbable: boolean;
    nbCardsSinceLastHonourCard: number;
    playerThatAddedLastHonourCard: PlayerId;
  }): Pile {
    return new Pile(
      data.cards,
      data.grabbable,
      data.nbCardsSinceLastHonourCard,
      data.playerThatAddedLastHonourCard,
    );
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
npx vitest run src/model/Pile.test.ts
```

Expected:
```
✓ src/model/Pile.test.ts (4)
  ✓ Pile > getAutoGrabPlayer > returns null when pile is not grabbable
  ✓ Pile > getAutoGrabPlayer > returns the player index when pile is grabbable
  ✓ Pile > getAutoGrabPlayer > returns null when playerThatAddedLastHonourCard id is not numeric
  ✓ Pile > fromJSON > constructs a Pile instance with all fields accessible

Test Files  1 passed (1)
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/model/Pile.ts frontend/src/model/Pile.test.ts
git commit -m "refactor: Pile interface to class with getAutoGrabPlayer and fromJSON"
```

---

## Task 3: `Player` — interface to class

**Files:**
- Modify: `frontend/src/model/Player.ts`
- Create: `frontend/src/model/Player.test.ts`

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/model/Player.test.ts`:

```typescript
import { describe, it, expect } from 'vitest';
import Player from './Player';

describe('Player', () => {
  describe('hasAvailableAction', () => {
    it('returns true when the action is in the list', () => {
      const player = new Player('0', 26, ['SEND', 'SLAP']);
      expect(player.hasAvailableAction('SEND')).toBe(true);
      expect(player.hasAvailableAction('SLAP')).toBe(true);
    });

    it('returns false when the action is not in the list', () => {
      const player = new Player('0', 26, []);
      expect(player.hasAvailableAction('SEND')).toBe(false);
    });

    it('is case-sensitive', () => {
      const player = new Player('0', 26, ['SEND']);
      expect(player.hasAvailableAction('send')).toBe(false);
    });
  });

  describe('fromJSON', () => {
    it('constructs a Player instance', () => {
      const data = { id: '1', nbCards: 10, availableActions: ['SEND'] };
      const player = Player.fromJSON(data);
      expect(player).toBeInstanceOf(Player);
      expect(player.id).toBe('1');
      expect(player.nbCards).toBe(10);
      expect(player.hasAvailableAction('SEND')).toBe(true);
    });
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
npx vitest run src/model/Player.test.ts
```

Expected: FAIL — `Player is not a constructor`.

- [ ] **Step 3: Replace `Player` interface with a class**

Replace the entire contents of `frontend/src/model/Player.ts`:

```typescript
export default class Player {
  constructor(
    public readonly id: string,
    public readonly nbCards: number,
    public readonly availableActions: string[],
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
  }): Player {
    return new Player(data.id, data.nbCards, data.availableActions);
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
npx vitest run src/model/Player.test.ts
```

Expected:
```
✓ src/model/Player.test.ts (4)

Test Files  1 passed (1)
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/model/Player.ts frontend/src/model/Player.test.ts
git commit -m "refactor: Player interface to class with hasAvailableAction and fromJSON"
```

---

## Task 4: `BatailleCorse` — add constructor and `fromJSON`

**Files:**
- Modify: `frontend/src/model/BatailleCorse.ts`
- Create: `frontend/src/model/BatailleCorse.test.ts`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/model/BatailleCorse.test.ts`:

```typescript
import { describe, it, expect } from 'vitest';
import BatailleCorse from './BatailleCorse';
import Pile from './Pile';
import Player from './Player';

describe('BatailleCorse.fromJSON', () => {
  it('constructs Pile and Player instances with their behaviour methods', () => {
    const data = {
      currentPlayer: { id: '0', nbCards: 26, availableActions: ['SEND'] },
      pile: {
        cards: [],
        grabbable: true,
        nbCardsSinceLastHonourCard: 0,
        playerThatAddedLastHonourCard: { id: '0' },
      },
      players: [
        { id: '0', nbCards: 26, availableActions: ['SEND'] },
        { id: '1', nbCards: 26, availableActions: [] },
      ],
      winner: null,
    };

    const game = BatailleCorse.fromJSON(data);

    expect(game).toBeInstanceOf(BatailleCorse);
    expect(game.pile).toBeInstanceOf(Pile);
    expect(game.players[0]).toBeInstanceOf(Player);
    expect(game.players[1]).toBeInstanceOf(Player);
    expect(game.pile.getAutoGrabPlayer()).toBe(0);
    expect(game.players[0].hasAvailableAction('SEND')).toBe(true);
    expect(game.players[1].hasAvailableAction('SEND')).toBe(false);
    expect(game.winner).toBeNull();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npx vitest run src/model/BatailleCorse.test.ts
```

Expected: FAIL — `BatailleCorse.fromJSON is not a function`.

- [ ] **Step 3: Add constructor and `fromJSON` to `BatailleCorse`**

Replace the entire contents of `frontend/src/model/BatailleCorse.ts`:

```typescript
import Card from "./Card";
import Pile from "./Pile";
import Player from "./Player";
import type PlayerId from "./PlayerId";

export default class BatailleCorse {
  constructor(
    public readonly currentPlayer: Player,
    public readonly pile: Pile,
    public readonly players: Player[],
    public readonly winner: PlayerId | null,
  ) {}

  static fromJSON(data: {
    currentPlayer: { id: string; nbCards: number; availableActions: string[] };
    pile: {
      cards: { rank: string; suit: string; name: string }[];
      grabbable: boolean;
      nbCardsSinceLastHonourCard: number;
      playerThatAddedLastHonourCard: { id: string };
    };
    players: { id: string; nbCards: number; availableActions: string[] }[];
    winner: { id: string } | null;
  }): BatailleCorse {
    return new BatailleCorse(
      Player.fromJSON(data.currentPlayer),
      Pile.fromJSON(data.pile),
      data.players.map(p => Player.fromJSON(p)),
      data.winner ?? null,
    );
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
npx vitest run src/model/BatailleCorse.test.ts
```

Expected:
```
✓ src/model/BatailleCorse.test.ts (1)

Test Files  1 passed (1)
```

- [ ] **Step 5: Verify all existing tests still pass**

```bash
npx vitest run
```

Expected: All 9 tests pass (Pile × 4, Player × 4, BatailleCorse × 1).

- [ ] **Step 6: Commit**

```bash
git add frontend/src/model/BatailleCorse.ts frontend/src/model/BatailleCorse.test.ts
git commit -m "refactor: add BatailleCorse constructor and fromJSON factory"
```

---

## Task 5: Test fixtures

**Files:**
- Create: `frontend/src/model/fixtures/index.ts`

No unit tests for fixtures — they are test helpers. They must be correct by inspection.

- [ ] **Step 1: Create the fixtures file**

Create `frontend/src/model/fixtures/index.ts`:

```typescript
import type Card from '../Card';
import Pile from '../Pile';
import Player from '../Player';
import BatailleCorse from '../BatailleCorse';
import type Response from '../Response';

// ---------------------------------------------------------------------------
// Primitive builders
// ---------------------------------------------------------------------------

export function buildCard(overrides: Partial<Card> = {}): Card {
  return { rank: '2', suit: 'heart', name: '2 of hearts', ...overrides };
}

// ---------------------------------------------------------------------------
// Domain builders — return real class instances with behaviour methods
// ---------------------------------------------------------------------------

export function buildPile(overrides: Partial<{
  cards: Card[];
  grabbable: boolean;
  nbCardsSinceLastHonourCard: number;
  playerThatAddedLastHonourCard: { id: string };
}> = {}): Pile {
  return new Pile(
    overrides.cards ?? [],
    overrides.grabbable ?? false,
    overrides.nbCardsSinceLastHonourCard ?? 0,
    overrides.playerThatAddedLastHonourCard ?? { id: '0' },
  );
}

export function buildPlayer(overrides: Partial<{
  id: string;
  nbCards: number;
  availableActions: string[];
}> = {}): Player {
  return new Player(
    overrides.id ?? '0',
    overrides.nbCards ?? 26,
    overrides.availableActions ?? [],
  );
}

export function buildGame(overrides: Partial<{
  pile: Pile;
  players: Player[];
  winner: { id: string } | null;
}> = {}): BatailleCorse {
  const players = overrides.players ?? [
    buildPlayer({ id: '0' }),
    buildPlayer({ id: '1' }),
  ];
  return new BatailleCorse(
    players[0],
    overrides.pile ?? buildPile(),
    players,
    overrides.winner ?? null,
  );
}

// ---------------------------------------------------------------------------
// Response builders
// ---------------------------------------------------------------------------

export function buildResponse(overrides: Partial<Response> = {}): Response {
  return {
    success: true,
    eventType: 'SEND',
    eventData: {},
    message: '',
    state: buildGame(),
    ...overrides,
  };
}

export function buildCreateResponse(
  gameId: string,
  tokens: Record<number, string>,
  state: BatailleCorse = buildGame(),
): Response {
  return {
    success: true,
    eventType: 'CREATE',
    eventData: { game: { id: gameId }, tokens },
    message: 'Game created',
    state,
  };
}

export function buildGrabResponse(
  winnerPlayerId: string,
  state: BatailleCorse = buildGame(),
): Response {
  return {
    success: true,
    eventType: 'GRAB',
    eventData: { player: { id: winnerPlayerId } },
    message: '',
    state,
  };
}

export function buildSlapResponse(
  isSuccessful: boolean,
  slapperPlayerId: string,
  state: BatailleCorse = buildGame(),
): Response {
  return {
    success: true,
    eventType: 'SLAP',
    eventData: { isSuccessful, player: { id: slapperPlayerId } },
    message: '',
    state,
  };
}
```

- [ ] **Step 2: Verify TypeScript is happy with the fixtures**

```bash
npx tsc --noEmit
```

Expected: No errors. If there are type errors about `Response.state` being `BatailleCorse` while we pass a `BatailleCorse` instance — that's fine, they're compatible.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/model/fixtures/index.ts
git commit -m "test: add domain fixture factories for unit tests"
```

---

## Task 6: `GameEvent` discriminated union

**Files:**
- Create: `frontend/src/application/GameEvent.ts`

No tests needed — this is a type definition only.

- [ ] **Step 1: Create the application directory and `GameEvent.ts`**

Create `frontend/src/application/GameEvent.ts`:

```typescript
import type BatailleCorse from '../model/BatailleCorse';
import type Card from '../model/Card';

export type GameEvent =
  | { type: 'state-update'; state: BatailleCorse }
  | { type: 'game-id-change'; gameId: string }
  | { type: 'send'; playerIndex: number; seq: number; topCard: Card | undefined }
  | { type: 'grab'; winnerPlayerIndex: number; seq: number; pileCards: Card[] }
  | { type: 'slap' }
  | { type: 'successful-slap'; winnerPlayerIndex: number; seq: number; pileCards: Card[] }
  | { type: 'erroneous-slap'; playerIndex: number; seq: number };
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/application/GameEvent.ts
git commit -m "feat: add GameEvent discriminated union"
```

---

## Task 7: Refactor `AI`

**Files:**
- Modify: `frontend/src/model/ai/AI.ts`
- Create: `frontend/src/model/ai/AI.test.ts`

- [ ] **Step 1: Write failing tests**

Create `frontend/src/model/ai/AI.test.ts`:

```typescript
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import AI from './AI';
import { buildGame, buildPile, buildPlayer, buildCard } from '../fixtures';

describe('AI.play', () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); });

  it('calls slap when pile has a rank-10 card on top', () => {
    const ai = new AI(1, 0);
    const state = buildGame({
      pile: buildPile({ cards: [buildCard({ rank: '10' })] }),
      players: [buildPlayer({ id: '0' }), buildPlayer({ id: '1' })],
    });
    const actions = { send: vi.fn(), slap: vi.fn() };

    ai.play(state, actions);
    vi.runAllTimers();

    expect(actions.slap).toHaveBeenCalledOnce();
    expect(actions.send).not.toHaveBeenCalled();
  });

  it('calls slap when top two cards share the same rank', () => {
    const ai = new AI(1, 0);
    const state = buildGame({
      pile: buildPile({ cards: [buildCard({ rank: '7' }), buildCard({ rank: '7' })] }),
      players: [buildPlayer({ id: '0' }), buildPlayer({ id: '1' })],
    });
    const actions = { send: vi.fn(), slap: vi.fn() };

    ai.play(state, actions);
    vi.runAllTimers();

    expect(actions.slap).toHaveBeenCalledOnce();
  });

  it('calls send when pile is not slap-worthy and SEND is available', () => {
    const ai = new AI(1, 0);
    const state = buildGame({
      pile: buildPile({ cards: [] }),
      players: [buildPlayer({ id: '0' }), buildPlayer({ id: '1', availableActions: ['SEND'] })],
    });
    const actions = { send: vi.fn(), slap: vi.fn() };

    ai.play(state, actions);
    vi.runAllTimers();

    expect(actions.send).toHaveBeenCalledOnce();
    expect(actions.slap).not.toHaveBeenCalled();
  });

  it('does nothing when pile is not slap-worthy and SEND is unavailable', () => {
    const ai = new AI(1, 0);
    const state = buildGame({
      pile: buildPile({ cards: [] }),
      players: [buildPlayer({ id: '0' }), buildPlayer({ id: '1', availableActions: [] })],
    });
    const actions = { send: vi.fn(), slap: vi.fn() };

    ai.play(state, actions);
    vi.runAllTimers();

    expect(actions.send).not.toHaveBeenCalled();
    expect(actions.slap).not.toHaveBeenCalled();
  });

  it('cancel prevents the pending action from firing', () => {
    const ai = new AI(1, 500);
    const state = buildGame({
      pile: buildPile({ cards: [] }),
      players: [buildPlayer({ id: '0' }), buildPlayer({ id: '1', availableActions: ['SEND'] })],
    });
    const actions = { send: vi.fn(), slap: vi.fn() };

    ai.play(state, actions);
    ai.cancel();
    vi.runAllTimers();

    expect(actions.send).not.toHaveBeenCalled();
  });

  it('a second play() call replaces the pending first call', () => {
    const ai = new AI(1, 500);
    const slapState = buildGame({
      pile: buildPile({ cards: [buildCard({ rank: '10' })] }),
    });
    const sendState = buildGame({
      pile: buildPile({ cards: [] }),
      players: [buildPlayer({ id: '0' }), buildPlayer({ id: '1', availableActions: ['SEND'] })],
    });
    const actions = { send: vi.fn(), slap: vi.fn() };

    ai.play(slapState, actions);
    ai.play(sendState, actions); // replaces the pending slap
    vi.runAllTimers();

    expect(actions.slap).not.toHaveBeenCalled();
    expect(actions.send).toHaveBeenCalledOnce();
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
npx vitest run src/model/ai/AI.test.ts
```

Expected: FAIL — compilation errors because the current `AI.play()` takes no arguments.

- [ ] **Step 3: Replace `AI.ts` with the decoupled version**

Replace the entire contents of `frontend/src/model/ai/AI.ts`:

```typescript
import type BatailleCorse from '../BatailleCorse';
import type Pile from '../Pile';

export default class AI {
  private timeoutId: ReturnType<typeof setTimeout> | undefined;

  constructor(
    private readonly playerIndex: number,
    private readonly reactionTime: number,
  ) {}

  /**
   * Schedules the AI's next action based on the current game state.
   * State and actions are injected at call time — no store dependency.
   * A second call before the timeout fires replaces the pending action.
   */
  play(state: BatailleCorse, actions: { send(): void; slap(): void }): void {
    clearTimeout(this.timeoutId);
    const variation = Math.floor(Math.random() * 200) - 100;
    const delay = Math.max(0, this.reactionTime + variation);
    this.timeoutId = setTimeout(() => {
      if (this.shouldAttemptSlap(state.pile)) {
        actions.slap(); // server validates; AI accepts any resulting penalty
      } else if (state.players[this.playerIndex]?.hasAvailableAction('SEND')) {
        actions.send();
      }
    }, delay);
  }

  /** Cancels the pending scheduled action, if any. */
  cancel(): void {
    clearTimeout(this.timeoutId);
    this.timeoutId = undefined;
  }

  /**
   * Internal AI heuristic for when to attempt a slap.
   * NOT authoritative game rules — the server validates all slap attempts.
   * These conditions are game-variant-specific and will need to become
   * configurable when game variants are introduced.
   */
  private shouldAttemptSlap(pile: Pile): boolean {
    const cards = pile.cards;
    if (cards.length >= 1 && cards[0].rank === '10') return true;
    if (cards.length >= 2 && cards[0].rank === cards[1].rank) return true;
    if (cards.length >= 3 && cards[0].rank === cards[2].rank) return true;
    if (cards.length >= 2) {
      const r0 = Number(cards[0].rank);
      const r1 = Number(cards[1].rank);
      if (!isNaN(r0) && !isNaN(r1) && r0 + r1 === 10) return true;
    }
    return false;
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
npx vitest run src/model/ai/AI.test.ts
```

Expected:
```
✓ src/model/ai/AI.test.ts (6)

Test Files  1 passed (1)
```

- [ ] **Step 5: Run all tests to ensure nothing is broken**

```bash
npx vitest run
```

Expected: All tests pass. (The store still references the old `AI` API — that's fine for now; TypeScript compilation of the full app will show errors, but tests pass.)

- [ ] **Step 6: Commit**

```bash
git add frontend/src/model/ai/AI.ts frontend/src/model/ai/AI.test.ts
git commit -m "refactor: AI decoupled from store — play(state, actions) + cancel()"
```

---

## Task 8: `GameSession` — tests then implementation

**Files:**
- Create: `frontend/src/application/GameSession.ts`
- Create: `frontend/src/application/GameSession.test.ts`

- [ ] **Step 1: Create a skeleton `GameSession` so imports resolve**

Create `frontend/src/application/GameSession.ts` with stub methods that throw:

```typescript
import BatailleCorse from '../model/BatailleCorse';
import type Response from '../model/Response';
import type { GameEvent } from './GameEvent';
import AI from '../model/ai/AI';

export interface WebSocketPort {
  publish(destination: string, body?: string): void;
  subscribeToGame(gameId: string): void;
}

export interface GameSessionCallbacks {
  onEvent(event: GameEvent): void;
  awaitAnimation(): Promise<void>;
}

export default class GameSession {
  constructor(
    private readonly webSocket: WebSocketPort,
    private readonly callbacks: GameSessionCallbacks,
    private readonly aiFactory: () => AI,
  ) {}

  create(_playerName?: string): void { throw new Error('not implemented'); }
  hydrate(_id: string, _gameState: BatailleCorse): void { throw new Error('not implemented'); }
  restoreTokens(_tokens: Record<number, string>): void { throw new Error('not implemented'); }
  send(_playerIndex: number): void { throw new Error('not implemented'); }
  slap(_playerIndex: number): void { throw new Error('not implemented'); }
  grab(_playerIndex: number): void { throw new Error('not implemented'); }
  onResponse(_response: Response): Promise<void> { throw new Error('not implemented'); }
  cancelAll(): void { throw new Error('not implemented'); }
}
```

- [ ] **Step 2: Write all workflow tests**

Create `frontend/src/application/GameSession.test.ts`:

```typescript
import { describe, it, expect, vi, afterEach } from 'vitest';
import GameSession, { type WebSocketPort, type GameSessionCallbacks } from './GameSession';
import AI from '../model/ai/AI';
import type { GameEvent } from './GameEvent';
import {
  buildCreateResponse,
  buildGrabResponse,
  buildSlapResponse,
  buildGame,
  buildPile,
  buildPlayer,
  buildCard,
  buildResponse,
} from '../model/fixtures';

// ---------------------------------------------------------------------------
// Test harness
// ---------------------------------------------------------------------------

function makeSession() {
  const events: GameEvent[] = [];
  const published: { dest: string; body?: string }[] = [];
  const subscribed: string[] = [];

  const webSocket: WebSocketPort = {
    publish: (dest, body) => published.push({ dest, body }),
    subscribeToGame: (id) => subscribed.push(id),
  };

  const callbacks: GameSessionCallbacks = {
    onEvent: (e) => events.push(e),
    awaitAnimation: () => Promise.resolve(),
  };

  const session = new GameSession(webSocket, callbacks, () => new AI(1, 0));

  return { session, events, published, subscribed };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('GameSession', () => {
  afterEach(() => { vi.useRealTimers(); });

  // --- CREATE ---------------------------------------------------------------

  describe('CREATE event', () => {
    it('emits game-id-change and subscribes to per-game channel', async () => {
      const { session, events, subscribed } = makeSession();
      session.create('Alice');
      await session.onResponse(buildCreateResponse('game-1', { 0: 'tok-a', 1: 'tok-b' }));
      expect(events).toContainEqual({ type: 'game-id-change', gameId: 'game-1' });
      expect(subscribed).toContain('game-1');
    });

    it('stores tokens in localStorage', async () => {
      const { session } = makeSession();
      session.create();
      await session.onResponse(buildCreateResponse('game-42', { 0: 'tok-x', 1: 'tok-y' }));
      const stored = JSON.parse(localStorage.getItem('tokens:game-42')!);
      expect(stored).toEqual({ 0: 'tok-x', 1: 'tok-y' });
    });

    it('ignores CREATE events when no create is pending', async () => {
      const { session, events, subscribed } = makeSession();
      // no session.create() call — pendingCreate stays false
      await session.onResponse(buildCreateResponse('game-1', { 0: 'tok-a', 1: 'tok-b' }));
      expect(events.find(e => e.type === 'game-id-change')).toBeUndefined();
      expect(subscribed).toHaveLength(0);
    });
  });

  // --- GRAB -----------------------------------------------------------------

  describe('GRAB event', () => {
    it('emits grab event with a snapshot of the pile before state update', async () => {
      const { session, events } = makeSession();
      const cardBeforeGrab = buildCard({ rank: 'A', suit: 'spade' });
      session.hydrate('game-1', buildGame({ pile: buildPile({ cards: [cardBeforeGrab] }) }));
      session.restoreTokens({ 0: 'tok-a', 1: 'tok-b' });

      // after the grab, the server clears the pile
      const response = buildGrabResponse('0', buildGame({ pile: buildPile({ cards: [] }) }));
      await session.onResponse(response);

      const grabEvent = events.find(e => e.type === 'grab') as Extract<GameEvent, { type: 'grab' }> | undefined;
      expect(grabEvent).toBeDefined();
      expect(grabEvent!.winnerPlayerIndex).toBe(0);
      expect(grabEvent!.pileCards).toContainEqual(cardBeforeGrab);
    });

    it('emits state-update after the grab', async () => {
      const { session, events } = makeSession();
      session.hydrate('game-1', buildGame());
      session.restoreTokens({ 0: 'tok-a', 1: 'tok-b' });
      await session.onResponse(buildGrabResponse('0'));
      expect(events.find(e => e.type === 'state-update')).toBeDefined();
    });
  });

  // --- SLAP -----------------------------------------------------------------

  describe('SLAP event', () => {
    it('emits successful-slap event with winner and pile snapshot', async () => {
      const { session, events } = makeSession();
      const cardInPile = buildCard({ rank: 'K' });
      session.hydrate('game-1', buildGame({ pile: buildPile({ cards: [cardInPile] }) }));
      session.restoreTokens({ 0: 'tok-a', 1: 'tok-b' });

      await session.onResponse(buildSlapResponse(true, '1'));

      const slapEvent = events.find(e => e.type === 'successful-slap') as
        Extract<GameEvent, { type: 'successful-slap' }> | undefined;
      expect(slapEvent).toBeDefined();
      expect(slapEvent!.winnerPlayerIndex).toBe(1);
      expect(slapEvent!.pileCards).toContainEqual(cardInPile);
    });

    it('emits erroneous-slap event with the slapper index', async () => {
      const { session, events } = makeSession();
      session.hydrate('game-1', buildGame());
      session.restoreTokens({ 0: 'tok-a', 1: 'tok-b' });

      await session.onResponse(buildSlapResponse(false, '0'));

      const errEvent = events.find(e => e.type === 'erroneous-slap') as
        Extract<GameEvent, { type: 'erroneous-slap' }> | undefined;
      expect(errEvent).toBeDefined();
      expect(errEvent!.playerIndex).toBe(0);
    });
  });

  // --- SEND / SLAP user actions ---------------------------------------------

  describe('send()', () => {
    it('publishes to /app/send with the correct token', () => {
      const { session, published } = makeSession();
      session.hydrate('game-1', buildGame());
      session.restoreTokens({ 0: 'tok-a', 1: 'tok-b' });

      session.send(0);

      expect(published).toHaveLength(1);
      expect(published[0].dest).toBe('/app/send');
      expect(JSON.parse(published[0].body!).token).toBe('tok-a');
      expect(JSON.parse(published[0].body!).gameId).toBe('game-1');
    });

    it('emits a send event with topCard snapshot', () => {
      const { session, events } = makeSession();
      const topCard = buildCard({ rank: 'Q', suit: 'diamond' });
      session.hydrate('game-1', buildGame({ pile: buildPile({ cards: [topCard] }) }));
      session.restoreTokens({ 0: 'tok-a', 1: 'tok-b' });

      session.send(0);

      const sendEvent = events.find(e => e.type === 'send') as
        Extract<GameEvent, { type: 'send' }> | undefined;
      expect(sendEvent).toBeDefined();
      expect(sendEvent!.topCard).toEqual(topCard);
    });
  });

  describe('slap()', () => {
    it('publishes to /app/slap with the correct token', () => {
      const { session, published } = makeSession();
      session.hydrate('game-1', buildGame());
      session.restoreTokens({ 0: 'tok-a', 1: 'tok-b' });

      session.slap(1);

      expect(published).toHaveLength(1);
      expect(published[0].dest).toBe('/app/slap');
      expect(JSON.parse(published[0].body!).token).toBe('tok-b');
    });

    it('emits a slap event immediately', () => {
      const { session, events } = makeSession();
      session.hydrate('game-1', buildGame());
      session.restoreTokens({ 0: 'tok-a', 1: 'tok-b' });

      session.slap(0);

      expect(events).toContainEqual({ type: 'slap' });
    });
  });

  // --- Auto-grab timer ------------------------------------------------------

  describe('auto-grab timer', () => {
    it('fires grab after 1500 ms when pile becomes grabbable', async () => {
      vi.useFakeTimers();
      const { session, published } = makeSession();
      session.hydrate('game-1', buildGame());
      session.restoreTokens({ 0: 'tok-a', 1: 'tok-b' });

      await session.onResponse(buildResponse({
        eventType: 'SEND',
        state: buildGame({
          pile: buildPile({ grabbable: true, playerThatAddedLastHonourCard: { id: '0' } }),
        }),
      }));

      vi.advanceTimersByTime(1499);
      expect(published.some(p => p.dest === '/app/grab')).toBe(false);

      vi.advanceTimersByTime(1);
      expect(published.some(p => p.dest === '/app/grab')).toBe(true);
    });

    it('cancels pending auto-grab when next event arrives', async () => {
      vi.useFakeTimers();
      const { session, published } = makeSession();
      session.hydrate('game-1', buildGame());
      session.restoreTokens({ 0: 'tok-a', 1: 'tok-b' });

      // First event: pile is grabbable — timer starts
      await session.onResponse(buildResponse({
        eventType: 'SEND',
        state: buildGame({
          pile: buildPile({ grabbable: true, playerThatAddedLastHonourCard: { id: '0' } }),
        }),
      }));

      vi.advanceTimersByTime(500);

      // Second event: pile is no longer grabbable — timer is cancelled
      await session.onResponse(buildResponse({
        eventType: 'SEND',
        state: buildGame({ pile: buildPile({ grabbable: false }) }),
      }));

      vi.advanceTimersByTime(2000);
      expect(published.some(p => p.dest === '/app/grab')).toBe(false);
    });

    it('cancelAll stops a pending auto-grab', async () => {
      vi.useFakeTimers();
      const { session, published } = makeSession();
      session.hydrate('game-1', buildGame());
      session.restoreTokens({ 0: 'tok-a', 1: 'tok-b' });

      await session.onResponse(buildResponse({
        eventType: 'SEND',
        state: buildGame({
          pile: buildPile({ grabbable: true, playerThatAddedLastHonourCard: { id: '0' } }),
        }),
      }));

      session.cancelAll();
      vi.advanceTimersByTime(2000);
      expect(published.some(p => p.dest === '/app/grab')).toBe(false);
    });
  });

  // --- Event queue ----------------------------------------------------------

  describe('event queue seq counters', () => {
    it('increments seq on each send event', () => {
      const { session, events } = makeSession();
      session.hydrate('game-1', buildGame());
      session.restoreTokens({ 0: 'tok-a', 1: 'tok-b' });

      session.send(0);
      session.send(0);

      const seqs = events
        .filter((e): e is Extract<GameEvent, { type: 'send' }> => e.type === 'send')
        .map(e => e.seq);
      expect(seqs[1]).toBe(seqs[0] + 1);
    });
  });
});
```

- [ ] **Step 3: Run tests to verify they all fail (skeleton throws)**

```bash
npx vitest run src/application/GameSession.test.ts
```

Expected: All tests FAIL with `Error: not implemented`.

- [ ] **Step 4: Implement `GameSession`**

Replace the entire contents of `frontend/src/application/GameSession.ts`:

```typescript
import BatailleCorse from '../model/BatailleCorse';
import type Card from '../model/Card';
import type Response from '../model/Response';
import type CreateEventData from '../model/event/CreateEventData';
import type GrabEventData from '../model/event/GrabEventData';
import type SlapEventData from '../model/event/SlapEventData';
import type { GameEvent } from './GameEvent';
import AI from '../model/ai/AI';

export interface WebSocketPort {
  publish(destination: string, body?: string): void;
  subscribeToGame(gameId: string): void;
}

export interface GameSessionCallbacks {
  onEvent(event: GameEvent): void;
  awaitAnimation(): Promise<void>;
}

export default class GameSession {
  private gameId: string | null = null;
  private state: BatailleCorse | undefined = undefined;
  private playerTokens: Record<number, string> = {};
  private pendingCreate = false;
  private autoGrabTimeoutId: ReturnType<typeof setTimeout> | null = null;
  private ai: AI;

  private readonly CATCHUP_THRESHOLD = 3;
  private readonly AUTO_GRAB_DELAY = 1500;
  private readonly eventQueue: Response[] = [];
  private isProcessingQueue = false;

  private sendSeq = 0;
  private grabSeq = 0;
  private successfulSlapSeq = 0;
  private erroneousSlapSeq = 0;

  constructor(
    private readonly webSocket: WebSocketPort,
    private readonly callbacks: GameSessionCallbacks,
    private readonly aiFactory: () => AI,
  ) {
    this.ai = aiFactory();
  }

  create(playerName?: string): void {
    this.pendingCreate = true;
    this.ai = this.aiFactory();
    this.webSocket.publish(
      '/app/create',
      playerName ? JSON.stringify({ playerName }) : undefined,
    );
  }

  hydrate(id: string, gameState: BatailleCorse): void {
    this.gameId = id;
    this.state = gameState;
  }

  restoreTokens(tokens: Record<number, string>): void {
    this.playerTokens = tokens;
  }

  send(playerIndex: number): void {
    const topCard = this.state?.pile.cards.at(0);
    this.callbacks.onEvent({ type: 'send', playerIndex, seq: ++this.sendSeq, topCard });
    this.webSocket.publish('/app/send', JSON.stringify({
      gameId: this.gameId,
      token: this.playerTokens[playerIndex],
    }));
  }

  slap(playerIndex: number): void {
    this.callbacks.onEvent({ type: 'slap' });
    this.webSocket.publish('/app/slap', JSON.stringify({
      gameId: this.gameId,
      token: this.playerTokens[playerIndex],
    }));
  }

  grab(playerIndex: number): void {
    this.webSocket.publish('/app/grab', JSON.stringify({
      gameId: this.gameId,
      token: this.playerTokens[playerIndex],
    }));
  }

  /** Starts queue processing. Returns a promise that resolves when the queue is drained. */
  onResponse(response: Response): Promise<void> {
    this.eventQueue.push(response);
    if (!this.isProcessingQueue) return this.drainQueue();
    return Promise.resolve();
  }

  /** Cancels the auto-grab timer and any pending AI action. */
  cancelAll(): void {
    if (this.autoGrabTimeoutId !== null) {
      clearTimeout(this.autoGrabTimeoutId);
      this.autoGrabTimeoutId = null;
    }
    this.ai.cancel();
  }

  private async drainQueue(): Promise<void> {
    this.isProcessingQueue = true;
    while (this.eventQueue.length > 0) {
      const response = this.eventQueue.shift()!;
      await this.processEvent(response);
    }
    this.isProcessingQueue = false;
  }

  private async processEvent(response: Response): Promise<void> {
    const skipAnimation = this.eventQueue.length >= this.CATCHUP_THRESHOLD;
    let needsAnimationWait = false;

    if (response.eventType === 'CREATE') {
      if (!this.pendingCreate) return;
      this.pendingCreate = false;
      const createData = response.eventData as CreateEventData;
      this.gameId = createData.game.id;
      this.playerTokens = createData.tokens;
      localStorage.setItem(`tokens:${this.gameId}`, JSON.stringify(createData.tokens));
      this.webSocket.subscribeToGame(this.gameId);
      this.callbacks.onEvent({ type: 'game-id-change', gameId: this.gameId });
    }

    if (response.eventType === 'GRAB') {
      const grabData = response.eventData as GrabEventData;
      const winnerPlayerIndex = Number(grabData.player?.id);
      if (!isNaN(winnerPlayerIndex)) {
        const pileCards: Card[] = [...(this.state?.pile.cards ?? [])];
        this.callbacks.onEvent({
          type: 'grab',
          winnerPlayerIndex,
          seq: ++this.grabSeq,
          pileCards,
        });
        needsAnimationWait = true;
      }
    }

    if (response.eventType === 'SLAP') {
      const slapData = response.eventData as SlapEventData;
      const slapperIndex = Number(slapData.player?.id);
      if (slapData.isSuccessful && !isNaN(slapperIndex)) {
        const pileCards: Card[] = [...(this.state?.pile.cards ?? [])];
        this.callbacks.onEvent({
          type: 'successful-slap',
          winnerPlayerIndex: slapperIndex,
          seq: ++this.successfulSlapSeq,
          pileCards,
        });
        needsAnimationWait = true;
      } else if (!slapData.isSuccessful && !isNaN(slapperIndex)) {
        this.callbacks.onEvent({
          type: 'erroneous-slap',
          playerIndex: slapperIndex,
          seq: ++this.erroneousSlapSeq,
        });
        needsAnimationWait = true;
      }
    }

    const newState = BatailleCorse.fromJSON(response.state as unknown as Parameters<typeof BatailleCorse.fromJSON>[0]);
    this.state = newState;
    this.callbacks.onEvent({ type: 'state-update', state: newState });

    // Reset and conditionally restart the auto-grab timer
    if (this.autoGrabTimeoutId !== null) {
      clearTimeout(this.autoGrabTimeoutId);
      this.autoGrabTimeoutId = null;
    }
    const grabPlayer = newState.pile.getAutoGrabPlayer();
    if (grabPlayer !== null) {
      this.autoGrabTimeoutId = setTimeout(() => {
        this.autoGrabTimeoutId = null;
        this.grab(grabPlayer);
      }, this.AUTO_GRAB_DELAY);
    }

    // Let the AI decide its next action
    this.ai.play(newState, {
      send: () => this.send(1),
      slap: () => this.slap(1),
    });

    if (needsAnimationWait && !skipAnimation) {
      await this.callbacks.awaitAnimation();
    }
  }
}
```

- [ ] **Step 5: Run all tests**

```bash
npx vitest run
```

Expected: All tests pass.

```
✓ src/model/Pile.test.ts (4)
✓ src/model/Player.test.ts (4)
✓ src/model/BatailleCorse.test.ts (1)
✓ src/model/ai/AI.test.ts (6)
✓ src/application/GameSession.test.ts (14)

Test Files  5 passed (5)
Tests       29 passed (29)
```

- [ ] **Step 6: Commit**

```bash
git add frontend/src/application/GameSession.ts frontend/src/application/GameSession.test.ts
git commit -m "feat: add GameSession application layer with workflow tests"
```

---

## Task 9: Store refactor — thin bridge

**Files:**
- Modify: `frontend/src/state/BatailleCorse.store.ts`

The store's public return object keeps the same shape. No component changes needed.

- [ ] **Step 1: Replace the store with the thin bridge**

Replace the entire contents of `frontend/src/state/BatailleCorse.store.ts`:

```typescript
import { defineStore } from "pinia";
import { ref } from "vue";

import webSocketService from '../service/WebSocketService';
import BatailleCorse from "../model/BatailleCorse";
import type Card from "../model/Card";
import type Response from "../model/Response";
import AI from "../model/ai/AI";
import { useSettingsStore } from './Settings.store';
import { DIFFICULTY } from '../model/Difficulty';
import GameSession from '../application/GameSession';
import type { GameEvent } from '../application/GameEvent';

export const useBatailleCorseStore = defineStore('bataille-corse-store', () => {

  const state = ref<BatailleCorse>();
  const gameId = ref<string | null>(null);
  const lastSend = ref<{ playerIndex: number; seq: number; topCard: Card | undefined } | null>(null);
  const lastGrab = ref<{ winnerPlayerIndex: number; seq: number; pileCards: Card[] } | null>(null);
  const lastSlap = ref<{ seq: number } | null>(null);
  const lastSuccessfulSlap = ref<{ winnerPlayerIndex: number; seq: number; pileCards: Card[] } | null>(null);
  const lastErroneousSlap = ref<{ playerIndex: number; seq: number } | null>(null);

  let animationResolve: (() => void) | null = null;
  // slapSeq lives in the store because the slap flash animation fires optimistically
  // at the moment the user presses the button, before the server responds.
  let slapSeq = 0;

  const settingsStore = useSettingsStore();

  const session = new GameSession(
    webSocketService,
    {
      onEvent(event: GameEvent) {
        switch (event.type) {
          case 'state-update':    state.value = event.state; break;
          case 'game-id-change':  gameId.value = event.gameId; break;
          case 'send':            lastSend.value = event; break;
          case 'grab':            lastGrab.value = event; break;
          case 'slap':            lastSlap.value = { seq: ++slapSeq }; break;
          case 'successful-slap': lastSuccessfulSlap.value = event; break;
          case 'erroneous-slap':  lastErroneousSlap.value = event; break;
        }
      },
      awaitAnimation: () => new Promise<void>(resolve => { animationResolve = resolve; }),
    },
    () => new AI(1, DIFFICULTY[settingsStore.difficulty].reactionTime),
  );

  function notifyAnimationComplete() {
    animationResolve?.();
    animationResolve = null;
  }

  return {
    state,
    gameId,
    lastSend,
    lastGrab,
    lastSlap,
    lastSuccessfulSlap,
    lastErroneousSlap,
    create:               (name?: string) => session.create(name),
    hydrate:              (id: string, s: BatailleCorse) => session.hydrate(id, s),
    restoreTokens:        (tokens: Record<number, string>) => session.restoreTokens(tokens),
    send:                 (playerIndex: number) => session.send(playerIndex),
    slap:                 (playerIndex: number) => session.slap(playerIndex),
    grab:                 (playerIndex: number) => session.grab(playerIndex),
    onResponse:           (r: Response) => { session.onResponse(r); },
    notifyAnimationComplete,
    cancelAutoGrab:       () => session.cancelAll(),
  };

});
```

- [ ] **Step 2: Check for TypeScript errors across the full project**

```bash
npx tsc --noEmit
```

Expected: No errors. If there are errors about `Pile` or `Player` usage in Vue components (they access fields like `pile.cards` and `player.nbCards` — these are still public `readonly` fields on the classes, so they remain compatible).

- [ ] **Step 3: Run all tests**

```bash
npx vitest run
```

Expected: All 29 tests pass.

- [ ] **Step 4: Smoke test in the browser**

Start the dev server (or use the running Docker dev container) and:
1. Open the app
2. Click "Deal Cards" — a new game should start and navigate to the room URL
3. Press Send — card animation should play
4. Press Slap — slap flash should appear
5. Wait for the AI to play — it should respond after a delay

- [ ] **Step 5: Commit**

```bash
git add frontend/src/state/BatailleCorse.store.ts
git commit -m "refactor: store becomes thin bridge delegating to GameSession"
```

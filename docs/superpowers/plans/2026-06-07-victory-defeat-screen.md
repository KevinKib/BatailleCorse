# Victory & Defeat Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show a distinct victory or defeat overlay when a Bataille Corse game ends, with a single "Back to home" action.

**Architecture:** Win/seat logic lives in the `BatailleCorse` model (`isOver`, `isWinner`, `isWinnerAt`). Reveal-timing logic lives in a new `useEndScreen` composable (shows the overlay after the final card animation + a short delay during live play, immediately on reload-into-finished-game). `GameScreen.vue` adds the overlay markup, two thin computeds, and wires the composable into its existing GRAB / successful-SLAP watchers and `onMounted`.

**Tech Stack:** Vue 3 (`<script setup>` + TS), Pinia, PrimeVue, Vitest (+ happy-dom, fake timers). No new dependencies. No backend changes.

**Reference spec:** `docs/superpowers/specs/2026-06-07-victory-defeat-screen-design.md`

**Conventions to honour:**
- Domain logic tested via fixtures/builders in `frontend/src/model/fixtures/index.ts` — no Mockito-style mocking of domain classes.
- Test names follow `givenX_thenY` phrasing where natural.
- `git add` every newly created file as soon as it exists.
- Frontend verification gate is `npm run build` (vite build), run from `frontend/`. A bare `vue-tsc` can give a false pass. Worktrees may lack `node_modules` — run `npm install` first if `vitest`/`vite` are missing.

---

## Task 1: Model — `isOver()` / `isWinner()` / `isWinnerAt()`

**Files:**
- Modify: `frontend/src/model/BatailleCorse.ts`
- Test: `frontend/src/model/BatailleCorse.test.ts`

- [ ] **Step 1: Write the failing tests**

Append to `frontend/src/model/BatailleCorse.test.ts` (keep the existing `fromJSON` describe block; add the import for `buildGame` / `buildPlayer` at the top of the file):

```ts
import { buildGame, buildPlayer } from './fixtures';

describe('BatailleCorse end-of-game queries', () => {
  it('givenNoWinner_thenIsOverIsFalse', () => {
    const game = buildGame({ winner: null });
    expect(game.isOver()).toBe(false);
  });

  it('givenWinner_thenIsOverIsTrue', () => {
    const game = buildGame({ winner: { id: '0' } });
    expect(game.isOver()).toBe(true);
  });

  it('givenWinner_thenIsWinnerMatchesOnlyTheWinningId', () => {
    const game = buildGame({ winner: { id: '0' } });
    expect(game.isWinner('0')).toBe(true);
    expect(game.isWinner('1')).toBe(false);
  });

  it('givenNoWinner_thenIsWinnerIsFalseForEveryId', () => {
    const game = buildGame({ winner: null });
    expect(game.isWinner('0')).toBe(false);
    expect(game.isWinner('1')).toBe(false);
  });

  it('givenWinner_thenIsWinnerAtResolvesTheSeatId', () => {
    const game = buildGame({
      players: [buildPlayer({ id: 'a' }), buildPlayer({ id: 'b' })],
      winner: { id: 'b' },
    });
    expect(game.isWinnerAt(0)).toBe(false);
    expect(game.isWinnerAt(1)).toBe(true);
  });

  it('givenNoWinner_thenIsWinnerAtIsFalse', () => {
    const game = buildGame({ winner: null });
    expect(game.isWinnerAt(0)).toBe(false);
    expect(game.isWinnerAt(1)).toBe(false);
  });
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `frontend/`): `npx vitest run src/model/BatailleCorse.test.ts`
Expected: FAIL — `game.isOver is not a function` (methods not defined yet).

- [ ] **Step 3: Add the methods to the model**

In `frontend/src/model/BatailleCorse.ts`, add these three methods to the `BatailleCorse` class body (after the constructor, before `fromJSON`):

```ts
  isOver(): boolean {
    return this.winner !== null;
  }

  isWinner(playerId: string): boolean {
    return this.winner?.id === playerId;
  }

  isWinnerAt(playerIndex: number): boolean {
    return this.isWinner(this.players[playerIndex]?.id);
  }
```

Note: `isWinner` takes `string`, but `this.players[playerIndex]?.id` can be `undefined` for an out-of-range index. That is intentional — an undefined id never equals a real winner id, so `isWinnerAt` safely returns `false`. If TypeScript's `strict` settings reject `undefined` here, widen the parameter to `isWinner(playerId: string | undefined)`; the body is unchanged.

- [ ] **Step 4: Run the tests to verify they pass**

Run (from `frontend/`): `npx vitest run src/model/BatailleCorse.test.ts`
Expected: PASS — all six new tests plus the existing `fromJSON` test green.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/model/BatailleCorse.ts frontend/src/model/BatailleCorse.test.ts
git commit -m "feat: add isOver/isWinner/isWinnerAt to BatailleCorse model"
```

---

## Task 2: `useEndScreen` composable

**Files:**
- Create: `frontend/src/composables/useEndScreen.ts`
- Test: `frontend/src/composables/useEndScreen.test.ts`

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/composables/useEndScreen.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { useEndScreen, END_SCREEN_DELAY_MS } from './useEndScreen';

describe('useEndScreen', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('givenGameNotOver_whenRevealAfterAnimation_thenOverlayStaysHidden', () => {
    const { showEndOverlay, revealAfterAnimation } = useEndScreen(() => false);
    revealAfterAnimation();
    vi.advanceTimersByTime(END_SCREEN_DELAY_MS + 100);
    expect(showEndOverlay.value).toBe(false);
  });

  it('givenGameOver_whenRevealAfterAnimation_thenOverlayShowsOnlyAfterDelay', () => {
    const { showEndOverlay, revealAfterAnimation } = useEndScreen(() => true);
    revealAfterAnimation();
    expect(showEndOverlay.value).toBe(false);          // not yet — delay pending
    vi.advanceTimersByTime(END_SCREEN_DELAY_MS - 1);
    expect(showEndOverlay.value).toBe(false);
    vi.advanceTimersByTime(1);
    expect(showEndOverlay.value).toBe(true);           // delay elapsed
  });

  it('givenGameOver_whenRevealImmediatelyIfOver_thenOverlayShowsSynchronously', () => {
    const { showEndOverlay, revealImmediatelyIfOver } = useEndScreen(() => true);
    revealImmediatelyIfOver();
    expect(showEndOverlay.value).toBe(true);
  });

  it('givenGameNotOver_whenRevealImmediatelyIfOver_thenOverlayStaysHidden', () => {
    const { showEndOverlay, revealImmediatelyIfOver } = useEndScreen(() => false);
    revealImmediatelyIfOver();
    expect(showEndOverlay.value).toBe(false);
  });
});
```

`git add` the new test file now.

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `frontend/`): `npx vitest run src/composables/useEndScreen.test.ts`
Expected: FAIL — cannot resolve module `./useEndScreen`.

- [ ] **Step 3: Implement the composable**

Create `frontend/src/composables/useEndScreen.ts`:

```ts
import { ref } from 'vue';

/** Short beat after the final card lands before the end overlay fades in. */
export const END_SCREEN_DELAY_MS = 600;

/**
 * Owns the visibility/timing of the end-of-game overlay, kept out of the
 * component so it is unit-testable. `isOver` is a getter into reactive game
 * state (read at call time, not captured).
 */
export function useEndScreen(isOver: () => boolean, delayMs = END_SCREEN_DELAY_MS) {
  const showEndOverlay = ref(false);

  // Live win: the caller invokes this once the final card animation resolves.
  function revealAfterAnimation() {
    if (!isOver()) return;
    setTimeout(() => { showEndOverlay.value = true; }, delayMs);
  }

  // Reload into a finished game: no animation in flight, reveal at once.
  function revealImmediatelyIfOver() {
    if (isOver()) showEndOverlay.value = true;
  }

  return { showEndOverlay, revealAfterAnimation, revealImmediatelyIfOver };
}
```

`git add` the new source file now.

- [ ] **Step 4: Run the tests to verify they pass**

Run (from `frontend/`): `npx vitest run src/composables/useEndScreen.test.ts`
Expected: PASS — all four tests green.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/composables/useEndScreen.ts frontend/src/composables/useEndScreen.test.ts
git commit -m "feat: add useEndScreen composable for end-overlay timing"
```

---

## Task 3: Wire computeds + composable into `GameScreen.vue`

**Files:**
- Modify: `frontend/src/view/alpha/GameScreen.vue`

No test in this task (no component-test harness exists; logic is covered by Tasks 1–2). Verification is the build gate in Task 5.

- [ ] **Step 1: Import the composable**

In the `<script setup>` import block of `GameScreen.vue`, add alongside the other composable imports (near the `useCardAnimation` / `useHotkeys` imports):

```ts
import { useEndScreen } from '../../composables/useEndScreen';
```

- [ ] **Step 2: Add the computeds and instantiate the composable**

After the existing `opponentLabel` / `shareLink` computeds block (around the other `computed(...)` declarations), add:

```ts
const isGameOver = computed(() => batailleCorse.value?.isOver() ?? false);
const didIWin = computed(() => batailleCorse.value?.isWinnerAt(myPlayerIndex.value) ?? false);

const { showEndOverlay, revealAfterAnimation, revealImmediatelyIfOver } =
  useEndScreen(() => isGameOver.value);
```

- [ ] **Step 3: Call `revealAfterAnimation()` after the terminal animations**

In the `lastGrab` watcher, the success path ends with:

```ts
  await animation.animatePileToWinner(srcRect, destEl.getBoundingClientRect(), pileCards);
  batailleCorseStore.notifyAnimationComplete();
```

Add the reveal call immediately after `notifyAnimationComplete()`:

```ts
  await animation.animatePileToWinner(srcRect, destEl.getBoundingClientRect(), pileCards);
  batailleCorseStore.notifyAnimationComplete();
  revealAfterAnimation();
```

In the `lastSuccessfulSlap` watcher, do the same — its success path also ends with `animatePileToWinner(...)` then `notifyAnimationComplete()`. Add `revealAfterAnimation();` right after that `notifyAnimationComplete()`:

```ts
  await animation.animatePileToWinner(srcRect, destEl.getBoundingClientRect(), pileCards);
  batailleCorseStore.notifyAnimationComplete();
  revealAfterAnimation();
```

Do NOT add it to the early-return branches (those mean the animation could not run / the move was a no-op for animation; the next state update path still applies). Leave `lastSend` and `lastErroneousSlap` untouched — a SEND or an erroneous slap never ends the game.

- [ ] **Step 4: Reveal immediately on reload-into-finished-game**

In `onMounted`, immediately after this existing line:

```ts
  batailleCorseStore.hydrate(gameId, gameState);
```

add:

```ts
  revealImmediatelyIfOver();
```

(`hydrate` sets `state.value` synchronously, so `isGameOver` is correct on the next line.)

- [ ] **Step 5: Commit**

```bash
git add frontend/src/view/alpha/GameScreen.vue
git commit -m "feat: wire end-of-game detection and overlay timing into GameScreen"
```

---

## Task 4: Overlay markup + styling

**Files:**
- Modify: `frontend/src/view/alpha/GameScreen.vue`

- [ ] **Step 1: Add the overlay markup**

In the template, directly after the existing waiting-overlay block (the `<div v-if="isWaiting" class="waiting-overlay">...</div>`, before its closing `</div>` of `.gamescreen`), add the end overlay so it sits inside `.gamescreen`:

```html
    <div v-if="showEndOverlay" class="end-overlay" data-cy="end-overlay">
      <div :class="['end-card', didIWin ? 'end-card--victory' : 'end-card--defeat']">
        <div v-if="didIWin" class="end-trophy" data-cy="victory-flourish">🏆</div>
        <h1 class="end-title">{{ didIWin ? 'VICTORY' : 'DEFEAT' }}</h1>
        <p class="end-sub">
          {{ didIWin ? `You beat ${opponentLabel}!` : `${opponentLabel} won.` }}
        </p>
        <RouterLink to="/" class="end-home-button">
          <Button label="Back to home" icon="pi pi-home" rounded />
        </RouterLink>
      </div>
    </div>
```

Note: `RouterLink` and `Button` are already imported/used in this file (the existing Back button and waiting-overlay use them), so no new imports are needed.

- [ ] **Step 2: Add the styles**

In the `<style scoped>` block, after the `.waiting-copied` rule (end of the waiting-overlay styles), add:

```css
.end-overlay {
  position: absolute;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.82);
  backdrop-filter: blur(4px);
}

.end-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  background: rgba(0, 0, 0, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 16px;
  padding: 40px 48px;
  max-width: 460px;
  text-align: center;
}

.end-title {
  font-family: "Gabarito", sans-serif;
  font-size: 2.4rem;
  font-weight: 800;
  letter-spacing: 0.06em;
  margin: 0;
}

.end-sub {
  font-size: 0.95rem;
  color: rgba(255, 255, 255, 0.75);
  margin: 0;
}

.end-home-button {
  margin-top: 10px;
}

/* Victory: gold accent + a brief trophy bounce / glow pulse. */
.end-card--victory {
  border-color: rgba(245, 200, 66, 0.55);
  box-shadow: 0 0 48px 6px rgba(245, 200, 66, 0.25);
}

.end-card--victory .end-title {
  color: #f5c842;
  text-shadow: 0 2px 16px rgba(245, 200, 66, 0.45);
}

.end-trophy {
  font-size: 3.2rem;
  line-height: 1;
  animation: trophy-bounce 1.6s ease-in-out infinite;
}

@keyframes trophy-bounce {
  0%, 100% { transform: translateY(0) scale(1); }
  30%      { transform: translateY(-10px) scale(1.06); }
  60%      { transform: translateY(0) scale(1); }
}

/* Defeat: muted / somber, no flourish. */
.end-card--defeat {
  border-color: rgba(248, 113, 113, 0.35);
}

.end-card--defeat .end-title {
  color: #cbd5d1;
}
```

- [ ] **Step 2b: Respect reduced-motion (accessibility)**

Append this rule to the same `<style scoped>` block so the bounce is disabled for users who prefer reduced motion:

```css
@media (prefers-reduced-motion: reduce) {
  .end-trophy { animation: none; }
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/view/alpha/GameScreen.vue
git commit -m "feat: add victory/defeat end overlay markup and styling"
```

---

## Task 5: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Ensure dependencies are present**

If this is a fresh worktree, `node_modules` may be missing. From `frontend/`:

Run: `npm install`
Expected: completes without errors (skip if `node_modules` already present).

- [ ] **Step 2: Run the full unit-test suite**

Run (from `frontend/`): `npx vitest run`
Expected: PASS — all model and composable tests green, including the new
`BatailleCorse` end-of-game tests and the `useEndScreen` tests.

- [ ] **Step 3: Run the real build gate**

Run (from `frontend/`): `npm run build`
Expected: vite build succeeds with no TypeScript errors. (This is the authoritative
type/compile gate — a bare `vue-tsc` can give a false pass.)

- [ ] **Step 4: Manual smoke check (optional but recommended)**

Start the app, play a solo game to completion (fastest difficulty), and confirm:
- The final card animation lands, then ~0.6s later the overlay fades in.
- Winning shows gold **VICTORY** + bouncing trophy; losing shows muted **DEFEAT**, no trophy.
- "Back to home" returns to `/`.
- Reloading the finished game's URL shows the overlay immediately.

- [ ] **Step 5: Final commit (if any verification fixups were needed)**

```bash
git add -A
git commit -m "chore: verification fixups for victory/defeat screen"
```

(Skip if Steps 2–3 passed with no changes.)

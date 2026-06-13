# Game Duration Timer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a cosmetic, frontend-only game-duration timer shown as a top-right HUD chip on the game screen, counting up while the game is played and freezing at game over.

**Architecture:** All timing lives in a unit-tested composable (`useGameDuration`) that takes `isActive`/`isOver` getters, ticks a `now` ref each second, and freezes elapsed time when the game ends. A dumb presentational component (`GameTimer.vue`) renders the formatted string. `GameScreen.vue` wires the composable to its existing `isWaiting`/`isGameOver` computeds and places the chip. No backend changes.

**Tech Stack:** Vue 3 (`<script setup lang="ts">`), Pinia (already wired in the view), Vitest + fake timers, PrimeIcons.

---

## File Structure

- Create: `frontend/src/composables/useGameDuration.ts` — timing logic + `formatDuration` helper.
- Create: `frontend/src/composables/useGameDuration.test.ts` — Vitest unit tests (fake timers).
- Create: `frontend/src/components/GameTimer.vue` — presentational HUD chip (prop `time: string`).
- Modify: `frontend/src/view/alpha/GameScreen.vue` — instantiate composable, render `<GameTimer>`, clean up on unmount.

---

## Task 1: `formatDuration` + `useGameDuration` composable

**Files:**
- Create: `frontend/src/composables/useGameDuration.ts`
- Test: `frontend/src/composables/useGameDuration.test.ts`

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/composables/useGameDuration.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { ref, nextTick } from 'vue';
import { useGameDuration, formatDuration } from './useGameDuration';

describe('formatDuration', () => {
  it('givenUnderOneMinute_thenMmSs', () => {
    expect(formatDuration(5_000)).toBe('00:05');
  });

  it('givenMinutesAndSeconds_thenMmSs', () => {
    expect(formatDuration(65_000)).toBe('01:05');
  });

  it('givenOverOneHour_thenHMmSs', () => {
    expect(formatDuration(3_725_000)).toBe('1:02:05'); // 1h 2m 5s
  });

  it('givenZero_thenZeroZero', () => {
    expect(formatDuration(0)).toBe('00:00');
  });
});

describe('useGameDuration', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('givenInactive_thenStaysAtZero', async () => {
    const active = ref(false);
    const over = ref(false);
    const { formattedDuration } = useGameDuration(() => active.value, () => over.value);

    await nextTick();
    vi.advanceTimersByTime(10_000);

    expect(formattedDuration.value).toBe('00:00');
  });

  it('givenActive_thenCountsUp', async () => {
    const active = ref(true);
    const over = ref(false);
    const { formattedDuration } = useGameDuration(() => active.value, () => over.value);

    await nextTick();
    vi.advanceTimersByTime(65_000);

    expect(formattedDuration.value).toBe('01:05');
  });

  it('givenGameOver_thenFreezesAtFinalDuration', async () => {
    const active = ref(true);
    const over = ref(false);
    const { formattedDuration } = useGameDuration(() => active.value, () => over.value);

    await nextTick();
    vi.advanceTimersByTime(30_000);

    over.value = true;
    active.value = false; // active = !over in the real view
    await nextTick();

    vi.advanceTimersByTime(60_000); // time keeps passing...
    expect(formattedDuration.value).toBe('00:30'); // ...but the value is frozen
  });

  it('givenGameOver_thenStopsTheInterval', async () => {
    const active = ref(true);
    const over = ref(false);
    useGameDuration(() => active.value, () => over.value);

    await nextTick();
    expect(vi.getTimerCount()).toBe(1);

    over.value = true;
    active.value = false;
    await nextTick();

    expect(vi.getTimerCount()).toBe(0);
  });

  it('givenCancel_thenStopsIntervalAndWatch', async () => {
    const active = ref(true);
    const over = ref(false);
    const { cancel } = useGameDuration(() => active.value, () => over.value);

    await nextTick();
    cancel();

    expect(vi.getTimerCount()).toBe(0);
  });
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd frontend && npm run test -- useGameDuration`
Expected: FAIL — `Failed to resolve import './useGameDuration'` / module not found.

- [ ] **Step 3: Write the composable**

Create `frontend/src/composables/useGameDuration.ts`:

```ts
import { computed, ref, watch } from 'vue';

/** Format an elapsed millisecond count as mm:ss, rolling to h:mm:ss past an hour. */
export function formatDuration(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const seconds = totalSeconds % 60;
  const minutes = Math.floor(totalSeconds / 60) % 60;
  const hours = Math.floor(totalSeconds / 3600);
  const pad = (n: number) => String(n).padStart(2, '0');
  return hours > 0
    ? `${hours}:${pad(minutes)}:${pad(seconds)}`
    : `${pad(minutes)}:${pad(seconds)}`;
}

const TICK_MS = 1000;

/**
 * Cosmetic, frontend-only game-duration timer. Starts counting the first time the
 * game becomes active (state loaded, not waiting, not over), ticks once a second,
 * and freezes the elapsed value at game over. It is NOT authoritative — it resets
 * on refresh and is not shared between players.
 *
 * `isActive` / `isOver` are getters into reactive state (read at call time), the
 * same pattern as `useEndScreen`. The caller wires `cancel()` into onBeforeUnmount.
 */
export function useGameDuration(
  isActive: () => boolean,
  isOver: () => boolean,
) {
  const startEpochMs = ref<number | null>(null);
  const frozenMs = ref<number | null>(null);
  const now = ref(Date.now());
  let intervalId: ReturnType<typeof setInterval> | null = null;

  function startTicking() {
    if (intervalId !== null) return;
    now.value = Date.now();
    intervalId = setInterval(() => { now.value = Date.now(); }, TICK_MS);
  }

  function stopTicking() {
    if (intervalId !== null) {
      clearInterval(intervalId);
      intervalId = null;
    }
  }

  const stopWatch = watch(
    [() => isActive(), () => isOver()],
    ([active, over]) => {
      if (over) {
        if (frozenMs.value === null && startEpochMs.value !== null) {
          frozenMs.value = Date.now() - startEpochMs.value;
        }
        stopTicking();
        return;
      }
      if (active) {
        if (startEpochMs.value === null) startEpochMs.value = Date.now();
        startTicking();
      } else {
        stopTicking();
      }
    },
    { immediate: true },
  );

  const elapsedMs = computed(() => {
    if (frozenMs.value !== null) return frozenMs.value;
    if (startEpochMs.value === null) return 0;
    return Math.max(0, now.value - startEpochMs.value);
  });

  const formattedDuration = computed(() => formatDuration(elapsedMs.value));

  /** Stop the interval and the watcher; wire into the view's onBeforeUnmount. */
  function cancel() {
    stopTicking();
    stopWatch();
  }

  return { formattedDuration, cancel };
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd frontend && npm run test -- useGameDuration`
Expected: PASS — all cases in both `describe` blocks green.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/composables/useGameDuration.ts frontend/src/composables/useGameDuration.test.ts
git commit -m "feat(timer): game-duration composable (frontend-only, freezes at game over)"
```

---

## Task 2: `GameTimer.vue` presentational chip

**Files:**
- Create: `frontend/src/components/GameTimer.vue`

- [ ] **Step 1: Write the component**

Create `frontend/src/components/GameTimer.vue`:

```vue
<template>
  <div class="game-timer" data-cy="game-timer">
    <i class="pi pi-clock game-timer__icon" aria-hidden="true"></i>
    <span class="game-timer__time">{{ time }}</span>
  </div>
</template>

<script setup lang="ts">
defineProps<{ time: string }>();
</script>

<style scoped>
/* Top-right HUD chip, styled to match the .player_tag felt pills. Pinned inside
   .gamescreen (position: relative). z-index sits above the board but below the
   waiting/disconnect/end overlays (1900-2000) so those cover it. */
.game-timer {
  position: absolute;
  top: calc(10px + env(safe-area-inset-top, 0px));
  right: calc(10px + env(safe-area-inset-right, 0px));
  z-index: 1500;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.8rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.06em;
  color: rgba(255, 255, 255, 0.8);
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  padding: 4px 12px;
  pointer-events: none;
}

.game-timer__icon {
  font-size: 0.78rem;
  opacity: 0.7;
}
</style>
```

- [ ] **Step 2: Build to verify the component compiles**

Run: `cd frontend && npm run build`
Expected: build succeeds (no Vue/TS errors). The component is not yet referenced; this just confirms it compiles.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/GameTimer.vue
git commit -m "feat(timer): GameTimer HUD chip component"
```

---

## Task 3: Wire the timer into `GameScreen.vue`

**Files:**
- Modify: `frontend/src/view/alpha/GameScreen.vue`

- [ ] **Step 1: Add the imports**

In the `<script setup>` import block of `frontend/src/view/alpha/GameScreen.vue`, alongside the other component imports near the top, add:

```ts
import GameTimer from '../../components/GameTimer.vue';
```

And alongside the other composable imports (next to `import { useEndScreen } ...`), add:

```ts
import { useGameDuration } from '../../composables/useGameDuration';
```

- [ ] **Step 2: Instantiate the composable**

In `frontend/src/view/alpha/GameScreen.vue`, immediately after the `useEndScreen(...)` block (the `const { showEndOverlay, revealImmediatelyIfOver, cancel: cancelEndScreen } = useEndScreen(...)` statement), add:

```ts
// Cosmetic game-duration timer. Active while a game is loaded and in play; the
// composable freezes the value at game over. `isGameOver` already exists above.
const isTimerActive = computed(() =>
  !!batailleCorse.value && !isWaiting.value && !isGameOver.value);
const { formattedDuration, cancel: cancelGameDuration } =
  useGameDuration(() => isTimerActive.value, () => isGameOver.value);
```

(`computed`, `isWaiting`, `isGameOver`, and `batailleCorse` are all already in scope in this file.)

- [ ] **Step 3: Render the chip**

In the template, make `<GameTimer>` the first child inside the root `.gamescreen` div, immediately before `<RulesPanel />`:

```html
  <div class="gamescreen flex">

    <GameTimer :time="formattedDuration" />

    <RulesPanel />
```

- [ ] **Step 4: Clean up on unmount**

In the existing `onBeforeUnmount(() => { ... })` block, add a call next to `cancelEndScreen();`:

```ts
  cancelEndScreen();
  cancelGameDuration();
```

- [ ] **Step 5: Build and run the full test suite**

Run: `cd frontend && npm run build`
Expected: build succeeds with no type errors.

Run: `cd frontend && npm run test`
Expected: PASS — existing suites plus the new `useGameDuration` tests are green.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/view/alpha/GameScreen.vue
git commit -m "feat(timer): show game-duration HUD on the game screen"
```

---

## Self-Review Notes

- **Spec coverage:** frontend-only source (Task 1, no backend touched) ✓; active definition `state && !waiting && !over` (Task 3 Step 2) ✓; freeze at game over (Task 1 composable + test) ✓; top-right HUD chip (Task 2 + Task 3 Step 3) ✓; mm:ss / h:mm:ss format (Task 1 `formatDuration` + tests) ✓; composable + dumb component + thin view split (matches Vue conventions) ✓.
- **Naming consistency:** `useGameDuration`, `formatDuration`, `formattedDuration`, `cancel`/`cancelGameDuration`, `isTimerActive` used identically across tasks.
- **Reset-on-refresh / reload-into-finished-game:** acceptable per spec — on reload `startEpochMs` is null and `isOver` may already be true, so `formattedDuration` reads `00:00`; the end overlay covers the chip anyway.
- **Verification note:** worktrees may lack `node_modules`; run `npm install` in `frontend/` first if `npm run test`/`build` fails on missing deps.

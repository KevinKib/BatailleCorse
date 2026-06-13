# GameScreen.vue De-bloat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decompose `frontend/src/view/alpha/GameScreen.vue` into focused composables and presentational components with zero change in observable behavior.

**Architecture:** Extract 3 overlay components (template + scoped CSS) and 5 composables (logic + lifecycle) out of the route-level view. The view stays a thin composition surface: layout markup, inline animation layers, a few view-model computeds, and wiring. Each slice is applied incrementally so the build stays green after every task. Riskiest extraction (`useGameAnimations`) is last.

**Tech Stack:** Vue 3.5 + `<script setup lang="ts">` + Pinia, Vite (build), Vitest + @vue/test-utils + happy-dom (unit), PrimeVue, vue-router.

**Conventions (from the project `vue-best-practices` skill + spec):**
- SFC section order: `<template>` → `<script>` → `<style>` (project convention).
- Composables taking >1 input use a single typed **options object**; single-input composables stay positional (matching `useEndScreen`/`useGameDuration`).
- **Cleanup convention (matches the codebase):** composables that only set up reactive effects/timers (`useDisconnectCountdown`, `useTurnIndicator`) **return a `cancel()`** that the view wires into its existing `onBeforeUnmount` — exactly like `useEndScreen`/`useGameDuration`/`useCardAnimation`. This keeps them unit-testable without a component instance. Composables that must register `onMounted`/`onBeforeRouteLeave` in setup anyway (`useLeaveGuard`, `useGameBootstrap`) pair those with an internal `onBeforeUnmount`.
- State a consumer only reads is returned `readonly` (computeds are already read-only — return them directly; wrap raw `ref`s in `readonly()`).
- Components use named `interface` `defineProps<Props>()` / `defineEmits<Emits>()`.
- Preserve every `data-cy` attribute exactly (Cypress depends on them).

---

## File Structure

**Create:**
- `frontend/src/components/WaitingOverlay.vue` — "waiting for opponent" overlay + share link (self-contained).
- `frontend/src/components/DisconnectOverlay.vue` — opponent-disconnected overlay (presentational; props in).
- `frontend/src/components/EndGameOverlay.vue` — victory/defeat overlay (presentational; props in, `playAgain` out).
- `frontend/src/composables/useDisconnectCountdown.ts` — opponent disconnect deadline → remaining seconds.
- `frontend/src/composables/useDisconnectCountdown.test.ts`
- `frontend/src/composables/useTurnIndicator.ts` — per-seat turn glow + one-time first-turn hint.
- `frontend/src/composables/useTurnIndicator.test.ts`
- `frontend/src/composables/useLeaveGuard.ts` — route-leave confirm + `beforeunload` + forfeit.
- `frontend/src/composables/useGameBootstrap.ts` — onMounted hydrate→restore→subscribe + session cleanup.
- `frontend/src/composables/useGameAnimations.ts` — wraps `useCardAnimation` + all action-event watchers + slap-impact flash.

**Modify (every task):** `frontend/src/view/alpha/GameScreen.vue` — remove the extracted block, wire the new unit.

---

## Task 0: Setup & baseline

**Files:** none (environment only).

- [ ] **Step 1: Install dependencies (worktree lacks `node_modules`)**

Run: `cd frontend && npm ci`
Expected: completes without error; `node_modules/` present. (Does not modify `package-lock.json`.)

- [ ] **Step 2: Baseline build (the real gate per project convention)**

Run: `cd frontend && npm run build`
Expected: `vite build` succeeds, no type errors.

- [ ] **Step 3: Baseline tests**

Run: `cd frontend && npm run test`
Expected: all existing suites pass (records the green baseline before refactor).

No commit (no file changes).

---

## Task 1: Extract `WaitingOverlay.vue`

**Files:**
- Create: `frontend/src/components/WaitingOverlay.vue`
- Modify: `frontend/src/view/alpha/GameScreen.vue`

- [ ] **Step 1: Create the component**

`frontend/src/components/WaitingOverlay.vue`:

```vue
<template>
  <div class="waiting-overlay">
    <div class="waiting-card">
      <h2 class="waiting-title">Waiting for opponent…</h2>
      <p class="waiting-sub">Share this link to invite a player</p>
      <div class="share-row">
        <InputText :value="shareLink" readonly class="share-input" />
        <Button label="Copy" icon="pi pi-copy" rounded @click="copyShareLink" />
      </div>
      <p v-if="copied" class="waiting-copied">Copied!</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Button, InputText } from 'primevue';

const route = useRoute();
const router = useRouter();

const shareLink = computed(() => {
  const { href } = router.resolve({ name: 'join', params: { id: route.params.id } });
  return `${window.location.origin}${href}`;
});

const copied = ref(false);
async function copyShareLink() {
  await navigator.clipboard.writeText(shareLink.value);
  copied.value = true;
  setTimeout(() => { copied.value = false; }, 1500);
}
</script>

<style scoped>
.waiting-overlay {
  position: absolute;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.78);
  backdrop-filter: blur(3px);
}

.waiting-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  background: var(--panel-bg);
  border: 1px solid var(--panel-border);
  box-shadow: var(--panel-shadow);
  border-radius: 16px;
  padding: 36px 40px;
  max-width: 460px;
}

.waiting-title {
  font-family: "Gabarito", sans-serif;
  font-size: 1.6rem;
  font-weight: 700;
  color: var(--gold);
  margin: 0;
}

.waiting-sub {
  font-size: 0.72rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.5);
  margin: 0;
}

.share-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.share-input {
  flex: 1;
}

.waiting-copied {
  font-size: 0.72rem;
  color: #4ade80;
  margin: 0;
}
</style>
```

- [ ] **Step 2: Wire into GameScreen, remove the inline block**

In `GameScreen.vue`:
- Replace the inline waiting overlay (the `<div v-if="isWaiting" class="waiting-overlay">…</div>` block) with:

```html
<WaitingOverlay v-if="isWaiting" />
```

- Add import: `import WaitingOverlay from '../../components/WaitingOverlay.vue';`
- Delete from `<script>`: the `copied` ref and `copyShareLink` function, and the `shareLink` computed.
- Delete the `.waiting-*` / `.share-*` CSS rules (the 7 rules now living in the component).
- Remove `InputText` from the `primevue` import if it is no longer used elsewhere in the file (it is only used by the waiting overlay — change `import { Button, InputText } from 'primevue';` to `import { Button } from 'primevue';`).

- [ ] **Step 3: Build**

Run: `cd frontend && npm run build`
Expected: succeeds, no unused-import or type errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/WaitingOverlay.vue frontend/src/view/alpha/GameScreen.vue
git commit -m "refactor(game-screen): extract WaitingOverlay component"
```

---

## Task 2: Extract `DisconnectOverlay.vue`

**Files:**
- Create: `frontend/src/components/DisconnectOverlay.vue`
- Modify: `frontend/src/view/alpha/GameScreen.vue`

- [ ] **Step 1: Create the component**

`frontend/src/components/DisconnectOverlay.vue`:

```vue
<template>
  <div class="disconnect-overlay" data-cy="disconnect-overlay">
    <div class="disconnect-card">
      <h2 class="disconnect-title">{{ opponentLabel }} disconnected</h2>
      <p class="disconnect-sub">Waiting for them to return…</p>
      <p class="disconnect-countdown" data-cy="disconnect-countdown">
        You win in {{ secondsRemaining }}s
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  opponentLabel: string;
  secondsRemaining: number;
}

defineProps<Props>();
</script>

<style scoped>
.disconnect-overlay {
  position: absolute;
  inset: 0;
  z-index: 1900;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  pointer-events: none;
  padding-top: 12vh;
}

.disconnect-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  background: var(--panel-bg);
  border: 1px solid rgba(var(--accent-negative-rgb), 0.45);
  box-shadow: var(--panel-shadow);
  border-radius: 14px;
  padding: 18px 28px;
  text-align: center;
}

.disconnect-title {
  font-family: "Gabarito", sans-serif;
  font-size: 1.2rem;
  font-weight: 700;
  color: #f87171;
  margin: 0;
}

.disconnect-sub {
  font-size: 0.78rem;
  color: rgba(255, 255, 255, 0.6);
  margin: 0;
}

.disconnect-countdown {
  font-size: 1rem;
  font-weight: 800;
  color: var(--gold);
  margin: 4px 0 0;
}
</style>
```

- [ ] **Step 2: Wire into GameScreen, remove the inline block**

In `GameScreen.vue`:
- Replace the inline `<div v-if="opponentDisconnected" class="disconnect-overlay">…</div>` block with:

```html
<DisconnectOverlay
  v-if="opponentDisconnected"
  :opponent-label="opponentLabel"
  :seconds-remaining="secondsRemaining"
/>
```

- Add import: `import DisconnectOverlay from '../../components/DisconnectOverlay.vue';`
- Delete the `.disconnect-*` CSS rules (4 rules) from `<style>`.
- Leave `opponentDisconnected`, `opponentLabel`, `secondsRemaining` in the script (still computed inline; extracted in Task 4).

- [ ] **Step 3: Build**

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/DisconnectOverlay.vue frontend/src/view/alpha/GameScreen.vue
git commit -m "refactor(game-screen): extract DisconnectOverlay component"
```

---

## Task 3: Extract `EndGameOverlay.vue`

**Files:**
- Create: `frontend/src/components/EndGameOverlay.vue`
- Modify: `frontend/src/view/alpha/GameScreen.vue`

- [ ] **Step 1: Create the component**

`frontend/src/components/EndGameOverlay.vue`:

```vue
<template>
  <div class="end-overlay" data-cy="end-overlay">
    <div :class="['end-card', didIWin ? 'end-card--victory' : 'end-card--defeat']">
      <div v-if="didIWin" class="end-trophy" data-cy="victory-flourish">🏆</div>
      <h1 class="end-title">{{ didIWin ? 'VICTORY' : 'DEFEAT' }}</h1>
      <p class="end-sub">{{ subtitle }}</p>
      <div class="end-actions">
        <Button
          class="end-replay-button"
          :label="rematchButton.label"
          :disabled="rematchButton.disabled"
          icon="pi pi-replay"
          severity="success"
          rounded
          data-cy="play-again"
          @click="emit('playAgain')"
        />
        <RouterLink :to="{ name: 'home' }" class="end-home-button">
          <Button label="Back to home" icon="pi pi-home" rounded />
        </RouterLink>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Button } from 'primevue';
import { RouterLink } from 'vue-router';
import type { RematchButton } from '../model/RematchButton';

interface Props {
  didIWin: boolean;
  subtitle: string;
  rematchButton: RematchButton;
}

interface Emits {
  playAgain: [];
}

defineProps<Props>();
const emit = defineEmits<Emits>();
</script>

<style scoped>
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
  background: var(--panel-bg);
  border: 1px solid var(--panel-border);
  box-shadow: var(--panel-shadow);
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

.end-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}

/* Victory: gold accent + a brief trophy bounce / glow pulse. */
.end-card--victory {
  border-color: rgba(var(--accent-active-rgb), 0.55);
  box-shadow: var(--panel-shadow), 0 0 48px 6px rgba(var(--accent-active-rgb), 0.25);
}

.end-card--victory .end-title {
  color: var(--gold);
  text-shadow: 0 2px 16px rgba(var(--accent-active-rgb), 0.45);
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
  border-color: rgba(var(--accent-negative-rgb), 0.35);
}

.end-card--defeat .end-title {
  color: #cbd5d1;
}

@media (prefers-reduced-motion: reduce) {
  .end-trophy { animation: none; }
}
</style>
```

- [ ] **Step 2: Create the shared `RematchButton` type**

`frontend/src/model/RematchButton.ts`:

```ts
export interface RematchButton {
  label: string;
  disabled: boolean;
}
```

- [ ] **Step 3: Wire into GameScreen, remove the inline block**

In `GameScreen.vue`:
- Replace the inline `<div v-if="showEndOverlay" class="end-overlay">…</div>` block with:

```html
<EndGameOverlay
  v-if="showEndOverlay"
  :did-i-win="didIWin"
  :subtitle="endSubtitle"
  :rematch-button="rematchButton"
  @play-again="onPlayAgain"
/>
```

- Add imports:
  - `import EndGameOverlay from '../../components/EndGameOverlay.vue';`
  - `import type { RematchButton } from '../../model/RematchButton';`
- Type the existing computed so it matches the prop: change `const rematchButton = computed(() => {` to `const rematchButton = computed<RematchButton>(() => {`.
- Delete the `.end-*`, `.end-card*`, `.end-trophy`, `@keyframes trophy-bounce` CSS rules from `<style>`.
- In the `@media (prefers-reduced-motion: reduce)` block in GameScreen, **remove only `.end-trophy`** from the selector list (it now lives in the component); keep the other selectors (`.player_tag--active`, `.turn-hint__dot`, `.action_button--my-turn`, `.card.card-punch`, `.card.card-shake`).

- [ ] **Step 4: Build**

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/EndGameOverlay.vue frontend/src/model/RematchButton.ts frontend/src/view/alpha/GameScreen.vue
git commit -m "refactor(game-screen): extract EndGameOverlay component"
```

---

## Task 4: Extract `useDisconnectCountdown`

**Files:**
- Create: `frontend/src/composables/useDisconnectCountdown.ts`
- Test: `frontend/src/composables/useDisconnectCountdown.test.ts`
- Modify: `frontend/src/view/alpha/GameScreen.vue`

- [ ] **Step 1: Write the failing test**

`frontend/src/composables/useDisconnectCountdown.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { ref, nextTick } from 'vue';
import { useDisconnectCountdown } from './useDisconnectCountdown';

type Conn =
  | { status: 'disconnected'; seat: number; deadlineEpochMs: number }
  | { status: 'connected'; seat: number }
  | null;

function setup(initial: Conn) {
  const conn = ref<Conn>(initial);
  const mode = ref<'solo' | 'multiplayer'>('multiplayer');
  const over = ref(false);
  const api = useDisconnectCountdown({
    mode: () => mode.value,
    opponentConnection: () => conn.value,
    myPlayerIndex: () => 0,
    isGameOver: () => over.value,
  });
  return { conn, mode, over, ...api };
}

describe('useDisconnectCountdown', () => {
  beforeEach(() => vi.useFakeTimers({ now: 1_000_000 }));
  afterEach(() => vi.useRealTimers());

  it('givenOpponentConnected_thenNotDisconnectedAndZeroSeconds', () => {
    const { opponentDisconnected, secondsRemaining, cancel } = setup({ status: 'connected', seat: 1 });
    expect(opponentDisconnected.value).toBe(false);
    expect(secondsRemaining.value).toBe(0);
    cancel();
  });

  it('givenOpponentDisconnected_thenComputesRemainingSecondsFromDeadline', async () => {
    const deadline = Date.now() + 10_000;
    const { opponentDisconnected, secondsRemaining, cancel } = setup({ status: 'disconnected', seat: 1, deadlineEpochMs: deadline });
    await nextTick();
    expect(opponentDisconnected.value).toBe(true);
    expect(secondsRemaining.value).toBe(10);

    vi.advanceTimersByTime(4_000); // timer ticks update `now`
    expect(secondsRemaining.value).toBe(6);
    cancel();
  });

  it('givenGameOver_thenNotConsideredDisconnected', () => {
    const deadline = Date.now() + 10_000;
    const { opponentDisconnected, over, cancel } = setup({ status: 'disconnected', seat: 1, deadlineEpochMs: deadline });
    over.value = true;
    expect(opponentDisconnected.value).toBe(false);
    cancel();
  });

  it('givenDisconnectedSeatIsMine_thenNotConsideredDisconnected', () => {
    const deadline = Date.now() + 10_000;
    const { opponentDisconnected, cancel } = setup({ status: 'disconnected', seat: 0, deadlineEpochMs: deadline });
    expect(opponentDisconnected.value).toBe(false);
    cancel();
  });

  it('givenReconnect_thenTimerStopsAndSecondsResetToZero', async () => {
    const deadline = Date.now() + 10_000;
    const ctx = setup({ status: 'disconnected', seat: 1, deadlineEpochMs: deadline });
    await nextTick();
    expect(vi.getTimerCount()).toBeGreaterThan(0);

    ctx.conn.value = { status: 'connected', seat: 1 };
    await nextTick();
    expect(vi.getTimerCount()).toBe(0);
    expect(ctx.secondsRemaining.value).toBe(0);
    ctx.cancel();
  });

  it('givenActiveCountdown_whenCancel_thenTimerStops', async () => {
    const deadline = Date.now() + 10_000;
    const { cancel } = setup({ status: 'disconnected', seat: 1, deadlineEpochMs: deadline });
    await nextTick();
    expect(vi.getTimerCount()).toBeGreaterThan(0);
    cancel();
    expect(vi.getTimerCount()).toBe(0);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/composables/useDisconnectCountdown.test.ts`
Expected: FAIL — cannot resolve `./useDisconnectCountdown`.

- [ ] **Step 3: Write the composable**

`frontend/src/composables/useDisconnectCountdown.ts`:

```ts
import { computed, ref, watch } from 'vue';

type OpponentConnection =
  | { status: 'disconnected'; seat: number; deadlineEpochMs: number }
  | { status: 'connected'; seat: number }
  | null;

interface UseDisconnectCountdownOptions {
  mode: () => 'solo' | 'multiplayer';
  opponentConnection: () => OpponentConnection;
  myPlayerIndex: () => number;
  isGameOver: () => boolean;
}

/**
 * Opponent-disconnect countdown. The deadline is server-provided (absolute epoch
 * ms); the local clock only renders the remaining seconds. The 250ms ticker runs
 * only while the opponent is disconnected, and is cleared on reconnect/game-end.
 * The caller wires `cancel()` into onBeforeUnmount (same pattern as useEndScreen).
 */
export function useDisconnectCountdown(options: UseDisconnectCountdownOptions) {
  const { mode, opponentConnection, myPlayerIndex, isGameOver } = options;

  const now = ref(Date.now());
  let countdownTimer: ReturnType<typeof setInterval> | null = null;

  const opponentDisconnected = computed(() => {
    const oc = opponentConnection();
    return mode() === 'multiplayer'
      && oc?.status === 'disconnected'
      && oc.seat !== myPlayerIndex()
      && !isGameOver();
  });

  const secondsRemaining = computed(() => {
    const oc = opponentConnection();
    if (oc?.status !== 'disconnected') return 0;
    return Math.max(0, Math.ceil((oc.deadlineEpochMs - now.value) / 1000));
  });

  const stop = watch(opponentDisconnected, (active) => {
    if (active && countdownTimer === null) {
      now.value = Date.now();
      countdownTimer = setInterval(() => { now.value = Date.now(); }, 250);
    } else if (!active && countdownTimer !== null) {
      clearInterval(countdownTimer);
      countdownTimer = null;
    }
  });

  /** Stop the interval and the watcher; wire into the view's onBeforeUnmount. */
  function cancel() {
    stop();
    if (countdownTimer !== null) { clearInterval(countdownTimer); countdownTimer = null; }
  }

  return { opponentDisconnected, secondsRemaining, cancel };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/composables/useDisconnectCountdown.test.ts`
Expected: PASS (6 tests).

- [ ] **Step 5: Wire into GameScreen, remove the inline block**

In `GameScreen.vue`:
- Add import: `import { useDisconnectCountdown } from '../../composables/useDisconnectCountdown';`
- Delete the inline `now` ref, `countdownTimer`, `opponentDisconnected` computed, `secondsRemaining` computed, and the `watch(opponentDisconnected, …)` block (the whole `--- Opponent disconnect countdown ---` section).
- Replace with:

```ts
const { opponentDisconnected, secondsRemaining, cancel: cancelDisconnectCountdown } = useDisconnectCountdown({
  mode: () => mode.value,
  opponentConnection: () => opponentConnection.value,
  myPlayerIndex: () => myPlayerIndex.value,
  isGameOver: () => isGameOver.value,
});
```

- In `onBeforeUnmount`, delete the line `if (countdownTimer !== null) clearInterval(countdownTimer);` and add `cancelDisconnectCountdown();`.

- [ ] **Step 6: Build**

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/composables/useDisconnectCountdown.ts frontend/src/composables/useDisconnectCountdown.test.ts frontend/src/view/alpha/GameScreen.vue
git commit -m "refactor(game-screen): extract useDisconnectCountdown composable"
```

---

## Task 5: Extract `useTurnIndicator`

**Files:**
- Create: `frontend/src/composables/useTurnIndicator.ts`
- Test: `frontend/src/composables/useTurnIndicator.test.ts`
- Modify: `frontend/src/view/alpha/GameScreen.vue`

- [ ] **Step 1: Write the failing test**

`frontend/src/composables/useTurnIndicator.test.ts`:

```ts
import { describe, it, expect } from 'vitest';
import { ref, nextTick } from 'vue';
import { useTurnIndicator, type TurnState } from './useTurnIndicator';

function setup(opts?: { canSend?: (i: number) => boolean }) {
  const canSend = opts?.canSend ?? (() => false);
  const state = ref<TurnState | undefined>({ canSend });
  const waiting = ref(false);
  const ended = ref(false);
  const api = useTurnIndicator({
    state: () => state.value,
    myPlayerIndex: () => 0,
    opponentIndex: () => 1,
    isWaiting: () => waiting.value,
    showEndOverlay: () => ended.value,
  });
  return { state, waiting, ended, ...api };
}

describe('useTurnIndicator', () => {
  it('givenMyTurn_thenShowMyTurnTrueOpponentFalse', () => {
    const { showMyTurn, showOpponentTurn, cancel } = setup({ canSend: (i) => i === 0 });
    expect(showMyTurn.value).toBe(true);
    expect(showOpponentTurn.value).toBe(false);
    cancel();
  });

  it('givenWaitingOverlay_thenCuesSuppressed', () => {
    const ctx = setup({ canSend: () => true });
    ctx.waiting.value = true;
    expect(ctx.showMyTurn.value).toBe(false);
    expect(ctx.showOpponentTurn.value).toBe(false);
    ctx.cancel();
  });

  it('givenEndOverlay_thenCuesSuppressed', () => {
    const ctx = setup({ canSend: () => true });
    ctx.ended.value = true;
    expect(ctx.showMyTurn.value).toBe(false);
    ctx.cancel();
  });

  it('givenFirstTimeMyTurn_thenHintShows_andDismissesForeverAfterIPlay', async () => {
    const canSend = ref(true);
    const state = ref<TurnState | undefined>({ canSend: (i) => i === 0 && canSend.value });
    const { showMyTurn, showFirstTurnHint, cancel } = useTurnIndicator({
      state: () => state.value,
      myPlayerIndex: () => 0,
      opponentIndex: () => 1,
      isWaiting: () => false,
      showEndOverlay: () => false,
    });

    await nextTick();
    expect(showMyTurn.value).toBe(true);
    expect(showFirstTurnHint.value).toBe(true);

    canSend.value = false;        // I played; it is no longer my turn
    await nextTick();
    expect(showFirstTurnHint.value).toBe(false);

    canSend.value = true;         // my turn again later
    await nextTick();
    expect(showFirstTurnHint.value).toBe(false); // never returns
    cancel();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/composables/useTurnIndicator.test.ts`
Expected: FAIL — cannot resolve `./useTurnIndicator`.

- [ ] **Step 3: Write the composable**

`frontend/src/composables/useTurnIndicator.ts`:

```ts
import { computed, readonly, ref, watch } from 'vue';

export const YOUR_TURN_LABEL = 'YOUR TURN';

/** Minimal structural view of the game model this composable needs. `BatailleCorse` satisfies it. */
export interface TurnState {
  canSend(playerIndex: number): boolean;
}

interface UseTurnIndicatorOptions {
  state: () => TurnState | undefined;
  myPlayerIndex: () => number;
  opponentIndex: () => number;
  isWaiting: () => boolean;
  showEndOverlay: () => boolean;
}

/**
 * Per-seat turn glow driven by the backend's `canSend(seat)` (the server only
 * offers SEND to the player whose turn it is and only while a card can be added),
 * plus a one-time "YOUR TURN" hint shown the first time it becomes the player's
 * turn and then dismissed for the rest of the game. Cues are suppressed while an
 * overlay owns the screen.
 */
export function useTurnIndicator(options: UseTurnIndicatorOptions) {
  const { state, myPlayerIndex, opponentIndex, isWaiting, showEndOverlay } = options;

  const showTurnCues = computed(() => !isWaiting() && !showEndOverlay());
  const showMyTurn = computed(() =>
    showTurnCues.value && (state()?.canSend(myPlayerIndex()) ?? false));
  const showOpponentTurn = computed(() =>
    showTurnCues.value && (state()?.canSend(opponentIndex()) ?? false));

  const showFirstTurnHint = ref(false);
  let firstTurnHintConsumed = false;

  const stop = watch(showMyTurn, (mine) => {
    if (firstTurnHintConsumed) return;
    if (mine) {
      showFirstTurnHint.value = true;
    } else if (showFirstTurnHint.value) {
      showFirstTurnHint.value = false;
      firstTurnHintConsumed = true;
    }
  }, { immediate: true });

  /** Stop the first-turn-hint watcher; wire into the view's onBeforeUnmount. */
  function cancel() {
    stop();
  }

  return { showMyTurn, showOpponentTurn, showFirstTurnHint: readonly(showFirstTurnHint), YOUR_TURN_LABEL, cancel };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/composables/useTurnIndicator.test.ts`
Expected: PASS (4 tests).

- [ ] **Step 5: Wire into GameScreen, remove the inline block**

In `GameScreen.vue`:
- Add import: `import { useTurnIndicator } from '../../composables/useTurnIndicator';`
- Delete the whole `--- Turn indicator ---` section: the `YOUR_TURN_LABEL` const, `showTurnCues`, `showMyTurn`, `showOpponentTurn` computeds, the `showFirstTurnHint` ref, `firstTurnHintConsumed`, and the `watch(showMyTurn, …)` block.
- Replace with:

```ts
const { showMyTurn, showOpponentTurn, showFirstTurnHint, YOUR_TURN_LABEL, cancel: cancelTurnIndicator } = useTurnIndicator({
  state: () => batailleCorse.value,
  myPlayerIndex: () => myPlayerIndex.value,
  opponentIndex: () => opponentIndex.value,
  isWaiting: () => isWaiting.value,
  showEndOverlay: () => showEndOverlay.value,
});
```

(`showEndOverlay` is already defined above this point by `useEndScreen`; keep this call after it.)

- In `onBeforeUnmount`, add `cancelTurnIndicator();`.

- [ ] **Step 6: Build**

Run: `cd frontend && npm run build`
Expected: succeeds. The template still references `showMyTurn`, `showOpponentTurn`, `showFirstTurnHint`, `YOUR_TURN_LABEL` — now sourced from the composable.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/composables/useTurnIndicator.ts frontend/src/composables/useTurnIndicator.test.ts frontend/src/view/alpha/GameScreen.vue
git commit -m "refactor(game-screen): extract useTurnIndicator composable"
```

---

## Task 6: Extract `useLeaveGuard`

**Files:**
- Create: `frontend/src/composables/useLeaveGuard.ts`
- Modify: `frontend/src/view/alpha/GameScreen.vue`

- [ ] **Step 1: Write the composable**

`frontend/src/composables/useLeaveGuard.ts`:

```ts
import { onBeforeUnmount, onMounted } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';

interface UseLeaveGuardOptions {
  isInProgress: () => boolean;
  mode: () => 'solo' | 'multiplayer';
  forfeit: () => void;
}

/**
 * Confirms before leaving an in-progress game. Confirming in multiplayer forfeits
 * (opponent wins immediately); solo just leaves. A finished/not-started game leaves
 * freely. Browser close/refresh shows the native prompt only — a hard close cannot
 * reliably send a forfeit, so it falls back to the server disconnect timer.
 */
export function useLeaveGuard(options: UseLeaveGuardOptions) {
  const { isInProgress, mode, forfeit } = options;

  onBeforeRouteLeave(() => {
    if (!isInProgress()) return true;
    const message = mode() === 'multiplayer'
      ? 'Leave the game? You will forfeit and your opponent wins.'
      : 'Leave the game? Your current game will be lost.';
    const confirmed = window.confirm(message);
    if (!confirmed) return false;
    if (mode() === 'multiplayer') forfeit();
    return true;
  });

  function handleBeforeUnload(event: BeforeUnloadEvent) {
    if (!isInProgress()) return;
    event.preventDefault();
    event.returnValue = '';
  }

  onMounted(() => window.addEventListener('beforeunload', handleBeforeUnload));
  onBeforeUnmount(() => window.removeEventListener('beforeunload', handleBeforeUnload));
}
```

- [ ] **Step 2: Wire into GameScreen, remove the inline block**

In `GameScreen.vue`:
- Add import: `import { useLeaveGuard } from '../../composables/useLeaveGuard';`
- Remove `onBeforeRouteLeave` from the `vue-router` import (change `import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router';` to `import { useRoute, useRouter } from 'vue-router';`).
- Delete the `--- Leave confirmation ---` section: the `onBeforeRouteLeave(...)` call and the `handleBeforeUnload` function.
- In `onMounted`, delete the line `window.addEventListener('beforeunload', handleBeforeUnload);` (the composable owns it now).
- In `onBeforeUnmount`, delete `window.removeEventListener('beforeunload', handleBeforeUnload);`.
- Add (near the other composable calls, after `isInProgress` is defined):

```ts
useLeaveGuard({
  isInProgress: () => isInProgress.value,
  mode: () => mode.value,
  forfeit: () => batailleCorseStore.forfeit(myPlayerIndex.value),
});
```

- [ ] **Step 3: Build**

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/composables/useLeaveGuard.ts frontend/src/view/alpha/GameScreen.vue
git commit -m "refactor(game-screen): extract useLeaveGuard composable"
```

---

## Task 7: Extract `useGameBootstrap`

**Files:**
- Create: `frontend/src/composables/useGameBootstrap.ts`
- Modify: `frontend/src/view/alpha/GameScreen.vue`

- [ ] **Step 1: Write the composable**

`frontend/src/composables/useGameBootstrap.ts`:

```ts
import { onBeforeUnmount, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useBatailleCorseStore } from '../state/BatailleCorse.store';
import webSocketService from '../service/WebSocketService';
import type BatailleCorse from '../model/BatailleCorse';

interface UseGameBootstrapOptions {
  /** From useEndScreen — reveal the end overlay synchronously if reloading into a finished game. */
  revealImmediatelyIfOver: () => void;
}

/**
 * Owns the game's mount/unmount lifecycle: validates the stored tokens, fetches and
 * hydrates the initial state, restores the session, loads the multiplayer view, and
 * subscribes to the game's websocket topic. On unmount it cancels the auto-grab timer
 * and unsubscribes. Redirects home if tokens are missing or the fetch fails.
 */
export function useGameBootstrap(options: UseGameBootstrapOptions) {
  const { revealImmediatelyIfOver } = options;
  const store = useBatailleCorseStore();
  const route = useRoute();
  const router = useRouter();

  onMounted(async () => {
    const gameId = route.params.id as string;

    const stored = localStorage.getItem(`tokens:${gameId}`);
    if (!stored) {
      router.replace({ name: 'home' });
      return;
    }

    const response = await fetch(`/api/game/${gameId}`);
    if (!response.ok) {
      router.replace({ name: 'home' });
      return;
    }

    const gameState = await response.json() as BatailleCorse;
    store.hydrate(gameId, gameState);
    revealImmediatelyIfOver();
    store.restoreSession(JSON.parse(stored));
    await store.loadSessionView(gameId);
    webSocketService.subscribeToGame(gameId);
  });

  onBeforeUnmount(() => {
    store.cancelAutoGrab();
    webSocketService.unsubscribeFromGame();
  });
}
```

- [ ] **Step 2: Wire into GameScreen, remove the inline block**

In `GameScreen.vue`:
- Add import: `import { useGameBootstrap } from '../../composables/useGameBootstrap';`
- Delete the entire `onMounted(async () => { … })` block.
- In `onBeforeUnmount`, delete `batailleCorseStore.cancelAutoGrab();` and `webSocketService.unsubscribeFromGame();` (the composable owns them).
- Add (after the `useEndScreen` call, since it needs `revealImmediatelyIfOver`):

```ts
useGameBootstrap({ revealImmediatelyIfOver });
```

- Check the remaining direct uses of `route`, `router`, `BatailleCorse` type, and `webSocketService` in GameScreen:
  - `route` / `router` are no longer used in GameScreen after this task → remove the `useRoute`/`useRouter` calls and the `vue-router` import entirely.
  - `webSocketService` import → remove if unused.
  - `import type BatailleCorse from '../../model/BatailleCorse';` → remove if unused.

- [ ] **Step 3: Build**

Run: `cd frontend && npm run build`
Expected: succeeds, no unused-import errors. (If any of `route`/`router`/`webSocketService`/`BatailleCorse` are still referenced, keep their imports; the build will tell you.)

- [ ] **Step 4: Commit**

```bash
git add frontend/src/composables/useGameBootstrap.ts frontend/src/view/alpha/GameScreen.vue
git commit -m "refactor(game-screen): extract useGameBootstrap composable"
```

---

## Task 8: Extract `useGameAnimations` (riskiest — do carefully)

**Files:**
- Create: `frontend/src/composables/useGameAnimations.ts`
- Modify: `frontend/src/view/alpha/GameScreen.vue`

This moves `useCardAnimation` construction, all six action-event watchers, and the slap-impact flash into one composable. Move the watcher bodies **verbatim** — only the element accessors change (`pile.value?.rootCard` → `opts.playerDeckEl()`, `opponentCard.value?.rootCard` → `opts.opponentDeckEl()`).

- [ ] **Step 1: Write the composable**

`frontend/src/composables/useGameAnimations.ts`:

```ts
import { nextTick, readonly, ref, watch } from 'vue';
import { storeToRefs } from 'pinia';
import { useCardAnimation } from './useCardAnimation';
import { useBatailleCorseStore } from '../state/BatailleCorse.store';

interface UseGameAnimationsOptions {
  playerDeckEl: () => HTMLImageElement | null | undefined;
  opponentDeckEl: () => HTMLImageElement | null | undefined;
  centerPileEl: () => HTMLImageElement | null | undefined;
  centerPileAreaEl: () => HTMLDivElement | null | undefined;
}

/**
 * Orchestrates every card animation in the game view: wraps `useCardAnimation`
 * and wires the store's optimistic action events (send/grab/slap/successful-slap/
 * erroneous-slap, plus the live pile-card swap) to it, calling
 * `notifyAnimationComplete()` at the same points as before so the store's event
 * queue stays in lockstep. Also owns the one-shot slap-impact "juice" on the
 * central card. All animations are cancelled on unmount by the caller via
 * `cancelAllAnimations`.
 */
export function useGameAnimations(opts: UseGameAnimationsOptions) {
  const store = useBatailleCorseStore();
  const { state: batailleCorse, myPlayerIndex, lastSend, lastGrab, lastSlap,
          lastSuccessfulSlap, lastErroneousSlap } = storeToRefs(store);

  const animation = useCardAnimation(
    opts.playerDeckEl,
    opts.opponentDeckEl,
    opts.centerPileEl,
    opts.centerPileAreaEl,
  );

  const {
    ghostCard, slapGhosts, isPileAnimating, isPileFlashing, frozenPileCard, cardDeltaIndicator,
  } = animation;

  // During send animation, the server responds within a few ms with the new card face.
  // Watch for that update and switch the ghost image immediately.
  watch(() => batailleCorse.value?.pile.cards.at(0), (newCard) => {
    animation.onNewPileCard(newCard);
  });

  // SEND is optimistic: topCard is snapshotted in the store at call time, no flush:'sync' needed.
  watch(lastSend, async (event) => {
    if (!event) return;
    const sourceEl = event.playerIndex === myPlayerIndex.value ? opts.playerDeckEl() : opts.opponentDeckEl();
    if (!sourceEl) return;
    await nextTick();
    const destRect = animation.getCenterPileRect();
    if (!destRect || destRect.width === 0) return;
    animation.animateSend(sourceEl.getBoundingClientRect(), destRect, event.topCard);
    // SEND is non-blocking in the queue — no notifyAnimationComplete needed.
  });

  // GRAB: pileCards snapshot is embedded in the event by the store.
  watch(lastGrab, async (event) => {
    if (!event) { animation.cancelPileAnimation(); store.notifyAnimationComplete(); return; }
    const { pileCards, winnerPlayerIndex } = event;
    await nextTick();
    const destEl = winnerPlayerIndex === myPlayerIndex.value ? opts.playerDeckEl() : opts.opponentDeckEl();
    const srcRect = animation.getCenterPileRect();
    if (!destEl || !srcRect || srcRect.width === 0) {
      animation.cancelPileAnimation();
      store.notifyAnimationComplete();
      return;
    }
    animation.showDeltaOnGrab(pileCards.length, destEl);
    await animation.animatePileToWinner(srcRect, destEl.getBoundingClientRect(), pileCards);
    store.notifyAnimationComplete();
  });

  // Immediate flash on any slap attempt, before the server responds.
  watch(lastSlap, () => animation.flashPile());

  // Slap "juice": a one-shot impact on the central card — a scale punch when the
  // slap lands, a red shake when it was a mistake. Re-armed via null→nextTick so
  // rapid repeat slaps replay the animation rather than no-op on the same class.
  const slapImpact = ref<'success' | 'error' | null>(null);
  let slapImpactTimer: ReturnType<typeof setTimeout> | null = null;
  function flashSlapImpact(kind: 'success' | 'error') {
    if (slapImpactTimer) clearTimeout(slapImpactTimer);
    slapImpact.value = null;
    void nextTick(() => {
      slapImpact.value = kind;
      slapImpactTimer = setTimeout(() => { slapImpact.value = null; }, 400);
    });
  }

  // Successful slap: pileCards snapshot is embedded in the event by the store.
  watch(lastSuccessfulSlap, async (event) => {
    if (!event) return;
    flashSlapImpact('success');
    const { pileCards, winnerPlayerIndex } = event;
    await nextTick();
    const destEl = winnerPlayerIndex === myPlayerIndex.value ? opts.playerDeckEl() : opts.opponentDeckEl();
    if (!destEl) { store.notifyAnimationComplete(); return; }
    const srcRect = animation.getCenterPileRect();
    if (!srcRect || srcRect.width === 0) { store.notifyAnimationComplete(); return; }
    animation.showDeltaOnSlap(pileCards.length, destEl);
    await animation.animatePileToWinner(srcRect, destEl.getBoundingClientRect(), pileCards);
    store.notifyAnimationComplete();
  });

  // Erroneous slap: animate 2 ghost cards from slapper's deck to center, show -2 indicator.
  watch(lastErroneousSlap, async (event) => {
    if (!event) return;
    flashSlapImpact('error');
    await nextTick();
    const srcEl = event.playerIndex === myPlayerIndex.value ? opts.playerDeckEl() : opts.opponentDeckEl();
    const destRect = animation.getCenterPileRect();
    if (!srcEl || !destRect) { store.notifyAnimationComplete(); return; }
    await animation.animateErroneousSlap(srcEl.getBoundingClientRect(), destRect);
    animation.showDeltaAlways(-2, srcEl);
    store.notifyAnimationComplete();
  });

  /** Cancel all in-flight animations and the pending slap-impact timer; wire into onBeforeUnmount. */
  function cancel() {
    if (slapImpactTimer) { clearTimeout(slapImpactTimer); slapImpactTimer = null; }
    animation.cancelAllAnimations();
  }

  return {
    ghostCard, slapGhosts, isPileAnimating, isPileFlashing, frozenPileCard, cardDeltaIndicator,
    slapImpact: readonly(slapImpact),
    cancel,
  };
}
```

> **Note on `lastSend`/`lastErroneousSlap` event shape:** the watchers reference `event.playerIndex`. Confirm against the store's event types (`lastSend` carries `playerIndex`, `lastErroneousSlap` carries `playerIndex`). These are the exact field names used in the current GameScreen watchers — do not rename.

- [ ] **Step 2: Wire into GameScreen, remove the inline block**

In `GameScreen.vue`:
- Add import: `import { useGameAnimations } from '../../composables/useGameAnimations';`
- Remove imports now unused in the view: `useCardAnimation`, and from `vue` the `watch` import if no other `watch` remains in the file (check — after Tasks 4/5 the disconnect/turn watchers are gone; the only remaining watchers were the animation ones, so `watch` can likely be removed). Keep `computed`, `ref`, `onBeforeUnmount`, `useTemplateRef`, `nextTick` only if still used (see below).
- Delete the `const animation = useCardAnimation(...)` block, the `const { ghostCard, … } = animation;` destructure, all six `watch(...)` animation blocks, the `slapImpact` ref, `slapImpactTimer`, and `flashSlapImpact`.
- Keep the four `useTemplateRef` declarations (`pile`, `opponentCard`, `centerPile`, `centerPileArea`) — they bind the DOM refs the template needs.
- Replace with:

```ts
const {
  ghostCard, slapGhosts, isPileAnimating, isPileFlashing, frozenPileCard, cardDeltaIndicator,
  slapImpact, cancel: cancelAnimations,
} = useGameAnimations({
  playerDeckEl: () => pile.value?.rootCard,
  opponentDeckEl: () => opponentCard.value?.rootCard,
  centerPileEl: () => centerPile.value?.rootCard,
  centerPileAreaEl: () => centerPileArea.value,
});
```

- In `onBeforeUnmount`, replace `animation.cancelAllAnimations();` with `cancelAnimations();`, and delete `if (slapImpactTimer !== null) clearTimeout(slapImpactTimer);` (the composable's `cancel()` now covers both).
- `isPileAnimating` is consumed by the existing `useEndScreen(() => isGameOver.value, () => isPileAnimating.value)` call — it now comes from `useGameAnimations`, so this call must appear **before** `useEndScreen`. Reorder if needed.
- Remove `nextTick` from the `vue` import if no longer used in the view.

- [ ] **Step 3: Build**

Run: `cd frontend && npm run build`
Expected: succeeds. Watch for: unused imports (`watch`, `nextTick`, `useCardAnimation`), and the `useEndScreen`/`useGameAnimations` ordering (isPileAnimating must be defined first).

- [ ] **Step 4: Run full unit suite**

Run: `cd frontend && npm run test`
Expected: all suites pass (including the two new composable suites).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/composables/useGameAnimations.ts frontend/src/view/alpha/GameScreen.vue
git commit -m "refactor(game-screen): extract useGameAnimations composable"
```

---

## Task 9: Final cleanup, full verification & manual smoke

**Files:** Modify: `frontend/src/view/alpha/GameScreen.vue` (only if leftover dead imports/refs remain).

- [ ] **Step 1: Audit the slimmed view**

Open `GameScreen.vue`. Confirm the `<script setup>` now contains only: store wiring (`storeToRefs`), the four `useTemplateRef`s, the composable calls (`useGameAnimations`, `useEndScreen`, `useGameDuration`, `useDisconnectCountdown`, `useTurnIndicator`, `useLeaveGuard`, `useGameBootstrap`, `useHotkeys`), the action helpers (`send`, `slap`, `isButtonDisabled`, `onPlayAgain`), and the view-model computeds (`difficultyLabel`, `opponentIndex`, `isSolo`, `isWaiting`, `opponentLabel`, `pileIsEmpty`, `isGameOver`, `didIWin`, `endSubtitle`, `rematchButton`, `isInProgress`). Remove any import with no remaining reference.

Confirm the view's `onBeforeUnmount` is now just the returned-`cancel` calls (everything else moved into its composable):

```ts
onBeforeUnmount(() => {
  cancelAnimations();
  cancelEndScreen();
  cancelGameDuration();
  cancelDisconnectCountdown();
  cancelTurnIndicator();
});
```

- [ ] **Step 2: Full build + tests**

Run: `cd frontend && npm run build && npm run test`
Expected: both succeed.

- [ ] **Step 3: Manual smoke (solo game)**

Run: `cd frontend && npm run dev`, open the app, start a solo game, and verify each behavior is unchanged:
- Send animation (card flies deck→pile); pile card face updates.
- Grab: pile flies to winner with `+N` delta indicator.
- Slap success: card-punch juice + pile flies to winner + `+N` delta.
- Slap error: card-shake juice + 2 ghost cards fly deck→center + `-2` delta.
- Turn glow on the active player's name tag; "YOUR TURN" hint appears on first turn only and never returns.
- Game timer ticks during play.
- End overlay (victory/defeat) appears after the final animation settles; trophy bounce on victory; "Play Again" works; "Back to home" navigates.
- Leave confirmation prompt when navigating away mid-game.

- [ ] **Step 4: Manual smoke (multiplayer, if a second client is available)**

- Waiting overlay shows with a working share link + Copy.
- Disconnect overlay shows with a live `You win in Ns` countdown when the opponent disconnects; clears on reconnect.

- [ ] **Step 5: Final commit (only if Step 1 changed anything)**

```bash
git add frontend/src/view/alpha/GameScreen.vue
git commit -m "refactor(game-screen): drop dead imports after debloat"
```

---

## Notes for the implementer

- **Behavior-preserving refactor.** If a build or test fails, the cause is almost always a moved accessor or an import — not a logic change. Do not "improve" the watcher bodies; move them verbatim.
- **Composable call ordering matters:** `useEndScreen` needs `isPileAnimating` (from `useGameAnimations`) and produces `showEndOverlay`; `useTurnIndicator` and `useGameBootstrap` consume `showEndOverlay` / `revealImmediatelyIfOver`. Order: `useGameAnimations` → `useEndScreen` → (`useGameDuration`, `useDisconnectCountdown`, `useTurnIndicator`, `useLeaveGuard`, `useGameBootstrap`).
- **`data-cy` attributes** moved into components must stay byte-identical (`disconnect-overlay`, `disconnect-countdown`, `end-overlay`, `victory-flourish`, `play-again`). The `turn-hint` `data-cy` stays in GameScreen (the hint markup remains in the view).
- The redundant root-level `.claude/skills/vue-best-practices/SKILL.md` duplicate is unrelated to this plan; leave it.
```

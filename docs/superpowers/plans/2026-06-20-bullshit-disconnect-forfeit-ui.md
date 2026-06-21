# Bullshit Disconnect / Forfeit UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the already-broadcast Bullshit opponent-presence events (disconnect / reconnect / forfeit) into the UI, via game-agnostic reusable bricks, so remaining players see who dropped and why a seat vanished.

**Architecture:** Four new game-neutral frontend bricks live in the shared `composables/` and `components/` roots — a presence-state reducer (`useSeatPresence`), a multi-seat countdown (`useSeatDisconnectCountdown`), a per-seat badge (`SeatDisconnectBadge`), and a transient banner (`ForfeitBanner`) — plus a shared event-data model. The Bullshit store delegates event reduction to the presence brick (transient state, separate from authoritative game state, mirroring the existing `reveal` pattern); `BullshitGameScreen` consumes the countdown and renders the badge per `OpponentSeat` and the forfeit banner. BatailleCorse is untouched (follow-up issue #71).

**Tech Stack:** Vue 3 `<script setup>` + TypeScript, Pinia, PrimeVue, Vitest + `@vue/test-utils`.

**Spec:** `docs/superpowers/specs/2026-06-20-bullshit-disconnect-forfeit-ui-design.md`

**Conventions:**
- Test files are `*.test.ts` (NOT `.spec.ts`), colocated next to source.
- Run a single test file: `cd frontend && npx vitest run src/path/to/file.test.ts`
- Full gate (worktree lacks `node_modules`): `cd frontend && npm ci` once, then
  `npm run test` and `npm run build`.
- `git add` every new file immediately. Commit trailer:
  `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`
- Bullshit copy is inline English (no i18n); seat numbers display 1-based (`seat + 1`).

---

## File Structure

**Create:**
- `frontend/src/model/SeatLifecycleEvents.ts` — shared event-data interfaces.
- `frontend/src/composables/useSeatPresence.ts` — Brick A: presence-state reducer.
- `frontend/src/composables/useSeatPresence.test.ts`
- `frontend/src/composables/useSeatDisconnectCountdown.ts` — Brick B: multi-seat countdown.
- `frontend/src/composables/useSeatDisconnectCountdown.test.ts`
- `frontend/src/components/SeatDisconnectBadge.vue` — Brick C: per-seat badge.
- `frontend/src/components/SeatDisconnectBadge.test.ts`
- `frontend/src/components/ForfeitBanner.vue` — Brick D: transient banner.
- `frontend/src/components/ForfeitBanner.test.ts`

**Modify:**
- `frontend/src/state/Bullshit.store.ts` — delegate to `useSeatPresence`, expose refs, reset on rematch.
- `frontend/src/state/Bullshit.store.test.ts` — presence delegation + reset tests.
- `frontend/src/components/bullshit/OpponentSeat.vue` — two additive props + badge.
- `frontend/src/components/bullshit/OpponentSeat.test.ts` — disconnected-state test.
- `frontend/src/view/bullshit/BullshitGameScreen.vue` — countdown wiring + badge props + banner.
- `frontend/src/view/bullshit/BullshitGameScreen.test.ts` — badge + banner tests.

---

## Task 0: Install dependencies (worktree only)

- [ ] **Step 1: Install**

Run: `cd frontend && npm ci`
Expected: completes; `node_modules` present. (Worktrees start without it; the build/test gate needs it.)

---

## Task 1: Shared event-data model

**Files:**
- Create: `frontend/src/model/SeatLifecycleEvents.ts`

- [ ] **Step 1: Create the model file**

These mirror the backend's shared `presentation.dto.event` records (game-agnostic — not under `bullshit/`).

```ts
// Per-seat lifecycle event payloads broadcast on /topic/game/{id}/seat/{token}.
// Game-agnostic: any game on the shared session/presence layer emits these.
export interface OpponentDisconnectedEventData {
  disconnectedSeat: number;
  deadlineEpochMs: number;
}

export interface OpponentReconnectedEventData {
  reconnectedSeat: number;
}

export interface ForfeitEventData {
  loserSeat: number;
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/model/SeatLifecycleEvents.ts
git commit -m "feat(frontend): shared seat-lifecycle event-data types

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: Brick A — `useSeatPresence`

**Files:**
- Create: `frontend/src/composables/useSeatPresence.ts`
- Test: `frontend/src/composables/useSeatPresence.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { ref } from 'vue';
import { useSeatPresence, SEAT_LIFECYCLE_EVENT, FORFEIT_NOTICE_HOLD_MS } from './useSeatPresence';

describe('useSeatPresence', () => {
  beforeEach(() => vi.useFakeTimers({ now: 1_000_000 }));
  afterEach(() => vi.useRealTimers());

  it('givenDisconnectEvent_thenRecordsSeatDeadline', () => {
    const p = useSeatPresence();
    p.applyPresenceEvent(SEAT_LIFECYCLE_EVENT.OPPONENT_DISCONNECTED,
      { disconnectedSeat: 2, deadlineEpochMs: 1_060_000 });
    expect(p.disconnections.value).toEqual({ 2: 1_060_000 });
  });

  it('givenReconnectEvent_thenRemovesSeat', () => {
    const p = useSeatPresence();
    p.applyPresenceEvent(SEAT_LIFECYCLE_EVENT.OPPONENT_DISCONNECTED,
      { disconnectedSeat: 2, deadlineEpochMs: 1_060_000 });
    p.applyPresenceEvent(SEAT_LIFECYCLE_EVENT.OPPONENT_RECONNECTED, { reconnectedSeat: 2 });
    expect(p.disconnections.value).toEqual({});
  });

  it('givenForfeitEvent_thenRemovesSeatAndShowsNoticeThatAutoClears', () => {
    const p = useSeatPresence();
    p.applyPresenceEvent(SEAT_LIFECYCLE_EVENT.OPPONENT_DISCONNECTED,
      { disconnectedSeat: 3, deadlineEpochMs: 1_060_000 });
    p.applyPresenceEvent(SEAT_LIFECYCLE_EVENT.FORFEIT, { loserSeat: 3 });
    expect(p.disconnections.value).toEqual({});
    expect(p.forfeitNotice.value).toEqual({ seat: 3 });

    vi.advanceTimersByTime(FORFEIT_NOTICE_HOLD_MS);
    expect(p.forfeitNotice.value).toBeNull();
  });

  it('givenPresentSeatsGetter_thenLiveDisconnectionsDropsAbsentSeats', () => {
    const present = ref<number[]>([0, 1]);
    const p = useSeatPresence({ presentSeats: () => present.value });
    p.applyPresenceEvent(SEAT_LIFECYCLE_EVENT.OPPONENT_DISCONNECTED,
      { disconnectedSeat: 1, deadlineEpochMs: 1_060_000 });
    expect(p.liveDisconnections.value).toEqual({ 1: 1_060_000 });

    present.value = [0];          // seat 1 left the table
    expect(p.liveDisconnections.value).toEqual({});
  });

  it('givenUnknownEvent_thenNoOp', () => {
    const p = useSeatPresence();
    p.applyPresenceEvent('CALL_BULLSHIT', { whatever: true });
    expect(p.disconnections.value).toEqual({});
    expect(p.forfeitNotice.value).toBeNull();
  });

  it('givenReset_thenClearsStateAndTimer', () => {
    const p = useSeatPresence();
    p.applyPresenceEvent(SEAT_LIFECYCLE_EVENT.OPPONENT_DISCONNECTED,
      { disconnectedSeat: 2, deadlineEpochMs: 1_060_000 });
    p.applyPresenceEvent(SEAT_LIFECYCLE_EVENT.FORFEIT, { loserSeat: 5 });
    p.reset();
    expect(p.disconnections.value).toEqual({});
    expect(p.forfeitNotice.value).toBeNull();
    vi.advanceTimersByTime(FORFEIT_NOTICE_HOLD_MS);   // timer must not resurrect anything
    expect(p.forfeitNotice.value).toBeNull();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/composables/useSeatPresence.test.ts`
Expected: FAIL — `Failed to resolve import './useSeatPresence'`.

- [ ] **Step 3: Write the implementation**

```ts
import { ref, computed } from 'vue';
import type { Ref, ComputedRef } from 'vue';
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

/** How long the "Player N forfeited" notice stays up before auto-dismissing. */
export const FORFEIT_NOTICE_HOLD_MS = 4000;

export interface SeatPresence {
  disconnections: Ref<Record<number, number>>;       // seat -> deadlineEpochMs
  forfeitNotice: Ref<{ seat: number } | null>;
  liveDisconnections: ComputedRef<Record<number, number>>;
  applyPresenceEvent(eventType: string, eventData: unknown): void;
  reset(): void;
}

export interface UseSeatPresenceOptions {
  /** Seats currently present at the table; used to drop stale disconnect entries. */
  presentSeats?: () => number[];
  forfeitNoticeHoldMs?: number;
}

/**
 * Transient opponent-presence state reduced from the per-seat lifecycle events.
 * Game-agnostic: hold a map of disconnected seats (seat -> server deadline) plus a
 * timed-hold forfeit notice. Mirrors the `reveal` transient-state pattern.
 */
export function useSeatPresence(options: UseSeatPresenceOptions = {}): SeatPresence {
  const { presentSeats, forfeitNoticeHoldMs = FORFEIT_NOTICE_HOLD_MS } = options;

  const disconnections = ref<Record<number, number>>({});
  const forfeitNotice = ref<{ seat: number } | null>(null);
  let forfeitTimer: ReturnType<typeof setTimeout> | null = null;

  const liveDisconnections = computed<Record<number, number>>(() => {
    if (!presentSeats) return disconnections.value;
    const present = new Set(presentSeats());
    return Object.fromEntries(
      Object.entries(disconnections.value).filter(([seat]) => present.has(Number(seat))),
    );
  });

  function applyPresenceEvent(eventType: string, eventData: unknown): void {
    switch (eventType) {
      case SEAT_LIFECYCLE_EVENT.OPPONENT_DISCONNECTED: {
        const { disconnectedSeat, deadlineEpochMs } = eventData as OpponentDisconnectedEventData;
        disconnections.value = { ...disconnections.value, [disconnectedSeat]: deadlineEpochMs };
        break;
      }
      case SEAT_LIFECYCLE_EVENT.OPPONENT_RECONNECTED: {
        const { reconnectedSeat } = eventData as OpponentReconnectedEventData;
        const next = { ...disconnections.value };
        delete next[reconnectedSeat];
        disconnections.value = next;
        break;
      }
      case SEAT_LIFECYCLE_EVENT.FORFEIT: {
        const { loserSeat } = eventData as ForfeitEventData;
        const next = { ...disconnections.value };
        delete next[loserSeat];
        disconnections.value = next;
        forfeitNotice.value = { seat: loserSeat };
        if (forfeitTimer !== null) clearTimeout(forfeitTimer);
        forfeitTimer = setTimeout(() => {
          forfeitNotice.value = null;
          forfeitTimer = null;
        }, forfeitNoticeHoldMs);
        break;
      }
    }
  }

  function reset(): void {
    disconnections.value = {};
    forfeitNotice.value = null;
    if (forfeitTimer !== null) {
      clearTimeout(forfeitTimer);
      forfeitTimer = null;
    }
  }

  return { disconnections, forfeitNotice, liveDisconnections, applyPresenceEvent, reset };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/composables/useSeatPresence.test.ts`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/composables/useSeatPresence.ts frontend/src/composables/useSeatPresence.test.ts
git commit -m "feat(frontend): useSeatPresence brick — reduces seat lifecycle events to transient state

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Brick B — `useSeatDisconnectCountdown`

**Files:**
- Create: `frontend/src/composables/useSeatDisconnectCountdown.ts`
- Test: `frontend/src/composables/useSeatDisconnectCountdown.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { ref, nextTick } from 'vue';
import { useSeatDisconnectCountdown } from './useSeatDisconnectCountdown';

function setup(initial: Record<number, number>) {
  const map = ref<Record<number, number>>(initial);
  const over = ref(false);
  const api = useSeatDisconnectCountdown({
    disconnections: () => map.value,
    isGameOver: () => over.value,
  });
  return { map, over, ...api };
}

describe('useSeatDisconnectCountdown', () => {
  beforeEach(() => vi.useFakeTimers({ now: 1_000_000 }));
  afterEach(() => vi.useRealTimers());

  it('givenNoDisconnections_thenNoTicker', () => {
    const { cancel } = setup({});
    expect(vi.getTimerCount()).toBe(0);
    cancel();
  });

  it('givenDisconnectedSeat_thenComputesRemainingSecondsFromDeadline', async () => {
    const ctx = setup({});
    ctx.map.value = { 1: Date.now() + 10_000 };
    await nextTick();
    expect(ctx.secondsRemainingFor(ctx.map.value[1])).toBe(10);

    vi.advanceTimersByTime(4_000);
    expect(ctx.secondsRemainingFor(ctx.map.value[1])).toBe(6);
    ctx.cancel();
  });

  it('givenAllReconnected_thenTickerStops', async () => {
    const ctx = setup({ 1: Date.now() + 10_000 });
    await nextTick();
    expect(vi.getTimerCount()).toBeGreaterThan(0);

    ctx.map.value = {};
    await nextTick();
    expect(vi.getTimerCount()).toBe(0);
    ctx.cancel();
  });

  it('givenGameOver_thenTickerStops', async () => {
    const ctx = setup({ 1: Date.now() + 10_000 });
    await nextTick();
    expect(vi.getTimerCount()).toBeGreaterThan(0);

    ctx.over.value = true;
    await nextTick();
    expect(vi.getTimerCount()).toBe(0);
    ctx.cancel();
  });

  it('givenPastDeadline_thenClampsToZero', async () => {
    const ctx = setup({ 1: Date.now() - 5_000 });
    await nextTick();
    expect(ctx.secondsRemainingFor(ctx.map.value[1])).toBe(0);
    ctx.cancel();
  });

  it('givenActiveTicker_whenCancel_thenStops', async () => {
    const ctx = setup({ 1: Date.now() + 10_000 });
    await nextTick();
    expect(vi.getTimerCount()).toBeGreaterThan(0);
    ctx.cancel();
    expect(vi.getTimerCount()).toBe(0);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/composables/useSeatDisconnectCountdown.test.ts`
Expected: FAIL — cannot resolve import.

- [ ] **Step 3: Write the implementation**

```ts
import { ref, computed, watch } from 'vue';

export interface UseSeatDisconnectCountdownOptions {
  disconnections: () => Record<number, number>;
  isGameOver: () => boolean;
}

/**
 * Multi-seat opponent-disconnect countdown. Deadlines are server-provided (absolute
 * epoch ms); the local clock only renders remaining seconds. One 250ms ticker runs
 * while >=1 seat is disconnected and the game isn't over. Generalizes BatailleCorse's
 * useDisconnectCountdown from a single connection to a map of seats. The caller wires
 * cancel() into onBeforeUnmount.
 */
export function useSeatDisconnectCountdown(options: UseSeatDisconnectCountdownOptions) {
  const { disconnections, isGameOver } = options;

  const now = ref(Date.now());
  let timer: ReturnType<typeof setInterval> | null = null;

  const active = computed(
    () => Object.keys(disconnections()).length > 0 && !isGameOver(),
  );

  const stop = watch(
    active,
    (on) => {
      if (on && timer === null) {
        now.value = Date.now();
        timer = setInterval(() => { now.value = Date.now(); }, 250);
      } else if (!on && timer !== null) {
        clearInterval(timer);
        timer = null;
      }
    },
    { immediate: true },
  );

  function secondsRemainingFor(deadlineEpochMs: number): number {
    return Math.max(0, Math.ceil((deadlineEpochMs - now.value) / 1000));
  }

  /** Stop the interval and the watcher; wire into the view's onBeforeUnmount. */
  function cancel(): void {
    stop();
    if (timer !== null) {
      clearInterval(timer);
      timer = null;
    }
  }

  return { secondsRemainingFor, cancel };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/composables/useSeatDisconnectCountdown.test.ts`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/composables/useSeatDisconnectCountdown.ts frontend/src/composables/useSeatDisconnectCountdown.test.ts
git commit -m "feat(frontend): useSeatDisconnectCountdown brick — multi-seat grace countdown

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: Brick C — `SeatDisconnectBadge.vue`

**Files:**
- Create: `frontend/src/components/SeatDisconnectBadge.vue`
- Test: `frontend/src/components/SeatDisconnectBadge.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import SeatDisconnectBadge from './SeatDisconnectBadge.vue';

describe('SeatDisconnectBadge', () => {
  it('renders the countdown seconds when provided', () => {
    const wrapper = mount(SeatDisconnectBadge, { props: { secondsRemaining: 42 } });
    expect(wrapper.get('[data-test="seat-disconnect-badge"]').text()).toContain('42');
    expect(wrapper.text().toLowerCase()).toContain('reconnecting');
  });

  it('omits the seconds when null', () => {
    const wrapper = mount(SeatDisconnectBadge, { props: { secondsRemaining: null } });
    expect(wrapper.get('[data-test="seat-disconnect-badge"]').text()).not.toMatch(/\d/);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/components/SeatDisconnectBadge.test.ts`
Expected: FAIL — cannot resolve import.

- [ ] **Step 3: Write the component**

```vue
<template>
  <div class="seat-disconnect-badge" data-test="seat-disconnect-badge">
    <span class="dot" aria-hidden="true"></span>
    <span class="text">
      Reconnecting<template v-if="secondsRemaining !== null">… {{ secondsRemaining }}s</template>
    </span>
  </div>
</template>

<script setup lang="ts">
defineProps<{ secondsRemaining: number | null }>();
</script>

<style scoped>
.seat-disconnect-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 0.62rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  white-space: nowrap;
  color: #fff;
  background: rgba(0, 0, 0, 0.72);
  border: 1px solid rgba(var(--accent-negative-rgb), 0.75);
  border-radius: 999px;
  padding: 3px 9px;
  box-shadow: 0 0 12px 1px rgba(var(--accent-negative-rgb), 0.45);
}
.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: rgb(var(--accent-negative-rgb));
  animation: badge-pulse 1.2s ease-in-out infinite;
}
@keyframes badge-pulse {
  0%, 100% { opacity: 1; }
  50%      { opacity: 0.35; }
}
@media (prefers-reduced-motion: reduce) {
  .dot { animation: none; }
}
</style>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/components/SeatDisconnectBadge.test.ts`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/SeatDisconnectBadge.vue frontend/src/components/SeatDisconnectBadge.test.ts
git commit -m "feat(frontend): SeatDisconnectBadge brick — per-seat reconnect countdown badge

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: Brick D — `ForfeitBanner.vue`

**Files:**
- Create: `frontend/src/components/ForfeitBanner.vue`
- Test: `frontend/src/components/ForfeitBanner.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ForfeitBanner from './ForfeitBanner.vue';

describe('ForfeitBanner', () => {
  it('renders the given label', () => {
    const wrapper = mount(ForfeitBanner, { props: { label: 'Player 3 forfeited' } });
    expect(wrapper.get('[data-test="forfeit-banner"]').text()).toBe('Player 3 forfeited');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/components/ForfeitBanner.test.ts`
Expected: FAIL — cannot resolve import.

- [ ] **Step 3: Write the component**

```vue
<template>
  <div class="forfeit-banner" data-test="forfeit-banner" role="status">{{ label }}</div>
</template>

<script setup lang="ts">
defineProps<{ label: string }>();
</script>

<style scoped>
.forfeit-banner {
  font-size: 0.8rem;
  font-weight: 700;
  letter-spacing: 0.05em;
  color: #fff;
  background: rgba(0, 0, 0, 0.78);
  border: 1px solid rgba(var(--accent-negative-rgb), 0.7);
  border-radius: 999px;
  padding: 6px 18px;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.5);
}
</style>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/components/ForfeitBanner.test.ts`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/ForfeitBanner.vue frontend/src/components/ForfeitBanner.test.ts
git commit -m "feat(frontend): ForfeitBanner brick — transient forfeit notice

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: Wire the Bullshit store to `useSeatPresence`

**Files:**
- Modify: `frontend/src/state/Bullshit.store.ts`
- Test: `frontend/src/state/Bullshit.store.test.ts`

- [ ] **Step 1: Write the failing tests**

Append these tests inside the existing `describe('Bullshit store', ...)` block in
`frontend/src/state/Bullshit.store.test.ts` (after the `playAgain()` test, before the
closing `});` of the describe):

```ts
  describe('opponent presence', () => {
    it('records a disconnect, exposes it via liveDisconnections, and clears on reconnect', () => {
      const store = useBullshitStore();
      store.applyEvent({ type: 'seat-change', seat: 0 });
      store.applyEvent({ type: 'state-update', state: state() });   // players 0 and 1

      store.applyEvent({ type: 'event', eventType: 'OPPONENT_DISCONNECTED',
        eventData: { disconnectedSeat: 1, deadlineEpochMs: 1_060_000 }, message: 'm' });
      expect(store.liveDisconnections).toEqual({ 1: 1_060_000 });

      store.applyEvent({ type: 'event', eventType: 'OPPONENT_RECONNECTED',
        eventData: { reconnectedSeat: 1 }, message: 'm' });
      expect(store.liveDisconnections).toEqual({});
    });

    it('drops a disconnect for a seat no longer present in the game', () => {
      const store = useBullshitStore();
      store.applyEvent({ type: 'seat-change', seat: 0 });
      store.applyEvent({ type: 'state-update', state: state() });   // players 0 and 1
      store.applyEvent({ type: 'event', eventType: 'OPPONENT_DISCONNECTED',
        eventData: { disconnectedSeat: 1, deadlineEpochMs: 1_060_000 }, message: 'm' });

      // seat 1 leaves the table (forfeited/removed by a later state-update)
      store.applyEvent({ type: 'state-update', state: state({
        players: [{ id: '0', handCount: 2, isCurrentPlayer: true }],
      }) });
      expect(store.liveDisconnections).toEqual({});
    });

    it('shows a forfeit notice and clears the disconnect for that seat', () => {
      const store = useBullshitStore();
      store.applyEvent({ type: 'seat-change', seat: 0 });
      store.applyEvent({ type: 'state-update', state: state() });
      store.applyEvent({ type: 'event', eventType: 'OPPONENT_DISCONNECTED',
        eventData: { disconnectedSeat: 1, deadlineEpochMs: 1_060_000 }, message: 'm' });

      store.applyEvent({ type: 'event', eventType: 'FORFEIT',
        eventData: { loserSeat: 1 }, message: 'm' });
      expect(store.forfeitNotice).toEqual({ seat: 1 });
      expect(store.liveDisconnections).toEqual({});
    });

    it('CALL_BULLSHIT still records a reveal (unchanged)', () => {
      const store = useBullshitStore();
      const reveal = { callerSeat: 1, claimantSeat: 0, truthful: false, pickerSeat: 0, revealedCards: [] };
      store.applyEvent({ type: 'event', eventType: 'CALL_BULLSHIT', eventData: reveal, message: 'm' });
      expect(store.reveal).toEqual(reveal);
    });

    it('playAgain resets stale presence', async () => {
      const store = useBullshitStore();
      store.restore('g1', 0, 'tok');
      store.applyEvent({ type: 'state-update', state: state() });
      store.applyEvent({ type: 'event', eventType: 'OPPONENT_DISCONNECTED',
        eventData: { disconnectedSeat: 1, deadlineEpochMs: 1_060_000 }, message: 'm' });
      expect(store.liveDisconnections).toEqual({ 1: 1_060_000 });

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
        new Response(JSON.stringify({ playerId: 0, token: 't' }), { status: 200 }));
      await store.playAgain();
      expect(store.disconnections).toEqual({});
      expect(store.forfeitNotice).toBeNull();
      fetchSpy.mockRestore();
    });
  });
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && npx vitest run src/state/Bullshit.store.test.ts`
Expected: FAIL — `store.liveDisconnections` is undefined / presence not wired.

- [ ] **Step 3: Rewrite the store**

Replace the entire contents of `frontend/src/state/Bullshit.store.ts` with:

```ts
import { defineStore } from 'pinia';
import { ref, computed, watch } from 'vue';

import webSocketService from '../service/WebSocketService';
import BullshitSession, { type BullshitSessionEvent } from '../application/BullshitSession';
import type { BullshitState, BullshitView } from '../model/bullshit/BullshitState';
import type { LobbyView } from '../model/bullshit/LobbyView';
import type { CallBullshitEventData } from '../model/bullshit/BullshitEvents';
import type Card from '../model/Card';
import { useSeatPresence, FORFEIT_NOTICE_HOLD_MS } from '../composables/useSeatPresence';

export const REVEAL_HOLD_MS = 3000;
export { FORFEIT_NOTICE_HOLD_MS };

export const useBullshitStore = defineStore('bullshit-store', () => {
  const state = ref<BullshitView | null>(null);
  const gameId = ref<string | null>(null);
  const mySeat = ref<number>(0);
  const reveal = ref<CallBullshitEventData | null>(null);
  const selectedCards = ref<Card[]>([]);
  let revealTimer: ReturnType<typeof setTimeout> | null = null;

  const game = computed<BullshitState | null>(() =>
    state.value && state.value.started ? state.value : null);
  const lobby = computed<LobbyView | null>(() =>
    state.value && !state.value.started ? state.value : null);

  const presence = useSeatPresence({
    presentSeats: () => (game.value?.players ?? []).map(p => Number(p.id)),
  });

  const session = new BullshitSession(webSocketService, {
    onEvent(event: BullshitSessionEvent) { applyEvent(event); },
  });

  function applyEvent(event: BullshitSessionEvent) {
    switch (event.type) {
      case 'state-update': state.value = event.state; break;
      case 'game-id-change': gameId.value = event.gameId; break;
      case 'seat-change': mySeat.value = event.seat; break;
      case 'event':
        if (event.eventType === 'CALL_BULLSHIT') {
          reveal.value = event.eventData as CallBullshitEventData;
          if (revealTimer !== null) clearTimeout(revealTimer);
          revealTimer = setTimeout(() => { reveal.value = null; revealTimer = null; }, REVEAL_HOLD_MS);
        } else {
          presence.applyPresenceEvent(event.eventType, event.eventData);
        }
        break;
    }
  }

  const me = computed(() => game.value?.players.find(p => p.id === String(mySeat.value)) ?? null);
  const isMyTurn = computed(() => me.value?.isCurrentPlayer ?? false);
  const canDiscard = computed(() => game.value?.availableActions.includes('DISCARD') ?? false);
  const canCallBullshit = computed(() => game.value?.availableActions.includes('CALL_BULLSHIT') ?? false);
  const iWon = computed(() =>
    game.value?.outcome.status === 'FINISHED' && game.value.outcome.winnerId === String(mySeat.value));
  const isHost = computed(() => mySeat.value === 0);
  const canStart = computed(() => lobby.value?.canStart ?? false);
  const phase = computed<'connecting' | 'lobby' | 'playing' | 'finished'>(() => {
    if (!state.value) return 'connecting';
    if (!state.value.started) return 'lobby';
    if (state.value.outcome.status === 'FINISHED') return 'finished';
    return 'playing';
  });

  // A reopened room lands back in the lobby; drop any presence from the finished game.
  watch(phase, (p) => { if (p === 'lobby') presence.reset(); });

  function playAgain() {
    presence.reset();
    return session.playAgain();
  }

  function toggleCard(card: Card) {
    const i = selectedCards.value.findIndex(c => c.name === card.name);
    if (i >= 0) selectedCards.value.splice(i, 1);
    else if (selectedCards.value.length < 4) selectedCards.value.push(card);
  }
  function clearSelection() { selectedCards.value = []; }

  return {
    state, game, lobby, gameId, mySeat, reveal, selectedCards,
    disconnections: presence.disconnections,
    liveDisconnections: presence.liveDisconnections,
    forfeitNotice: presence.forfeitNotice,
    isMyTurn, canDiscard, canCallBullshit, iWon, isHost, canStart, phase,
    applyEvent, toggleCard, clearSelection,
    create: (name?: string) => session.create(name),
    join: (id: string, name?: string) => session.join(id, name),
    restore: (id: string, seat: number, token: string) => session.restore(id, seat, token),
    hydrate: () => session.hydrate(),
    startGame: () => session.startGame(),
    discard: () => { session.discard(selectedCards.value); clearSelection(); },
    callBullshit: () => session.callBullshit(),
    playAgain,
  };
});
```

Note: `game`/`lobby` and `presence` are moved above `applyEvent` so the presence
getter can read `game`. `applyEvent` is a hoisted function declaration, so the
`session` callback referencing it remains valid.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd frontend && npx vitest run src/state/Bullshit.store.test.ts`
Expected: PASS (all existing + 5 new tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/state/Bullshit.store.ts frontend/src/state/Bullshit.store.test.ts
git commit -m "feat(frontend): wire Bullshit store to useSeatPresence, reset on rematch

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: Add disconnect state to `OpponentSeat`

**Files:**
- Modify: `frontend/src/components/bullshit/OpponentSeat.vue`
- Test: `frontend/src/components/bullshit/OpponentSeat.test.ts`

- [ ] **Step 1: Write the failing test**

Append inside the existing `describe('OpponentSeat', ...)` block in
`frontend/src/components/bullshit/OpponentSeat.test.ts`:

```ts
  it('shows the disconnect badge with countdown only when disconnected', () => {
    const connected = mount(OpponentSeat, { props: { label: 'Player 2', handCount: 4, active: false } });
    expect(connected.find('[data-test="seat-disconnect-badge"]').exists()).toBe(false);

    const disconnected = mount(OpponentSeat, {
      props: { label: 'Player 2', handCount: 4, active: false, disconnected: true, secondsRemaining: 30 },
    });
    const badge = disconnected.get('[data-test="seat-disconnect-badge"]');
    expect(badge.text()).toContain('30');
    expect(disconnected.get('.opponent-seat').classes()).toContain('opponent-seat--disconnected');
  });
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/components/bullshit/OpponentSeat.test.ts`
Expected: FAIL — badge not found / class absent.

- [ ] **Step 3: Update the component**

Replace the contents of `frontend/src/components/bullshit/OpponentSeat.vue` with:

```vue
<template>
  <div class="opponent-seat" :class="{ 'opponent-seat--disconnected': disconnected }">
    <span :class="['seat-label', { 'seat-label--active': active }]" data-test="seat-label">{{ label }}</span>
    <div class="seat-card">
      <PlayingCard :hidden="true" rank="10" suit="spade" />
      <div class="seat-chip" data-test="seat-count">
        <CardCounter :count="handCount" />
      </div>
      <SeatDisconnectBadge
        v-if="disconnected"
        class="seat-disconnect"
        :seconds-remaining="secondsRemaining ?? null" />
    </div>
  </div>
</template>

<script setup lang="ts">
import PlayingCard from '../PlayingCard.vue';
import CardCounter from '../CardCounter.vue';
import SeatDisconnectBadge from '../SeatDisconnectBadge.vue';

defineProps<{
  label: string;
  handCount: number;
  active: boolean;
  disconnected?: boolean;
  secondsRemaining?: number | null;
}>();
</script>

<style scoped>
.opponent-seat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.seat-card {
  position: relative;
}

/* Match the screen's fluid card sizing; height auto + intrinsic aspect ratio. */
.opponent-seat :deep(.playing_card) {
  width: var(--seat-card-w, 56px);
  height: auto;
  aspect-ratio: 167.575 / 243.1375;
}

.seat-chip {
  position: absolute;
  bottom: -6px;
  right: -10px;
}

/* Dim a dropped opponent's card; the badge sits centred over it. */
.opponent-seat--disconnected .seat-card :deep(.playing_card) {
  filter: grayscale(1) brightness(0.6);
}
.seat-disconnect {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 1;
}

.seat-label {
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.8);
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  padding: 3px 10px;
  white-space: nowrap;
}

/* Active-turn glow — same treatment as BatailleCorse's name tag. */
.seat-label--active {
  color: #ffffff;
  border-color: rgba(var(--accent-active-rgb), 0.9);
  box-shadow: 0 0 16px 3px rgba(var(--accent-active-rgb), 0.55);
  animation: seat-glow-pulse 1.8s ease-in-out infinite;
}

@keyframes seat-glow-pulse {
  0%, 100% { box-shadow: 0 0 12px 2px rgba(var(--accent-active-rgb), 0.40); }
  50%      { box-shadow: 0 0 22px 6px rgba(var(--accent-active-rgb), 0.70); }
}

@media (prefers-reduced-motion: reduce) {
  .seat-label--active { animation: none; }
}
</style>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/components/bullshit/OpponentSeat.test.ts`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/bullshit/OpponentSeat.vue frontend/src/components/bullshit/OpponentSeat.test.ts
git commit -m "feat(frontend): OpponentSeat shows a disconnect badge over a dropped seat

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 8: Wire `BullshitGameScreen` — countdown, badge props, forfeit banner

**Files:**
- Modify: `frontend/src/view/bullshit/BullshitGameScreen.vue`
- Test: `frontend/src/view/bullshit/BullshitGameScreen.test.ts`

- [ ] **Step 1: Write the failing tests**

Append inside the existing `describe('BullshitGameScreen', ...)` block in
`frontend/src/view/bullshit/BullshitGameScreen.test.ts`:

```ts
  describe('opponent presence', () => {
    beforeEach(() => vi.useFakeTimers({ now: 1_000_000 }));
    afterEach(() => vi.useRealTimers());

    it('shows a disconnect badge with countdown on a dropped opponent seat', () => {
      const store = useBullshitStore();
      store.applyEvent({ type: 'seat-change', seat: 0 });
      store.applyEvent({ type: 'state-update', state: playingState() });   // players 0 (me) + 1
      store.applyEvent({ type: 'event', eventType: 'OPPONENT_DISCONNECTED',
        eventData: { disconnectedSeat: 1, deadlineEpochMs: Date.now() + 30_000 }, message: '' });

      const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } });

      const badge = wrapper.get('[data-test="seat-disconnect-badge"]');
      expect(badge.text()).toContain('30');
    });

    it('shows a forfeit banner naming the forfeiting player', async () => {
      const store = useBullshitStore();
      store.applyEvent({ type: 'seat-change', seat: 0 });
      store.applyEvent({ type: 'state-update', state: playingState() });
      store.applyEvent({ type: 'event', eventType: 'FORFEIT', eventData: { loserSeat: 1 }, message: '' });

      const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } });

      expect(wrapper.get('[data-test="forfeit-banner"]').text()).toBe('Player 2 forfeited');
    });
  });
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && npx vitest run src/view/bullshit/BullshitGameScreen.test.ts`
Expected: FAIL — badge / banner selectors not found.

- [ ] **Step 3: Update the script block**

In `frontend/src/view/bullshit/BullshitGameScreen.vue`, update the imports and add the
countdown wiring. Change the import lines at the top of `<script setup>`:

Replace:
```ts
import { computed } from 'vue';
import { Button } from 'primevue';
import { useBullshitStore } from '../../state/Bullshit.store';
import { useBullshitBootstrap } from '../../composables/useBullshitBootstrap';
import PlayingCard from '../../components/PlayingCard.vue';
import CardCounter from '../../components/CardCounter.vue';
import EndGameOverlay from '../../components/EndGameOverlay.vue';
import OpponentSeat from '../../components/bullshit/OpponentSeat.vue';
import type Card from '../../model/Card';

const props = defineProps<{ gameId: string }>();
const store = useBullshitStore();
useBullshitBootstrap(props.gameId);
```

With:
```ts
import { computed, onBeforeUnmount } from 'vue';
import { Button } from 'primevue';
import { useBullshitStore } from '../../state/Bullshit.store';
import { useBullshitBootstrap } from '../../composables/useBullshitBootstrap';
import { useSeatDisconnectCountdown } from '../../composables/useSeatDisconnectCountdown';
import PlayingCard from '../../components/PlayingCard.vue';
import CardCounter from '../../components/CardCounter.vue';
import EndGameOverlay from '../../components/EndGameOverlay.vue';
import ForfeitBanner from '../../components/ForfeitBanner.vue';
import OpponentSeat from '../../components/bullshit/OpponentSeat.vue';
import type Card from '../../model/Card';

const props = defineProps<{ gameId: string }>();
const store = useBullshitStore();
useBullshitBootstrap(props.gameId);

const countdown = useSeatDisconnectCountdown({
  disconnections: () => store.liveDisconnections,
  isGameOver: () => store.phase === 'finished',
});
onBeforeUnmount(() => countdown.cancel());

function seatDisconnected(seat: number): boolean {
  return seat in store.liveDisconnections;
}
function seatSecondsRemaining(seat: number): number | null {
  const deadline = store.liveDisconnections[seat];
  return deadline == null ? null : countdown.secondsRemainingFor(deadline);
}
```

- [ ] **Step 4: Update the template — pass props to `OpponentSeat`**

In the same file, replace the `<OpponentSeat ... />` element inside the opponents
loop:

Replace:
```html
          <OpponentSeat
            :label="`Player ${Number(opp.id) + 1}`"
            :hand-count="opp.handCount"
            :active="opp.isCurrentPlayer" />
```

With:
```html
          <OpponentSeat
            :label="`Player ${Number(opp.id) + 1}`"
            :hand-count="opp.handCount"
            :active="opp.isCurrentPlayer"
            :disconnected="seatDisconnected(Number(opp.id))"
            :seconds-remaining="seatSecondsRemaining(Number(opp.id))" />
```

- [ ] **Step 5: Update the template — add the forfeit banner**

In the same file, add the forfeit banner as the first child inside the playing
`<template v-else>` block, immediately before `<div class="table-frame">`:

Replace:
```html
    <template v-else>
      <div class="table-frame">
```

With:
```html
    <template v-else>
      <Transition name="forfeit-fade">
        <ForfeitBanner
          v-if="store.forfeitNotice"
          class="forfeit-notice"
          :label="`Player ${store.forfeitNotice.seat + 1} forfeited`" />
      </Transition>
      <div class="table-frame">
```

- [ ] **Step 6: Update the styles — position the banner**

In the same file's `<style scoped>` block, add (e.g. just before the closing `</style>`):

```css
.forfeit-notice {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1800;
}
.forfeit-fade-enter-active,
.forfeit-fade-leave-active { transition: opacity 0.25s ease, transform 0.25s ease; }
.forfeit-fade-enter-from,
.forfeit-fade-leave-to { opacity: 0; transform: translate(-50%, -8px); }
```

Note: `.bullshit-screen` is `position: relative`'s containing block by default (it is
a flex column, not positioned). Add `position: relative;` to the existing
`.bullshit-screen` rule so the absolutely-positioned banner anchors to the screen:

Find the `.bullshit-screen {` rule and add `position: relative;` to it (alongside the
existing `display: flex;`).

- [ ] **Step 7: Run tests to verify they pass**

Run: `cd frontend && npx vitest run src/view/bullshit/BullshitGameScreen.test.ts`
Expected: PASS (all existing + 2 new tests).

- [ ] **Step 8: Commit**

```bash
git add frontend/src/view/bullshit/BullshitGameScreen.vue frontend/src/view/bullshit/BullshitGameScreen.test.ts
git commit -m "feat(frontend): show disconnect badges + forfeit banner on the Bullshit table

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 9: Full verification gate

- [ ] **Step 1: Run the full frontend test suite**

Run: `cd frontend && npm run test`
Expected: all suites PASS (no regressions in BatailleCorse or Bullshit).

- [ ] **Step 2: Run the build (the real gate)**

Run: `cd frontend && npm run build`
Expected: build succeeds with no type errors.

- [ ] **Step 3: Confirm no stray files / clean tree**

Run: `git status`
Expected: clean working tree (all changes committed). If any new file is untracked,
`git add` and amend the relevant commit.

---

## Self-Review (completed during planning)

- **Spec coverage:** Brick A (Task 2), Brick B (Task 3), Brick C (Task 4), Brick D
  (Task 5), shared model (Task 1), store delegation + reset (Task 6), `OpponentSeat`
  props/badge (Task 7), screen countdown/badge/banner + `onBeforeUnmount` (Task 8),
  build+test gate (Task 9). `liveDisconnections` stale-seat filtering: Task 2 + Task 6
  tests. All spec requirements mapped.
- **Type consistency:** `disconnections: Record<number, number>`,
  `forfeitNotice: { seat: number } | null`, `secondsRemainingFor(deadline): number`,
  `seconds-remaining` prop typed `number | null`, `SEAT_LIFECYCLE_EVENT` strings, and
  `FORFEIT_NOTICE_HOLD_MS` are used identically across Tasks 1–8.
- **No placeholders:** every code/edit step shows complete content.
- **Out of scope honored:** no backend/DTO/domain edits; BatailleCorse untouched
  (issue #71).

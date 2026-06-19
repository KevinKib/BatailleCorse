# Bullshit Table Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the flat Bullshit playing screen into a felt **table** the 2–6 players sit around — opponents surround a central pile well, my hand sits at the bottom edge.

**Architecture:** Frontend-only. A new `OpponentSeat.vue` renders one opponent (label + face-down card-back + poker-chip count + active-turn glow). `BullshitGameScreen.vue`'s `playing` branch becomes an oval felt frame: opponents are absolutely positioned on the oval perimeter via a computed angle-per-seat (skipping the bottom), a recessed pile well + claim badge sits in the center, and my selectable card row sits on a felt placemat at the bottom. No backend/store/DTO changes — same `store.game`/`store.reveal`. All visuals reuse the shared `--felt-*`/`--accent-*`/`--panel-*`/`--card-shadow` tokens from `App.vue` (no new tokens). BatailleCorse untouched.

**Tech Stack:** Vue 3 `<script setup>` + TS, Pinia, Vitest + `@vue/test-utils`. Frontend from `frontend/` (`node_modules` already installed): unit `npx vitest run <path>`, gate `npm run build`.

**Source spec:** `docs/superpowers/specs/2026-06-19-bullshit-table-layout-design.md`

---

## File Structure

**Frontend — created**
- `frontend/src/components/bullshit/OpponentSeat.vue` — one opponent around the table (label, card-back, chip, active glow).
- `frontend/src/components/bullshit/OpponentSeat.test.ts` — unit test for it.

**Frontend — modified**
- `frontend/src/view/bullshit/BullshitGameScreen.vue` — `playing` branch only: oval felt frame, opponent positioning, center well + claim badge + reveal, bottom hand placemat, my-turn glow.
- `frontend/src/view/bullshit/BullshitGameScreen.test.ts` — add table-layout assertions; keep existing tests green.

**Reused as-is:** `PlayingCard.vue` (face-down = non-empty `rank`/`suit` + `:hidden`), `CardCounter.vue` (poker chip), tokens in `App.vue`.

---

## Task 1: `OpponentSeat.vue` component

**Files:**
- Create: `frontend/src/components/bullshit/OpponentSeat.vue`
- Test: `frontend/src/components/bullshit/OpponentSeat.test.ts`

- [ ] **Step 1: Write the failing test.**

```ts
import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import OpponentSeat from './OpponentSeat.vue';

describe('OpponentSeat', () => {
  it('renders the label and hand-count chip', () => {
    const wrapper = mount(OpponentSeat, { props: { label: 'Player 2', handCount: 4, active: false } });
    expect(wrapper.text()).toContain('Player 2');
    expect(wrapper.get('[data-test="seat-count"]').text()).toContain('4');
  });

  it('applies the active class only when active', () => {
    const inactive = mount(OpponentSeat, { props: { label: 'Player 2', handCount: 4, active: false } });
    expect(inactive.get('[data-test="seat-label"]').classes()).not.toContain('seat-label--active');

    const activeSeat = mount(OpponentSeat, { props: { label: 'Player 2', handCount: 4, active: true } });
    expect(activeSeat.get('[data-test="seat-label"]').classes()).toContain('seat-label--active');
  });
});
```

- [ ] **Step 2: Run to verify it fails:** `cd frontend && npx vitest run src/components/bullshit/OpponentSeat.test.ts` → FAIL (cannot resolve `./OpponentSeat.vue`).

- [ ] **Step 3: Implement the component.**

```vue
<template>
  <div class="opponent-seat">
    <span :class="['seat-label', { 'seat-label--active': active }]" data-test="seat-label">{{ label }}</span>
    <div class="seat-card">
      <PlayingCard :hidden="true" rank="10" suit="spade" />
      <div class="seat-chip" data-test="seat-count">
        <CardCounter :count="handCount" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import PlayingCard from '../PlayingCard.vue';
import CardCounter from '../CardCounter.vue';

defineProps<{ label: string; handCount: number; active: boolean }>();
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

- [ ] **Step 4: Run to verify it passes:** `npx vitest run src/components/bullshit/OpponentSeat.test.ts` → PASS (both tests).

- [ ] **Step 5: Commit:**
```bash
cd "C:\Users\kevin\Documents\GitHub\IntelliJ\BatailleCorse\.claude\worktrees\sharp-leakey-22c0d5"
git add frontend/src/components/bullshit/OpponentSeat.vue frontend/src/components/bullshit/OpponentSeat.test.ts
git commit -m "feat(frontend): OpponentSeat component (label, card-back, chip, turn glow)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: Oval frame + opponents positioned around the table

Replaces the flat `.opponents` row. Adds the felt frame, the seat-positioning computed, and renders an `OpponentSeat` per opponent on the oval perimeter. The center/hand/actions markup stays as-is in this task (re-styled in Tasks 3–4).

**Files:**
- Modify: `frontend/src/view/bullshit/BullshitGameScreen.vue`
- Test: `frontend/src/view/bullshit/BullshitGameScreen.test.ts`

- [ ] **Step 1: Write the failing test.** Add to `BullshitGameScreen.test.ts` (reuse the file's `playingState`/`router`/pinia setup):

```ts
it('renders one opponent seat per opponent, positioned around the table', () => {
  const store = useBullshitStore();
  store.applyEvent({ type: 'seat-change', seat: 0 });
  store.applyEvent({ type: 'state-update', state: playingState({
    players: [
      { id: '0', handCount: 5, isCurrentPlayer: false },
      { id: '1', handCount: 4, isCurrentPlayer: true },
      { id: '2', handCount: 3, isCurrentPlayer: false },
      { id: '3', handCount: 2, isCurrentPlayer: false },
    ],
  }) });
  const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router] } });

  const seats = wrapper.findAllComponents(OpponentSeat);
  expect(seats).toHaveLength(3);                       // 4 players, minus me (seat 0)
  expect(seats.some(s => s.props('active') === true)).toBe(true);  // seat 1 is current
});
```

Add the import at the top of the test file (next to the existing imports):
```ts
import OpponentSeat from '../../components/bullshit/OpponentSeat.vue';
```

- [ ] **Step 2: Run to verify it fails:** `npx vitest run src/view/bullshit/BullshitGameScreen.test.ts` → FAIL (no `OpponentSeat` rendered).

- [ ] **Step 3: Implement.** In `BullshitGameScreen.vue`:

  **(a)** Add the import and the seat-position computed to the `<script setup>` block. Add `OpponentSeat` to the imports and replace the `opponents` computed with one that carries position + active, leaving the rest of the script intact:

```ts
import OpponentSeat from '../../components/bullshit/OpponentSeat.vue';
```

```ts
// Opponents are everyone but me, in seat order.
const opponents = computed(() =>
  (store.game?.players ?? []).filter(p => p.id !== String(store.mySeat)));

// Angles (degrees) around the table for K opponents, skipping the bottom (my zone).
// 0 = right, 90 = top, 180 = left. Single opp sits at top; pairs sit up on the
// top corners; 3+ spread evenly from left, over the top, to the right.
function seatAngles(count: number): number[] {
  if (count <= 0) return [];
  if (count === 1) return [90];
  if (count === 2) return [135, 45];
  return Array.from({ length: count }, (_, i) => 180 - i * (180 / (count - 1)));
}

// Map each opponent to a point on the table ellipse (percent of the frame).
const RX = 44; // horizontal radius (%)
const RY = 40; // vertical radius (%)
const seatPositions = computed(() => {
  const angles = seatAngles(opponents.value.length);
  return angles.map(deg => {
    const r = (deg * Math.PI) / 180;
    return { left: 50 + RX * Math.cos(r), top: 50 - RY * Math.sin(r) };
  });
});
```

  **(b)** Replace the `<div class="opponents"> … </div>` block (the flat row inside the `<template v-else>` playing branch) with the oval-positioned seats:

```html
      <div class="opponents-ring">
        <div
          v-for="(opp, i) in opponents"
          :key="opp.id"
          class="seat-slot"
          :style="{ left: seatPositions[i].left + '%', top: seatPositions[i].top + '%' }">
          <OpponentSeat
            :label="`Player ${Number(opp.id) + 1}`"
            :hand-count="opp.handCount"
            :active="opp.isCurrentPlayer" />
        </div>
      </div>
```

  **(c)** Turn the `playing` branch's root `<template v-else>` content into a positioned frame. Wrap the playing-branch markup in a `<div class="table-frame"> … </div>` (inside the existing `<template v-else>`), so the ring/center/hand all position relative to it. Add these styles to the `<style scoped>` block:

```css
.table-frame {
  position: relative;
  width: 100%;
  max-width: 900px;
  /* Oval play area; height tracks width so the ellipse math stays proportional. */
  aspect-ratio: 16 / 11;
  margin: 0 auto;
  border-radius: 50% / 42%;
  /* Felt: radial gradient + vignette, reusing the shared felt tokens. */
  background:
    radial-gradient(ellipse at 50% 46%, transparent 18%, rgba(0, 0, 0, 0.55) 100%),
    radial-gradient(ellipse at 50% 42%, var(--felt-center) 0%, var(--felt-mid) 52%, var(--felt-edge) 100%);
  border: 1px solid rgba(255, 255, 255, 0.06);
  box-shadow: inset 0 2px 30px rgba(0, 0, 0, 0.55), 0 10px 40px rgba(0, 0, 0, 0.45);
  isolation: isolate;
  /* Fluid card sizes consumed by seats and the center. */
  --seat-card-w: clamp(40px, 7vmin, 60px);
  --pile-card-w: clamp(60px, 13vmin, 104px);
}

.opponents-ring {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.seat-slot {
  position: absolute;
  transform: translate(-50%, -50%);
}

/* Pass the fluid seat-card width down into each seat. */
.seat-slot :deep(.opponent-seat) {
  --seat-card-w: clamp(40px, 7vmin, 60px);
}
```

  Leave the existing `.opponent`/`.opponents` CSS in place for now if other rules reference it — it will be removed in Task 4's cleanup; the new classes don't collide.

- [ ] **Step 4: Run to verify it passes:** `npx vitest run src/view/bullshit/BullshitGameScreen.test.ts` → PASS (new test green; existing tests still green).

- [ ] **Step 5: Commit:**
```bash
git add frontend/src/view/bullshit/BullshitGameScreen.vue frontend/src/view/bullshit/BullshitGameScreen.test.ts
git commit -m "feat(frontend): position Bullshit opponents around an oval felt table

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Center pile well + claim badge + reveal

Restyles the center of the table: claim badge, recessed pile well with a face-down card-back + chip, last-play caption, and the reveal repositioned over the well.

**Files:**
- Modify: `frontend/src/view/bullshit/BullshitGameScreen.vue`
- Test: `frontend/src/view/bullshit/BullshitGameScreen.test.ts`

- [ ] **Step 1: Write the failing test.** Add to `BullshitGameScreen.test.ts`:

```ts
it('shows the claim badge and a last-play caption only when a claim is on the table', () => {
  const store = useBullshitStore();
  store.applyEvent({ type: 'seat-change', seat: 0 });
  store.applyEvent({ type: 'state-update', state: playingState({
    currentTarget: { label: 'QUEEN' },
    table: { state: 'CLAIM', claimantId: '1', count: 3 },
    discardPileSize: 7,
  }) });
  const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router] } });

  expect(wrapper.get('[data-test="claim-badge"]').text()).toContain('QUEEN');
  expect(wrapper.get('[data-test="last-play"]').text()).toContain('Player 2');  // claimantId 1 -> "Player 2"
  expect(wrapper.get('[data-test="last-play"]').text()).toContain('3');
});

it('hides the last-play caption when there is no claim', () => {
  const store = useBullshitStore();
  store.applyEvent({ type: 'seat-change', seat: 0 });
  store.applyEvent({ type: 'state-update', state: playingState({ table: { state: 'NO_CLAIM' } }) });
  const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router] } });

  expect(wrapper.find('[data-test="last-play"]').exists()).toBe(false);
});
```

- [ ] **Step 2: Run to verify it fails:** `npx vitest run src/view/bullshit/BullshitGameScreen.test.ts` → FAIL (`[data-test="claim-badge"]` not found).

- [ ] **Step 3: Implement.** In `BullshitGameScreen.vue`, replace the existing `<div class="table"> … </div>` block (the `Claim:` / `last-claim` / `pile` text) and the existing `<div v-if="store.reveal" …>` block with a single centered well:

```html
      <div class="table-center">
        <div class="claim-badge" data-test="claim-badge">
          Claim: <strong>{{ store.game?.currentTarget.label }}</strong>
        </div>

        <div class="pile-well">
          <PlayingCard :hidden="true" rank="10" suit="spade" />
          <div class="pile-chip">
            <CardCounter :count="store.game?.discardPileSize ?? 0" />
          </div>

          <div v-if="store.reveal" data-test="reveal" class="reveal">
            <div class="revealed-cards">
              <PlayingCard v-for="(c, i) in store.reveal.revealedCards" :key="i" :rank="c.rank" :suit="c.suit" />
            </div>
            <p class="reveal-caption">
              Player {{ store.reveal.callerSeat + 1 }} called bullshit on Player {{ store.reveal.claimantSeat + 1 }} —
              claim was {{ store.reveal.truthful ? 'TRUE' : 'FALSE' }} — Player {{ store.reveal.pickerSeat + 1 }} takes the pile
            </p>
          </div>
        </div>

        <p v-if="store.game?.table.state === 'CLAIM'" class="last-play" data-test="last-play">
          Player {{ Number(store.game.table.claimantId) + 1 }} played {{ store.game.table.count }} card(s) face-down
        </p>
      </div>
```

  Add to `<style scoped>`:

```css
.table-center {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  text-align: center;
}

.claim-badge {
  font-size: 0.8rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.85);
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(var(--accent-active-rgb), 0.4);
  border-radius: 999px;
  padding: 4px 14px;
}
.claim-badge strong { color: var(--gold); }

.pile-well {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: clamp(8px, 2vmin, 16px) clamp(12px, 3vmin, 22px);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 14px;
  background: radial-gradient(ellipse at 50% 45%, rgba(0, 0, 0, 0.28) 0%, rgba(0, 0, 0, 0.5) 100%);
  box-shadow: inset 0 3px 22px rgba(0, 0, 0, 0.65), inset 0 0 0 1px rgba(0, 0, 0, 0.35);
}
.pile-well :deep(.playing_card) {
  width: var(--pile-card-w);
  height: auto;
  aspect-ratio: 167.575 / 243.1375;
}
.pile-chip {
  position: absolute;
  bottom: 4px;
  right: -10px;
}

.reveal {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: max-content;
  max-width: 60vw;
}
.revealed-cards { display: flex; gap: 0.25rem; justify-content: center; }
.revealed-cards :deep(.playing_card) {
  width: var(--seat-card-w);
  height: auto;
  aspect-ratio: 167.575 / 243.1375;
}
.reveal-caption {
  margin: 0;
  font-size: 0.78rem;
  color: rgba(255, 255, 255, 0.9);
  background: rgba(0, 0, 0, 0.7);
  border-radius: 8px;
  padding: 4px 8px;
}

.last-play {
  margin: 0;
  font-size: 0.78rem;
  color: rgba(255, 255, 255, 0.7);
}
```

  Note: the test mounts `playingState` whose default `reveal` is unset, so the reveal block is absent unless a `CALL_BULLSHIT` event fired — the existing "shows the reveal panel after a CALL_BULLSHIT event" test still finds `[data-test="reveal"]`.

- [ ] **Step 4: Run to verify it passes:** `npx vitest run src/view/bullshit/BullshitGameScreen.test.ts` → PASS (new + existing, including the reveal test).

- [ ] **Step 5: Commit:**
```bash
git add frontend/src/view/bullshit/BullshitGameScreen.vue frontend/src/view/bullshit/BullshitGameScreen.test.ts
git commit -m "feat(frontend): center pile well + claim badge + reveal on the Bullshit table

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: My zone (bottom placemat), my-turn glow, cleanup + gate

Sits my hand on a felt placemat at the bottom edge, glows my name when it's my turn, removes the now-dead flat-row CSS, and runs the full gate.

**Files:**
- Modify: `frontend/src/view/bullshit/BullshitGameScreen.vue`

- [ ] **Step 1: Implement the bottom zone.** In the `playing` branch, the hand + actions already exist (`.hand`, `.actions`). Wrap them in a bottom placemat with a "you" name tag that glows on your turn. Replace the existing `<div class="hand"> … </div>` and `<div class="actions"> … </div>` with:

```html
      <div class="my-zone">
        <span :class="['my-tag', { 'my-tag--active': store.isMyTurn }]">You</span>
        <div class="hand">
          <button
            v-for="(card, i) in store.game?.myHand ?? []"
            :key="card.name"
            :data-test="`hand-card-${i}`"
            class="hand-card"
            :class="{ selected: isSelected(card) }"
            type="button"
            @click="store.toggleCard(card)">
            <PlayingCard :rank="card.rank" :suit="card.suit" />
          </button>
        </div>
        <div class="actions">
          <button
            data-test="discard"
            type="button"
            :disabled="!store.isMyTurn || store.selectedCards.length === 0"
            @click="store.discard()">
            Discard as {{ store.game?.currentTarget.label }}
          </button>
          <button
            data-test="call"
            type="button"
            :disabled="!store.canCallBullshit"
            @click="store.callBullshit()">
            Call Bullshit
          </button>
        </div>
      </div>
```

  The `.my-zone` is a sibling of `.table-frame` inside the playing-branch `<template v-else>` (it sits below the table, not on the oval). Confirm the structure inside `<template v-else>` is now:
  `<div class="table-frame"> <div class="opponents-ring">…</div> <div class="table-center">…</div> </div>` followed by `<div class="my-zone">…</div>`.

- [ ] **Step 2: Add styles + remove dead CSS.** In `<style scoped>`: remove the now-unused `.opponents`, `.opponent`, `.opponent.active`, `.table`, `.claim`, `.last-claim`, `.pile`, `.reveal` (old, replaced by Task 3's `.reveal`), and `.revealed-cards` (old) rules — keep only the new rules added in Tasks 2–3. Add:

```css
.my-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
  width: 100%;
  max-width: 900px;
  padding: 12px 0 calc(8px + env(safe-area-inset-bottom, 0px));
  background: linear-gradient(to top, rgba(0, 0, 0, 0.30) 0%, rgba(0, 0, 0, 0.04) 100%);
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 14px 14px 0 0;
}

.my-tag {
  font-size: 0.8rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.8);
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  padding: 4px 14px;
}
.my-tag--active {
  color: #ffffff;
  border-color: rgba(var(--accent-active-rgb), 0.9);
  box-shadow: 0 0 16px 3px rgba(var(--accent-active-rgb), 0.55);
  animation: seat-glow-pulse 1.8s ease-in-out infinite;
}
@media (prefers-reduced-motion: reduce) {
  .my-tag--active { animation: none; }
}
```

  (`seat-glow-pulse` is defined in `OpponentSeat.vue`'s scoped styles; redefine it here too since scoped styles don't cross components — add the same `@keyframes seat-glow-pulse { 0%,100%{box-shadow:0 0 12px 2px rgba(var(--accent-active-rgb),0.40);} 50%{box-shadow:0 0 22px 6px rgba(var(--accent-active-rgb),0.70);} }` block to this file's `<style scoped>`.)

  Keep the existing `.hand`, `.hand-card`, `.hand-card.selected`, `.actions` rules (they still apply). Ensure `.hand-card` cards use a fluid width:
```css
.hand :deep(.playing_card) {
  width: clamp(48px, 9vmin, 76px);
  height: auto;
  aspect-ratio: 167.575 / 243.1375;
}
```

- [ ] **Step 3: Run the screen suite:** `npx vitest run src/view/bullshit/BullshitGameScreen.test.ts` → PASS (all, including discard enable/disable and reveal).

- [ ] **Step 4: Full frontend gate:**
  - `cd frontend && npx vitest run` → all suites pass.
  - `cd frontend && npm run build` → succeeds (type-check gate).

- [ ] **Step 5: Commit:**
```bash
git add frontend/src/view/bullshit/BullshitGameScreen.vue
git commit -m "feat(frontend): seat my hand on a felt placemat with my-turn glow; drop flat-row CSS

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Final verification

- [ ] Frontend suite green: `cd frontend && npx vitest run`.
- [ ] Build green: `cd frontend && npm run build`.
- [ ] Visual smoke (optional, dev server): 2/3/6-player games render opponents around the oval, current player's seat glows, center shows claim badge + pile chip, reveal overlays the well, my hand sits on the bottom placemat.

---

## Notes for the executor

- **Frontend only.** Do not touch the backend, store, DTOs, the `lobby`/`finished` branches, or BatailleCorse.
- **Preserve every `data-test` hook** used by existing tests: `lobby`, `player-count`, `start`, `start-hint`, `end`, `discard`, `call`, `reveal`, `hand-card-{i}`. New hooks added: `seat-label`, `seat-count`, `claim-badge`, `last-play`.
- **Face-down cards:** `PlayingCard` only renders (v-show) when `rank` and `suit` are non-empty, and shows the back when `:hidden` is true — so a card-back needs BOTH a dummy `rank`/`suit` AND `:hidden="true"` (as BatailleCorse's `GameScreen.vue` does).
- **No new design tokens.** Reuse `--felt-*`, `--accent-active-rgb`, `--gold`, `--card-shadow` from `App.vue`.
- **`prefers-reduced-motion`:** the turn-glow animations must be disabled under it (both in `OpponentSeat.vue` and the screen's `.my-tag--active`).
- Player labels are 1-based (`seat id + 1`), matching today's lobby ("Player 1") and the existing screen.

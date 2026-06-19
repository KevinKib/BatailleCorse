# Bullshit Reveal Animation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Animate the call-Bullshit reveal — the face-down claimed cards flip over the centre well one after another, then a TRUTHFUL/BLUFF verdict badge appears; the reveal holds for a fixed window then fades.

**Architecture:** Frontend only. `Bullshit.store.ts` switches the reveal from "cleared by the next event" to a **timed hold** (auto-clear timer). `BullshitGameScreen.vue` renders each revealed card as a 3D flip (two stacked faces) with a per-card stagger, adds a verdict badge driven by `reveal.truthful`, and wraps the reveal in a `<Transition>` for fade-out. No backend/DTO/event changes. Reuses shared `--accent-positive-rgb`/`--accent-negative-rgb` tokens; honours `prefers-reduced-motion`.

**Tech Stack:** Vue 3 `<script setup>` + TS, Pinia, Vitest + `@vue/test-utils`. Run from `frontend/` (`node_modules` installed): unit `npx vitest run <path>`, gate `npm run build`.

**Source spec:** `docs/superpowers/specs/2026-06-19-bullshit-reveal-animation-design.md`

---

## File Structure

**Frontend — modified**
- `frontend/src/state/Bullshit.store.ts` — reveal becomes a timed hold; export `REVEAL_HOLD_MS`.
- `frontend/src/state/Bullshit.store.test.ts` — timer-based reveal tests.
- `frontend/src/view/bullshit/BullshitGameScreen.vue` — flip + verdict + transition (reveal block only).
- `frontend/src/view/bullshit/BullshitGameScreen.test.ts` — verdict badge + faces assertions.

---

## Task 1: Reveal timed hold (store)

**Files:**
- Modify: `frontend/src/state/Bullshit.store.ts`
- Test: `frontend/src/state/Bullshit.store.test.ts`

- [x] **Step 1: Write the failing tests.** In `Bullshit.store.test.ts`, add an import for the constant and three tests (reuse the existing pinia `beforeEach`):

```ts
import { useBullshitStore, REVEAL_HOLD_MS } from './Bullshit.store';
```

```ts
describe('reveal timed hold', () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); });

  it('clears the reveal after the hold window', () => {
    const store = useBullshitStore();
    const reveal = { callerSeat: 1, claimantSeat: 0, truthful: false, pickerSeat: 0, revealedCards: [] };
    store.applyEvent({ type: 'event', eventType: 'CALL_BULLSHIT', eventData: reveal, message: 'm' });
    expect(store.reveal).toEqual(reveal);

    vi.advanceTimersByTime(REVEAL_HOLD_MS);
    expect(store.reveal).toBeNull();
  });

  it('does not clear the reveal early when another event arrives during the hold', () => {
    const store = useBullshitStore();
    const reveal = { callerSeat: 1, claimantSeat: 0, truthful: true, pickerSeat: 1, revealedCards: [] };
    store.applyEvent({ type: 'event', eventType: 'CALL_BULLSHIT', eventData: reveal, message: 'm' });

    vi.advanceTimersByTime(REVEAL_HOLD_MS - 1);
    store.applyEvent({ type: 'event', eventType: 'DISCARD', eventData: {}, message: 'm' });
    expect(store.reveal).toEqual(reveal);   // still showing — the timer owns dismissal

    vi.advanceTimersByTime(1);
    expect(store.reveal).toBeNull();
  });
});
```

The existing `records a CALL_BULLSHIT reveal` test stays as-is (it asserts the reveal is set; it does not advance timers).

- [x] **Step 2: Run to verify it fails:** `cd frontend && npx vitest run src/state/Bullshit.store.test.ts` → FAIL (`REVEAL_HOLD_MS` is not exported; reveal still clears on the DISCARD event).

- [x] **Step 3: Implement.** In `Bullshit.store.ts`:

  (a) Add the exported constant at module top level, just after the imports (before `export const useBullshitStore`):

```ts
export const REVEAL_HOLD_MS = 3000;
```

  (b) Inside the `defineStore` setup, declare a per-store timer handle next to the refs (after `const selectedCards = ref<Card[]>([]);`):

```ts
  let revealTimer: ReturnType<typeof setTimeout> | null = null;
```

  (c) Replace the `'event'` case in `applyEvent`:

```ts
      case 'event':
        if (event.eventType === 'CALL_BULLSHIT') reveal.value = event.eventData as CallBullshitEventData;
        else reveal.value = null;
        break;
```

  with a timed hold (set + restart timer; other events no longer clear it):

```ts
      case 'event':
        if (event.eventType === 'CALL_BULLSHIT') {
          reveal.value = event.eventData as CallBullshitEventData;
          if (revealTimer !== null) clearTimeout(revealTimer);
          revealTimer = setTimeout(() => { reveal.value = null; revealTimer = null; }, REVEAL_HOLD_MS);
        }
        break;
```

- [x] **Step 4: Run to verify it passes:** `npx vitest run src/state/Bullshit.store.test.ts` → PASS (new tests + the existing reveal/toggle tests).

- [x] **Step 5: Commit:**
```bash
cd "C:\Users\kevin\Documents\GitHub\IntelliJ\BatailleCorse\.claude\worktrees\sharp-leakey-22c0d5"
git add frontend/src/state/Bullshit.store.ts frontend/src/state/Bullshit.store.test.ts
git commit -m "feat(frontend): hold the Bullshit reveal for a fixed window (timer-based)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: Flip + verdict animation (screen)

**Files:**
- Modify: `frontend/src/view/bullshit/BullshitGameScreen.vue`
- Test: `frontend/src/view/bullshit/BullshitGameScreen.test.ts`

- [x] **Step 1: Write the failing test.** In `BullshitGameScreen.test.ts`, update/extend the reveal test. Add a test that drives a CALL_BULLSHIT reveal and asserts the verdict badge text and that a flip front+back face render per card. Reuse the file's `playingState`/`router`/pinia setup:

```ts
it('shows a BLUFF verdict and a flip face per revealed card on a false claim', () => {
  const store = useBullshitStore();
  store.applyEvent({ type: 'seat-change', seat: 0 });
  store.applyEvent({ type: 'state-update', state: playingState() });
  store.applyEvent({ type: 'event', eventType: 'CALL_BULLSHIT', message: '',
    eventData: { callerSeat: 1, claimantSeat: 0, truthful: false, pickerSeat: 0,
      revealedCards: [{ rank: 'KING', suit: 'SPADE', name: 'SPADE_KING' }, { rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' }] } });
  const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router] } });

  expect(wrapper.get('[data-test="verdict"]').text()).toBe('BLUFF');
  expect(wrapper.findAll('.flip-card')).toHaveLength(2);
  expect(wrapper.findAll('.flip-front').length).toBe(2);
  expect(wrapper.findAll('.flip-back').length).toBe(2);
});

it('shows a TRUTHFUL verdict on a true claim', () => {
  const store = useBullshitStore();
  store.applyEvent({ type: 'seat-change', seat: 0 });
  store.applyEvent({ type: 'state-update', state: playingState() });
  store.applyEvent({ type: 'event', eventType: 'CALL_BULLSHIT', message: '',
    eventData: { callerSeat: 1, claimantSeat: 0, truthful: true, pickerSeat: 1, revealedCards: [{ rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' }] } });
  const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router] } });

  expect(wrapper.get('[data-test="verdict"]').text()).toBe('TRUTHFUL');
});
```

The existing "shows the reveal panel after a CALL_BULLSHIT event" test (asserts `[data-test="reveal"]` exists) stays green.

- [x] **Step 2: Run to verify it fails:** `npx vitest run src/view/bullshit/BullshitGameScreen.test.ts` → FAIL (no `[data-test="verdict"]` / no `.flip-card`).

- [x] **Step 3: Implement.** In `BullshitGameScreen.vue`, replace the current reveal block:

```html
          <div v-if="store.reveal" data-test="reveal" class="reveal">
            <div class="revealed-cards">
              <PlayingCard v-for="(c, i) in store.reveal.revealedCards" :key="i" :rank="c.rank" :suit="c.suit" />
            </div>
            <p class="reveal-caption">
              Player {{ store.reveal.callerSeat + 1 }} called bullshit on Player {{ store.reveal.claimantSeat + 1 }} —
              claim was {{ store.reveal.truthful ? 'TRUE' : 'FALSE' }} — Player {{ store.reveal.pickerSeat + 1 }} takes the pile
            </p>
          </div>
```

  with the flip + verdict + transition version:

```html
          <Transition name="reveal-fade">
            <div v-if="store.reveal" data-test="reveal" class="reveal"
                 :style="{ '--n': store.reveal.revealedCards.length }">
              <div class="revealed-cards">
                <div v-for="(c, i) in store.reveal.revealedCards" :key="i" class="flip-card" :style="{ '--i': i }">
                  <div class="flip-inner">
                    <div class="flip-face flip-back"><PlayingCard :hidden="true" rank="10" suit="spade" /></div>
                    <div class="flip-face flip-front"><PlayingCard :rank="c.rank" :suit="c.suit" /></div>
                  </div>
                </div>
              </div>
              <div class="verdict" data-test="verdict"
                   :class="store.reveal.truthful ? 'verdict--truthful' : 'verdict--bluff'">
                {{ store.reveal.truthful ? 'TRUTHFUL' : 'BLUFF' }}
              </div>
              <p class="reveal-caption">
                Player {{ store.reveal.callerSeat + 1 }} called bullshit on Player {{ store.reveal.claimantSeat + 1 }} —
                Player {{ store.reveal.pickerSeat + 1 }} takes the pile
              </p>
            </div>
          </Transition>
```

  Then update the styles. Replace the existing `.reveal` and `.revealed-cards` rules:

```css
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
```

  with the flip + verdict styles (note `.reveal` now centres via `inset`/flex so `transform` is free for the leave transition):

```css
.reveal {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  pointer-events: none;
}

.revealed-cards { display: flex; gap: 0.3rem; justify-content: center; perspective: 800px; }

.flip-card {
  width: var(--seat-card-w);
  aspect-ratio: 167.575 / 243.1375;
}
.flip-inner {
  position: relative;
  width: 100%;
  height: 100%;
  transform-style: preserve-3d;
  transform: rotateY(180deg);
  animation: card-flip 520ms ease-out forwards;
  animation-delay: calc(var(--i) * 120ms);
}
.flip-face {
  position: absolute;
  inset: 0;
  backface-visibility: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
.flip-face :deep(.playing_card) {
  width: 100%;
  height: auto;
  aspect-ratio: 167.575 / 243.1375;
}
.flip-front { transform: rotateY(0deg); }
.flip-back { transform: rotateY(180deg); }

@keyframes card-flip {
  from { transform: rotateY(180deg); }
  to   { transform: rotateY(0deg); }
}

.verdict {
  font-weight: 800;
  letter-spacing: 0.12em;
  font-size: 0.95rem;
  color: #fff;
  padding: 4px 16px;
  border-radius: 999px;
  opacity: 0;
  /* Appear as the last card finishes flipping: (n-1) stagger + flip duration. */
  animation: verdict-in 360ms ease-out forwards;
  animation-delay: calc((var(--n) - 1) * 120ms + 520ms);
}
.verdict--truthful {
  background: rgba(var(--accent-positive-rgb), 0.25);
  border: 1px solid rgba(var(--accent-positive-rgb), 0.8);
  box-shadow: 0 0 18px 2px rgba(var(--accent-positive-rgb), 0.5);
}
.verdict--bluff {
  background: rgba(var(--accent-negative-rgb), 0.25);
  border: 1px solid rgba(var(--accent-negative-rgb), 0.8);
  box-shadow: 0 0 18px 2px rgba(var(--accent-negative-rgb), 0.5);
}
@keyframes verdict-in {
  from { opacity: 0; transform: scale(0.8); }
  to   { opacity: 1; transform: scale(1); }
}

.reveal-fade-enter-active { transition: opacity 0.2s ease; }
.reveal-fade-leave-active { transition: opacity 0.35s ease, transform 0.35s ease; }
.reveal-fade-enter-from { opacity: 0; }
.reveal-fade-leave-to { opacity: 0; transform: scale(0.92); }

@media (prefers-reduced-motion: reduce) {
  .flip-inner { animation: none; transform: rotateY(0deg); }
  .verdict { animation: none; opacity: 1; }
}
```

  Keep the existing `.reveal-caption` rule as-is.

- [x] **Step 4: Run to verify it passes:** `npx vitest run src/view/bullshit/BullshitGameScreen.test.ts` → PASS (new verdict/face tests + existing reveal/discard/lobby/end tests).

- [x] **Step 5: Frontend gate:**
  - `cd frontend && npx vitest run` → all suites pass.
  - `cd frontend && npm run build` → succeeds (type-check gate).

- [x] **Step 6: Commit:**
```bash
git add frontend/src/view/bullshit/BullshitGameScreen.vue frontend/src/view/bullshit/BullshitGameScreen.test.ts
git commit -m "feat(frontend): flip the revealed cards + TRUTHFUL/BLUFF verdict on call-Bullshit

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Final verification

- [x] Frontend suite green: `cd frontend && npx vitest run`.
- [x] Build green: `cd frontend && npm run build`.
- [x] Visual smoke (optional, dev server): call Bullshit → cards flip in sequence over the well, verdict badge appears (green TRUTHFUL / red BLUFF), reveal fades after ~3s while the board updates behind it.

---

## Notes for the executor

- **Frontend only.** Do not touch the backend, DTOs, events, the lobby/finished branches, or BatailleCorse.
- **Preserve `data-test` hooks:** `reveal` stays; `verdict` is added. Other hooks (`discard`, `call`, `hand-card-{i}`, `claim-badge`, `last-play`, seat hooks) are untouched.
- **Face-down card:** `PlayingCard` shows the back only with `:hidden="true"` AND a non-empty `rank`/`suit` (hence `rank="10" suit="spade"` on the back face).
- **`--seat-card-w`** is provided by `.table-frame` (set in #67); the flip cards inherit it.
- **`prefers-reduced-motion`** must disable both the flip and the verdict animation (faces and badge show statically).
- `.reveal` now centres via `inset: 0` + flex (no base `transform`), so the leave transition uses a bare `scale(0.92)` — do **not** reintroduce a `translate(...)` there, or the block will jump during fade-out.

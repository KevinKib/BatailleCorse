# Turn Indicator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make "is it my turn to Send?" visually unmistakable on the game screen via a glowing active-player name tag plus a self-only "YOUR TURN" caption.

**Architecture:** Turn state is derived in the `BatailleCorse` model (`isTurnOf`), surfaced through `GameScreen.vue` computeds, and rendered as layered CSS cues: a green glow/pulse on whichever player's name tag is active (scales to N players), a `YOUR TURN` caption pill above the local player's tag (shown only on the local player's turn), and a pulse on the Send button. Cues are suppressed while waiting for an opponent or after game over, and animations are dropped under `prefers-reduced-motion`.

**Tech Stack:** Vue 3 (`<script setup>` + Composition API), TypeScript, Pinia, PrimeVue, Vitest (model unit tests), Cypress (e2e).

---

## Setup (once, before Task 1)

This is a git worktree; it has no `node_modules`. Install dependencies before running any test or build:

```bash
cd frontend
npm install
```

## File Structure

- `frontend/src/model/BatailleCorse.ts` — add `isTurnOf(playerIndex)` (turn-state logic in the model).
- `frontend/src/model/fixtures/index.ts` — add a `currentPlayer` override to `buildGame` so turn states can be tested.
- `frontend/src/model/BatailleCorse.test.ts` — unit tests for `isTurnOf`.
- `frontend/src/view/alpha/GameScreen.vue` — computeds, template cues, CSS.
- `frontend/cypress/specs/turn-indicator.cy.ts` — e2e assertion that the caption shows on the local player's turn.

---

### Task 1: `isTurnOf` model method + fixture support

**Files:**
- Modify: `frontend/src/model/fixtures/index.ts` (buildGame, lines 45-60)
- Modify: `frontend/src/model/BatailleCorse.ts` (add method after `isWinnerAt`, lines 21-23)
- Test: `frontend/src/model/BatailleCorse.test.ts`

- [ ] **Step 1: Add a `currentPlayer` override to the `buildGame` fixture**

The current `buildGame` hardcodes `currentPlayer = players[0]`, which makes it impossible to test a non-first seat being active. Replace the `buildGame` function in `frontend/src/model/fixtures/index.ts` with:

```ts
export function buildGame(overrides: Partial<{
  currentPlayer: Player;
  pile: Pile;
  players: Player[];
  winner: { id: string } | null;
}> = {}): BatailleCorse {
  const players = overrides.players ?? [
    buildPlayer({ id: '0' }),
    buildPlayer({ id: '1' }),
  ];
  return new BatailleCorse(
    overrides.currentPlayer ?? players[0],
    overrides.pile ?? buildPile(),
    players,
    overrides.winner ?? null,
  );
}
```

- [ ] **Step 2: Write the failing tests**

Append this block to `frontend/src/model/BatailleCorse.test.ts`:

```ts
describe('BatailleCorse turn queries', () => {
  it('givenCurrentPlayerAtIndex_thenIsTurnOfIsTrue', () => {
    const players = [buildPlayer({ id: 'a' }), buildPlayer({ id: 'b' })];
    const game = buildGame({ players, currentPlayer: players[0] });
    expect(game.isTurnOf(0)).toBe(true);
  });

  it('givenOtherPlayerAtIndex_thenIsTurnOfIsFalse', () => {
    const players = [buildPlayer({ id: 'a' }), buildPlayer({ id: 'b' })];
    const game = buildGame({ players, currentPlayer: players[0] });
    expect(game.isTurnOf(1)).toBe(false);
  });

  it('givenCurrentPlayerIsSecondSeat_thenIsTurnOfTracksThatSeat', () => {
    const players = [buildPlayer({ id: 'a' }), buildPlayer({ id: 'b' })];
    const game = buildGame({ players, currentPlayer: players[1] });
    expect(game.isTurnOf(0)).toBe(false);
    expect(game.isTurnOf(1)).toBe(true);
  });

  it('givenIndexOutOfRange_thenIsTurnOfIsFalse', () => {
    const game = buildGame();
    expect(game.isTurnOf(5)).toBe(false);
  });
});
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd frontend && npx vitest run src/model/BatailleCorse.test.ts`
Expected: FAIL — `game.isTurnOf is not a function`.

- [ ] **Step 4: Implement `isTurnOf`**

In `frontend/src/model/BatailleCorse.ts`, add this method immediately after the `isWinnerAt` method (after line 23, before `fromJSON`):

```ts
  isTurnOf(playerIndex: number): boolean {
    return this.currentPlayer.id === this.players[playerIndex]?.id;
  }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd frontend && npx vitest run src/model/BatailleCorse.test.ts`
Expected: PASS — all 4 new tests plus the existing suites green.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/model/BatailleCorse.ts frontend/src/model/BatailleCorse.test.ts frontend/src/model/fixtures/index.ts
git commit -m "feat: add isTurnOf turn-state query to BatailleCorse model"
```

---

### Task 2: Render the turn cues in GameScreen

**Files:**
- Modify: `frontend/src/view/alpha/GameScreen.vue` (template, script computeds, CSS)

- [ ] **Step 1: Add turn computeds and the caption label constant**

In `frontend/src/view/alpha/GameScreen.vue`, immediately after the `useEndScreen(...)` block (after line 279, where `showEndOverlay` is destructured), add:

```ts
// --- Turn indicator ---
// "Whose turn" is conveyed by the glowing name tag (scales to N players);
// the caption is a self-only cue answering "is it MY turn".
const YOUR_TURN_LABEL = 'YOUR TURN';

const isMyTurn = computed(() => batailleCorse.value?.isTurnOf(myPlayerIndex.value) ?? false);
const isOpponentTurn = computed(() => batailleCorse.value?.isTurnOf(opponentIndex.value) ?? false);

// Suppress turn cues while an overlay owns the screen (waiting / game over).
const showTurnCues = computed(() => !isWaiting.value && !showEndOverlay.value);
const showMyTurn = computed(() => showTurnCues.value && isMyTurn.value);
const showOpponentTurn = computed(() => showTurnCues.value && isOpponentTurn.value);
```

- [ ] **Step 2: Add the active class to the opponent name tag**

In the template, replace the opponent tag (line 7):

```html
        <h1 class="player_tag">{{ opponentLabel }}</h1>
```

with:

```html
        <h1 :class="['player_tag', { 'player_tag--active': showOpponentTurn }]">{{ opponentLabel }}</h1>
```

- [ ] **Step 3: Add the caption and active class to the local player's zone**

In the template, replace the opening of the bottom `middle_side` and the player tag (lines 49-50):

```html
      <div class="middle_side">
        <h1 class="player_tag">{{ myName || settingsStore.playerName || 'You' }}</h1>
```

with:

```html
      <div class="middle_side">
        <Transition name="turn-fade">
          <div v-if="showMyTurn" class="turn-caption" data-cy="turn-indicator">
            <span class="turn-caption__dot"></span>{{ YOUR_TURN_LABEL }}
          </div>
        </Transition>
        <h1 :class="['player_tag', { 'player_tag--active': showMyTurn }]">{{ myName || settingsStore.playerName || 'You' }}</h1>
```

- [ ] **Step 4: Pulse the Send button on your turn**

In the template, replace the Send button (lines 64-65):

```html
          <Button class="action_button" icon="pi pi-arrow-up" severity="success" label="Send" rounded
            @click="send(myPlayerIndex)" :disabled="isButtonDisabled(myPlayerIndex, 'send')"/>
```

with:

```html
          <Button :class="['action_button', { 'action_button--my-turn': showMyTurn }]" icon="pi pi-arrow-up" severity="success" label="Send" rounded
            @click="send(myPlayerIndex)" :disabled="isButtonDisabled(myPlayerIndex, 'send')"/>
```

- [ ] **Step 5: Add the turn-indicator CSS**

In the `<style scoped>` block, add the following just before the closing `</style>` (after the `.end-card--defeat .end-title` / existing rules, and before the existing reduced-motion media query):

```css
/* --- Turn indicator --- */
.player_tag--active {
  color: #ffffff;
  border-color: rgba(74, 222, 128, 0.9);
  box-shadow: 0 0 16px 3px rgba(74, 222, 128, 0.55);
  animation: turn-glow-pulse 1.8s ease-in-out infinite;
}

@keyframes turn-glow-pulse {
  0%, 100% { box-shadow: 0 0 12px 2px rgba(74, 222, 128, 0.40); }
  50%      { box-shadow: 0 0 22px 6px rgba(74, 222, 128, 0.70); }
}

.turn-caption {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: fit-content;
  margin: 0 auto 6px;
  font-family: "Gabarito", sans-serif;
  font-size: 0.82rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  color: #4ade80;
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.7);
}

.turn-caption__dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #4ade80;
  box-shadow: 0 0 8px 2px rgba(74, 222, 128, 0.8);
  animation: turn-glow-pulse 1.8s ease-in-out infinite;
}

.action_button--my-turn {
  animation: send-pulse 1.6s ease-in-out infinite;
}

@keyframes send-pulse {
  0%, 100% { transform: scale(1); }
  50%      { transform: scale(1.06); }
}

.turn-fade-enter-active,
.turn-fade-leave-active {
  transition: opacity 0.25s ease;
}

.turn-fade-enter-from,
.turn-fade-leave-to {
  opacity: 0;
}
```

- [ ] **Step 6: Extend the reduced-motion media query**

In the `<style scoped>` block, replace the existing reduced-motion rule (lines 675-677):

```css
@media (prefers-reduced-motion: reduce) {
  .end-trophy { animation: none; }
}
```

with:

```css
@media (prefers-reduced-motion: reduce) {
  .end-trophy,
  .player_tag--active,
  .turn-caption__dot,
  .action_button--my-turn { animation: none; }
}
```

- [ ] **Step 7: Build to verify the component compiles**

Run: `cd frontend && npm run build`
Expected: build succeeds with no TypeScript or template errors. (`npm run build` runs `vue-tsc`/vite and is the real type+compile gate — a bare type-check script does not exist here.)

- [ ] **Step 8: Commit**

```bash
git add frontend/src/view/alpha/GameScreen.vue
git commit -m "feat: add explicit turn indicator (glow + YOUR TURN caption) to game screen"
```

---

### Task 3: End-to-end assertion for the caption

This runs against the full stack (frontend dev server + backend), matching the existing `send-card.cy.ts`. It verifies the `YOUR TURN` caption is visible to player 0 when the game starts (player 0 is the current player on a fresh game).

**Files:**
- Create: `frontend/cypress/specs/turn-indicator.cy.ts`

- [ ] **Step 1: Write the e2e spec**

Create `frontend/cypress/specs/turn-indicator.cy.ts`:

```ts
describe('Turn indicator', () => {
  it('shows the YOUR TURN caption to the player whose turn it is', () => {
    cy.createGame();

    // Wait until the game has hydrated (Send becomes enabled on player 0's turn).
    cy.contains('button', 'Send').should('not.be.disabled');

    // On a fresh game it is player 0's turn, so the self-only caption is visible.
    cy.get('[data-cy="turn-indicator"]', { timeout: 10000 })
      .should('be.visible')
      .and('contain.text', 'YOUR TURN');
  });
});
```

- [ ] **Step 2: Run the e2e spec (requires the app + backend running)**

Start the backend and the frontend dev server (per the project's normal run setup), then run:

Run: `cd frontend && npx cypress run --spec cypress/specs/turn-indicator.cy.ts`
Expected: PASS — the `turn-indicator` element is found, visible, and contains "YOUR TURN".

- [ ] **Step 3: Commit**

```bash
git add frontend/cypress/specs/turn-indicator.cy.ts
git commit -m "test: e2e assertion for YOUR TURN caption visibility"
```

---

## Manual verification checklist (for the user)

After Task 2, run the app and confirm:
- On your turn: your name tag glows green and pulses, the `YOUR TURN` caption sits above your tag, and the Send button pulses.
- On the opponent's turn: their (top) name tag glows/pulses; your caption disappears.
- While waiting for an opponent / after game over: no turn cues show.
- With OS "reduce motion" enabled: colors and caption still show, but nothing animates.

> Note from design review: the `YOUR TURN` caption may read as too much in practice — flagged to evaluate during manual testing. If so, the caption can be toned down (smaller / no dot / lower opacity) or removed, leaving the glowing tag + Send pulse as the cue.

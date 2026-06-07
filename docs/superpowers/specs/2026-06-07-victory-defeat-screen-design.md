# Victory & Defeat Screens — Design

**Date:** 2026-06-07
**Status:** Approved (pending spec review)

## Goal

When a game ends, show the player a distinct **victory** screen if they won and a
**defeat** screen if they lost, with a single way back to the home page.

## Scope

- Frontend only. No backend changes — the game state already reports the winner.
- Applies to both solo (vs. Computer) and multiplayer games; the logic is identical.
- Single action on the screen: **Back to home**. No rematch, share, or board-dismiss
  (explicitly out of scope — YAGNI).

## Result Detection (logic lives in the model)

The game state already carries everything needed:

- `BatailleCorse.winner` — `{ id } | null`. Non-null means the game is over.
- `BatailleCorse.players[myPlayerIndex].id` — identifies the local player.

Per project convention, deducible game logic lives in the model, not the component.
`BatailleCorse` gains three methods:

```ts
isOver(): boolean {
  return this.winner !== null;
}

isWinner(playerId: string): boolean {
  return this.winner?.id === playerId;
}

// Seat-aware convenience: resolves the player's id from their seat index,
// so the component never has to derive an id from the players array.
isWinnerAt(playerIndex: number): boolean {
  return this.isWinner(this.players[playerIndex]?.id);
}
```

The component holds only pure delegating computeds — no rules, no array lookups:

```ts
const isGameOver = computed(() => batailleCorse.value?.isOver() ?? false);
const didIWin = computed(() => batailleCorse.value?.isWinnerAt(myPlayerIndex.value) ?? false);
```

No win-condition or player-lookup logic is encoded in the `.vue` file — it only forwards
`myPlayerIndex` and reads `isGameOver` / `didIWin`.

## Overlay UI

A full-screen overlay reusing the structure and styling conventions of the existing
`waiting-overlay` in `GameScreen.vue` (centered card, dimmed + blurred board behind,
`z-index` above the board, below nothing else).

```
VICTORY                              DEFEAT
┌──────────────────────────┐         ┌──────────────────────────┐
│        🏆                 │         │                          │
│      VICTORY              │         │      DEFEAT              │
│  You beat <Opponent>!     │         │  <Opponent> won.         │
│                           │         │                          │
│    [ Back to home ]       │         │    [ Back to home ]      │
└──────────────────────────┘         └──────────────────────────┘
  gold accent, trophy bounce          muted/somber, no flourish
```

- **Victory:** gold accent (`#f5c842`, already in the palette) plus a pure-CSS flourish
  — a brief trophy bounce / glow pulse via `@keyframes`. No new dependencies.
- **Defeat:** muted gray/red palette, no flourish.
- Opponent label reuses the existing `opponentLabel` computed
  (`Computer (<difficulty>)` in solo, opponent name in multiplayer).
- Action: a single **Back to home** `Button` wrapped in `RouterLink to="/"`, reusing the
  existing PrimeVue `Button` component.
- A `data-cy` hook on the overlay and on the victory-only flourish element for E2E tests.

## Timing — overlay appears after the final animation, plus a short delay

The only moves that can end a game are **GRAB** and **successful SLAP** (a SEND never
ends it). Both already `await animation.animatePileToWinner(...)` and then call
`notifyAnimationComplete()` in their watchers in `GameScreen.vue`.

To keep this orchestration logic out of the `.vue` (and unit-testable), it lives in a
small composable `useEndScreen`, following the existing `composables/` pattern
(`useCardAnimation`, `useHotkeys`):

```ts
// useEndScreen(isOver: () => boolean, delayMs = END_SCREEN_DELAY_MS)
export function useEndScreen(isOver: () => boolean, delayMs = END_SCREEN_DELAY_MS) {
  const showEndOverlay = ref(false);

  // Live win: called after the final card animation resolves; waits a short beat.
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

`END_SCREEN_DELAY_MS` is a named constant (~600ms) in the composable.

Wiring in `GameScreen.vue`:

- Both terminal watchers (GRAB, successful SLAP) call `revealAfterAnimation()` right
  after their `await animation.animatePileToWinner(...)` resolves.
- `onMounted`, immediately after `batailleCorseStore.hydrate(...)`, calls
  `revealImmediatelyIfOver()` to cover reload-into-finished-game.
- The overlay renders on `showEndOverlay`.

## Testing

**Model unit tests** (`BatailleCorse.test.ts`, using existing fixtures/builders, no
Mockito):

- `isOver()` returns false when `winner` is null, true when set.
- `isWinner(id)` returns true only for the winning player's id; false for the opponent's
  id and when there is no winner.
- `isWinnerAt(index)` returns true for the winning seat, false for the losing seat, and
  false when there is no winner.

**Composable unit tests** (`useEndScreen.test.ts`, vitest + fake timers):

- `revealAfterAnimation()` does nothing when `isOver()` is false.
- `revealAfterAnimation()` flips `showEndOverlay` to true only after `delayMs` elapses
  (assert still false before the timer, true after `vi.advanceTimersByTime`).
- `revealImmediatelyIfOver()` flips `showEndOverlay` synchronously when over, no-op when
  not over.

**E2E note:** A full Cypress victory/defeat flow requires driving a real game to
completion deterministically, which needs a backend test-seeding hook that does not yet
exist. End-to-end coverage of the finished-game screen is therefore **deferred** (out of
scope for this plan); the model + composable unit tests cover the deducible logic. The
overlay markup itself is verified manually against the `vite build` output.

## Files Touched

- `frontend/src/model/BatailleCorse.ts` — add `isOver()` / `isWinner()` / `isWinnerAt()`.
- `frontend/src/model/BatailleCorse.test.ts` — cover the three methods.
- `frontend/src/composables/useEndScreen.ts` — `showEndOverlay` + reveal timing.
- `frontend/src/composables/useEndScreen.test.ts` — timer-based unit tests.
- `frontend/src/view/alpha/GameScreen.vue` — overlay markup + styling, `didIWin` /
  `isGameOver` computeds, wire `useEndScreen` into the two terminal watchers and onMounted.

## Out of Scope

- Rematch / play-again.
- Share-result.
- Dismissing the overlay to inspect the final board.
- Any backend changes.

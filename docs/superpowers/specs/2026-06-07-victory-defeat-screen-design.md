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
`BatailleCorse` gains two methods:

```ts
isOver(): boolean {
  return this.winner !== null;
}

isWinner(playerId: string): boolean {
  return this.winner?.id === playerId;
}
```

The component holds only thin computeds that delegate to the model:

```ts
const isGameOver = computed(() => batailleCorse.value?.isOver() ?? false);
const didIWin = computed(() => {
  const game = batailleCorse.value;
  if (!game) return false;
  return game.isWinner(game.players[myPlayerIndex.value].id);
});
```

No win-condition rules are encoded in the `.vue` file — it only reads `isGameOver`
and `didIWin`.

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

Immediately after that await resolves, each watcher calls a shared local helper:

```ts
function revealEndScreenIfOver() {
  if (!isGameOver.value) return;
  setTimeout(() => { showEndOverlay.value = true; }, END_SCREEN_DELAY_MS); // ~600ms
}
```

`showEndOverlay` is a local `ref(false)` in the component; the overlay renders on it.
`END_SCREEN_DELAY_MS` is a named constant.

### Edge case — reload after the game is already over

`onMounted` hydrates state from `/api/game/{id}`, which includes `winner`, but with no
animation in flight. On mount, if `isGameOver` is already true, set
`showEndOverlay = true` immediately (no delay). Both paths converge on the same ref.

## Testing

**Model unit tests** (`BatailleCorse.test.ts`, using existing fixtures/builders, no
Mockito):

- `isOver()` returns false when `winner` is null, true when set.
- `isWinner(id)` returns true only for the winning player's id; false for the opponent's
  id and when there is no winner.

**Component / E2E** (Cypress-style, matching existing `data-cy` conventions):

- Overlay hidden while the game is in progress.
- Winner sees the **victory** variant (with the flourish element present).
- Loser sees the **defeat** variant (no flourish element).
- **Back to home** navigates to `/`.

## Files Touched

- `frontend/src/model/BatailleCorse.ts` — add `isOver()` / `isWinner()`.
- `frontend/src/model/BatailleCorse.test.ts` — cover the two methods.
- `frontend/src/view/alpha/GameScreen.vue` — overlay markup, styling, `showEndOverlay`
  ref, `revealEndScreenIfOver()`, mount-time check, delay constant.
- E2E spec — victory/defeat/navigation coverage.

## Out of Scope

- Rematch / play-again.
- Share-result.
- Dismissing the overlay to inspect the final board.
- Any backend changes.

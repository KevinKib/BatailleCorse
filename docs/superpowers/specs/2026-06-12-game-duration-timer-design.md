# Game Duration Timer — Design

**Date:** 2026-06-12
**Status:** Approved

## Goal

Show a cosmetic timer on the game screen that displays how long the current game
has been running. It is a flavour element, not an authoritative or shared clock.

## Decisions

- **Source:** Frontend-only. No backend, DTO, or rehydration changes. The cost of
  an authoritative server-stamped start time is not justified for a cosmetic value.
- **Lifecycle:** Starts counting when the game becomes playable; **freezes** at game
  over, displaying the final duration. Resets on page refresh (accepted tradeoff).
- **Placement:** Small top-corner HUD chip (**top-left** of the board), visible
  during play. Top-right is taken by the rules toggle panel. The end overlay covers
  the board at game over, so the frozen value sits behind it — no special
  end-screen rendering.
- **Format:** `mm:ss`, rolling to `h:mm:ss` once elapsed time passes one hour.

## Active definition

The timer is *active* (running) when all are true:

- game state is loaded (`batailleCorse` is non-null),
- not in the "waiting for opponent" state (`isWaiting` is false),
- the game is not over (`isGameOver` is false).

The start epoch is recorded the first time the timer becomes active and is **not**
reset thereafter (e.g. it keeps running across a transient disconnect overlay). On
game over, the elapsed value is frozen and the interval is cleared.

## Structure

Follows the project's Vue conventions: timing logic in a composable, a dumb
presentational component for display, the view only composes.

### 1. `frontend/src/composables/useGameDuration.ts`

Owns all timing.

- **Inputs:** `isActive: () => boolean`, `isOver: () => boolean` (both passed as
  getters so they track the view's reactive computeds).
- **Internals:** records `startEpochMs` on first activation; a `now` ref ticks every
  1000 ms via `setInterval` while active; on `isOver` becoming true, freeze the
  elapsed value and clear the interval. Clears the interval on unmount
  (`onBeforeUnmount`/scope dispose).
- **Output:** a single `formattedDuration: ComputedRef<string>` (`mm:ss` /
  `h:mm:ss`). Before activation it reads `00:00`.

Mirrors the existing `now`/`setInterval` pattern already used for the disconnect
countdown in `GameScreen.vue`, and the testing style of `useEndScreen.test.ts`.

### 2. `frontend/src/components/GameTimer.vue`

Pure presentation. One prop `time: string`. Renders a small HUD pill styled to
match the existing `.player_tag` felt-chip aesthetic. No logic, no store access.

### 3. `frontend/src/view/alpha/GameScreen.vue`

- Instantiate `useGameDuration` wired to the existing `isWaiting`/`isGameOver`
  computeds (active = state loaded && !waiting && !over).
- Render `<GameTimer :time="formattedDuration" />` pinned to the **top-left** corner
  of `.gamescreen` (already `position: relative; isolation: isolate`). Sits below
  overlay z-indexes so end/waiting overlays cover it.
- The active flag reuses the existing `isInProgress` computed (single source of
  truth) rather than a duplicate predicate.

## Testing

- **`useGameDuration.test.ts`** (Vitest, fake timers): starts at `00:00`; advances
  with elapsed active time; freezes on `isOver`; ignores time while inactive;
  formats `mm:ss` and `h:mm:ss` correctly; clears its interval on dispose.
- Component `GameTimer.vue` is trivial display — covered via the composable tests;
  no separate component test unless a render concern emerges.

## Out of scope (YAGNI)

- Backend start-time tracking, DTO fields, GET `/api/game/{id}` rehydration.
- Persistence across refresh.
- Pausing during disconnect / waiting beyond the active definition above.
- Per-player or per-turn timing.

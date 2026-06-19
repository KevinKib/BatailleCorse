# Bullshit Table Layout — Design

**Date:** 2026-06-19
**Status:** Approved (brainstorm)
**Slice:** Bullshit screen polish — N-seat table layout

## Goal

Replace the flat, plain-flexbox Bullshit playing screen with a felt **table** that
the 2–6 players sit *around*: opponents surround a central pile well, the local
player's hand sits at the bottom edge. Brings the Bullshit screen up to the
felt-table visual language already used by BatailleCorse's `GameScreen.vue`.

## Scope

- **Frontend only.** Touches the `playing` branch of
  `frontend/src/view/bullshit/BullshitGameScreen.vue` and adds one component
  `frontend/src/components/bullshit/OpponentSeat.vue`.
- **No backend / store / DTO changes.** The screen consumes the same
  `store.game` it does today: `players[] ({id, handCount, isCurrentPlayer})`,
  `myHand`, `currentTarget`, `table`, `discardPileSize`, plus `store.reveal`.
- **Lobby and finished branches are untouched** (they render by `store.phase`).
- **BatailleCorse is untouched.** No shared "table" component is introduced —
  BC keeps its own felt shell, consistent with the presentation-split decision.
- All visuals reuse the shared design tokens defined in `App.vue`
  (`--felt-center/-mid/-edge`, `--felt-noise`, `--accent-active-rgb`,
  `--panel-*`, `--card-shadow`). **No new design tokens** — single source of truth.

## Layout

A central **oval felt table** fills the play area (radial felt gradient + static
grain `::before`, mirroring `GameScreen.vue`'s shell). Card and table dimensions
derive from `clamp()`/`vmin` so the whole board scales with the viewport.

### Opponents surround the table

The 1–5 opponents (everyone except me) are absolutely positioned on the oval's
perimeter via a computed angle per seat. My seat owns the bottom edge; opponents
fill the ring from the left side, across the top, to the right side. Distribution
by opponent count:

| Opponents | Positions |
|---|---|
| 1 | top-center |
| 2 | top-left, top-right |
| 3 | left, top, right |
| 4 | left, top-left, top-right, right |
| 5 | left, upper-left, top, upper-right, right |

Each opponent's `left%`/`top%` is computed by mapping its index to a point on an
ellipse over an angle range that **skips the bottom** (the local player's zone).
The ring radius derives from `clamp()`/`vmin`; on narrow screens it shrinks so the
side seats pull inward toward the top and never overflow or collide.

### Table center

A recessed felt pile **well** (reused from `GameScreen.vue`'s `pile_slot`
treatment: inset shadow, faint-suit empty state) sits in the middle of the oval:

- **Face-down card-back** + poker-chip `CardCounter` showing `discardPileSize`.
- **Claim badge** above the well showing `currentTarget.label` (e.g. "Claim: ACE").
- **Last-play caption**: when `table.state === 'CLAIM'`, reads
  `Player {claimantId + 1} played {count} card(s)`; hidden when `NO_CLAIM`
  (well shows its empty card-spot).
- **Reveal**: when `store.reveal` is set (after a `CALL_BULLSHIT`), the revealed
  cards render face-up over the well with the existing
  "Player X called bullshit on Player Y … Player Z takes the pile" caption. Same
  data and markup as today, repositioned into the center; clears on the next
  state-driven event exactly as now.

### My zone (bottom edge)

Today's selectable, wrapping card row — full `PlayingCard`s, selected card lifts
(`translateY(-12px)`) — sat on a felt **placemat** pinned to the bottom. Discard /
Call Bullshit buttons sit below it, with their existing disabled logic
(`isMyTurn`/selection for Discard, `canCallBullshit` for Call). Hand **fanning** is
explicitly deferred to a later slice; this slice keeps the flat row.

## Components

### `OpponentSeat.vue` (new)

A single opponent around the table. Props: `label: string`, `handCount: number`,
`active: boolean`. Renders:

- the label tag (`Player N`, where N = seat id + 1, matching today's convention),
- a face-down `PlayingCard`,
- a `CardCounter` chip bound to `handCount`,
- when `active`, the BatailleCorse turn treatment: the label tag glows with the
  `--accent-active-rgb` `turn-glow-pulse` animation (respecting
  `prefers-reduced-motion`).

A seat at `handCount === 0` still renders normally; the finished overlay takes
over via `store.phase`, so no mid-game empty-seat state is needed.

### `BullshitGameScreen.vue` (modified, `playing` branch only)

- Wraps the play area in the oval felt frame.
- Positions each opponent seat via a computed
  `seatPositions(opponentCount) -> {left, top}[]` (ellipse angles skipping the
  bottom).
- Highlights my own placemat/name with the same glow when it's my turn.
- Houses the center well (claim badge, well, chip, last-play caption, reveal) and
  the bottom hand placemat + actions.

## Active-turn highlight

Consistent across the table: any seat (opponent or mine) whose player
`isCurrentPlayer` gets the glowing name-tag pulse, reusing BC's `turn-glow-pulse`
keyframes and `--accent-active-rgb`.

## Responsiveness

Seat size, pile-card size, and oval radius all derive from `clamp()`/`vmin`. The
table scales with the viewport; the ring radius shrinks on narrow screens so the
5-opponent case stays on-screen without collisions.

## Testing

- **`OpponentSeat.vue`** unit test (Vitest + `@vue/test-utils`): renders label and
  hand-count chip; applies the active class only when `active` is true.
- **`BullshitGameScreen`** (extend existing suite): one `OpponentSeat` per
  opponent (4-player state → 3 seats); current player's seat carries the active
  class; claim badge shows `currentTarget.label`; last-play caption shows only for
  `table.state === 'CLAIM'`. All existing tests stay green (they key off stable
  `data-test` hooks, preserved).
- **Gate:** `npx vitest run` fully green + `npm run build` clean (type-check gate).

## Out of scope (later polish slices)

Hand fanning; disconnect/forfeit overlays; reveal *animation* (this slice only
repositions the static reveal); turn timer; rules panel; hand sorting (#60).

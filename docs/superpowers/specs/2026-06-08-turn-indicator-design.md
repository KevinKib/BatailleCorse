# Turn Indicator — Design

**Date:** 2026-06-08
**Status:** Implemented; revised after first playtest (see Revision below)

## Revision (2026-06-08, post-implementation playtest)

The original design used a **persistent** "YOUR TURN" caption above the local
player's name tag. In playtesting this read as too much for a fast-reaction game —
a constant banner competes for the attention the player needs on the central pile.

**Change:** the persistent caption is replaced by a **one-time onboarding hint**.
The permanent turn cue is now the **glowing name tag + the Send-button glow pulse**
alone. Because a glow is a *learned* signal (a first-time player has no way to know
green-glow = "my turn"), the "YOUR TURN" label still appears — but only during the
**local player's first turn of the game**. It is tied to turn state, not a timer: it
shows when that first turn begins and disappears the instant the player plays (even if
that's within a second), then never returns. It floats above the name tag (absolutely
positioned) so it never shifts layout. This teaches the association once, then keeps
the steady state uncluttered.

The sections below describe the original approach; the layered-cue principle and the
model/derivation still hold. Only the caption's lifecycle changed: persistent →
one-time hint (`showFirstTurnHint`, `data-cy="turn-hint"`).

**Grabbable-pile suppression:** the cues are also hidden while the pile is grabbable
(`pile.grabbable`, i.e. the pile is full and in the ~1.5s auto-grab window). In that
state no player can SEND — the backend only offers SEND when the pile is not full —
so a glowing name would be misleading. This is folded into `showTurnCues`, the single
gate every cue (both glows + the hint) already reads.

## Problem

A returning user reported that the cue telling a player whether it's their turn to
play is not visible enough. Today the **only** signal that it's your turn is whether
the **Send** button is enabled or greyed out — there is no explicit turn indicator on
`GameScreen.vue`. We need to make "is it my turn?" visually obvious.

## Turn semantics

Only **Send** is turn-gated. **Slap** is always available to both players at any time,
regardless of whose turn it is. Therefore the indicator must communicate *who may Send
next* without implying the local player is fully locked out (Slap stays live).

## Approach: layered, self-anchored cue (4-player ready)

Card/board games rarely rely on a single cue; they layer redundant signals (color +
motion + text). We adopt that here, structured so it scales to a future 2–4 player game
with no rework.

Two independent responsibilities:

1. **"Whose turn is it?" → the glowing name tag.** Each player's name tag glows when it
   is their turn. The glow simply moves to whoever is active. This scales to any number
   of players unchanged.
2. **"Is it *my* turn?" → a self-only caption.** A `YOUR TURN` caption appears in the
   local player's bottom zone **only when it is the local player's turn**, and hides
   otherwise. We deliberately do **not** show an "opponent's turn" caption: with 3+
   opponents it is ambiguous *which* opponent, and the glowing tag already answers
   "who."

### The cues, when it is your turn

- **Name tag glow** — your bottom name tag gets a green ring/glow (`player_tag--active`).
- **Motion** — a gentle "breathing" pulse on that glow, plus a pulse on the **Send**
  button when it is actually sendable.
- **Caption** — a small pill reading `● YOUR TURN`, positioned directly **above** your
  name tag (fixed, predictable, at eye level near your cards and buttons). Visible only
  on your turn.

### When it is not your turn

- Your caption is hidden.
- The active player's name tag (e.g. the opponent on top) carries the glow/pulse, so the
  signal stays anchored to whoever is up.

Single active-glow style is sufficient (green). No separate per-side color split.

## Layout

Bottom (local player) zone:

```
        ┌─────────────────┐
        │   ● YOUR TURN    │   ← caption pill (only visible on your turn)
        └─────────────────┘
        ┌─────────────────┐
        │    KEVIN (you)   │   ← name tag, glows green on your turn
        └─────────────────┘
            [ card stack ]
         [ Send ]   [ Slap ]    ← Send also pulses on your turn
```

Opponent (top) zone: the existing name tag gains the same `player_tag--active`
glow/pulse when it is their turn. No caption on the opponent side.

## Turn-state derivation (logic in the model)

Per the codebase convention of keeping deducible game logic in model classes, add a
method to `BatailleCorse` alongside the existing `isWinnerAt`:

```ts
isTurnOf(playerIndex: number): boolean {
  return this.currentPlayer.id === this.players[playerIndex]?.id;
}
```

In `GameScreen.vue`, computeds delegate to it (no logic in the component):

- `isMyTurn` = `batailleCorse.isTurnOf(myPlayerIndex)`
- `isOpponentTurn` = `batailleCorse.isTurnOf(opponentIndex)`

The indicator is driven off **state** (`currentPlayer`), not per-action events —
consistent with how the existing end-screen/winner logic is driven off state here.

## Edge cases

- **Waiting for opponent** (`waiting`) and **game over** (`showEndOverlay`): suppress the
  turn indicator entirely; those overlays own the screen.
- **Reduced motion** (`prefers-reduced-motion: reduce`): drop the pulse animations, keep
  the static glow color + caption text. Matches the existing trophy `@media` rule.
- **Solo mode**: unchanged — the AI is the opponent; the turn flips the same way.

## Conventions & testing

- Turn label string (`YOUR TURN`) defined once (single source of truth), not inlined.
- `data-cy="turn-indicator"` on the caption (with state) for e2e assertions, matching
  existing `data-cy` usage on the screen.
- **Model unit test** for `isTurnOf` (vitest, via builders/fixtures — no Mockito on
  domain classes), covering: current player at index, not current player, missing index.

## Out of scope

- Turn timers / countdowns.
- Actual 3–4 player support (this design only ensures the indicator pattern scales to it;
  it does not add multi-player seating/layout).

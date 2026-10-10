# Bullshit Reveal Animation — Design

**Date:** 2026-06-19
**Status:** Approved (brainstorm)
**Slice:** Bullshit screen polish — call-Bullshit reveal animation

## Goal

Animate the call-Bullshit reveal instead of popping a static block: the face-down
claimed cards flip over the centre well one after another, then a verdict beat
shows whether the claim was truthful or a bluff. Builds on the centre-well reveal
shipped in #67.

## Scope

- **Frontend only.** `frontend/src/view/bullshit/BullshitGameScreen.vue` (the reveal
  block over the centre well) and `frontend/src/state/Bullshit.store.ts` (reveal
  timing). No backend / DTO / event changes — same `store.reveal`
  (`CallBullshitEventData`: `callerSeat`, `claimantSeat`, `truthful`, `pickerSeat`,
  `revealedCards`).
- Reuses the shared `--accent-positive-rgb` / `--accent-negative-rgb` tokens. No new
  design tokens.
- Lobby/finished branches and BatailleCorse untouched.

## Timing (store)

Today `reveal` is set on a `CALL_BULLSHIT` event and cleared by the *next* event.
Switch to a **timed hold**:

- On a `CALL_BULLSHIT` event: set `reveal` and start a `REVEAL_HOLD_MS` auto-clear
  timer (single named constant, ~3000ms). Clear any existing timer first so a new
  reveal restarts cleanly and the timer can't double-fire.
- Other events **no longer clear `reveal`** — the timer owns dismissal, so the board
  can update behind the reveal without cutting the animation short.
- Trade-off (intended): the reveal lingers ~`REVEAL_HOLD_MS` even if the next player
  acts instantly, guaranteeing the animation completes.

## Animation (screen)

- **Card flip:** each revealed card is a `.flip-card` with two stacked faces — back
  (`PlayingCard :hidden`) and front (`PlayingCard` showing `rank`/`suit`) —
  `transform-style: preserve-3d`, `backface-visibility: hidden`. The inner element
  animates `rotateY(180deg) → 0` over ~500ms, **staggered** per card via
  `animation-delay: calc(var(--i) * 120ms)`, so cards turn one after another.
- **Verdict beat:** after the flips, a badge reading **TRUTHFUL** (green,
  `--accent-positive-rgb`) or **BLUFF** (red, `--accent-negative-rgb`) driven by
  `reveal.truthful`, with a brief glow. The existing caption
  ("Player X called bullshit … takes the pile") stays.
- **Enter/leave:** wrap the reveal in a Vue `<Transition>` so it fades/scales out
  when the timer clears it.
- **Reduced motion:** under `prefers-reduced-motion: reduce`, skip the flip and glow
  — faces render directly with the static verdict badge.

## Testing

- **Store** (`Bullshit.store.test.ts`, Vitest fake timers): a `CALL_BULLSHIT` event
  sets `reveal`; it clears after `REVEAL_HOLD_MS`; a non-reveal event arriving during
  the hold does **not** clear it early.
- **Screen** (`BullshitGameScreen.test.ts`): the existing "reveal panel exists after
  a CALL_BULLSHIT event" test stays green; add that the verdict badge reads TRUTHFUL
  vs BLUFF off `truthful`, and that both the back and front faces render per revealed
  card. CSS motion itself is not unit-tested; `npm run build` is the gate.

## Out of scope

Pile-sliding-to-the-picker (cross-table motion) — deferred. Other polish slices
(disconnect/forfeit overlay, turn timer, rules panel, hand sort #60).

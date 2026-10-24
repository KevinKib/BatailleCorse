# Shared card animation module

One module, `frontend/src/animation/`, that any table plugs into in about 30 lines: flights, rings, arrows, a burst
queue, a speed setting (Normal / Fast / Instant), reduced motion, and texts that follow the picture. Spec:
`docs/specs/2026-10-21-shared-card-animation-design.md`.

## Layout

```
animation/
  index.ts                 the public API: games import this barrel, nothing else
  boundaries.test.ts       the import rules below, checked
  example.test.ts          the example of "Plugging a table in", run on a fake table
  core/                    pure TypeScript: no Vue, no DOM, time passed in
    movement.ts            TableMovement<A>, MotionPlan<A>, eventSpanMs, scaleMovements, scalePlan
    tempo.ts               LegTempo, QueueBounds, leg(byOpponent, tempo)
    tempoQueue.ts          createTempoQueue, Admission
    speed.ts               AnimationSpeed, nextAnimationSpeed, speedFactor
    reducedMotion.ts       prefersReducedMotion, canAnimate
    freshEntries.ts        the new entries of a newest-first log, oldest first
    heldValues.ts          values kept on screen until a time (judge a click on what is shown)
    displayQueue.ts        the states a table shows, held back until their picture is over
  dom/                     measuring places on screen (no Vue)
    rect.ts                MotionRect, MotionTarget, CARD_RATIO, visibleRect, cardAt, cardIn
    anchors.ts             defineAnchors
  vue/                     the layer and the composables
    CardMotionLayer.vue    the fixed overlay that flies copies, draws rings and arrows
    AnimationSpeedButton.vue, useAnimationSpeed.ts
    useTableMotion.ts      the orchestrator
    useDisplayedState.ts   texts follow the picture, buttons follow the state
    useInFlightCounts.ts, useSeatBubbles.ts, useTableSettled.ts
```

`core/` never imports `vue/` or `dom/` (nor Vue); `dom/` never imports Vue or `vue/`; nothing in `animation/` imports a
game (`view/`, `state/`, `model/<game>/`, `components/<game>/`). A game imports the barrel (`../../animation`: the
project has no path alias), never a file inside the module. `boundaries.test.ts` checks all of it.

## Plugging a table in

A new card game, "Crazy Eights", whose log entries are `{ id, seat, card }` (a card played from a hand to the pile).
The same code runs on a fake table in `example.test.ts`: if one changes, the other changes with it.

```ts
// model/crazyeights/motion.ts: the game's only animation logic, pure and unit-tested
import { leg, type LegTempo, type MotionPlan, type TableMovement } from '../../animation';
import type Card from '../Card';
export type CeEntry = { id: number; seat: number; card: Card };
export type CeAnchor = { kind: 'seat'; seat: number } | { kind: 'pile' };
export const CE_TEMPO = {
  opponent: { holdBeforeMs: 180, flightMs: 700, holdAfterMs: 250 } satisfies LegTempo,
  own: { holdBeforeMs: 100, flightMs: 480, holdAfterMs: 250 } satisfies LegTempo,
  queue: { maxQueued: 4, maxLagMs: 3500 },
};
export function planMove(e: CeEntry, mySeat: number): MotionPlan<CeAnchor> {
  const t = leg(e.seat !== mySeat, CE_TEMPO);
  const from: CeAnchor = { kind: 'seat', seat: e.seat }, to: CeAnchor = { kind: 'pile' };
  const move: TableMovement<CeAnchor> = { kind: 'travel', from, to, card: e.card, flip: false, turnDown: false,
    actor: e.seat, byOpponent: e.seat !== mySeat, delayMs: t.holdBeforeMs, durationMs: t.flightMs,
    holdAfterMs: t.holdAfterMs, marks: [from, to] };
  return { movements: [move] };
}

// view/crazyeights/CrazyEightsGameScreen.vue <script setup>
import {
  AnimationSpeedButton, CardMotionLayer, defineAnchors, scalePlan, useAnimationSpeed, useTableMotion,
  type CardMotionLayerPlay,
} from '../../animation';
const screenEl = ref<HTMLElement | null>(null);
const layer = ref<{ play: CardMotionLayerPlay<CeAnchor> } | null>(null);
const { speed, cycle } = useAnimationSpeed();
const resolve = defineAnchors<CeAnchor>(() => screenEl.value, {
  seat: (a, q) => q.cardIn(q.find(`[data-seat="${a.seat}"]`)),
  pile: (_, q) => q.cardIn(q.find('[data-test="pile"]')),
});
useTableMotion({ layer, speed, bounds: CE_TEMPO.queue, log: () => store.events,
  plan: (e: CeEntry, { factor }) => scalePlan(planMove(e, store.mySeat), factor) });
// <template>: <div ref="screenEl"> the seats and the pile </div>
//             <CardMotionLayer ref="layer" :resolve="resolve" mark-style="soft" />
//             <AnimationSpeedButton :speed="speed" :names="ui.speed.names" :label="ui.speed.label" @cycle="cycle" />
```

`scalePlan(plan, factor)` is `scaleMovements` applied to the movements and the follow-ups (and to `atMs` and
`spanMs`); `leg(byOpponent, tempo)` picks `tempo.opponent` or `tempo.own`. The layer's ref is typed on what the
orchestrator needs (`CardMotionLayerPlay`), as the Kobo and President screens do: `CardMotionLayer` is a generic
component. The speed button's `names` and `label` are the game's texts, from `locales/`. A table with no speed button
omits `speed` (a fixed Normal, as Kobo and Bullshit).

### What `useTableMotion` does for you

For each new entry of the log (newest first, each entry a new object), oldest first: asks `plan` for its motion
(`null`: no picture), awaits `prepare` when the game gave one, admits the event through the tempo queue (a burst plays
one event after the other; past `maxQueued` / `maxLagMs` only the rings show, at once), hands it to the layer and
calls `onPlayed(played, entry)` with `endsAt`, the timestamp (of `now()`) at which its flights are over (the holds
after landing excluded; `now()` when nothing flies). `motion.play(plan)` does the same for a change that does not come
from the log (President's exchange, Bullshit's own discard) and resolves to the same `Played`; `motion.backlogMs()`
says how long until everything admitted is over, `motion.factor` and `motion.still` give the speed as it plans.
`plan` is called as the entry arrives, so it may read the state of that moment (Kobo keeps "the state before" this way);
`prepare`, `beforePlay` and `onPlayed` only get the entry and the plan: a game that needs more keys it on the entry
(a `WeakMap`, as Kobo and President do).

The escape hatches, and nothing else, are for a game's oddities: `prepare` (wait for a face, before admission),
`beforePlay` (wait after admission, keeping the event's place in the queue from its arrival: President's held pile;
the wait comes off the start), `followUps` (movements that start later in the same event), `spanMs` (an event that
holds the queue longer than its flights), `onPlayed` (holds, counts, end gating) and `play`.

### Texts follow the picture

A table reads what it **says** (texts, counts, badges, banners) from the state it has shown so far
(`useDisplayedState`: a state reserved for a move is shown when that move's cards have landed, in order, and never
later than `maxLagMs`), and what it **lets you do** (buttons, legal plays, clicks) from the latest state. When a click
must be judged on what the player saw, keep the values on screen with `heldValues` (`holdValues(values, until)` for
the places a flight touches, `mergeHeld` to add a hold to the one in force, `liveHeld(held, now)` for what is still
held): Kobo's match does this.

### Waiting for the table to finish

`useTableSettled({ backlogMs, busy?, settleMs })` knows when a table has shown everything it was told: the queue is
empty (`motion.backlogMs`), no picture is held (`busy`) and `settleMs` of rest have passed. `whenSettled(then?)` starts
a wait (a new one replaces the one before; `cancel` drops it); `then` is told in the very timer that finds the table
settled, for a sheet that must show in that turn of the event loop. President's round sheet waits on it
(`usePresidentEndSequence`). Only something visual waits for it, never the game.

### Smaller pieces

- `useSeatBubbles({ gapMs })`: a short bubble over a seat ("Pass"), outside the queue: a burst pops `gapMs` apart, a new
  bubble on a seat replaces the old one.
- `useInFlightCounts({ maxLagMs })`: a seat's card count kept as it was until its flying cards land (never longer
  than `maxLagMs`); President's exchange uses it.
- `useAnimationSpeed()` + `AnimationSpeedButton`: Normal / Fast / Instant, kept in `localStorage`
  (`ANIMATION_SPEED_KEY`), Instant by default under reduced motion; Mangola uses them without the rest of the module.
- `prefersReducedMotion()`, `canAnimate()` (reduced motion off and `Element.animate` present: false in happy-dom).
- `CARD_RATIO`, `visibleRect`, `cardAt`, `cardIn`: the DOM helpers the resolvers of `defineAnchors` get as `q`.

## Who uses what

| Table | Pieces |
|-------|--------|
| Kobo (`view/kobo/KoboGameScreen.vue`, `model/kobo/motion.ts`, `tempo.ts`, `displayed.ts`) | `useTableMotion` on its log (`prepare`: one frame for my drawn face; `onPlayed`: the held slot values), `defineAnchors` (seven kinds), `CardMotionLayer` (glow rings), `heldValues`; no speed button |
| President (`view/president/PresidentGameScreen.vue`, `model/president/motion.ts`, `exchangeMotion.ts`, `tempo.ts`) | `useTableMotion` on its log (`followUps` + `spanMs`: the sweep; `beforePlay`: the held closed pile; `onPlayed`: when the texts are told) and `motion.play` for the exchange, `defineAnchors` (six kinds), `CardMotionLayer` (soft rings), `useDisplayedState`, `useSeatBubbles`, `useInFlightCounts`, `useTableSettled`, the speed button |
| Bullshit (`view/bullshit/BullshitGameScreen.vue`, `model/bullshit/motion.ts`) | `useTableMotion` with no log, `motion.play` for my discard and a taken pile, `CardMotionLayer` (`zIndex` 3000, `landing` + `easing`, no rings, no arrows); no speed button |
| Mangola | `useAnimationSpeed` and `AnimationSpeedButton` only (its sowing is its own) |
| Bataille Corse, Three and Six Men's Morris | nothing (Bataille Corse's session waits for its picture; Morris has no card that moves) |

## Rules every table follows

1. The picture never gates the state, the buttons or the clicks (Bataille Corse is the one table whose session waits
   for its picture, by design, and does not use this module). A place that cannot be found skips its picture; a
   picture never throws.
2. Purely visual: nothing in `animation/` is read back by game logic, except the displayed values a game chooses to
   judge a click on (Kobo's match), through `heldValues`.
3. The first state received (invite link, reload, reconnection) never animates: `useTableMotion` takes the log as it is
   at mount as already known.
4. Instant and reduced motion: nothing flies, rings stay as still outlines for `markReducedMs`.
5. Every duration of a game lives in its own files (`model/<game>/tempo.ts` by convention; Bullshit keeps its few in
   `model/bullshit/motion.ts`); `animation/` has no game number in it.
6. Visible text: none in `animation/` (the speed button's labels come from the locales).

## The layer, in short

`CardMotionLayer` is a fixed overlay (default z-index 1600, `zIndex` prop to change it). Its props: `resolve` (the
function `defineAnchors` returns), `markMs` / `markReducedMs` (how long a ring stays, moving or still), `markStyle`
(`glow`, Kobo's default, or `soft`, President's), `arrowId` (unique on the page). `play(movements, { marksOnly,
startInMs, still })` returns `{ endsInMs }` (the holds after landing included; `useTableMotion` calls it for you). A movement may carry `landing` (`tiltDeg`, `fade`, `scaleTo`) and `easing`
for a look other than "arrives and stays"; without them the keyframes are the layer's own, exactly.

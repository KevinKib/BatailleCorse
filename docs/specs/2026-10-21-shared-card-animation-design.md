# Shared card animation module: design

Written by Claude Opus 5.5 on `origin/main` at `93b892c` (2026-10-21). Plan:
`docs/plans/2026-10-21-shared-card-animation-plan.md`. Tracking: roadmap issue #1.

## 1. Request

Owner, 2026-10-21: "the animation execution of the President game is an example for many other games; modularize
it so it is easily usable by the other frontends" (Kobo, Bullshit, Bataille Corse, Mangola, Six/Three Men's Morris,
President).

Classified **architectural** (a shared frontend module, every animated table touched). Behaviour-preserving: no
visible change is proposed, so no Claude Design canvas. Anything that would change a table's look is listed in
section 9 as a follow-up for the owner.

## 2. Key decisions

1. **One module, `frontend/src/animation/`**, in three layers: `core/` (pure TypeScript, no Vue, no DOM), `dom/`
   (measuring places on screen) and `vue/` (the layer component and the composables). A public barrel
   `animation/index.ts` and a short `animation/README.md` usage guide. The pieces that are already shared today
   (`model/table/motion.ts`, `model/table/queue.ts`, `components/table/CardMotionLayer.vue`,
   `components/table/motionTarget.ts`, `composables/useAnimationSpeed.ts`, `components/table/AnimationSpeedButton.vue`,
   `useInFlightCounts`, `useSeatBubbles`, `prefersReducedMotion`) move there unchanged first.
2. **One orchestrator, `useTableMotion`**, replaces the loop that Kobo and President each wrote by hand: find the new
   log entries, plan their movements, decide "still" (Instant or reduced motion), admit them through the tempo
   queue, play them on the layer, report when each one ends. What stays per game is only the **event -> motion
   mapping** (a pure `plan` function), the **anchor resolvers** and the game's **tempo** numbers.
3. **Anchors resolved by a typed registry per kind** (`defineAnchors`): a game declares one resolver per anchor kind
   (exhaustive at compile time) using shared DOM helpers (`visibleRect`, `cardIn`, `cardAt`, `CARD_RATIO`), instead
   of a hand-written `switch` plus copied helpers in every screen.
4. **A generic displayed state** (`useDisplayedState`): the texts of a table follow the picture, not the raw state;
   the buttons and the clicks keep following the state. It is aligned with PR #294
   (`fix/president-texts-follow-animation`, in progress at the time of writing): whichever lands first, the other
   adopts its names (section 5.6).
5. **Migration with no visible change, one PR per game, Kobo first** (proved by characterisation tests written on
   today's code before it moves, the existing Cypress specs, and before/after DOM captures), then President and
   Bullshit. Bataille Corse, Mangola and the Morris games are not migrated (section 7): only their imports move.

## 3. What exists today

### 3.1 Already shared (written for Kobo, reused by President, PR #279 and before)

| File | What it is | Users |
|------|------------|-------|
| `model/table/motion.ts` | `TableMovement<Anchor>` (`travel`, `glance`, `reveal`, `ring`; holds, flight, marks, `restsOn`, `leavesBack`, `byOpponent`), `eventSpanMs`, `scaleMovements` | Kobo, President |
| `model/table/queue.ts` | `createTempoQueue(now, bounds)`: plays a burst one event after the other, past `maxQueued` / `maxLagMs` only the rings | Kobo (via `model/kobo/queue.ts`), President |
| `components/table/CardMotionLayer.vue` | the fixed overlay that flies copies (`PlayingCard`), arrows, rings (`glow` or `soft`), "unders" (what a place keeps showing until a copy lands), reveals; never throws, never waits, cleans up on unmount; `play(movements, { marksOnly, startInMs, still })` | Kobo (via `KoboMotionLayer.vue`), President |
| `components/table/motionTarget.ts` | `MotionRect`, `MotionTarget { rect, cards }` | Kobo, President |
| `composables/useAnimationSpeed.ts` + `components/table/AnimationSpeedButton.vue` | Normal / Fast / Instant, kept in `localStorage`, Instant by default under reduced motion | President, Mangola |
| `composables/usePileAnimation.ts` `prefersReducedMotion` | the media query, guarded | everyone |

### 3.2 Written for President, generic in substance but living in President files

- `speedFactor(speed)` in `model/president/motion.ts` (1, 0.5, 0).
- `LegTempo` type in `model/kobo/tempo.ts` (imported by President's tempo).
- `useSeatBubbles` (typed on President's `SeatBubble`), `useInFlightCounts` (already generic).
- `usePresidentEndSequence`: its settle logic ("the queue is empty, no picture is held, `settleMs` of rest after a
  `graceMs`") is generic; its round/final/abandoned mapping (`endKind`) is President's.
- `sourcesFor` / `PendingSources`: "measure my cards at the click, use those rects when the move comes back in the
  log". Generic idea (any game where my own cards leave my hand before the state draws them).

### 3.3 Duplicated by hand in `KoboGameScreen.vue` and `PresidentGameScreen.vue`

- `CARD_RATIO = 243.1375 / 167.575`, `visibleRect`, `find`, a "card-sized rectangle in the middle of a box" helper
  (`cardIn` in Kobo, `cardAt` in President).
- The fresh-entries watch (the log is newest first: take what comes before the head already known, replay oldest
  first).
- The admission: `still ? { startInMs: 0, motion: false } : tempoQueue.admit(eventSpanMs(moves))`, then
  `layer.play(moves, { marksOnly: !admission.motion, startInMs, still })`.
- "After admission" side effects keyed on the end time of the flights: Kobo's `holdDisplayed(planHold(...))`,
  President's `inFlight.add(...)`.

### 3.4 Per game and staying so

- Kobo: `model/kobo/motion.ts` (`planMovements` from `KoboLogEntry`), `model/kobo/tempo.ts` (`TEMPO`),
  `model/kobo/displayed.ts` (slot revisions and discard rank kept on screen until the flight lands, read by the
  match judgement), the one-frame wait for my drawn card's face, the sounds.
- President: `model/president/motion.ts` (`planMovements`, `planSweep`, `sweepStartMs`, `planBubbles`),
  `exchangeMotion.ts`, `tempo.ts`, `trickView.ts` (the closed pile picture), `endSequence.ts` (`endKind`,
  `endTimings`), the held closed-trick picture during the sweep (`beginHold` / `scheduleHold`), the exchange
  acknowledgement timing (`exchangeView.ts`).

### 3.5 The other frontends

- **Bullshit** (`composables/usePileAnimation.ts`): two hand-made ghost systems on `<body>` (z-index 3000):
  `flyCardsToPile` (my own discarded cards, 380 ms, 70 ms stagger, landing tilted and fading) and `flyPileToTaker`
  (up to 3 backs to the taker, 460 ms, shrinking and fading, `onSettled` holds the end screen). No rings, no arrows,
  no queue, no speed setting; opponents' plays do not fly.
- **Bataille Corse** (`useCardAnimation`, `useGameAnimations`): a reflex game whose session **waits for the
  animation** (`GameSession.awaitAnimation` / `notifyAnimationComplete`): the opposite contract (the picture gates
  the state). PNG ghost, frozen pile card, slap ghosts and impact "juice", tuned for latency.
- **Mangola** (`useSowingAnimation`): seeds sown by a travelling hand, frame by frame over the board counts, with a
  Skip button; uses `useAnimationSpeed` and `AnimationSpeedButton`. No cards.
- **Three / Six Men's Morris** (`MorrisBoard.vue`): a CSS `Transition` on a piece appearing and a glow on a mill.
  No cards, no flights.

## 4. Scope

In scope:

- The module (section 5), its own tests and README.
- Moving the already shared pieces into it, all imports updated, behaviour identical.
- Migrating Kobo, then President, then Bullshit onto `useTableMotion` + `defineAnchors`, with no visible change.
- Generic `useDisplayedState`, aligned with PR #294.
- Architecture docs: `docs/architecture/add-a-game.md` and `game-conventions.md` point new games to the module.

Out of scope (no change):

- Any new animation or any visible change (Kobo's speed button, opponents' plays flying in Bullshit, piece moves in
  Morris, rings in Mangola): section 9.
- Bataille Corse's animation system and its session gating (section 7.1).
- Mangola's sowing replay; Morris's piece transitions.
- Backend: none.

## 5. The module

### 5.1 Layout

```
frontend/src/animation/
  README.md                 how a table plugs in (the sketch of 5.3), the rules of 5.5
  index.ts                  the public API (games import the barrel only)
  core/                     pure TypeScript: no Vue, no DOM, time passed in
    movement.ts             TableMovement<A>, eventSpanMs, scaleMovements        (from model/table/motion.ts)
    tempo.ts                LegTempo, QueueBounds, leg(byOpponent, tempo)        (LegTempo from model/kobo/tempo.ts)
    tempoQueue.ts           createTempoQueue, Admission                          (from model/table/queue.ts)
    speed.ts                AnimationSpeed, ANIMATION_SPEEDS, nextAnimationSpeed, speedFactor
    reducedMotion.ts        prefersReducedMotion, canAnimate
    freshEntries.ts         freshEntries(log, knownHead): the new entries, oldest first
    heldValues.ts           a value per place kept until a time (Kobo's DisplayedHold, generic)
    displayedTimeline.ts    states with the time from which each is shown (5.6)
    *.test.ts
  dom/
    rect.ts                 MotionRect, MotionTarget, CARD_RATIO, visibleRect, cardAt, cardIn
    anchors.ts              defineAnchors
    *.test.ts
  vue/
    CardMotionLayer.vue     unchanged API, plus `play` returning when it ends (5.4)
    AnimationSpeedButton.vue
    useAnimationSpeed.ts
    useTableMotion.ts       the orchestrator (5.3)
    useDisplayedState.ts    (5.6)
    useInFlightCounts.ts
    useSeatBubbles.ts       generic on the bubble kind
    useTableSettled.ts      the settle logic of usePresidentEndSequence
    *.test.ts
```

`core/` never imports from `vue/` or `dom/`; `dom/` never imports Vue; nothing in `animation/` imports a game
(`model/<game>`, `state/`, `view/`). A game imports the barrel (`../../animation`: the project has no path
alias), never a file inside it. These rules are checked by a unit test that scans the imports (`animation/boundaries.test.ts`), like the backend's ArchUnit
rules.

### 5.2 Public API (types)

```ts
// core
export type { TableMovement, MotionKind } from './core/movement';
export { eventSpanMs, scaleMovements, scalePlan } from './core/movement';
export { leg, type LegTempo, type QueueBounds } from './core/tempo';
export { createTempoQueue, type Admission } from './core/tempoQueue';
export { ANIMATION_SPEEDS, nextAnimationSpeed, speedFactor, type AnimationSpeed } from './core/speed';
export { prefersReducedMotion, canAnimate } from './core/reducedMotion';
export { freshEntries } from './core/freshEntries';
export { holdValues, mergeHeld, liveHeld, type Held } from './core/heldValues';
// dom
export { CARD_RATIO, visibleRect, cardAt, cardIn, type MotionRect, type MotionTarget } from './dom/rect';
export { defineAnchors, type AnchorResolvers, type DomQuery } from './dom/anchors';
// vue
export { default as CardMotionLayer } from './vue/CardMotionLayer.vue';
export { default as AnimationSpeedButton } from './vue/AnimationSpeedButton.vue';
export { useAnimationSpeed } from './vue/useAnimationSpeed';
export { useTableMotion, type MotionPlan, type Played, type TableMotion } from './vue/useTableMotion';
export { useDisplayedState } from './vue/useDisplayedState';
export { useInFlightCounts } from './vue/useInFlightCounts';
export { useSeatBubbles } from './vue/useSeatBubbles';
export { useTableSettled } from './vue/useTableSettled';
```

The new signatures:

```ts
/** One resolver per anchor kind; `q` measures inside the table's root element. */
type AnchorResolvers<A extends { kind: string }> = {
  [K in A['kind']]: (anchor: Extract<A, { kind: K }>, q: DomQuery) => MotionTarget | null;
};
interface DomQuery {
  find(selector: string): HTMLElement | null;
  findAll(selector: string): HTMLElement[];
  rect(el: Element | null | undefined): MotionRect | null;   // visibleRect
  cardIn(host: Element | null, fallbackWidth?: number): MotionTarget | null;
  cardAt(box: MotionRect, width: number): MotionRect;
}
function defineAnchors<A extends { kind: string }>(
  root: () => HTMLElement | null, resolvers: AnchorResolvers<A>,
): (anchor: A) => MotionTarget | null;     // a resolver that throws or finds nothing gives null

/** What one entry (or one state change) shows. */
interface MotionPlan<A> {
  movements: TableMovement<A>[];
  /** Movements that start later in the same event (President's sweep), counted from the event's start. */
  followUps?: { movements: TableMovement<A>[]; atMs: number }[];
  /** The time the event holds the queue, when it is not `eventSpanMs(movements)` (a sweep, a badge hold). */
  spanMs?: number;
}
/** What happened to an event, for the game's side effects (holds, counts, end gating). */
interface Played<A> {
  plan: MotionPlan<A>;
  motion: boolean;            // false: rings only (backlog full, Instant, reduced motion)
  still: boolean;
  startInMs: number;
  /** When its flights are over, as a timestamp of `now()` (equal to now when nothing flies). */
  endsAt: number;
}
interface TableMotionOptions<E, A> {
  layer: Ref<{ play: CardMotionLayerPlay<A> } | null>;
  bounds: QueueBounds;
  /** Defaults to a fixed 'normal' (a table without a speed button). */
  speed?: Ref<AnimationSpeed>;
  now?: () => number;                      // Date.now; tests pass a fake clock
  /** The log, newest first (each entry a new object). Omit for a table that only calls `play`. */
  log?: () => readonly E[];
  plan?: (entry: E, ctx: { factor: number; still: boolean }) => MotionPlan<A> | null;
  /** Awaited before admission (Kobo: one frame for my drawn face; President: the held pile). */
  prepare?: (entry: E, plan: MotionPlan<A>) => Promise<MotionPlan<A>> | MotionPlan<A>;
  onPlayed?: (played: Played<A>, entry: E | null) => void;
}
interface TableMotion<A> {
  /** Plays a plan that does not come from the log (President's exchange from two states). */
  play(plan: MotionPlan<A>): Promise<Played<A>>;
  /** How long until everything admitted is over (0 when idle). */
  backlogMs(): number;
  factor: ComputedRef<number>;             // speedFactor(speed), never 0 for planning (|| 1)
  still: ComputedRef<boolean>;             // Instant or reduced motion
}
function useTableMotion<E, A>(options: TableMotionOptions<E, A>): TableMotion<A>;
```

### 5.3 How a new table plugs in (the README's example)

A new card game, "Crazy Eights", whose log entries are `{ id, seat, card }` (a card played from a hand to the pile):

```ts
// model/crazyeights/motion.ts: the game's only animation logic, pure and unit-tested
import { leg, type LegTempo, type MotionPlan, type TableMovement } from '../../animation';
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
const screenEl = ref<HTMLElement | null>(null);
const layer = ref<InstanceType<typeof CardMotionLayer> | null>(null);
const { speed, cycle } = useAnimationSpeed();
const resolve = defineAnchors<CeAnchor>(() => screenEl.value, {
  seat: (a, q) => q.cardIn(q.find(`[data-seat="${a.seat}"]`)),
  pile: (_, q) => q.cardIn(q.find('[data-test="pile"]')),
});
useTableMotion({ layer, speed, bounds: CE_TEMPO.queue, log: () => store.events,
  plan: (e, { factor }) => scalePlan(planMove(e, store.mySeat), factor) });
// <template>: <CardMotionLayer ref="layer" :resolve="resolve" mark-style="soft" />
//             <AnimationSpeedButton :speed="speed" @cycle="cycle" />
```

About 30 lines for a table with flights, rings, arrows, the burst queue, the speed button, Instant and reduced
motion. (`scalePlan(plan, factor)` is `scaleMovements` applied to `movements` and `followUps`; `leg(byOpponent,
tempo)` picks `tempo.opponent` or `tempo.own`. Both are in `core`.)

### 5.4 Changes to today's pieces (all additive, defaults keep today's behaviour)

- `CardMotionLayer.play(...)` returns `{ endsInMs: number }` (the longest `startInMs + delay + duration +
  holdAfter` it scheduled, 0 when it played nothing): Bullshit's end screen waits on it instead of `onSettled`.
- `TableMovement` gains two optional fields for Bullshit's look, unused by Kobo and President:
  `landing?: { tiltDeg: number; fade: boolean; scaleTo?: 'target' | 'centre' }` and `easing?: string`. Absent, the
  keyframes are today's, byte for byte (a characterisation test on the generated keyframes guards it).
- `CardMotionLayer` gains a `zIndex` prop (default 1600, today's value; Bullshit passes 3000, its value).
- `useSeatBubbles<K extends string>` is typed on its kind; President passes its `SeatBubble['kind']`.
- Nothing else changes in the layer: the `glow` ring (Kobo) and the `soft` ring (President) stay as they are; Kobo's
  static "touched slot" highlight (`.slot--event` in `KoboTableau.vue`) is a state highlight, not an animation, and
  stays Kobo's.

### 5.5 Rules every table follows (README)

1. The picture never gates the state, the buttons or the clicks (except Bataille Corse, section 7.1). A place that
   cannot be found skips its picture; a picture never throws.
2. Purely visual: nothing in `animation/` is read back by game logic, except the displayed values a game chooses to
   judge a click on (Kobo's match), through `heldValues`.
3. The first state received (invite link, reload, reconnection) never animates: `useTableMotion` takes the log as it
   is at mount as already known.
4. Instant and reduced motion: nothing flies, rings stay as still outlines for `markReducedMs`.
5. Every duration of a game lives in its `tempo.ts`; `animation/` has no game number in it.
6. Visible text: none in `animation/` (the speed button's labels already come from the locales).

### 5.6 Displayed state (texts follow the animations)

The owner's feedback behind PR #294: the texts of the President table (the "N or nothing" banner, the phase line,
the chips, the counts, the closed-trick badge) change before the cards that explain them have landed. The general
answer:

- `core/displayedTimeline.ts`: a list of `{ state, visibleAt }`; `at(now)` is the newest state whose time has come
  (the oldest one before that). Pure, tested with a fake clock. Its lag is capped by the queue's `maxLagMs`, so the
  text never falls further behind the game than the picture does.
- `vue/useDisplayedState(source, motion)`: watches `source` (the store's state); each state that comes with an
  admitted event becomes visible at that event's `endsAt` (`onPlayed`); a state with no event, a still table, or
  the first state shows at once. Returns `displayed: Readonly<Ref<S | null>>`.
- A table reads `displayed` for what it **says** (texts, counts, badges, banners) and the store's state for what it
  **lets you do** (buttons, legal plays, clicks). Kobo's slot-revision hold (`model/kobo/displayed.ts`) is the
  narrower case of "judge a click on what is shown" and is rebuilt on `heldValues`, unchanged in behaviour.

**Alignment with PR #294** (`fix/president-texts-follow-animation`, draft, no code yet when this was written):

- If #294 merges first with a President-only displayed layer, chunk 0 of the plan lifts its generic part into
  `animation/` keeping its exported names (re-exported from the President file until the President chunk), and
  this section is amended to its design where they differ: #294 is the owner's live request and wins on behaviour.
- If this module merges first, #294 builds on `useDisplayedState` (the plan's chunk 0 is short and docs-led, so the
  author of #294 is told in the PR).
- In both cases the President migration chunk starts only after #294 is merged, because both rewrite the same part
  of `PresidentGameScreen.vue`. PR #293 (`fix/president-completion-offer`, also open) touches the same file and the
  same rule applies.

## 6. Migration, game by game

Each migration is one PR, behaviour-preserving, proved before it is merged (section 8).

1. **Chunk 0, the module.** Move the shared pieces (3.1, 3.2 generic parts) into `animation/`, update every import
   (Kobo, President, Mangola, Bullshit), write the new pieces (`useTableMotion`, `defineAnchors`, `freshEntries`,
   `heldValues`, `displayedTimeline`, `useDisplayedState`, `useTableSettled`, the README) with their unit tests on
   fakes. No screen uses the new pieces yet. Behaviour identical; all suites green.
2. **Kobo.** `KoboGameScreen` uses `useTableMotion` (log, `plan` = `planMovements` with its context, `prepare` =
   the one-frame wait for my drawn face, `onPlayed` = `holdDisplayed(planHold(...))` with `endsAt`), `defineAnchors`
   (its seven kinds), the shared `CARD_RATIO` / `cardIn`. `KoboMotionLayer.vue` goes (the screen passes Kobo's
   `markMs` / `markReducedMs` / `arrowId` to `CardMotionLayer` directly), `model/kobo/queue.ts` goes,
   `model/kobo/displayed.ts` is rebuilt on `heldValues`. No speed button (fixed Normal), as today.
3. **President.** `PresidentGameScreen` uses `useTableMotion` for the log (`plan` = `planMovements` + `planSweep` as
   a follow-up with `spanMs`, `prepare` = wait for the held closed pile, `onPlayed` = in-flight counts, bubbles,
   hold scheduling) and `motion.play(...)` for the exchange flights; `defineAnchors` for its six kinds;
   `usePresidentEndSequence` keeps `endKind` and delegates the settle wait to `useTableSettled`; `sourcesFor`
   stays President's (one game uses it). The displayed state of #294 moves onto `useDisplayedState` if #294
   did not already use it.
4. **Bullshit.** `flyCardsToPile` and `flyPileToTaker` become two `plan` functions in `model/bullshit/motion.ts`
   (my discarded cards from their measured rects to the pile, tilted and fading; up to 3 backs from the pile to
   the taker, shrinking and fading) played by `CardMotionLayer` with no marks, no arrows (`byOpponent: false`),
   `zIndex` 3000, through `motion.play`; the end screen waits on `endsAt`. `usePileAnimation.ts` keeps nothing
   (emptied, listed for `git rm`). No speed button, as today.

Not migrated: Bataille Corse, Mangola, Three and Six Men's Morris (section 7).

## 7. Why some tables are not migrated

### 7.1 Bataille Corse

Its session waits for each animation to finish before it applies the next event (`awaitAnimation` /
`notifyAnimationComplete`): the picture is part of the game's rhythm in a reflex game where a slap is judged in
milliseconds, and its ghosts are tuned PNG images with a frozen pile card. `useTableMotion` follows the opposite
rule (the picture never gates the state). Moving it would change its timing for no visible gain and with real
risk. It keeps its system; only `prefersReducedMotion` comes from the module if it uses it.

### 7.2 Mangola

It animates seeds, not cards, frame by frame over the board counts (`sowingFrames`), with its own Skip and speed
profiles. It already uses the shared speed setting and button: those imports move to `animation/` in chunk 0, which
is its whole migration.

### 7.3 Three and Six Men's Morris

No card and no flight: a piece appears with a CSS transition, a mill glows. Nothing to migrate. A later "a piece
slides from point to point" would be a visible change (section 9).

## 8. Tests and proof of no visible change

- **Module unit tests** (Vitest, happy-dom, fake timers and a fake clock): every `core/` function; `defineAnchors`
  (exhaustive kinds, a throwing resolver gives null, nothing measured gives null); `useTableMotion` (fresh entries
  only, oldest first; the first log is not replayed; still when Instant or reduced motion: `marksOnly` and `still`
  passed, no admission; a full queue gives rings only; `followUps` played at `startInMs + atMs`; `onPlayed` gets
  `endsAt`; `play` for non-log plans shares the queue; `backlogMs`); `useDisplayedState` (follows `endsAt`, shows at
  once when still, never lags past `maxLagMs`); `useTableSettled`; the boundaries test of 5.1.
- **Characterisation tests first (strict TDD of a refactor).** Before a screen is migrated, its chunk adds a test on
  today's code that drives a scripted log through the screen with a stubbed layer and records every
  `play(movements, options)` call and every side effect (Kobo's `holdDisplayed`, President's in-flight counts,
  bubbles and held pile, Bullshit's ghosts' keyframes), and commits the recording as the expected value. The
  migration must leave it green. The existing `KoboMotionLayer.test.ts`, `CardMotionLayer.test.ts`,
  `KoboGameScreen.test.ts`, `PresidentGameScreen.test.ts`, `BullshitGameScreen.pile.test.ts` and
  `BullshitGameScreen.end.test.ts` stay green (only their imports may change).
- **Cypress**, on a shifted-port e2e stack, per chunk: Kobo `kobo-animations.cy.ts`; President
  `president-animations`, `president-exchange-flights`, `president-exchange-sync`, `president-round-end`,
  `president-soft-rings`, `president-stillness`; Bullshit `bullshit-pile-animation`, `bullshit-end-screen`,
  `bullshit-hotkeys-and-take`. The integration chunk plays them all, plus `mangola-animation-speed`.
- **Before/after captures** (skill `ui-change`): for Kobo, President and Bullshit, the DOM of the layer mid-flight
  under `cy.clock` (ghost count, rects, keyframes, ring roles and styles) and screenshots at 375 px and 1280 px, on
  `main` then on the branch; the diff must be empty. They go in the PR body.

## 9. Possible follow-ups (visible changes, owner decision, not in this plan)

- Kobo gets the speed button (Normal / Fast / Instant) like President and Mangola.
- Bullshit flies the opponents' discards to the pile (backs), with the soft rings and arrows of President.
- Morris: a piece slides from point to point (the layer would need a slot to draw a token instead of a card).
- One ring look for every table (Kobo's `glow` vs President's `soft`).

Each would go through a Claude Design canvas first.

## 10. Risks

| Risk | Mitigation |
|------|------------|
| A timing detail lost in the move (Kobo's one-frame face wait, President's held pile and its `while (holdDone)` loop, `Date.now()` read after an await) | `prepare` is awaited at the same point as today; characterisation tests record start offsets; fake clock in the module tests |
| Import churn conflicts with open PRs (#293, #294) and parallel chunks | chunk 0 runs alone and merges first; the President chunk waits for #293 and #294; migration chunks touch only their game's files and never the barrel |
| `useTableMotion` grows game-specific options to fit President | the escape hatches are `prepare`, `followUps`, `spanMs`, `onPlayed` and `play`; anything else stays in the game's screen |
| Bullshit's look (tilt, fade, easing, z-index) drifts when played by the layer | optional `landing` / `easing` / `zIndex`, keyframes compared in a characterisation test, before/after captures |
| happy-dom lacks `Element.animate` | `canAnimate()` guards as today; tests stub it where they need flights |
| A future game imports a file inside `animation/` and couples to internals | the barrel rule and the boundaries test |

# Shared card animation module: plan

Spec: `docs/specs/2026-10-21-shared-card-animation-design.md`. **Produced by Claude Opus 5.5** on `origin/main` at
`93b892c` (2026-10-21). To be approved by the owner before any implementation.

**Status (2026-10-24):** chunks 0 to 3 merged (#297, #301, #302, #303), chunk 4 in #304. The emptied files were
removed by the chunks themselves (`KoboMotionLayer.vue`, `model/kobo/queue.ts`, `usePileAnimation.ts` and their tests):
nothing is left for `git rm`.

**Goal:** one frontend module, `frontend/src/animation/`, that any table plugs into in about 30 lines (flights,
rings, arrows, burst queue, speed, Instant, reduced motion, texts that follow the picture), with Kobo, President and
Bullshit moved onto it and **no visible change**.

## Key decisions (spec section 2)

1. One module in three layers: `animation/core/` (pure TS), `animation/dom/` (measuring places), `animation/vue/`
   (the layer and the composables), a barrel `animation/index.ts`, a README; today's shared pieces move there
   unchanged first.
2. One orchestrator, `useTableMotion`, replaces the loop Kobo and President each wrote; a game keeps only its pure
   event -> motion mapping, its anchors and its tempo numbers.
3. Anchors through a typed per-kind registry, `defineAnchors`, with shared DOM helpers (`visibleRect`, `cardIn`,
   `cardAt`, `CARD_RATIO`).
4. A generic displayed state, `useDisplayedState`: texts follow the picture, buttons follow the state; aligned with
   PR #294 (whichever merges first sets the names).
5. Behaviour-preserving migration, one PR per game, Kobo first, proved by characterisation tests written on today's
   code, the existing Cypress specs and before/after captures. Bataille Corse, Mangola and Morris are not migrated
   (spec section 7).

## Rules for every chunk

- Own worktree and branch from `origin/main`, a draft PR opened at launch with its real number; rebase on `main`
  before the final push.
- Strict TDD. For a refactor that means: the characterisation test is written and green **on today's code** and
  committed first; then the move; the test stays green. New pieces of the module: each test seen failing for the
  right reason before the code.
- No visible change. Any doubt goes to the owner, not into the PR.
- No visible text is added (the module has none; the speed button's labels already come from the locales).
- Suites before ready: `cd frontend && npm test && npx vite build`; `cd backend && mvn -s
  ../deploy/docker/settings.xml test` (untouched, run as the rule requires). Cypress where the chunk says so, on a
  shifted-port e2e stack with its own compose project name.
- No `rm` / `git rm`: a file that goes is emptied to a one-line comment and listed in the PR for the owner.
- Merge: no backend and no domain touched, so the merge-autonomy rule applies; the orchestrator merges.

## Chunks

| Chunk | Title | Size | Needs | Runs with | Model |
|-------|-------|------|-------|-----------|-------|
| 0 | `refactor: shared animation module` | M | - | first, alone | default (Sonnet), Opus review of the API |
| 1 | `refactor: Kobo on the shared animation module` | M | 0 | 2, 3 | default (Sonnet) |
| 2 | `refactor: President on the shared animation module` | M-L | 0, #293 and #294 merged | 1, 3 | default (Sonnet), Opus review |
| 3 | `refactor: Bullshit on the shared animation module` | S-M | 0 | 1, 2 | default (Sonnet) |
| 4 | `docs: shared animation module, integration` | S | 1, 2, 3 | last, alone | Opus (branch review) |

**Merge order:** 0, then 1 (Kobo first, as the owner asked), then 2 and 3 in either order, each rebased on `main`
after 1; then 4.

### Chunk 0: the module (shared interfaces and the moves)

Alone and first: every other chunk compiles against it. No screen uses the new pieces yet.

1. **Moves, behaviour identical** (`git mv` where the process allows it, otherwise new file + old emptied and
   listed), every import updated in the same commit:
   - `model/table/motion.ts` -> `animation/core/movement.ts` (+ test)
   - `model/table/queue.ts` -> `animation/core/tempoQueue.ts` (+ test)
   - `LegTempo` from `model/kobo/tempo.ts` -> `animation/core/tempo.ts` (Kobo's `TEMPO` stays in Kobo's file)
   - `speedFactor` from `model/president/motion.ts` and the speed type and cycle from
     `composables/useAnimationSpeed.ts` -> `animation/core/speed.ts`; `useAnimationSpeed` -> `animation/vue/`
   - `prefersReducedMotion` from `composables/usePileAnimation.ts` -> `animation/core/reducedMotion.ts` (plus
     `canAnimate`, the check the layer and Kobo's screen do today)
   - `components/table/motionTarget.ts` -> `animation/dom/rect.ts`; `components/table/CardMotionLayer.vue` and
     `AnimationSpeedButton.vue` -> `animation/vue/` (+ tests)
   - `composables/useInFlightCounts.ts`, `composables/useSeatBubbles.ts` (made generic on its kind) -> `animation/vue/`
2. **New pieces, test first, on fakes** (spec 5.2): `core/freshEntries.ts`, `core/heldValues.ts`,
   `core/displayedTimeline.ts`, `leg` and `scalePlan`; `dom/rect.ts` helpers (`visibleRect`, `cardAt`, `cardIn`,
   `CARD_RATIO`) and `dom/anchors.ts` (`defineAnchors`); `vue/useTableMotion.ts`, `vue/useDisplayedState.ts`,
   `vue/useTableSettled.ts`; the additive layer changes of spec 5.4 (`play` returns `{ endsInMs }`, optional
   `landing` / `easing` on a movement, `zIndex` prop), with a characterisation test of today's keyframes written
   before them.
3. `animation/index.ts` (the barrel, spec 5.2), `animation/boundaries.test.ts` (spec 5.1), `animation/README.md`
   (the example of spec 5.3 and the rules of 5.5).
4. **Alignment with #294:** at launch, read PR #294. If it is merged, lift its generic displayed layer into
   `animation/` with its names (re-exported from its President file until chunk 2) and amend spec 5.6; if it is
   still open, comment on #294 with the `useDisplayedState` signature so its author can build on it (that comment
   is the orchestrator's call: ask before posting). **Done in chunk 0:** #294 and #293 were already merged, so
   `createDisplayQueue` (`animation/core/displayQueue.ts`) and `useDisplayedState` (`animation/vue/`) were lifted with
   their tests and their names unchanged (no re-export file: the boundary rule makes games import the barrel), and
   spec 5.6 was amended to #294's design in place of the `displayedTimeline` sketch.
5. Suites. Cypress: `kobo-animations`, `president-animations`, `president-soft-rings`, `mangola-animation-speed`,
   `bullshit-pile-animation` (the imports moved under them).

Files touched outside `animation/`: imports only, in `view/kobo/KoboGameScreen.vue`, `components/kobo/KoboMotionLayer.vue`,
`model/kobo/*.ts`, `view/president/PresidentGameScreen.vue`, `model/president/*.ts`, `components/president/*`,
`view/mangola/MangolaGameScreen.vue`, `components/mangola/MangolaBoard.vue`, `composables/useSowingAnimation.ts`,
`view/bullshit/BullshitGameScreen.vue`, `composables/usePileAnimation.ts`, and their tests. **This is why chunk 0
runs alone.**

### Chunk 1: Kobo

1. Characterisation test, on `main` after chunk 0, committed first: `view/kobo/KoboGameScreen.motion.test.ts` drives
   a scripted log (draw, swap, discard, peek by a bot, blind swap, king swap, match on another seat with a gift,
   missed match, round end, a burst of 6 events, reduced motion) through the screen with a stubbed
   `CardMotionLayer`, and records every `play(movements, options)` call, every `holdDisplayed` argument and the
   sounds. The recording is the expected value.
2. Move: `useTableMotion` (log `store.events`, `plan` = `planMovements` with its `MotionContext`, `prepare` = the
   one-frame wait for my drawn face, `onPlayed` = `holdDisplayed(planHold(before, movements, endsAt))` when
   `motion && canAnimate()`, no `speed`), `defineAnchors<MotionAnchor>` for its seven kinds, `cardIn` and
   `CARD_RATIO` from the module. `CardMotionLayer` is used directly with Kobo's `markMs`, `markReducedMs`,
   `arrowId="kobo-arrow-head"`.
3. `model/kobo/displayed.ts` rebuilt on `core/heldValues.ts` (same exports, its test unchanged).
4. Goes (emptied, listed): `components/kobo/KoboMotionLayer.vue` (its test's cases move to the screen's motion test
   or to the module's layer test, none dropped), `model/kobo/queue.ts` and its test.
5. Proof: characterisation test, `KoboGameScreen.test.ts`, `kobo-animations.cy.ts` and every `kobo-*.cy.ts`, and
   before/after captures with the `ui-change` skill (layer DOM mid-flight under `cy.clock`, screenshots at 375 px and
   1280 px) in the PR body.

Files: `view/kobo/KoboGameScreen.vue`, `model/kobo/displayed.ts`, `model/kobo/motion.ts` (types only),
`components/kobo/KoboMotionLayer.vue`, `model/kobo/queue.ts`, their tests.

### Chunk 2: President

Starts only once #293 and #294 are merged (they rewrite the same part of `PresidentGameScreen.vue`).

1. Characterisation test first: `view/president/PresidentGameScreen.motion.test.ts`, a scripted log (opponent play,
   my play with measured sources, pass, a cut that closes a trick and sweeps, a quad completed out of turn, a
   forbidden finish, a burst, Instant, reduced motion, an exchange round with returns, the end of a round and of
   the match) with a stubbed layer; records the `play` calls, bubbles, in-flight counts, the held pile, the
   acknowledgement delay and when the round sheet shows.
2. Move: `useTableMotion` with `plan` = `planMovements` + `planSweep` as a `followUp` at `sweepStartMs` and its
   `spanMs`, `prepare` = the wait on the held closed pile (today's `while (holdDone)`), `onPlayed` = in-flight
   counts and hold scheduling; bubbles stay outside the queue as today; `motion.play(...)` for
   `planExchangeFlights`; `defineAnchors<PresidentAnchor>` for its six kinds; `speedFactor` from the module;
   `usePresidentEndSequence` keeps `endKind` and uses `useTableSettled` for the wait; the displayed state of #294
   on `useDisplayedState` if it is not already.
3. `sourcesFor`, `planSweep`, `trickView`, `exchangeView` stay President's.
4. Proof: characterisation test, `PresidentGameScreen.test.ts`, every `president-*.cy.ts`, before/after captures.

Files: `view/president/PresidentGameScreen.vue`, `composables/usePresidentEndSequence.ts`,
`model/president/motion.ts`, `model/president/exchangeMotion.ts`, `model/president/tempo.ts`, their tests.

### Chunk 3: Bullshit

1. Characterisation test first: the keyframes, durations, delays, easing, z-index and count of the ghosts that
   `flyCardsToPile` and `flyPileToTaker` launch today, and when the end screen shows (extends
   `BullshitGameScreen.pile.test.ts` and `.end.test.ts`).
2. Move: `model/bullshit/motion.ts` (+ test) plans my discard (from the measured rects of my selected cards to the
   pile, `landing: { tiltDeg, fade: true }`, Bullshit's easing, 380 ms, 70 ms apart) and the pile to its taker (up
   to 3 backs, `landing: { tiltDeg, fade: true, scaleTo: 'centre' }`, 460 ms, 90 ms apart), no marks, no arrows;
   played by `CardMotionLayer` (`zIndex` 3000) through `motion.play`; the end screen's `settling` ends at `endsAt`.
   No speed button.
3. Goes (emptied, listed): `composables/usePileAnimation.ts` and its test (its cases move to the motion test).
4. Proof: characterisation tests, `bullshit-pile-animation.cy.ts`, `bullshit-end-screen.cy.ts`,
   `bullshit-hotkeys-and-take.cy.ts`, before/after captures.

Files: `view/bullshit/BullshitGameScreen.vue`, `composables/usePileAnimation.ts`, new `model/bullshit/motion.ts`,
their tests.

### Chunk 4: integration

- `docs/architecture/add-a-game.md` and `game-conventions.md`: "animate a table" points to `animation/README.md`;
  `context-map.md` frontend notes if they name the old files.
- README of the module checked against the final API (the 30-line example compiles: it becomes
  `animation/example.test.ts`, a tiny fake table driven by a log).
- Every suite and every Cypress spec of Kobo, President, Bullshit, Mangola (its speed button moved) on one shifted
  stack; Opus review of the whole module and the three migrations against the spec.
- The list of emptied files for `git rm`; roadmap issue #1 commented by the orchestrator.

## Parallelisation and conflict files

- **Shared interfaces first:** chunk 0 alone (it moves files every screen imports).
- **Independent, each in its own worktree with its own sub-agent:** chunks 1, 2, 3. They touch disjoint game files.
- **Integration last:** chunk 4.

Files at risk of conflict:

| File | Who | Rule |
|------|-----|------|
| `frontend/src/animation/index.ts` | 0, 4 | migration chunks do not edit it; a missing export is a bug of chunk 0, fixed there in a follow-up commit on `main` |
| `frontend/src/animation/vue/CardMotionLayer.vue` | 0 (5.4 changes), 3 (if a detail of Bullshit's look needs one more option) | chunk 3 adds only optional props or fields, after rebasing on 1 |
| `frontend/src/animation/vue/useTableMotion.ts` | 0; 1 and 2 may find a gap | a gap is additive only and announced in the PR; the second chunk rebases |
| `frontend/src/view/president/PresidentGameScreen.vue` | 0 (imports), 2, PR #293, PR #294 | chunk 2 starts after #293 and #294 merge |
| `frontend/src/view/kobo/KoboGameScreen.vue`, `state/Kobo.store.ts` | 0 (imports), 1 | sequential |
| `frontend/src/view/bullshit/BullshitGameScreen.vue`, `composables/usePileAnimation.ts` | 0 (imports), 3 | sequential |
| `docs/architecture/*.md` | 4 only | |

## Risks (spec section 10)

Timing details lost in the move (guarded by characterisation tests and fake clocks), import churn against open PRs
(chunk 0 alone, chunk 2 after #293 / #294), an orchestrator that grows game options (escape hatches only:
`prepare`, `followUps`, `spanMs`, `onPlayed`, `play`), Bullshit's look drifting (optional `landing` / `easing` /
`zIndex`, keyframe characterisation), happy-dom without `Element.animate` (`canAnimate` guard, stubs).

## Out of scope (spec section 9, owner decision, canvas first)

Kobo's speed button, opponents' flights in Bullshit, sliding pieces in Morris, one ring look for every table.
Bataille Corse's own animation system stays (its session waits for the picture by design).

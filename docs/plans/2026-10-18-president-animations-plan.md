# President animations and visual parity: plan

Spec: `docs/specs/2026-10-15-president-design.md`, section 11, decision 7 (owner feedback and canvas choice of
2026-10-18; canvas https://claude.ai/artifact/PYAx7CWL4NcxEDXTitPaGT, option B). Written by Claude Opus 5.5 on
`origin/main` at `e133ff7`.

**Goal:** the President table shows what happens as pictures, not lines of text: a play flies to the pile, a pass
pops a bubble, a closed trick says why on the pile itself, and the table looks like Kobo's (room, felt, rings,
arrows, score table) with Mangola's speed button. Frontend only: the server already sends every fact needed
(`PresidentEventData`: `seat`, `cards`, `count`, `outPlace`, `outForbidden`, `trickWinner`, `trickEndReason`); what
it does not say (the cards of a trick that the new state already shows empty) is taken from the previous state.

## Key decisions (spec §11, decision 7)

1. **Option B** of the canvas: the card waits on its source (dashed ring), flies about 700 ms, lands with a plain
   ring; an arrow for an opponent's move; bursts through Kobo's queue; passes and bubbles outside the queue.
2. **Trick indicator** instead of the covered combination: the number of cards in the trick next to the pile.
3. **Shared visuals**: extracted, never copied; Kobo and Mangola unchanged.
4. **Trick closed** is a picture: closing flash, the closing play dimmed on the pile with a reason badge until the
   next play; the text line stays as a secondary, screen-reader line.
5. **Reduced motion and Instant** behave like option C (still rings, no flight).

## Rules for every chunk

- Own worktree and branch from `origin/main` (`feat/president-animations-<n>`), a draft PR opened at launch with
  its real number, never merged without the owner. Rebase on `main` regularly.
- Strict TDD for the logic (models, event log, motion plan, queue): a failing test observed first. A component
  test for every new component.
- Every visible text through `frontend/src/locales/` (`presidentUi`, or a shared block for a shared component).
- `ui-change` rules: list what a changed component shows before the change, check it is all still visible after,
  screenshots before and after at 375 and 1280 px on a shifted-port e2e stack.
- Suites: `cd frontend && npm test && npx vite build`; Cypress `president-*` and `kobo-*` (and `mangola-*` when a
  shared part Mangola uses changes) on a shifted-port e2e stack. Backend untouched.
- Do not edit `model/president/selection.ts` or `hint.ts` (the 2-cut rules are changed in parallel).

## Chunks

Each is about a day and its own PR. Chunks 2 to 4 depend on chunk 1 only (2, the exchange flights, first) and can run in parallel (their shared
files are listed under Conflicts).

| Chunk | Title | Size | Needs | Content | State |
|-------|-------|------|-------|---------|-------|
| 1 | `feat: President animations, chunk 1 (shared table, visible plays and passes)` | L | - | this spec amendment and plan; shared room and felt tokens (Kobo and President); shared score table (Kobo final totals, President standings); the shared travelling-card layer, queue and movement types (`components/table/CardMotionLayer.vue`, `model/table/motion.ts`, `model/table/queue.ts`; Kobo's files become thin wrappers); the President event log in the store, derived from the event data and the previous state (`model/president/eventLog.ts`); `model/president/motion.ts` (movements and seat bubbles of an entry, scaled by the speed); visible plays (flight, rings, arrow), pass bubbles and a dimmed passed seat, forbidden-finish and completion bubbles; the trick-closed badge with its reason and the closing flash; the trick indicator; the cards left in each hand as a fan of card backs on every seat, its count dropping when the flight lands (owner, 2026-10-18); the shared speed button (`components/table/AnimationSpeedButton.vue`, Mangola moved onto it); Cypress `president-animations.cy.ts` | #264 |
| 2 | `feat: President animations, chunk 2 (exchange flights)` | M | 1 | **first item (owner, 2026-10-18): every giver physically sees the cards leave the hand**: at the deal of an exchange round the Scum's and Vice-Scum's best cards fly from their hand to the receiver, the give-back cards fly the other way, faces for the giver and the receiver, backs for the others, the hand counts and fans following the landing; the model is chunk 1's `model/president/exchangeMotion.ts` (`planExchangeFlights`, tested), this chunk wires it into the screen (watch on successive states, my hand cards as the source and destination for me, the received cards landing in my hand) and adds the Cypress; reload during the exchange shows the state as it is. Adapted to the merged server: the pairs stay listed all round and the phase leaves `EXCHANGE` with the last return (and at the deal when `giveBack=lowest`), so `planExchangeFlights` keys on the round and the pairs, not on the phase; the summary of the exchange (`PresidentExchangeRecap`, faces in my own pair) is read from the same state | #269 |
| 3 | `feat: President animations, chunk 3 (trick sweep toward the winner)` | M | 1 | when a trick closes, after the closing badge's hold, its cards (the closing play, and the plays of the closed trick kept from the previous state) sweep card by card toward the winner's seat (620 ms, 90 ms between cards), ring on the winner; the badge stays on the empty pile until the next play; queue order kept with the plays of a burst | #279 (draft): the sweep is held on the pile as a picture (a next play already in the state flies in once it is over); also the phone felt that never scrolls (see "Chunk 3 addendum") |
| 4 | `feat: President animations, chunk 4 (round sheet from the bottom)` | M | 1 | the end-of-round sheet (`PresidentRoundSummary`) enters from the bottom in the place of my zone, as Kobo's score sheet (`sheet-slot`), the table staying above it; the places shown one by one (a place badge popping on each seat in finishing order); the round and total columns and the ready checks in the same table look as Kobo's round scores (shared parts extracted from `KoboRoundScores` / `KoboScoreSheet`); reduced motion: no slide | #283 (draft): also the end sequencing (see "Chunk 4 addendum"); the shared extraction from Kobo's score sheet is left out |
| 5 | `feat: President animations, chunk 5 (polish)` | S-M | 2, 3, 4 | tempo tuning after a playtest; the "went out" place badge animation; sound (Kobo's toggle, if the owner wants it); a whole-round Cypress at each speed; 8 players at 375 px with flights | - |

## Chunk 1 in detail

Order of work (strict TDD for every model):

1. Spec amendment and this plan (first commit of the PR).
2. **Shared room and felt.** `:root` tokens in `App.vue` (`--table-room-bg`, `--table-felt-bg`,
   `--table-felt-border`, `--table-felt-shadow`) with the values Kobo has today; Kobo's screen reads them (same
   computed styles); President's screen reads them too and drops its own felt page background and the panel
   behind my zone (Kobo has none).
3. **Shared score table.** `components/table/TotalsTable.vue` from `KoboFinalTotals` (rank, name, total, an
   optional pill, my row tinted); `KoboFinalTotals` and `PresidentStandings` become thin wrappers (Kobo's markup,
   data-test names and look unchanged).
4. **Shared motion.** `model/table/motion.ts` (the generic movement type, `eventSpanMs`, `scaleMovements`),
   `model/table/queue.ts` (`createTempoQueue` with its bounds as a parameter), `components/table/CardMotionLayer.vue`
   (Kobo's layer with the mark durations as props and a `ring` movement kind: rings without a flight). Kobo's
   `motion.ts`, `queue.ts` and `KoboMotionLayer.vue` re-export or wrap them; their tests are not edited.
5. **Event log** `model/president/eventLog.ts`: one entry per table event (id, type, seat, cards, out of turn,
   forbidden finish, place, and the closure: winner, reason, closing cards, number of cards in the closed trick
   taken from the previous state). The store keeps the latest entries, newest first, and the previous state.
   No entry for the first state received.
6. **Trick view** `model/president/trickView.ts`: what the pile shows from the state and the latest entry (the top
   play, or the closed play with its badge until the next play) and the trick indicator's count.
7. **Motion plan** `model/president/motion.ts` and `tempo.ts`: the movements of an entry (cards from the seat or
   my hand zone to the pile cards, rings, a ring on the winner when the trick closes) and its bubbles (pass,
   out on a 2, four completed), scaled by the speed; nothing at Instant but the still rings. Also
   `model/president/exchangeMotion.ts` (`planExchangeFlights`: the exchange flights from two successive states,
   faces inside my pair only), tested here and played by the screen in chunk 2.
8. **Components.** `PresidentTrick` (indicator, closed badge and flash, the notes as a secondary line),
   `PresidentSeat` (bubble, dimmed when passed, the fan of card backs with its count: `components/president/PresidentHandFan.vue`,
   its geometry in `model/president/handFan.ts`; the count shown is the state's minus the cards still in flight,
   from `model/president/motion.ts`), `components/table/AnimationSpeedButton.vue` (Mangola's button,
   moved; Mangola uses it), the screen wiring (the layer, the queue, the bubbles, the speed button next to the
   round chip).
9. **Cypress** `president-animations.cy.ts`: a play flies (a copy and rings exist), a pass bubble shows, a cut
   shows the "Closed by a 2" badge, each seat's fan of backs and its count drop after a play, Instant has no copies; the existing President and Kobo specs unchanged and
   green.

## Conflicts

| File | Chunks / others | Handling |
|------|-----------------|----------|
| `view/president/PresidentGameScreen.vue` | 1 to 5 | in series where possible; 2, 3 and 4 touch different blocks (my zone, the pile, the exchange) |
| `components/president/PresidentTrick.vue` | 1, 3 | 3 after 1 |
| `model/president/motion.ts`, `tempo.ts` | 1, 3, 4 | one planner per event kind; 3 and 4 add cases |
| `locales/en.ts`, `Messages.ts` | every chunk, the parallel 2-cut work | add keys in their own blocks; rebase |
| `model/president/selection.ts`, `hint.ts` | the parallel 2-cut work | not touched here |
| `components/table/*`, `model/table/*` | 1, Kobo and Mangola work | shared: never change Kobo's or Mangola's behaviour; their tests stay as they are |

## Risks

- **Bursts of bots** (0.7 to 1.3 s per move): the queue drops to rings past 4 waiting or 3.5 s late, so the picture
  never lags far behind the game.
- **The closed play left on the pile** could be read as the play to beat: it is dimmed, carries the badge and the
  rule chips say "lead", and the next play replaces it.
- **Shared extraction** (Kobo's layer, Mangola's speed button): Kobo's and Mangola's tests and Cypress specs are the
  guard; they are not edited.

## Chunk 3 addendum (owner rule: the felt never scrolls inside itself)

At 8 players the phone felt scrolled mid-round (titles and Passed lines under the plates, the chips under the pile;
`president-table-fit` only measured the start of a round). Done in chunk 3, on a phone (under 768 px):

- **Plate**: the markers (Passed, Out, Out on a 2, Ready) sit on the fan of card backs, which a seat that is out no
  longer has (`PresidentSeat` `compact`, `PresidentHandFan` `reserve`); the count stays under it, the title line stays.
- **Pile**: the chips are on the right of the cards instead of under them (`PresidentTrick`, phone only).
- **My plate**: one line (name, cards, marker) instead of a stack.
- **Guard**: `president-table-fit` plays a few moves (a cut, a four of a kind, passes, and with 8 players a finish
  on a 2 and passes with a seat out) and asserts `scrollHeight <= clientHeight` on the felt and the page at 375x667,
  360x640 and 375x812 for 3, 4, 6 and 8 players, plus a worst case with a title line added to every plate.

## Chunk 4 addendum (owner feedback: the game finishes, then the round sheet, then the final scores)

Frontend only (the server already sends `lastRound` with the `FINISHED` state of the last round):

- **The table finishes first**: `usePresidentEndSequence` shows the sheet only when the tempo queue is empty
  (`createTempoQueue(...).backlogMs()`, which includes the closing badge and the sweep), no held picture remains, and a
  settle pause (`PRESIDENT_TEMPO.roundEnd.settleMs`, scaled by the speed) has passed with nothing new. Instant and
  reduced motion have no waits.
- **The round sheet always comes before the final scores** (`model/president/endSequence.ts`, `endKind`): on the last
  round it stays, with a "Final scores" action instead of Ready; the final scores never appear by themselves. A forfeit
  has no round to show and goes to the final scores after the settle. A reload on an ended game shows its sheet, or the
  final scores, at once.
- **The sheet** rises from the bottom in the place of my zone (`sheet-slot`), the places coming in one by one.
- Left out: the shared parts extracted from Kobo's score sheet.

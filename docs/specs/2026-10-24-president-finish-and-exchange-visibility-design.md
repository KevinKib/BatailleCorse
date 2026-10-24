# President: seeing players finish and seeing the exchange (design)

Written by Claude Opus 5.5 on `origin/main` at `1edfe1d` (2026-10-24). Plan:
`docs/plans/2026-10-24-president-finish-and-exchange-visibility-plan.md`. Frontend only: no backend change is needed
(section 9).

## 1. Owner feedback and decisions

Two items of owner feedback, explored on the Claude Design canvas "Président : sorties et échange"
(https://claude.ai/artifact/VBNhvQEchfDR1pczaqEABU):

- **Item 2, "we don't see when people finish".** Today a seat that goes out loses its fan and shows the marker
  "Out, place 1" (0.56 rem on a phone, about 9 px) in its place, and its name is dimmed to 0.7: it reads as
  "inactive", not as a success. No bubble says it (`planBubbles` knows Pass, Out on a 2 and Four completed only).
  **Owner's choice: option A alone** (not A + B, which the canvas recommended): a gold medal "1st" in the fan's slot,
  a gold bubble "Out · 1st" over the seat, and the name no longer dimmed.
- **Item 3, "we don't see when the Scums give their cards".** Today the exchange flights of every pair leave at once
  (60 ms apart) right after the round sheet, while the eye is on one's own hand; the recap is a 0.75 rem line over
  the action bar on a wide screen and, on a phone, a banner that draws only my own pair (a Neutral on a phone sees no
  recap at all); the exchange panel's line of pairs is one truncated 1 rem line on a phone.
  **Owner's choice: option B** (the board "The exchange" in the pile's slot, with the 3 s bar), **with the pair by
  pair sequencing of option A**: the canvas recommendation for item 3 reads "B + le séquencement de A", so the
  sequencing is in scope (not a decision left open).

Interpretation to confirm (plan, decision 5): the owner's note for 2A says "name more greyed". The canvas option A
says "Le nom n'est plus grisé : contour doré" (the name is no longer dimmed: a gold outline). This spec reads the note
as "no more greyed", per the canvas.

## 2. Key decisions

1. **Finishing is told when the last card has landed**, through the existing displayed-state layer: the bubble
   "Out · 1st" pops in `onReveal` (where Pass and Four completed already pop), the medal reads the shown state. No new
   timer, no new queue.
2. **The medal takes the fan's reserved slot** (a seat that is out has no backs any more): a gold disc the height of
   the fan with the ordinal ("1st") and the word "Out". The seat label keeps full opacity and gets a thin gold
   outline. A forbidden finish ("Out on a 2", no place yet) keeps today's red marker and its bubble: no medal.
3. **The board "The exchange" replaces the trick plate** in `.table-center` while the shown phase is `EXCHANGE` or
   `EXCHANGE_VIEW` (the trick is empty then), absolutely over the plate, which stays laid out but hidden: nothing
   moves. It replaces the exchange recap (wide line and phone banner) and the exchange panel's line of pairs; it does
   not replace the panel's role, instruction and buttons, nor the action bar's phase line.
4. **Pairs are exchanged one after the other**, top pair first: `planExchangeFlights` offsets each pair after the
   previous one has landed (plus a gap), with the whole picture capped (pairs overlap past the cap). A board row says
   "done" only once its flights have landed (the screen keeps the legs in flight; a reload or Instant has none).
5. **One new polite list region** announces the table's news to assistive technology (a seat finishing, each leg of
   the exchange); the existing `president-live` region keeps "Your turn" alone.

## 3. Invariants this design keeps

| Invariant | How it is kept |
|---|---|
| Stillness (reserved slots, no layout shift; `president-stillness.cy.ts`, `president-table-fit.cy.ts`, 8 players at 360x640) | The medal lives in the fan's reserved `fan-picture` box (it never exceeds its height: 23 px on a phone, 32 px wide); the bubble is absolute (existing layer); the board is absolute in `.table-center` over the trick plate, which keeps its box (`visibility: hidden`); the recap that floated over the controls is gone; the panel's controls keep their fixed height (`--controls-h`). |
| Texts follow the animations (`useDisplayedState`, #294) | Bubble and announcement in `onReveal`; the medal and the label read `shown`; a board row turns "done" when its flights land; the board leaves when the shown phase is `PLAYING`. |
| Exchange sync acknowledgement (#289-#291) | Unchanged flow: the acknowledgement is armed at `ackDelayMs({ flightsEndMs, factor })`, `flightsEndMs` now the end of the sequenced flights; the 3 s bar on the board draws that reading time; the board's "Seen" button replaces the recap's cross (acknowledges at once). Reload: armed when the board shows in `EXCHANGE_VIEW`. |
| Shared animation module (`frontend/src/animation`) | Flights through `motion.play` (same queue, `useTableMotion`), anchors through `defineAnchors` (no new anchor), `beforePlay` and `whenSettled` untouched; the only change to the module is an optional `vars` on seat bubbles (generic, no game text). |
| Soft rings | Unchanged: the layer keeps `mark-style="soft"`. The canvas's "glow" rings and seat highlights for the exchange are not adopted. |
| Tempo queue | The sequenced exchange is one plan admitted through the queue (its span is the sequence's); a give-back during `EXCHANGE` is its own plan, queued behind. |
| Instant, Fast, reduced motion | Section 7. |
| Visible text through `frontend/src/locales/` | Section 8. |

## 4. Item 2: a seat finishing (option A)

### 4.1 What the table shows

| Moment | Opponent's plate | My plate |
|---|---|---|
| Last card in flight | Unchanged (backs, count) | Unchanged |
| Last card landed (the move is told) | Bubble "Out · 1st" (gold, as "Four completed") pops over the plate for `bubbleMs`; the fan's slot shows the medal: a gold disc "1st" (ordinal, the disc as tall as the fan) and "Out" beside it; the label is at full opacity with a 1 px gold border | Bubble "Out · 1st" over my plate; the marker beside my name becomes a compact medal (disc 18 px with "1st", then "Out") |
| Rest of the round | Medal and outlined label stay | Medal stays |
| Forbidden finish ("Out on a 2") | Today's bubble "Out on a 2" and red marker; no medal, no gold outline (the label is not dimmed either) | Same |
| Round over | The round sheet, unchanged | Unchanged |

The medal carries `data-test="seat-out"` (as the marker it replaces) and `data-place`; its accessible text is
"Out, 1st place" (the visible "1st" and "Out" are `aria-hidden`, a visually hidden span says it). The gold outline
must stay distinct from the turn ring (`--turn-ring-color` plus `--turn-ring` shadow): border only, no shadow, and a
seat that is out is never the current one.

A move that completes the four out of turn and empties the hand shows **"Out · 1st" only** (the closing badge
"Four of a kind" on the pile already tells the four; two bubbles on one seat 200 ms apart would replace each other).

### 4.2 Model and components

- `model/president/motion.ts`: `SeatBubble.kind` gains `'out'` and an optional `place`; `planBubbles(entry)` returns
  `{ seat, kind: 'out', place: entry.outPlace }` when `entry.outPlace !== null && !entry.outForbidden`, instead of
  `completed`. Pass and Out on a 2 unchanged.
- `animation/vue/useSeatBubbles.ts` (shared): `SeatBubble<K>` and `ShownBubble<K>` gain an optional
  `vars?: Record<string, string | number>`, passed through untouched (no text in the module). The screen passes
  `{ place: ordinal(place) }`.
- `components/president/PresidentSeat.vue`: the bubble text is `format(ui.motion.bubbles[kind], bubble.vars ?? {})`;
  `seat-bubble--out` styled as `--completed` (gold); `.president-seat--out .seat-label { opacity: 0.7 }` becomes
  `.president-seat--finished .seat-label { border-color: var(--gold) }` (finished = out and not forbidden), and a
  forbidden finish keeps full opacity too.
- `components/president/PresidentSeatMarkers.vue`: `player.out && !forbiddenFinish && place !== null` renders the
  medal (wide or compact through `narrow`/the side variant); otherwise today's markers.
- `view/president/PresidentGameScreen.vue`: `onReveal` passes the bubble's `vars` and announces the finish (5.8).

## 5. Item 3: the exchange board (option B, sequenced as option A)

### 5.1 Where it sits and what it replaces

The board is `components/president/PresidentExchangeBoard.vue`, rendered inside `.table-center`
(`.table-center--board` while it shows), `position: absolute; inset: 0`, over `PresidentTrick`, which stays mounted
and laid out with `visibility: hidden` (its box keeps the felt's layout; the pile anchors stay resolvable).
`.table-center` gets `position: relative` on a phone (it is `static` there today; this moves nothing). The board
shows while `shown.phase` is `EXCHANGE` or `EXCHANGE_VIEW` and the round has pairs; it leaves when the shown phase is
`PLAYING` (or the round ends), so the first trick starts in the same place.

| Today | With the board |
|---|---|
| `PresidentExchangeRecap` over the action bar (wide) and its fixed banner under the HUD (phone), with its cross, the 8 s phone timer, `recapHeld`, `closedRecapRound` | **Removed** (component, its test and `model/president/exchangeRecap.ts`); the board says it, for every seat, on every screen |
| The exchange panel's list of pairs (`exchange-pair`, one truncated line on a phone) | **Removed** from the panel; the board's rows say it |
| The panel's role line, received line, instruction, Give / Suggest / Clear | **Kept** (what I do stays where I do it) |
| The action bar's phase line during `EXCHANGE_VIEW` ("The exchange", "Waiting for Bob to see the exchange") | **Kept** |
| The trick plate "Nothing played yet" and the rule chips during the exchange | **Hidden** under the board (they come back with the first trick) |
| The "New" tag on received cards in my hand | **Kept** |

### 5.2 Content

- **Header** (one line): "The exchange" (gold caps, the board's `aria-label` too), then, when I am in no pair,
  "You are Neutral: no exchange for you" (a Neutral on a phone now sees the exchange); at the right, during
  `EXCHANGE_VIEW` until I have acknowledged, the **Seen** button (icon `pi-check`, visible 24 px, 44 px hit area,
  `aria-label` "I have seen the exchange"): it acknowledges at once (the recap's cross did). Its place is kept
  (`visibility: hidden`) when it does not apply.
- **One row per pair**, in the server's order (top pair first): the low seat (name, title), the two legs, the high
  seat (name, title), a state mark.
  - Leg 1, "best cards": `n` mini cards (backs; faces in my own pair: my `given` as the low, my `received` as the
    high) and an arrow toward the high seat.
  - Leg 2, "given back": the same, toward the low seat; before the high seat has given: "{high} is choosing {n}"
    ("You choose {n}" when the high seat is me).
  - State mark: pending (dashed circle) or done (check, `--accent-positive`), with `data-state` =
    `giving | choosing | returning | done` and `aria-label`.
- **Footer**: the reading bar (3 px, gold), only in `EXCHANGE_VIEW`, filling over `SUMMARY_READ_MS × factor` from the
  moment the last flight lands (the same moment the acknowledgement timer counts from). Once full it stays full while
  the table waits for the others (the phase line says for whom).

Row states, from the latest state and the legs still in flight:

| State | Meaning | Leg 1 | Leg 2 |
|---|---|---|---|
| `giving` | best cards flying (deal) | minis, arrow animating nothing (the flights do) | "{high} is choosing {n}" dimmed |
| `choosing` | best cards landed, return not given | minis | "{high} is choosing {n}" / "You choose {n}" |
| `returning` | given back, cards flying | minis | minis |
| `done` | return landed | minis | minis, check |

### 5.3 Size at every screen

The board fills exactly the box `.table-center` already has (wide: `min(27rem, 54%)` wide, about 150 px tall at
1280x720; phone: the felt's height under the opponents, about 100 px with 8 players at 360x640). Rows are one line
each (about 22 px on a phone, 26 px wide). The board is a size container: below a content height that fits header,
rows and bar, the header's title becomes visually hidden (still its `aria-label`) and the Neutral note joins the rows'
area; past that, the rows scroll **inside the board** (never the felt, never the page). Required to fit without any
scroll: `TWO` (2 pairs) and `ONE` at every screen and table size of `president-table-fit.cy.ts`, including 8 players
at 360x640; `SCALED` with 8 players (4 pairs) at 375x667 and wider. Tolerated: `SCALED` with 8 players at 360x640
scrolls inside the board.

Mini cards: 12x17 px on a phone, 14x20 px wide, overlapping by half past two cards (a leg of 4 cards stays under
40 px).

### 5.4 Pair by pair sequencing

`planExchangeFlights(before, after, mySeat, factor)`:

- At the deal (a new round): pair `k` starts once pair `k - 1`'s last card has landed, plus `exchange.pairGapMs`. In a
  pair: its best cards (stagger `cardStaggerMs` counted from the pair's own start, no longer across pairs); with
  give-back `lowest`, its return right behind (`opponent.flightMs` later, as today), before the next pair.
- A return during `EXCHANGE` (a high seat gave back): its own plan, as today; several pairs newly done in one state
  (a forfeit) are sequenced the same way.
- Cap: when the sequence would last longer than `exchange.maxSpanMs` (Normal), every pair's start is scaled down by
  the same factor so that the last pair ends at the cap (pairs overlap). Then `factor` scales everything, as today.
- `exchangeLandings(after, moves)` (new, pure): for each leg (`best` low to high, `back` high to low) of each pair,
  when its last card lands (ms from the plan's start). Leg keys: `exchangeLegKey(pair, leg)` = `"{high}-{low}:{leg}"`.

### 5.5 Legs in flight (texts follow the picture)

The screen keeps `flyingLegs: Set<string>`. In the `stateChange` watcher (pre-flush, before the render of the new
state), every leg the change makes fly is added at once; once the plan is admitted, each leg is removed at
`admission.startInMs + landsAt` (never later than `countLagMaxMs` after the state). With no motion (Instant, reduced
motion, rings only) nothing is added. The board's model reads the latest state and `flyingLegs`, so a row never says
"done" before its cards have landed, and a reload (no change, nothing in flight) shows the state as it is.

### 5.6 The acknowledgement

As today (#289-#291): `armAcknowledgement(ackDelayMs({ flightsEndMs, factor }))`, armed by the change that opens the
view (`flightsEndMs` = the end of the sequenced flights and their rings), or, without one (a reload), when the board
shows in `EXCHANGE_VIEW` (`flightsEndMs` = 0). The screen also gives the board `reading = { startsInMs: flightsEndMs,
durationMs: SUMMARY_READ_MS × factor, key: round }` to draw the bar. The Seen button calls `acknowledgeExchange()`.
Nothing about the server barrier or its 20 s timeout changes.

### 5.7 Model and components

- `model/president/exchangeBoard.ts` (new, pure): `exchangeBoard(state, mySeat, flyingLegs)` returns
  `{ rows: ExchangeBoardRow[]; neutral: boolean } | null` (null outside `EXCHANGE` / `EXCHANGE_VIEW` or with no pair).
  `ExchangeBoardRow = { key, high, low, count, mine, state, best: Card[] | null, back: Card[] | null }` (faces only in
  my pair, as `exchangeRecap` today, which it supersedes).
- `components/president/PresidentExchangeBoard.vue` (new): props `board`, `nameOf`, `titleOf`, `mySeat`, `canSee`
  (show the Seen button), `reading` (`{ startsInMs, durationMs, key } | null`), `still`; emits `seen`.
- `components/president/PresidentExchangePanel.vue`: the pairs list and its `pairLines` go.
- `model/president/exchangeMotion.ts`: section 5.4.
- `view/president/PresidentGameScreen.vue`: board wiring, `flyingLegs`, `reading`, the recap's code removed
  (`recap`, `showRecap`, `closedRecapRound`, `recapHeld`, `RECAP_PHONE_MS`, `recapTimer`, `closeRecap`, the
  `.recap-slot`), the reload arming moved from `showRecap` to the board's visibility.

### 5.8 Accessibility

A new list region in the screen, `<ul class="sr-only" aria-live="polite" aria-relevant="additions"
data-test="president-announcements">`, fed by `composables/useLiveAnnouncements.ts` (new: `announce(text)` appends an
item, the last 3 are kept, `cancel()` on unmount). It says:

| When | Text |
|---|---|
| An opponent's finish is told | "Bob is out, 1st" |
| My finish is told | "You are out, 1st" |
| A best-cards leg lands | "Carol gave their 2 best cards to Bob" (counts; in my pair with the cards: "You gave the ace of clubs and the 2 of spades to Bob" / "Carol gave you the king of hearts and the ace of clubs") |
| A given-back leg lands | "Bob gave 2 cards back to Carol" (my pair: with the cards) |
| Instant / reduced motion / reload | The exchange legs are not announced one by one: the board region (named "The exchange", rows with full text) is read as it is |

The board is a `role="region"` with its rows in a list; each row has a full sentence for screen readers (the visible
row is abbreviated). The medal's text is section 4.1.

## 6. What is not adopted from the canvas

- Option 2B (the announcement in the pile's slot) and 2C (the arrival rail): owner chose 2A alone.
- Option A's bubbles for the exchange ("Gives 2", "+2"), its HUD chip "The exchange · 1 of 2" and its "glow" rings:
  the board says it, and rings stay soft.
- Option B's coloured pair dots and seat glows: soft rings; the board names the seats.
- The medal's "title to come" (the canvas note): the title line already shows the current title; a second title would
  confuse. The round sheet says the new titles.
- A sound or a vibration (canvas "option facultative"): not asked.

## 7. Speeds and reduced motion

| | Normal | Fast | Instant | Reduced motion (Normal speed) |
|---|---|---|---|---|
| Finish bubble | at landing, `bubbleMs` | at landing, half | at once, no pop | at once, no pop |
| Exchange flights | pair by pair | pair by pair, half durations | none | still rings, all at once |
| Board rows "done" | at each leg's landing | same | at once | at once |
| Reading bar | 3 s after the last landing | 1.5 s | not drawn (acknowledged at once) | not drawn; the 3 s still hold (the sync spec 7.3: reduced motion lowers movement, not reading time) |
| Announcements | per leg, at landing | same | not per leg | not per leg |

## 8. Locales (`frontend/src/locales/en.ts`, `Messages.ts`)

New (names indicative; chunk 0 adds them all):

- `locales/format.ts`: `ordinal(n, forms)` with `OrdinalForms { one; two; few; other }` (English ordinal rules through
  `Intl.PluralRules('en', { type: 'ordinal' })`); `presidentUi.ordinal = { one: '{n}st', two: '{n}nd', few: '{n}rd',
  other: '{n}th' }`.
- `presidentUi.motion.bubbles.out: 'Out · {place}'`.
- `presidentUi.table.medal: { out: 'Out', label: 'Out, {place} place' }`.
- `presidentUi.exchange.board`: `title: 'The exchange'`, `neutral: 'You are Neutral: no exchange for you'`,
  `choosing: '{player} is choosing {n}'`, `youChoose: 'You choose {n}'`, `seen: 'I have seen the exchange'`,
  `states: { giving, choosing, returning, done }` (accessible names), `row` (one, other: the full sentence per pair).
- `presidentUi.announce`: `out: '{player} is out, {place}'`, `outYou: 'You are out, {place}'`, `best` / `bestMine` /
  `bestToMe`, `back` / `backMine` / `backToMe` (plural forms, with `{cards}` for my pair).

Removed by the chunks that remove their use: `presidentUi.exchange.recap.*`, `exchange.pairs`, `exchange.pair`,
`exchange.pending`, `exchange.done`; `table.out` stays (screen readers' fallback is the medal label) only if still
used, else removed with its last use.

## 9. Why frontend only

Everything the two items need is already on the wire: `players[].out`, `place`, `forbiddenFinish`, the event's
`outPlace`, the exchange pairs with `done`, my `given` / `received`, `EXCHANGE_VIEW` and `sawExchange`. The sequencing
is a picture: the server keeps answering at once.

## 10. Numbers (current / proposed)

Every number below is in `model/president/tempo.ts` or the screen. Lines marked **changed** need the owner's accord.

| Parameter | Current | Proposed | |
|---|---|---|---|
| Start of an exchange pair at the deal | all pairs together, cards `60 ms` apart across pairs | pair `k` after pair `k - 1` has landed + `exchange.pairGapMs` | **changed** |
| `exchange.pairGapMs` | (none) | `250 ms` | **new** |
| `exchange.maxSpanMs` (cap of the sequenced deal, Normal) | (none) | `6000 ms` | **new** |
| `cardStaggerMs` inside a pair | 60 ms (counted across all pairs) | 60 ms (counted inside the pair) | **changed** (scope) |
| Flight of an exchange card | 700 ms (opponent) / 480 ms (own), hold 180 / 100 before, 250 after | same | |
| Return at the deal (`lowest`) | `opponent.flightMs` after the best cards | same, inside its pair | |
| `countLagMaxMs` (counts and my hand wait for exchange cards) | 2000 ms | 6500 ms (covers the sequenced deal) | **changed** |
| `SUMMARY_READ_MS` (reading time before the acknowledgement) | 3000 ms × speed | same; drawn by the bar | |
| `RECAP_PHONE_MS` (phone banner) | 8000 ms | removed (no banner) | **changed** |
| Finish bubble life | (no bubble) | `bubbleMs` = 1400 ms × speed | **new** (existing value) |
| Announcements kept in the list region | (none) | 3 | **new** |
| Medal disc | (none) | the fan's height: 23 px phone, 32 px wide; 18 px on my plate | **new** |

Durations at Normal, 4 players, `TWO`: today about 1.0 s for the whole deal; proposed about 2.3 s (pair 1: 180 + 60 +
700 = 940 ms; gap 250; pair 2: 880 ms; holds). With `lowest`: about 3.7 s (today about 1.9 s). `SCALED` with 8 players and `lowest` hits
the 6 s cap.

## 11. Tests

| Level | What |
|---|---|
| Vitest, shared | `useSeatBubbles`: `vars` passed through and replaced with the bubble; `format.ordinal` (1st 2nd 3rd 4th 11th 12th 13th 21st 22nd); `useLiveAnnouncements` (append, keep 3, cancel) |
| Vitest, finish | `motion.test.ts`: `planBubbles` gives `out` with the place, `out` instead of `completed` on a completion that empties the hand, only `outForbidden` on a forbidden finish; `PresidentSeat.test.ts` / markers: the medal for an out opponent (text, `data-place`, accessible label), no medal on a forbidden finish, label not dimmed and outlined, bubble text "Out · 1st"; mine compact medal; `PresidentGameScreen.motion.test.ts`: the bubble and the announcement come at the reveal, not at the state |
| Vitest, exchange | `exchangeBoard.test.ts`: null outside the exchange; rows and states for the deal (flying, landed), choosing, returning, done; faces only in my pair; neutral; forfeit (a pair gone); a reload (nothing in flight); `exchangeMotion.test.ts`: pairs one after another, `lowest` inside its pair, the cap, the factor, `exchangeLandings`; `PresidentExchangeBoard.test.ts`: header, Neutral note, rows (minis, faces, choosing line), Seen button (shown only with `canSee`, emits), the bar (`reading` durations as CSS, none when `still`), aria; `PresidentExchangePanel.test.ts`: no pairs list; `PresidentGameScreen.test.ts`: the board in `.table-center` during `EXCHANGE` and `EXCHANGE_VIEW`, gone in `PLAYING`, no recap, Seen acknowledges, the acknowledgement timer per speed (the recap tests move to the board), a row stays pending until its leg lands (fake timers) |
| Cypress | New `president-finish.cy.ts`: a guest goes out, the bubble "Out · 1st" shows once the flight is over, the medal "1st" in the fan, the label at full opacity; screenshots 375x667, 360x640 with 8 players, 1280x720. New `president-exchange-board.cy.ts`: 4 players `TWO`, the board in the pile's slot, row 2's best leg still `giving` while row 1 has landed, rows done, the bar, the Seen button acknowledges, the board leaves with the first trick; a Neutral at 375x667 sees both rows; 8 players `TWO` at 360x640 and `SCALED` at 375x667 without overflow; screenshots at 360x640, 375x667, 375x812, 1280x720. Updated: `president-stillness.cy.ts` (the board comes in with the exchange, the medal when going out, the board leaves; the recap steps go), `president-table-fit.cy.ts` (medal and board at 8 players 360x640), `president-exchange-flights.cy.ts`, `president-exchange-sync.cy.ts`, `president-exchange.cy.ts`, `president-game.cy.ts` (recap and `exchange-pair` assertions move to the board; "Out, place 1" to the medal) |

## 12. Edge cases

| Case | Behaviour |
|---|---|
| Round 1, or exchange `NONE` | No board (no pair), the trick plate as today |
| A seat forfeits during the exchange | The rows follow the state (a pair the server settles turns `returning` / `done`; a pair gone leaves) |
| A high seat gives back while the deal is still sequencing | Its return is a plan of its own, queued behind the deal; its row turns `returning` then `done` at landing |
| Reload during `EXCHANGE` / `EXCHANGE_VIEW` | Board from the state, nothing in flight; in the view the bar starts from 0 and the acknowledgement is armed as today |
| The first lead is a bot that plays at once after the view | The `PLAYING` state is shown (no move before it), the board leaves, then the play flies to the pile |
| The last seat holding cards | The round ends with the move; its last place is told by the round sheet (no medal, as today) |
| A forbidden finish | No medal, no `out` bubble; today's marker and "Out on a 2" bubble |
| A long name | Truncated in the row with an ellipsis (the row's full sentence is for screen readers) |
| Me as the high seat choosing | Leg 2 reads "You choose {n}"; the panel's instruction and buttons stay |

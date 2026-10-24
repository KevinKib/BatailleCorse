# President finish and exchange visibility: plan

Spec: `docs/specs/2026-10-24-president-finish-and-exchange-visibility-design.md`. Written by Claude Opus 5.5 on
`origin/main` at `1edfe1d` (2026-10-24). Canvas: https://claude.ai/artifact/VBNhvQEchfDR1pczaqEABU.

**Goal:** a seat that finishes is seen finishing (gold medal "1st" in its fan's slot, bubble "Out · 1st", name not
dimmed), and the exchange is seen by everyone (the board "The exchange" in the pile's slot, pairs exchanged one after
the other, the 3 s reading bar). Frontend only.

## Key decisions (spec section 2)

1. **Item 2 = option A alone**: medal in the fan's reserved slot, bubble "Out · 1st" told when the last card lands
   (`onReveal`, the displayed-state layer), the label at full opacity with a gold border; a forbidden finish keeps
   today's red marker and bubble.
2. **Item 3 = option B with option A's pair by pair sequencing** (the canvas's recommendation for item 3): the board
   sits absolutely over the trick plate in `.table-center` during `EXCHANGE` and `EXCHANGE_VIEW` (the plate stays laid
   out, hidden: nothing moves), and leaves when the first trick starts.
3. **The board replaces** the exchange recap (wide line, phone banner, its cross and 8 s timer) and the exchange
   panel's line of pairs; the panel's instruction and buttons and the action bar's phase line stay. The recap's cross
   becomes the board's Seen button (acknowledges at once).
4. **A row is "done" only when its flights have landed** (`flyingLegs` kept by the screen); the acknowledgement flow
   of #289-#291 is unchanged, `flightsEndMs` being the end of the sequenced flights; rings stay soft.
5. **Numbers to approve** (spec section 10): pairs one after another with a 250 ms gap, the sequenced deal capped at
   6 s, `countLagMaxMs` 2000 -> 6500 ms, the phone banner's 8 s removed.

**To confirm with the owner before implementation:**
- the note "name more greyed" read as "no longer greyed" (the canvas says "Le nom n'est plus grisé : contour doré");
- the Seen button on the board (keeps today's early acknowledgement of the recap's cross);
- the numbers table of spec section 10 (rule: nothing numeric is implemented before the accord on that table).

## Rules for every chunk

- Own worktree and branch from `origin/main`, a numbered draft PR at launch (`gh pr create --draft`), French PR body
  from a UTF-8 file. Rebase on `origin/main` before the final push.
- Strict TDD: each new test is run alone and seen failing for the right reason before the code.
- Visible text through `frontend/src/locales/` only; every key is added by chunk 0 (chunks only use them and remove
  the keys whose last use they remove).
- Suites before ready: `cd backend && mvn -s ../deploy/docker/settings.xml test`; `cd frontend && npm test && npx
  vite build`. Cypress as each chunk says, on a shifted-port e2e stack with its own compose project name.
- No `rm` / `git rm` in a chunk: a removed file is emptied to a one-line comment and listed in the report (the
  orchestrator removes it).
- Frontend only: no chunk needs the owner's backend merge accord (the merge-autonomy exception is for President
  domain backend work).

## Chunks

| Chunk | Title | Size | Needs | Runs with | Model |
|---|---|---|---|---|---|
| 0 | `feat: President finish and exchange visibility, shared contract` | S (< 30 min) | - | first, alone | default (Sonnet) |
| F | `feat: President finish medal and bubble` | M | 0 | E1, E2 | default (Sonnet) |
| E1 | `feat: President exchange board` | M-L | 0 | F, E2 | default (Sonnet) |
| E2 | `feat: President exchange pair by pair` | S-M | 0 | F, E1 | default (Sonnet) |
| I | `feat: President exchange board, sequenced end to end` | M | F, E1, E2 | last, alone | default (Sonnet), Opus branch review |

Merge order: 0, then F, E1, E2 in any order (each is self-contained and green on its own: E1 ships the board with
"nothing in flight" until I connects the landings; E2 ships the sequencing with the current recap until E1 lands),
then I.

### Chunk 0: shared contract (no behaviour change)

- `animation/vue/useSeatBubbles.ts`: optional `vars?: Record<string, string | number>` on `SeatBubble<K>` and
  `ShownBubble<K>`, passed through (test in `useSeatBubbles.test.ts`); `animation/README.md` one line.
- `locales/format.ts`: `ordinal(n, forms)` and `OrdinalForms` in `Messages.ts` (test in `format.test.ts`).
- `locales/Messages.ts` and `en.ts`: every new key of spec section 8 (`ordinal`, `motion.bubbles.out`, `table.medal`,
  `exchange.board.*`, `announce.*`); nothing removed (`en.test.ts` follows).
- `model/president/tempo.ts`: `exchange: { pairGapMs: 250, maxSpanMs: 6000 }` and `countLagMaxMs: 6500` (once the
  owner has approved the table).
- `model/president/motion.ts`: `SeatBubble.kind` gains `'out'` and an optional `place` (type only; `planBubbles`
  unchanged).
- `model/president/exchangeBoard.ts`: the types `ExchangeBoard`, `ExchangeBoardRow`, `ExchangeRowState`, `ExchangeLeg`
  and `exchangeLegKey(pair, leg)` (implemented, tested); `exchangeBoard()` declared returning `null` (E1 implements).
- `model/president/exchangeMotion.ts`: `exchangeLandings(after, moves)` declared returning `{}` (E2 implements).
- `composables/useLiveAnnouncements.ts` (new, tested: append, keep 3, cancel) and its region in
  `PresidentGameScreen.vue` (`data-test="president-announcements"`, next to `president-live`), fed by nothing yet.

### Chunk F: the finish (spec section 4)

- `model/president/motion.ts`: `planBubbles` gives `{ kind: 'out', place }` (and no `completed` with it).
- `components/president/PresidentSeat.vue`: bubble text with `vars`, `seat-bubble--out` gold, `--finished` label
  (gold border, full opacity), no dimming on a forbidden finish.
- `components/president/PresidentSeatMarkers.vue`: the medal (fan variant, my side variant), `data-test="seat-out"`,
  `data-place`, accessible label.
- `view/president/PresidentGameScreen.vue`: in `onReveal`, `vars: { place: ordinal(...) }` and `announce(...)` for an
  `out` bubble (about 5 lines).
- Vitest: `motion.test.ts`, `PresidentSeat.test.ts` (+ markers), `PresidentGameScreen.motion.test.ts`.
- Cypress: new `president-finish.cy.ts`; update `president-game.cy.ts` (line 61) and `president-stillness.cy.ts`
  ("going out", line 164) to the medal; run `president-stillness`, `president-table-fit`, `president-trick-variants`,
  `president-animations`, `president-completion-offer`, `president-game`. Screenshots: 375x667, 360x640 with 8
  players, 1280x720.

### Chunk E1: the board (spec sections 5.1 to 5.3, 5.6, 5.7)

- `model/president/exchangeBoard.ts`: `exchangeBoard(state, mySeat, flyingLegs)` (test first: every state of the
  table of spec 5.2, faces only in my pair, neutral, forfeit, reload).
- `components/president/PresidentExchangeBoard.vue` and its test (header, Neutral note, rows, minis, choosing line,
  Seen button, bar from `reading`, `still`, aria, size container and inner scroll).
- `components/president/PresidentExchangePanel.vue`: the pairs list goes (test updated).
- `view/president/PresidentGameScreen.vue`: the board in `.table-center` (`position: relative` on a phone, the trick
  hidden under it), `reading` set in `armAcknowledgement`, Seen -> `acknowledgeExchange()`, reload arming moved to the
  board's visibility, `flyingLegs` as an empty set (I fills it); the recap's code and `.recap-slot` removed.
- Emptied: `components/president/PresidentExchangeRecap.vue`, `PresidentExchangeRecap.test.ts`,
  `model/president/exchangeRecap.ts`, `exchangeRecap.test.ts`; the keys `exchange.recap.*`, `exchange.pairs`,
  `exchange.pair`, `exchange.pending`, `exchange.done` removed.
- Vitest: the screen's recap tests move to the board (`PresidentGameScreen.test.ts`, the `recap=` line of the motion
  snapshot).
- Cypress: update `president-exchange-flights.cy.ts`, `president-exchange-sync.cy.ts`, `president-exchange.cy.ts`,
  `president-game.cy.ts` (exchange lines), `president-stillness.cy.ts` (the exchange scenario: the board comes in with
  the exchange, leaves with the first trick; the recap steps go), `president-table-fit.cy.ts` (board at 8 players
  360x640, no overflow); run every `president-exchange*`, `president-stillness`, `president-table-fit`.

### Chunk E2: pair by pair (spec section 5.4)

- `model/president/exchangeMotion.ts`: sequenced pairs, `lowest` inside its pair, the `maxSpanMs` cap, the factor;
  `exchangeLandings` implemented. Tests first in `exchangeMotion.test.ts` (offsets per pair, cap, factor, landings).
- No screen change: `playExchange` already computes its end from `eventSpanMs(moves)`; the counts and my hand already
  wait for each card (`countLagMaxMs` from chunk 0).
- Cypress: run `president-exchange-flights.cy.ts` and `president-exchange-sync.cy.ts` (unchanged assertions must pass
  with the longer deal; adjust only timeouts if a wait was tuned to the old 1 s).

### Chunk I: integration (spec sections 5.5, 5.8, 7)

- `view/president/PresidentGameScreen.vue`: `flyingLegs` filled in the `stateChange` watcher (pre-flush) from
  `exchangeLandings`, each leg removed at its landing (capped by `countLagMaxMs`); the exchange announcements at each
  leg's landing (not under Instant, reduced motion or a reload); `PresidentGameScreen.test.ts` with fake timers (a
  row stays pending until its leg lands; announcements per leg).
- Cypress: new `president-exchange-board.cy.ts` (spec section 11), the stillness scenario re-run on the sequenced
  exchange, screenshots at 360x640 (8 players), 375x667, 375x812, 1280x720; before/after captures of the exchange and of a finish in the PR.
- Every suite: backend `mvn test`, frontend `npm test` and `npx vite build`, every `president-*.cy.ts`.
- Docs: `docs/architecture` unchanged (no new context); spec status line; roadmap issue #1 commented.
- Opus review of the branch (all of 0, F, E1, E2, I) against the spec's invariants table.

## Parallelism and files at risk

F, E1 and E2 run in parallel after 0, each in its worktree with a sub-agent.

| File | Chunks | Risk and rule |
|---|---|---|
| `view/president/PresidentGameScreen.vue` | 0 (region), F (`onReveal`), E1 (exchange section, template `.table-center` and controls), I | Medium. F touches only the `onReveal` callback; E1 only the exchange section (from "The exchange (spec §11 decision 7)" to `closeRecap`) and the template's `.table-center` / `.controls`. Rebase and keep both. |
| `cypress/specs/president-stillness.cy.ts` | F ("going out" step), E1 (exchange scenario, recap steps), I | Low: different lines of the same scenario. |
| `cypress/specs/president-game.cy.ts` | F (line 61), E1 (exchange lines) | Low. |
| `cypress/specs/president-table-fit.cy.ts` | F (medal checks), E1 (board checks) | Low: separate assertions. |
| `model/president/motion.ts` | 0 (type), F | Low. |
| `model/president/exchangeMotion.ts` | 0 (declaration), E2 | Low. |
| `model/president/exchangeBoard.ts` | 0 (types), E1 | Low. |
| `locales/en.ts`, `Messages.ts` | 0 (adds), E1 (removes the recap keys) | Low: nothing else edits them. |
| `PresidentGameScreen.motion.test.ts` and its snapshot | F, E1 | Medium: regenerate the snapshot after the rebase, never hand-merge it. |

## Tests summary

| Chunk | Vitest | Cypress |
|---|---|---|
| 0 | `useSeatBubbles`, `format` (ordinal), `useLiveAnnouncements`, `exchangeLegKey`, `en.test.ts` | none |
| F | `motion`, `PresidentSeat` (+ markers), screen motion | new `president-finish`; stillness, table-fit, game, trick-variants, animations, completion-offer |
| E1 | `exchangeBoard`, `PresidentExchangeBoard`, `PresidentExchangePanel`, screen | exchange-flights, exchange-sync, exchange, game, stillness, table-fit |
| E2 | `exchangeMotion` | exchange-flights, exchange-sync |
| I | screen (legs in flight, announcements) | new `president-exchange-board`; every `president-*` |

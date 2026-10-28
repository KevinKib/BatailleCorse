# Kilo de merde: plan

Spec: `docs/specs/2026-10-28-kilo-de-merde-design.md` (rules retained, sources and their differences, the 22
decisions taken autonomously, numeric table, domain, contract, table layout, texts). Written by Claude Opus 5.5 on
`origin/main` at `8f7a36d1`.

## Key decisions (from the spec)

1. **Ducale's beginner rules, played in simultaneous pass steps** (A2): every player picks a card at once, then all
   picked cards move left; a 15 s pass timer passes for a slow or absent player (A3). "Everyone holds 4 cards"
   is then always true, so that rule needs no check.
2. **The slap**: valid when any hand is four of a kind (A4); a false slap costs the slapper the top kilo card;
   a valid slap opens a race, the last to slap draws, and a 5 s race timeout makes every non-slapper draw (A5).
   A drawn 7 is the flush: the drawer's kilos and the 7 go back into the pile, shuffled (A8).
3. **Length and end**: lobby option `rounds` (10 by default; 5, 15, 20); the game also ends when the kilo pile runs
   out (A9); a round stalls after 40 steps (A10); fewest kilos wins, ties share the place (A13). A forfeit voids the
   round and re-forms the table for `n - 1` players (A14).
4. **Bots `easy` / `medium` / `hard`**: random pass / `defaultPass` / a memory of the cards received, with slap
   reaction times as the main lever; bots never false-slap. The bot coordinator keys its tasks on round, step and
   race, never on `version()`, so a slap does not cancel the other bots' slaps.
5. **No owner questions, no canvas** (owner override): every UI choice is in spec §12.2 (ring of plates from 99's
   geometry promoted to `model/table`, the pile as the single slap target, tap a card to pass, remappable keys).
   The owner tests the finished game and overturns rows of spec §5 as he sees fit.

**Goal:** a playable Kilo de merde for 3 to 10 seats, humans and bots of three levels, with the lobby, the public
game list, the rules panel, English and French texts, the end screen, and one Cypress spec in the smoke list.

## Order and parallelism

```
P0 (interfaces, add-only) ──┬──> C1 (domain aggregate) ──> C3 (presentation, config, timers, coordinator) ──┐
                            ├──> C2 (bot strategies) ───────────────────────────────────────────────────────┤
                            ├──> F1 (model, session, store, bootstrap, motion) ─────────────────────────────┤──> I (entry,
T (texts en + fr) ──────────┴──> F2 (table components and screen) ──────────────────────────────────────────┘    e2e, docs)
```

- **P0** first, alone, short (under 30 minutes): shared types, nothing removed, nothing registered.
- **T** (texts) can start as soon as this plan is merged: it touches only the locales.
- **In parallel after P0**, each in its own worktree with its own sub-agent: **C1**, **C2**, **F1**; **F2** once
  P0 and T are merged (it codes against the P0 types and fixtures and the T keys, not a running backend).
- **C3** needs C1 (it drives the real aggregate in its ITs); it uses the P0 strategy port, so it does not wait for
  C2: until C2 lands its coordinator test uses a stub strategy and the config wires `defaultPass` for every level.
- **I** needs C2, C3, F1, F2. It is the game's only entry PR: never at the same time as another game's entry PR
  or a picker change (`add-a-game.md` §1).

**Merge accord.** Kilo de merde is not in the owner's restricted list (Mangola and President backend domains): the
orchestrator merges each chunk after review and green CI. No chunk waits for the owner.

**Tests (every chunk):** backend `cd backend && mvn -s ../deploy/docker/settings.xml test`; frontend
`cd frontend && npm test && npx vite build`. Strict TDD: the tests of each "tests first" list are committed red
(`test: …`) before the `feat: …` commit that makes them green (retrospective F8). **Cypress: nominal cases only,
one spec, `cypress/specs/kilo-de-merde-game.cy.ts`, written and run in I only**, added to
`frontend/cypress/smoke-specs.json` there (only an integration chunk edits that list). Each Cypress run once
(issue #312 rule); the CI `E2E smoke (Cypress)` job is the check when no local stack is available. A dev
environment only for F2 (fixtures) and I (manual play).

**Conventions:** one draft PR per chunk opened at its start with its real number, `gh pr ready` at the end;
branches `feat/kilo-de-merde-…`; conventional titles; backend paths relative to
`backend/src/main/java/org/kevinkib/cardgames/kilodemerde/` (tests in the mirror path), frontend paths relative to
`frontend/src/`; every visible text in `locales/Messages.ts`, `locales/en.ts` and `locales/fr/kilodemerde.ts`.

**Cost scale:** S about half a day, M about a day, L about two days.

## Status

| Chunk | Title | Cost | Needs | Parallel with | State |
|-------|-------|------|-------|---------------|-------|
| P0 | `feat: kilo de merde shared types (interfaces)` | S (< 30 min) | plan merged | T | [x] |
| T | `feat: kilo de merde texts (en, fr)` | S | plan merged | P0, C1, C2, C3, F1 | [x] |
| C1 | `feat: kilo de merde rules in the domain` | L | P0 | C2, F1, T, F2 | [x] |
| C2 | `feat: kilo de merde bot strategies` | M | P0 | C1, C3, F1, T, F2 | [x] |
| F1 | `feat: kilo de merde frontend model, session and store` | M | P0 | C1, C2, C3, T | [x] |
| F2 | `feat: kilo de merde table components and screen` | L | P0, T | C1, C2, C3, F1 | [x] |
| C3 | `feat: kilo de merde presentation, timers and bot coordinator` | L | C1 | C2, F1, F2 | [x] |
| I | `feat: kilo de merde playable (entry PR)` (integration) | M | C2, C3, F1, F2 | - | [x] |

About 9 to 11 days of work, 8 PRs. Critical path: P0, C1, C3, I (F2 runs alongside C1 and C3).

## Conflicts

| File | Chunks | Handling |
|------|--------|----------|
| `domain/KiloDeMerdeOptions.java`, `KiloDeck.java`, `KiloValue.java`, `KiloDeMerdeRules.java` (signatures), `Phase.java`, `RoundOutcome.java`, `DeckSource.java`, `event/*`, exceptions, `bot/KiloDeMerdeBotView.java`, `bot/KiloDeMerdeBotStrategy.java`, `bot/KiloDeMerdeBotLevel.java` | P0, then C1, C2 read | P0 creates them; C1 may fill `KiloDeMerdeRules` bodies but changes no signature; a signature change is a tiny follow-up on `main`, never in two branches |
| `domain/KiloDeMerde.java`, `KiloDeMerdeFactory.java`, `KiloDeMerdeResult.java`, `RandomDeckSource`, `ScriptedDeckSource` | C1 | C1 owns them |
| `domain/bot/*` (strategies, `KiloDeMerdeReflex`, `KiloDeMerdeBotTuning`) | C2 | C2 owns them; `KiloDeMerdeBotView.forSeat` and its parity test go to C3 |
| `presentation/**`, `config/KiloDeMerdeConfig.java` | C3 | C3 owns them |
| `application.properties`, `application-test.properties` | C3 (and every other game in flight) | append-only lines; rebase |
| Shared backend tests (`PerGameConfigTest`, `ArchitectureRules`, `ArchitectureTest`, `MalformedTokenIT`, `LobbyControllerTest`) | C3 | append-only; rebase on `origin/main` before the final push; never weaken a shared test; merge `main` in and re-run CI if `main` touched one since (project `CLAUDE.md`) |
| `model/kilodemerde/KiloDeMerdeState.ts`, `model/kilodemerde/fixtures.ts` | P0, then read only | as above |
| `model/kilodemerde/*` (others), `application/KiloDeMerdeSession.ts`, `state/KiloDeMerde.store.ts`, `composables/useKiloDeMerdeBootstrap.ts` | F1 | one chunk |
| `components/kilodemerde/*`, `view/kilodemerde/KiloDeMerdeGameScreen.vue` | F2 | one chunk |
| `model/ninetynine/ringLayout.ts` (+ test) → `model/table/ringLayout.ts`, and the 99 files importing it | F2 | a first `refactor:` commit in F2 that only moves the file and updates 99's imports; no behaviour change; conflicts only with a 99 change in flight (rebase) |
| `locales/Messages.ts`, `locales/en.ts`, `locales/fr/index.ts` | T, I (and every game or translation chunk in flight) | T adds `kiloDeMerde`, `kiloDeMerdeUi`, `options.kiloDeMerde`, `hotkeys.actions.pass1..4`; I adds `gamePicker.games['kilo-de-merde']` (en and fr); different namespaces; rebase; `parity.test.ts` must stay green |
| `locales/fr/kilodemerde.ts` | T, I | T creates it; I adds the picker lines only if they live there |
| `games.ts`, `games.test.ts`, `router.ts`, `router.test.ts`, `useGameRooms.ts`(+test), `GamePickerView.test.ts`, `en.test.ts`, `cypress/support/stompPlayer.ts`, `cypress/specs/game-picker.cy.ts`, `game-home.cy.ts`, `game-screens-homogeneity.cy.ts`, `cypress/smoke-specs.json` | I | entry PR only; never in parallel with another entry PR |
| `docs/architecture/context-map.md` | C3 (section), I ("not covered" line) | series |
| `CLAUDE.md` (game list) | I | rebase |
| The spec | C2 (benchmark result in §11), I (status) | different sections |

## P0 `feat: kilo de merde shared types (interfaces)` (S, under 30 minutes)

Add-only, nothing registered (no factory bean, so no shared test moves), no aggregate.

- Backend `kilodemerde/`: `package-info.java` (what the context is); `domain/KiloDeMerdeOptions` with `Rounds`
  (`FIVE`, `TEN`, `FIFTEEN`, `TWENTY`; default `TEN`) and the strict/lenient parse; `KiloDeck` and `KiloValue`
  **with their bodies** (two pure tables); `KiloDeMerdeRules` with `isFourOfAKind`, `anyFourOfAKind`,
  `defaultPass` and `STALL_LIMIT` **with bodies** (pure, short, and every other chunk needs them); `Phase`,
  `RoundOutcome`, `KiloDraw`; `DeckSource` port; the sealed `KiloDeMerdeEvent` records; the exception classes;
  `bot/KiloDeMerdeBotView` (record: hand, step, round, pass history, kilos and active seats, phase, race),
  `bot/KiloDeMerdeBotStrategy` (port `choosePass(view)`), `bot/KiloDeMerdeBotLevel`.
- Frontend: `model/kilodemerde/KiloDeMerdeState.ts` (the DTO and event types of spec §10.4) and
  `model/kilodemerde/fixtures.ts` (a 3-seat step with my choice pending, a 10-seat race in progress, a round over
  with all hands and a flush, a finished game with a shared 1st place).
- **Tests first:** `KiloDeMerdeOptionsTest` (default 10; strict parse refuses `rounds=7` and unknown keys),
  `KiloDeckTest` (spec §14), `KiloValueTest`, `KiloDeMerdeRulesTest` (four of a kind; `defaultPass` tie order of A15).

## T `feat: kilo de merde texts (en, fr)` (S)

Only the locales; the game stays invisible.

- `locales/Messages.ts`: `kiloDeMerde: RulesMessages`, `kiloDeMerdeUi` (an interface with every string of spec
  §12.2 and §13: pile label, round and pass line, countdown, "Passing…", "from {name}", race banner, the four
  outcome lines with `{name}`, `{card}`, `{kg}`, "last" tag, kilo popover title, standings headers, end subtitles
  for `ROUNDS_PLAYED`, `PILE_EMPTY`, `FORFEIT`, live-region lines, the refusal codes of spec §8.1), `options.kiloDeMerde`
  (`rounds`, `level` and their values), `hotkeys.actions.pass1..pass4`.
- `locales/en.ts`: the English of spec §13; `locales/fr/kilodemerde.ts` exporting `kiloDeMerde`, `kiloDeMerdeUi`,
  `kiloDeMerdeOptions`, composed in `fr/index.ts` (the 99 / draughts pattern), the French of spec §13 ("vous").
- **Tests first:** `en.test.ts` non-empty checks for `kiloDeMerde` and `kiloDeMerdeUi`; `parity.test.ts` (already
  there) goes red until the French file exists; `length.test.ts` and `noHardCodedText.test.ts` stay green.

## C1 `feat: kilo de merde rules in the domain` (L)

- `KiloDeMerde` aggregate (spec §8.2: `choosePass`, `resolveStep`, `slap`, `expirePassTimer`, `closeRace`,
  `nextRound`, `forfeit`, the queries), `KiloDeMerdeFactory` (`GAME_TYPE = "kilo-de-merde"`, 3 to 10,
  `supportsBots() = true`, descriptor `rounds`, seat option `level`), `KiloDeMerdeResult`, `RandomDeckSource`,
  `ScriptedDeckSource`, `KiloDeMerdeBuilder` (tests).
- **Tests first:** the fourteen worked examples of spec §8.3 as parameterised rows, `KiloDeMerdeEndTest`,
  `KiloDeMerdeForfeitTest`, `KiloDeMerdeFactoryTest`; version bumps on every accepted command, a refused command
  never mutates (a snapshot comparison helper).
- Any later change of a row of spec §5 is local: A2 (`resolveStep`), A4/A5 (`slap`, `closeRace`), A8/A9 (`drawFor`),
  A10 (`STALL_LIMIT`), A13 (ranking), A14 (`forfeit`).

## C2 `feat: kilo de merde bot strategies` (M)

Owns `kilodemerde/domain/bot/` except the P0 types. Works from `KiloDeMerdeBotView` built by hand, so it does not
wait for C1.

- `RandomKiloDeMerdeBot`, `GreedyKiloDeMerdeBot` (`defaultPass`), `MemoryKiloDeMerdeBot` (spec §11),
  `KiloDeMerdeReflex` (own-set and follow delays per level from an injected `RandomGenerator`),
  `KiloDeMerdeBotTuning` (the ranges of spec §7 with defaults; C3 binds them to properties).
- **Tests first:** every level returns a card of its hand on 1,000 random views; greedy equals `defaultPass`;
  memory keeps its target rank and passes a card it passed before on hand-written views; reflex delays stay in
  their ranges and are deterministic with a seeded generator.
- Opt-in `KiloDeMerdeBotStrengthBenchmark` (`-Dkilodemerde.bench=true`): since C1 may not be merged yet, it uses a
  small simulator in the test tree (deal, simultaneous steps, races resolved by sampled
  delays, draws with values), 200 games of 4 players, seats rotated; records mean kilos per level in spec §11. If C1
  is merged first, the benchmark drives `KiloDeMerde` instead and the simulator is not written.

## F1 `feat: kilo de merde frontend model, session and store` (M)

Codes against the P0 types and fixtures; nothing registered.

- `application/KiloDeMerdeSession.ts` (`KILO_DE_MERDE_SESSION_CONFIG`, `pass(card)` on `/app/kilo-de-merde/pass`,
  `slap()` on `/app/kilo-de-merde/slap`), `state/KiloDeMerde.store.ts` (`kilo-de-merde-store`, `useSeatRoom`),
  `composables/useKiloDeMerdeBootstrap.ts`; model helpers (`kiloLabel`, `seatOrder` over `opponentOrder.ts` with my
  left neighbour next, `roundSummary` from `lastOutcome` to a text code and its parameters, `countdown` from
  `passTimerEndsAt` / `raceEndsAt` and a clock), `tempo.ts`, `motion.ts` (anchors `KiloDeMerdeAnchor`: seats, pile,
  my hand; pure plans for `STEP`, `DRAW`, `FLUSH`, `ROUND_START`).
- **Tests first:** session (destinations, payloads), store, bootstrap, every helper, motion plans over the P0
  fixtures (a 10-seat step gives 10 flights, each to the next seat).

## F2 `feat: kilo de merde table components and screen` (L)

Starts when P0 and T are merged. Codes against the P0 types and fixtures (and F1's helpers once merged; until then
the tests use fixtures directly). Layout of spec §12.2, no canvas (owner override).

- First commit `refactor: move the 99 ring layout to model/table` (file and test moved, 99 imports updated, no
  behaviour change).
- `components/kilodemerde/`: `KiloDeMerdeTable.vue` (felt, ring, centre), `KiloDeMerdeSeatPlate.vue` (name, level,
  kilos, ready check, slap rank, last tag, revealed hand), `KiloDeMerdePile.vue` (the slap button with count and
  pulse), `KiloDeMerdeHand.vue` (tap to pass, "Passing…", received glow), `KiloDeMerdeRoundBanner.vue` (race and the
  four outcome kinds), `KiloDeMerdeKiloPopover.vue`, `KiloDeMerdeLobbyOptions.vue`, `KiloDeMerdeStandings.vue` (over
  `TotalsTable`); `view/kilodemerde/KiloDeMerdeGameScreen.vue` (shared chrome: `GameLobby`, `RulesPanel`,
  `GameTimer`, `EndGameOverlay`, `DisconnectOverlay`, `ForfeitBanner`; the shared animation barrel; the hotkeys
  through the shared hotkey composable; the live regions). Not routed yet (I does it).
- `frontend-spacing` skill: 375x667 and 1280x720 with 10 seats, reported in the PR with screenshots from the
  fixtures (`ui-change` skill for the visual check); a component test (`cypress/component/`) for the 10-seat ring
  geometry if the unit test of `ringLayout` does not already cover it.
- **Tests first:** each component (the pile is disabled outside `SLAP` in `availableActions` and keeps its name;
  my cards are disabled once I chose; the four outcome lines; the revealed hands only in `ROUND_OVER`; shared
  places), the screen over fixtures (a step, a race, a round over, the end), the hotkeys (Space slaps, 1-4 pass,
  off when disabled).

## C3 `feat: kilo de merde presentation, timers and bot coordinator` (L)

- `KiloDeMerdeRestController`, `KiloDeMerdeWebSocketController` (game-type check before start; `pass`, `slap`),
  `KiloDeMerdeGameActions`, `KiloDeMerdePassPayload`, `KiloDeMerdeDto.forViewer`, `KiloDeMerdeEventType` /
  `EventData`, the three timers of spec §10.2, `KiloDeMerdeTestDealController` (`test` profile),
  `KiloDeMerdeBotView.forSeat`, `KiloDeMerdeBotCoordinator`, `config/KiloDeMerdeConfig.java`, properties
  `kilodemerde.pass-timer-ms`, `race-timeout-ms`, `round-pause-ms`, `kilodemerde.bots.*` (test values of spec §7).
- Shared backend tests per `add-a-game.md` §2.2; a "Kilo de merde" section in `context-map.md`.
- **Tests first:** `KiloDeMerdeDtoTest` (no foreign hand, choice or received card during a round; all hands in
  `lastOutcome`; ready marks public), bot view parity with the DTO, `KiloDeMerdeGameActionsTest`,
  `KiloDeMerdeRestAndLifecycleTest`, `KiloDeMerdeWebSocketControllerTest`, `KiloDeMerdeWebSocketControllerIT` (STOMP:
  three seats, start, a step, a race to a draw, a refused double slap answered to the sender alone, a one-card
  pile to the end), timers with a scheduler stub (stale keys dropped; the pass timer off at 0),
  `KiloDeMerdeBotCoordinatorTest` (a bot slap in a race is not cancelled by another seat's slap; a bot passes once
  per step; a bot holding four slaps; a failing strategy falls back to `defaultPass`), `KiloDeMerdeFixedDealIT` and
  `KiloDeMerdeFixedDealAbsentInProductionIT`, an IT where a human plays against two `easy` bots to a round end.

## I `feat: kilo de merde playable (entry PR)` (integration, M)

- Merge check (C2, C3, F1, F2 on `main`; no other entry PR open), rebase.
- The shared frontend files of `add-a-game.md` §3.2: registry (`define('kilo-de-merde', …)` of spec §12.1, icon
  checked in the installed `primeicons`), route `kilo-de-merde-room`, `useGameRooms`, `gamePicker.games['kilo-de-merde']`
  in `en.ts` and French, `en.test.ts`, `GamePickerView.test.ts` counts, `games.test.ts`, `router.test.ts`,
  `stompPlayer.ts` `ScriptedGameType`.
- **Cypress (written red first): `cypress/specs/kilo-de-merde-game.cy.ts`, the only Kilo de merde spec**, nominal
  path of spec §14, with the deal: host (seat 0) `K♠ K♥ K♦ Q♣`, first scripted player (seat 1) `J♠ J♥ Q♦ Q♥`,
  second scripted player (seat 2, the host's right) `K♣ J♦ J♣ Q♠`, pile `[A♠]`; the host passes `Q♣`, seat 1
  passes `Q♦`, seat 2 passes `K♣`; the host slaps by clicking the pile; seat 1 then seat 2 slap through STOMP;
  end screen: host and seat 1 1st at 0 kg, seat 2 3rd at 100 kg. `expectGap` / `expectNoOverlap` on the hand and
  the pile. Added to `frontend/cypress/smoke-specs.json`; the game added to the lists of `game-picker.cy.ts`,
  `game-home.cy.ts` and `game-screens-homogeneity.cy.ts`. Each run once (issue #312 rule), on a shifted e2e stack
  if 5173 is taken; report the specs and the total Cypress time.
- Docs: project `CLAUDE.md` game list (intro line and contexts), the "not covered" line of `context-map.md`, this
  plan's status, a comment on roadmap issue #1.
- All suites, a review of the whole feature (`code-review` skill), manual play in a dev environment (two tabs plus
  bots of each level, 3 and 10 seats).

## Test plan summary

| Level | Where | What |
|-------|-------|------|
| Domain (strict TDD) | P0, C1, C2 | hand deck table, kilo values, four of a kind, `defaultPass`, the fourteen worked examples (steps, races, false slap, flush, timeouts, stall, ends, forfeit re-form), options; bots legal on random views, greedy and memory choices, reflex ranges |
| Benchmark (opt-in) | C2 | mean kilos per level, recorded in the spec |
| Presentation | C3 | DTO privacy, bot view parity, actions, REST, WebSocket unit and IT, timers keyed on counters, coordinator race behaviour, seam present / absent |
| Frontend unit | T, F1, F2, I | texts and parity, session, store, bootstrap, helpers, motion plans, components, screen, hotkeys, registry and picker counts |
| E2E (nominal only, smoke list) | I | `kilo-de-merde-game.cy.ts`: create, two scripted joins, start, a pass step, a valid slap and race, the last player takes 100 kg, end screen with a shared 1st place; shared picker specs |

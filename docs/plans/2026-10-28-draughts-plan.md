# Draughts (international, 10x10): plan

Spec: `docs/specs/2026-10-28-draughts-design.md` (rules from the FFJD with every assumption flagged, domain
design, bots, contract, interaction model, numbers, product questions). Written by Claude Opus 5.5 on
2026-10-28.

## Key decisions (from the spec)

1. **FFJD rules, as the FMJD official text (Annexe 1, Nov. 2015) states them**: flying kings, compulsory
   capture of the most pieces (a king counts one), pieces removed after the whole rafle, no promotion in
   passing; wins by no pieces, no move or resignation; draws by agreement, threefold repetition, 25 moves each
   of kings only, and the 16-move and 5-move short endings. One rule set, no rule options.
2. **A new context `draughts`** (id `draughts`), its own aggregate over a pure `MoveGenerator` on the 50 squares
   numbered 1-50 the FMJD way; the server sends each seat its legal moves (from, to, path, captured squares);
   resignation is the shared `/app/forfeit`.
3. **Bots `random` / `greedy` / `expert`**, Expert an alpha-beta capped at 6 plies and 300 ms, thinking outside
   the game lock; modest on purpose (to confirm, Q2).
4. **No clock in v1** (an optional Fischer clock is described with proposed numbers, Q3); lobby options
   `hostColour` (default `random`); draw offers are always allowed (the `drawOffers` option was removed).
5. **The look goes through a Claude Design canvas (D) first**: board, pieces and kings, plates, move list,
   lobby; the owner chooses before the components chunk.

**Goal:** a playable international draughts game, two humans or a human against three bot levels, with the
lobby, the public game list, the rules panel and the end screen, and one Cypress spec.

## Order and parallelism

```
P0 (interfaces) ──┬──> B1 (rules engine) ──┬──> B2 (aggregate, wire, wiring) ──┬──> B4 (bot coordinator) ──┐
                  │                        └──> B3 (bot strategies) ────────────┘                          │
                  ├──> F1 (model, session, store, bootstrap) ──────────────────────────────────────────────┤
D (canvas) ───────┴──> F2 (board and table components, after D) ────────────────────────────────────────────┴──> E (entry + integration)
```

- **P0** first, alone (under 30 minutes of work, add-only: types and constants, nothing removed).
- **In parallel after P0:** B1, F1, and F2 (as soon as D is chosen). Each in its own worktree with its own
  sub-agent. F1 and F2 code against the P0 types with fixtures, not against a running backend.
- **After B1:** B2 and B3 in parallel (B2 owns the aggregate and presentation, B3 owns `domain/bot/`; both read
  the B1 generator, neither edits it).
- **B4** needs B2 and B3. **E** needs B2, B4, F1, F2.
- **D** (the canvas) can start as soon as this spec is merged; it is the only step that waits for the owner.
- **Entry PR rule** (`docs/architecture/add-a-game.md`): E is the only PR that makes the game visible, and no two
  entry PRs (of any game) run at once; E waits for any other game's entry PR to merge, then rebases.

**Tests (every PR):** backend `cd backend && mvn -s ../deploy/docker/settings.xml test` (unit, `*IT`, ArchUnit);
frontend `cd frontend && npm test && npx vite build`. Strict TDD at domain level: each "tests first" list is
committed red (`test: ...`) before the `feat: ...` commit that makes it green (retrospective F8). **Cypress:
nominal cases only, one spec for the game, `draughts-game.cy.ts`, written and run in E only** (rule of issue
#312: the chunk runs only the spec it touches, once). A dev environment only for F2 (manual check of the
board) and E (a manual game against each level).

**Conventions:** one draft PR per chunk opened at its start with its real number, `gh pr ready` at the end;
branches `feat/draughts-...`, `test/...`; conventional titles; backend paths relative to
`backend/src/main/java/org/kevinkib/cardgames/draughts/` (tests in the mirror path), frontend paths relative to
`frontend/src/`; every visible text in `locales/Messages.ts` and `locales/en.ts` (plus `locales/fr/` if the
French bundle and its parity test have landed by then: the chunk then adds the French text too).

**Merge accord:** draughts is not in the owner's restricted list (only the Mangola and President backend
domains are): the orchestrator merges each chunk after review and green CI. D needs the owner's choice.

**Cost scale:** S about half a day, M about a day, L about two days.

## Status

| Chunk | Title | Cost | Needs | Parallel with | State |
|-------|-------|------|-------|---------------|-------|
| P0 | `feat: draughts interfaces (types, geometry, contract)` | S | spec merged | D | [x] |
| D | Claude Design canvas, 2-3 options, owner chooses | S | spec merged | P0, B1, F1 | [x] |
| B1 | `feat: draughts rules engine (move generation, majority capture)` | L | P0 | F1, F2, D | [x] |
| F1 | `feat: draughts frontend model, session and store` | M | P0 | B1, B2, B3, F2 | [x] |
| F2 | `feat: draughts board and table components` | L | P0, D | B1, B2, B3, F1 | [x] |
| B2 | `feat: draughts aggregate, endpoints and wiring` | L | B1 | B3, F1, F2 | [x] |
| B3 | `feat: draughts bot strategies` | M-L | B1 | B2, F1, F2 | [x] |
| B4 | `feat: draughts bot coordinator` | S | B2, B3 | F2 | [x] |
| E | `feat: draughts on the game picker` (entry, Cypress, docs) | M | B2, B4, F1, F2 | - | [x] |

About 10 to 12 days of work, 8 PRs plus the canvas. Critical path: P0, B1, B2, B4, E (with F2 waiting on D).

## Conflicts

| File | Chunks | Handling |
|------|--------|----------|
| `domain/BoardGeometry.java`, `domain/DraughtsPosition.java`, `domain/DraughtsMove.java`, `domain/Piece.java`, `domain/Colour.java`, `domain/DraughtsResult.java`, `domain/bot/DraughtsBot.java`, `domain/bot/DraughtsDecision.java` | P0, then read only | a change needed later goes in a tiny follow-up on `main`, never in two branches |
| `domain/MoveGenerator.java` | B1 | B1 owns it; B2 and B3 only call it |
| `domain/Draughts.java`, `domain/DraughtsFactory.java`, `presentation/**` except `presentation/bot/`, `config/DraughtsConfig.java` | B2, B4 | B2 creates; B4 only adds the coordinator bean to the config (a few lines, rebase) |
| `domain/bot/**` | B3 | one chunk |
| `presentation/bot/**`, `application.properties`, `application-test.properties` | B4 (B2 touches neither) | one chunk; the properties files are shared with every game: add lines only, rebase |
| Shared backend tests: `config/PerGameConfigTest`, `architecture/ArchitectureRules`, `architecture/ArchitectureTest`, `presentation/MalformedTokenIT`, `presentation/LobbyControllerTest` | B2 | any other new game in flight edits the same lists and counts: rebase, never weaken a test |
| `model/draughts/DraughtsState.ts`, `model/draughts/fixtures.ts` | P0, then read only | as above |
| `model/draughts/*` other than the two above, `application/DraughtsSession.ts`, `state/Draughts.store.ts`, `composables/useDraughtsBootstrap.ts` | F1 | one chunk |
| `components/draughts/*`, `view/draughts/DraughtsGameScreen.vue` | F2 | one chunk |
| `locales/Messages.ts`, `locales/en.ts` (and `locales/fr/*` once it exists) | F2 (screen texts `draughtsUi`, rules `draughts`, `options.draughts`), E (`gamePicker.games.draughts`) | different namespaces; rebase; the French translation chunks and other games touch the same files |
| `model/games.ts`, `games.test.ts`, `router.ts`, `router.test.ts`, `composables/useGameRooms*.ts`, `view/GamePickerView.test.ts`, `locales/en.test.ts`, `cypress/support/stompPlayer.ts`, `cypress/specs/game-picker.cy.ts`, `game-home.cy.ts`, `game-screens-homogeneity.cy.ts` | E | entry PR only, one at a time across games |
| `docs/architecture/context-map.md` | B2 (section), E ("not covered" line) | series |
| `CLAUDE.md` (game list) | E | rebase |
| this spec | B3 (benchmark result), F2 (UI addendum), E (status) | different sections |

## P0 `feat: draughts interfaces (types, geometry, contract)` (S, under 30 minutes)

Add-only: new files, nothing registered, nothing removed; the game stays invisible and unplayable.

- Backend `domain/`: `package-info.java` of `draughts` (context description), `Colour`, `Piece`,
  `BoardGeometry` (square to row and column, the four rays per square, the promotion rows: static tables),
  `DraughtsPosition` (record with `initial()`, `fromLayout(String, Colour)`, `pieceAt`, `key()`), `DraughtsMove`
  (record, notation), `DraughtsResult` with `Reason` (the seven reasons of the spec), `MoveGenerator` as a
  **class with the two public signatures** (`legalMoves(position)`, `apply(position, move)`) throwing
  `UnsupportedOperationException` (B1 fills them), `domain/bot/DraughtsBot` (port) and `DraughtsDecision`
  (sealed: `Play`, `AcceptDraw`, `DeclineDraw`).
- Frontend: `model/draughts/DraughtsState.ts` (the DTO types of the spec: board codes, players, legal moves,
  last move, draw offer and counters, outcome reasons, actions, event data) and `model/draughts/fixtures.ts`
  (three hand-written DTOs: the opening, a compulsory capture with an ambiguous destination, a finished game).
- **Tests first:** `BoardGeometryTest` (numbering of 2.6: corners 5 and 46; left band 6-16-26-36-46, right
  band 5-15-25-35-45; base rows; the rays form exactly 17 maximal diagonals, the long one 46 to 5),
  `DraughtsPositionTest` (initial layout 1-20 black, 31-50 white, White to move; `fromLayout` round trip; `key`
  separates men from kings and the side to move), `DraughtsMoveTest` (notation `32-28`, `19x30`).

## D Claude Design canvas (owner chooses) (S)

Artifact `quickstart` with intent `design`; **2 or 3 options** on the real positions listed in the spec
("What the canvas must cover"), at 375 px and 1280 px, dark theme (the app is dark-only); board and piece
look, king mark, plates with counts and draw controls, the hint for a compulsory capture, the multi-jump
preview and the ambiguous-destination chooser, the draw-count warnings, the move list, the lobby card and
options. One recommendation. The owner chooses; F2 writes the choice and the link into a "UI addendum" of the
spec.

## B1 `feat: draughts rules engine (move generation, majority capture)` (L)

Pure domain, owns `MoveGenerator`. No aggregate, no wire.

- `legalMoves`: plain moves (men forward, kings flying), capture search (men both ways, kings at distance,
  turning), captured set as obstacles (A2), start square free (A3), no piece jumped twice (4.8), majority filter
  (4.13), de-duplication by `(from, to, captured set)` (A8), promotion only when a man ends on its row (4.15),
  deterministic order.
- `apply`: moves the piece, removes the captured pieces, promotes, swaps the side to move.
- **Tests first** (`MoveGeneratorPlainTest`, `MoveGeneratorCaptureTest`, `MoveGeneratorKingTest`,
  `MoveGeneratorMajorityTest`, `MoveGeneratorPromotionTest`, `ApplyTest`), each position written with
  `fromLayout`:
  - the opening: 9 legal moves for White, none backwards;
  - a man captures backwards; a man may not move backwards without capturing;
  - capture is compulsory: with a capture on the board, no plain move is listed;
  - a king captures from a distance and may land on any free square behind; it cannot jump two adjacent
    pieces; it turns to continue a rafle;
  - the FFJD diagram case: three men must be taken rather than two kings (4.13); a king counts one; equal
    counts with a man and a king are both listed (4.14);
  - Turkish strike: a captured piece blocks the rest of the sequence and is not jumped twice;
  - the start square is free during a rafle (a king passes over its own start square);
  - a man passing the far row mid-rafle stays a man; one ending there promotes;
  - same start and end with two captured sets gives two moves; two paths with the same captured set give one;
  - a blocked side has no legal move;
  - property test: 500 random games from the opening (fixed seed): every listed move has the maximal count,
    no square is captured twice, `apply` keeps 0 to 20 pieces per side, and every call stays under a time bound.

## F1 `feat: draughts frontend model, session and store` (M)

Codes against the P0 types and fixtures; nothing registered (the game stays invisible).

- `model/draughts/board.ts` (pure): the coordinates of the 50 squares in each orientation (White or Black
  at the bottom), the selectable pieces (from `legalMoves`; only capturing pieces when `captureRequired`), the
  destinations of a selected piece, the candidates for an ambiguous destination and the piece that separates
  them, the intent of a tap (`select`, `deselect`, `play {from, to, captures}`, `choose`), arrow-key focus
  moves on the visible board.
- `model/draughts/motion.ts` and `tempo.ts`: the glide along `path`, then the removal of captured pieces in
  order (pure plans; no shared card animation, as Morris and Mangola).
- `application/DraughtsSession.ts` (`DRAUGHTS_SESSION_CONFIG`, `move`, `offerDraw`, `acceptDraw`,
  `declineDraw`), `state/Draughts.store.ts` (`defineStore('draughts-store')` over `useSeatRoom`),
  `composables/useDraughtsBootstrap.ts`.
- **Tests first:** `board.test.ts` (orientation flips; only legal pieces selectable; compulsory capture
  restricts them; an ambiguous destination yields a choice; a unique one plays; nothing is selectable for the
  seat not on turn), `motion.test.ts`, `DraughtsSession.test.ts` (destinations and payloads),
  `Draughts.store.test.ts`, `useDraughtsBootstrap.test.ts`.

## F2 `feat: draughts board and table components` (L)

Starts when D is chosen; codes against the P0 types and fixtures (with F1's `board.ts` once merged; until then
the component tests use fixtures directly). Vitest only.

- `components/draughts/DraughtsBoard.vue` (the chosen look; squares as buttons with `data-test="square-N"`;
  selection, destinations, path preview, captured marks, last move; the ambiguous chooser; reduced motion),
  `DraughtsPiece.vue` (man, king with a non-colour mark), `DraughtsPlayerPlate.vue` (counts, turn, bot level,
  warnings), `DraughtsMoveList.vue` if Q5 says yes, `DraughtsLobbyCard`/mini board if the canvas has one.
- `view/draughts/DraughtsGameScreen.vue`: composes the shared chrome (`GameLobby` with the generic options,
  `RulesPanel`, `GameTimer`, `EndGameOverlay`, `DisconnectOverlay`, `ForfeitBanner`, the shared resign
  confirm), the capture hint, the draw offer bar, the live region, end subtitles per reason.
- Locales: `draughtsUi`, `draughts` (rules panel text of the spec), `options.draughts` (`hostColour`,
  `level` with Easy, Medium, Expert), the error codes of the spec.
- UI addendum in the spec (choice and link).
- **Tests first:** `DraughtsBoard.test.ts` (50 dark squares in the right order for each orientation; only
  selectable pieces are enabled; destinations shown; the chooser on an ambiguous destination; the king mark is
  not colour-only), `DraughtsPlayerPlate.test.ts` (counts, warnings from the counters' thresholds),
  `DraughtsGameScreen.test.ts` (hint text with the count, draw offer flow, end subtitles for the seven reasons,
  announcements), `en.test.ts` non-empty checks for `draughts` and `draughtsUi`.

## B2 `feat: draughts aggregate, endpoints and wiring` (L)

Owns the aggregate, the factory, `presentation/` (except `bot/`) and `config/DraughtsConfig.java`.

- Domain: `Draughts` (commands, queries, draw counters 6.1 to 6.4, draw offers, forfeit, `fromPosition`),
  `DraughtsFactory` (option `hostColour`; seat option `level` declared with the keys of the
  spec, its values validated even before B3 plugs strategies), the exceptions and `DraughtsEvent`.
- Presentation: `DraughtsRestController`, `DraughtsWebSocketController` (start with the type check, `move`,
  draw commands, over `SeatCommands`, error codes), `DraughtsGameActions`, payloads, `DraughtsDto.forViewer`,
  event type and data, the test-only `DraughtsTestPositionController` (`@Profile("test")`).
- Config: `DraughtsConfig` (factory with its generator, constant under `test`; `SeatStateBroadcaster` with
  `sessionService::botSeats`; actions; `SeatLifecycleBroadcaster`).
- Shared tests (add-a-game §2.2): `PerGameConfigTest` (lists and counts), `ArchitectureRules.GAMES`,
  `ArchitectureTest` value sources, `MalformedTokenIT` (REST and every command), `LobbyControllerTest`.
- Docs: a "Draughts" section in `docs/architecture/context-map.md`.
- Usage statistics and the public game list need nothing: both work on the game type key [V]; an IT checks
  that `GET /api/lobbies/draughts` answers.
- **Tests first:** `DraughtsMoveTest` (turn, illegal move refused, captures matched as a set, ambiguous move
  refused without captures, version bumps), `DraughtsEndTest` (no pieces, blocked), `DraughtsDrawTest`
  (threefold with the same side to move only; 50 king-only plies; 6.3 at 32 plies; 6.4 at 10 plies; a capture
  switching 6.3 to 6.4 starts the new count; a man move resets the 6.2 count but not 6.3/6.4; agreement;
  offers off refused; a move declines the opponent's offer), `DraughtsForfeitTest`, `DraughtsFactoryTest`
  (type, player bounds, defaults, strict validation, host colour), `DraughtsDtoTest` (legal moves and hint for
  the seat on turn only; counters' thresholds; history notation), `DraughtsGameActionsTest`,
  `DraughtsWebSocketControllerTest`, `DraughtsWebSocketControllerIT` (create, join, start, a few moves, a
  refused move answered to the sender alone, resign through `/app/forfeit`), `DraughtsTestPositionIT` and
  `DraughtsTestPositionAbsentInProductionIT`.

## B3 `feat: draughts bot strategies` (M-L)

Owns `domain/bot/`. Works from `MoveGenerator` and `DraughtsPosition` (B1), not from the aggregate.

- `RandomDraughtsBot`, `GreedyDraughtsBot` (two plies by material), `ExpertDraughtsBot` (negamax, alpha-beta,
  iterative deepening, capture extension, time budget, depth cap, node cap; evaluation of the spec),
  `DraughtsBotLevel` (`random|greedy|expert`), the draw answer by material.
- **Tests first:** `RandomDraughtsBotTest` (always legal on 1,000 random positions), `GreedyDraughtsBotTest`
  (takes a free piece; avoids a move that loses two for one), `ExpertDraughtsBotTest` (finds a two-for-one shot
  at depth 4 that greedy misses; respects the depth cap and the node cap; deterministic with a constant
  generator; always answers with a zero budget, depth 1), `DraughtsDrawAnswerTest`.
- Opt-in `DraughtsStrengthBenchmark` (`-Ddraughts.bench=true`): 100 games per pairing (Expert-Medium,
  Medium-Easy), colours alternating, draw at 200 plies; record wins, losses, draws, mean and worst decision
  time in the spec's bot section. If Expert decides in more than 300 ms or wins nearly every game, lower the
  depth cap and say so in the PR.

## B4 `feat: draughts bot coordinator` (S)

- `presentation/bot/DraughtsBotCoordinator` (the Mangola pattern: snapshot under the monitor, think outside,
  re-check the version, act through `DraughtsGameActions`; fallback to the first legal move; draw answers),
  `DraughtsBotLevels`; `DraughtsConfig` registers it; properties `draughts.bots.*` in
  `application.properties` and zeros in `application-test.properties`.
- **Tests first:** coordinator test with a scheduler stub (a bot on turn is scheduled and moves; a human action
  between snapshot and decision drops it; a failing strategy falls back; a pending offer is answered), an IT
  where a human plays against the `random` bot and gets a reply, and `LobbyControllerTest` "add bot" accepted.

## E `feat: draughts on the game picker` (entry, integration) (M)

- Merge check: B2, B4, F1, F2 on `main`; no other game's entry PR open; rebase.
- Registry and shared files (add-a-game §3.2): `games.ts` (`define('draughts', { supportsBots: true, picker })`,
  icon and severity from the canvas), `games.test.ts`, `router.ts` and its test, `useGameRooms` and its test,
  `gamePicker.games.draughts` in the locales, `en.test.ts`, `GamePickerView.test.ts` counts,
  `stompPlayer.ts` (`ScriptedGameType`), the picker, home and homogeneity Cypress lists.
- **Cypress, one spec, nominal cases only: `cypress/specs/draughts-game.cy.ts`**, on a shifted e2e stack if
  5173 is taken: (1) two players (the second through `stompPlayer`) start a game, the test seam sets a short
  ending, White sees the capture hint, plays a two-piece rafle by clicks, the opponent replies, White takes the
  last piece and both see the end screen; (2) a human against the `random` bot from the opening: the bot
  replies to the first move. Run it once (issue #312 rule); the picker, home and homogeneity specs only for the
  lines this PR adds.
- Docs: `CLAUDE.md` game list (intro line and contexts), the "not covered" line of `context-map.md`, this
  plan's status table, the spec's status lines (benchmark from B3, UI addendum from F2); a comment on the
  roadmap issue #1 (or the game's tracking issue the orchestrator opens).
- All suites, then a review of the whole draughts feature (`code-review` skill) before `gh pr ready`.

## Test plan summary

| Level | Where | What |
|-------|-------|------|
| Domain (strict TDD) | P0, B1, B2, B3 | geometry, every capture rule with FMJD positions, majority, promotion, draws 6.1-6.4, offers, bots legal and deterministic |
| Property | B1 | 500 random games: invariants of the generator |
| Presentation unit + IT | B2, B4 | DTO projection, controllers, STOMP game, test seam present under `test` and absent elsewhere, bots reply |
| Frontend unit (Vitest) | F1, F2, E | board model, intents, session payloads, store, components, screen texts, registry |
| Benchmark (opt-in) | B3 | bot strength and decision time, recorded in the spec |
| Cypress (nominal only) | E | `draughts-game.cy.ts`: a human game to the end screen from a seeded ending, a bot reply |

# Mangola: plan

Spec: `docs/specs/2026-10-15-mangola-design.md` (rules with hand-checked examples, name research, placement
phase, contract, bots, UI decisions, product questions). Written by Claude Opus 5.5 on 2026-10-15 from
`origin/main` at `28c5832`. A tracking issue "Mangola" is opened when this plan is validated; roadmap #1 is
ticked when the game ships.

**Goal:** play Mangola (the owner's four-row sowing game, documented as Mangola in the DR Congo, where it also has other names) at two, or
against a bot of three levels, with a free hidden placement of each player's 32 seeds, an animated relay
sowing, and the shared lobby, forfeit, presence and rematch.

## Decisions to confirm (before any code)

1. **Endless sowings are illegal moves** (recommended), not a draw as in the JS (spec Q1). Changes the
   legal-move computation, the bots and one end reason.
2. **Placement is simultaneous and hidden**, confirm and "edit again" until both are ready, presets in the
   editor, no per-pit cap; host option `setup` = free / checkerboard / random (spec Q2, Q3).
3. **Captures exactly as the JS**: automatic, the capturing pit keeps its seeds, captured seeds sown from
   the pit after the start of the capturing lap (spec Q10; the 1952 Mangola takes the capturing pit too).
   The ten E-examples of the spec are the reference: the owner checks them against their memory.
4. **Name and id**: id `mangola`, display "Mangola", the rules panel says the game is also known in the DR Congo under other names (Ngola, Mongola, Amungola, Angola in Kinshasa) (spec Q4). Decided by the owner on 2026-10-15: the game is called Mangola; id `mangola`.
5. **Who starts**: host option `firstPlayer` = host / guest / random, default random; bots Easy / Medium /
   Expert with no perfect level (spec Q5, Q8).

The other product questions (repetition draw, no draw offer, bot pace) take the spec's recommendation
unless the owner says otherwise. The UI questions (spec, "UI decisions to take") are settled on a Claude
Design canvas before N5.

**Order:** backend first (N1 → N2 → N3), then the frontend (N4 and N5 in parallel, then N6, then the entry PR
N7), bots after N3 (N8 can run in parallel with the frontend), N9 after N7 and N8, N10 last. Nothing is
visible to players before N7 (the entry PR is the gate: `model/games.ts`).

**Tests (every PR):** backend `cd backend && mvn -s ../deploy/docker/settings.xml test` (JUnit, `*IT`,
ArchUnit); frontend `cd frontend && npm test && npx vite build`. Cypress on the e2e stack (or an offset
stack with `--config baseUrl=...`) for N7, N9, N10; waits on DOM states or frames only. Dev environment only
for N5 to N10 (manual test).

**Conventions:** branches `feat/mangola-...`, conventional titles, one draft PR opened at the start of each
step with its real number; test-first visible in git (a `test:` commit before the `feat:` commit,
`add-a-game.md` section 6); every visible text through `locales/`; backend paths relative to
`backend/src/main/java/org/kevinkib/cardgames/mangola/`, frontend paths relative to `frontend/src/`.

**Cost scale:** S about half a day, M about a day, L about two days.

## Status

| PR | Title | Cost | Needs | State |
|----|-------|------|-------|-------|
| N0 | `docs: Mangola (4-row mancala) spec and plan` | S | - | this PR (#217) |
| N1 | `feat: Mangola sowing engine (domain)` | L | N0 validated | [x] PR #222 (merged) |
| N2 | `feat: Mangola placement phase and set-up options` | M | N1 | [x] PR #226 (merged) |
| N3 | `feat: Mangola table, factory, DTO and broadcasters` | L | N2 | [x] PR #228 (draft, not merged) |
| N4 | `feat: Mangola frontend model, session and store` | M | N3 | [x] PR #231 |
| N5 | `feat: Mangola board and placement editor components` | M-L | N3, Claude Design choice | [ ] |
| N6 | `feat: Mangola sowing animation` | M | N4, N5 | [ ] |
| N7 | `feat: play Mangola` (entry PR: registry, route, screen, lobby, locales, rules, Cypress) | M | N6, no other entry PR open | [ ] |
| N8 | `feat: Mangola bots with three levels` (backend) | L | N3 | [ ] |
| N9 | `feat: play Mangola against a bot` (frontend + Cypress) | S | N7, N8 | [ ] |
| N10 | `test: Mangola end-to-end coverage` (placement, endless, rematch, presence) | S-M | N9 | [ ] |

Total about 10 to 12 days of work.

## Conflicts

| File | PRs | Handling |
|------|-----|----------|
| `domain/Mangola.java`, `domain/MangolaRules.java`, `domain/MangolaFactory.java` | N1, N2, N3, N8 | series (N8 after N3) |
| `presentation/dto/MangolaDto.java`, `config/MangolaConfig.java`, `resources/application*.properties` | N3, N8 | series |
| Shared backend tests (`PerGameConfigTest`, `ArchitectureRules`, `ArchitectureTest`, `MalformedTokenIT`, `LobbyControllerTest`) | N3 only | one PR touches them |
| `docs/architecture/context-map.md` | N3, N8 | series |
| `locales/Messages.ts`, `locales/en.ts` | N5, N6, N7, N9 | namespaces `mangola`, `mangolaUi`, `options.mangola`; rebase |
| `model/games.ts`, `router.ts`, `useGameRooms.ts`, `GamePickerView.test.ts`, picker Cypress specs | N7 (N9: one flag) | **entry PR alone**; after President's entry PR (#216's follow-up) or before it, never both open |
| `components/mangola/*`, `model/mangola/*`, `state/Mangola.store.ts` | N4, N5, N6 | N4 and N5 touch different files; N6 rebases on both |

## N1 `feat: Mangola sowing engine (domain)` (L)

Package `mangola/` with `package-info.java` and `domain/` only (no presentation, nothing wired, nothing
visible).

- `MangolaGeometry` (32 pits, the two loops, `next`, `isInner`, `facing`, `behind`, `owner`, names `a1..h4`),
  `MangolaBoard` (immutable), `MangolaRules` (record and `OWNER` constant; the switches of the spec, one value
  each, `endless` with `ILLEGAL` and `DRAW`), `SowingStep` (sealed: `Pick`, `Drop`, `SkipEmpty`, `Capture`,
  `Relay`, `ResumeAfter`), `Sowing.sow(board, seat, pit, rules)` with Brent's cycle check at lap boundaries
  and the 100,000-drop cap, `SowingResult`.
- `Mangola` aggregate in the `PLAY` phase only for now: `Mangola.fromLayout(id, rules, firstSeat, board)`,
  `sow`, `legalPits`, `forfeit`, `result`, `repetitions`, `position`, `version`, exceptions, `MangolaEvent`,
  `MangolaResult` (`NO_MOVE`, `FORFEIT`, `REPETITION`, `ENDLESS_SOWING`).
- **Tests first** (`test:` commit, then `feat:`):
  - `MangolaGeometryTest`: both loops (16 pits each, the JS order, `h2 → h1`, `a1 → a2`, `h4 → h3`,
    `a3 → a4`); `facing`/`behind` for every column; outer pits have none.
  - `SowingTest`: the ten E-examples of the spec, each asserting the final board **and** the trace;
    invariant: 64 seeds before and after, the opponent's half only loses seeds; a lap of 17 seeds drops
    into its own start pit (E8); the empty-pit rule never relays nor captures (E10); a capture needs both
    pits (E3 against E4); outer row never captures (E5); two captures in one move re-sow from the same lap
    start (E9).
  - `EndlessSowingTest`: the spec's 13-seed fixture is endless (cycle of 120 laps); `ILLEGAL` refuses it and
    leaves it out of `legalPits`; `DRAW` plays it and finishes `ENDLESS_SOWING` with no winner; the cap
    stops a synthetic long sowing; `legalPits` on the worst fixture runs under 50 ms.
  - `MangolaPlayTest`: turn order, refused moves (not your pit, fewer than two seeds, finished game, wrong
    turn), `NO_MOVE` win right after the move that leaves the opponent without a legal move, version bumps.
  - `MangolaRepetitionTest`: a scripted cross-turn cycle (built in the test; if none exists with the owner's
    rules, the rule is tested on positions injected through `fromLayout`) ends on the third occurrence.
  - **Characterisation against the JS** (`MangolaJsCharacterisationTest`): a fixture file
    `src/test/resources/mangola/js-games.txt` with about 20 random games generated once by the owner's
    `ngola.js` (start boards, moves, resulting boards, winner), produced by a throwaway Node script that is
    **not** committed (the script's source goes in the PR description); the Java engine must reproduce every
    board. It also pins that the JS sows 100,000 drops before declaring a loop (only the outcome is compared).
  - ArchUnit is not yet aware of `mangola` (N3 adds it); the PR checks domain purity by review.

## N2 `feat: Mangola placement phase and set-up options` (M)

- `Mangola` gains the `PLACEMENT` phase: `arrange(seat, counts)`, `confirmLayout`, `editLayout`,
  `layoutOf`, `isReady`, the reveal when both are ready (one version bump, `LayoutsRevealed`), and the
  construction `new Mangola(id, rules, firstSeat)` starting in placement for `setup = FREE`.
- `MangolaSetup` (`FREE`, `CHECKERBOARD`, `RANDOM`) and the preset boards (`checkerboard()`: the JS classic,
  4 where `(column + row)` is even; `random(RandomGenerator)`: 32 uniform drops per side over all 16 pits).
- `MangolaFactory` (not yet a bean): `GAME_TYPE = "mangola"`, 2 players, options `setup` (default `free`) and
  `firstPlayer` (default `random`) with label keys `options.mangola.setup` / `options.mangola.firstPlayer`;
  `create` draws the first seat and the random set-up with the injected `RandomGenerator`.
- **Tests first:** `MangolaPlacementTest` (layout of 16 non-negative counts summing to 32, else
  `InvalidLayoutException`; replace a draft; confirm without a layout refused; edit after confirm; edit
  after both confirmed refused (already revealed); reveal writes both halves and gives the turn to seat A;
  `sow` refused during placement; `arrange` refused after the reveal; forfeit during placement finishes
  with `FORFEIT`), `MangolaSetupTest` (checkerboard equals the JS classic board; random set-up with a fixed
  generator gives a known board and covers the 16th pit, unlike the JS), `MangolaFactoryTest` (game type,
  bounds, defaults, unknown values refused by `validate`, `firstPlayer` mapping with a fixed generator).

## N3 `feat: Mangola table, factory, DTO and broadcasters` (L)

Follows `docs/architecture/add-a-game.md` section 2 line by line.

- `presentation/`: `MangolaRestController` (`/api/mangola`), `MangolaWebSocketController` (`/app/mangola/start`
  with the game-type check, `arrange`, `confirmLayout`, `editLayout`, `sow` over `SeatCommands`),
  `MangolaGameActions`, `api/MangolaArrangePayload` (`counts`), `api/MangolaSowPayload` (`pit`),
  `dto/MangolaDto` (`forViewer`, hidden opponent draft as `null`s, `draftOnlyEndless`, `lastMove` with its
  trace), `dto/event/MangolaEventType`, `MangolaEventData`.
- `config/MangolaConfig.java`: `mangolaFactory`, `mangolaStateBroadcaster` (`BotSeats.none()` until N8),
  `mangolaGameActions`, `mangolaLifecycleBroadcaster`; a `RandomGenerator` bean (`ThreadLocalRandomGenerator`
  under `!test`, constant under `test`: host starts, deterministic random set-up).
- Shared backend tests: `PerGameConfigTest` (+ `MangolaConfig`, `mangola`, counts), `ArchitectureRules.GAMES`
  and both `@ValueSource` lists of `ArchitectureTest`, `MalformedTokenIT` (REST and the five commands),
  `LobbyControllerTest` (factory in the list).
- **Tests first:** `MangolaDtoTest` (each seat sees its own draft and `null`s for the other during hidden
  placement; both halves after the reveal; `legalPits` only for the viewer to move; trace in `lastMove`),
  `MangolaGameActionsTest`, `MangolaRestAndLifecycleTest`, `MangolaWebSocketControllerTest` (unit, over
  `presentation/support` and a `MangolaTable` fixture), `MangolaWebSocketControllerIT` (real STOMP: create,
  join, start, both arrange and confirm, the reveal frame to both seats with no draft leaked before it, a
  few sows including a capture, a refused sow answered to the sender alone, the end by `NO_MOVE` from a
  `checkerboard` set-up scripted to a short finish), `MangolaRandomAbsentInProductionIT`.
- Docs: `docs/architecture/context-map.md` gains a "Mangola" section (package, factory, options, endpoints,
  broadcasters, placement phase, what is not covered yet); `docs/architecture/game-conventions.md` unchanged
  (no new name).

## N4 `feat: Mangola frontend model, session and store` (M)

- `model/mangola/MangolaState.ts` (DTO types; pit names; `viewerRows(dto)`: the 4×8 grid rotated so the
  viewer's half is at the bottom; `trace` types), `application/MangolaSession.ts` (`MANGOLA_SESSION_CONFIG`,
  `arrange`, `confirmLayout`, `editLayout`, `sow`), `state/Mangola.store.ts` (`mangola-store` over
  `useSeatRoom`; local draft with a debounced `arrange`; stale states dropped by `version`),
  `composables/useMangolaBootstrap.ts`.
- **Tests first** (Vitest): rotation for seat A and seat B; the draft counter (seeds left) and the debounce;
  rehydrating a draft from the DTO after a reload; stale frames dropped; commands published to the right
  destinations.

## N5 `feat: Mangola board and placement editor components` (M-L)

Start after the owner chose on the Claude Design canvas (spec, "UI decisions to take", items 1, 3, 4, 5).
Use the `ui-change` skill for the before/after evidence at 375 px and 1280 px, light and dark.

- `components/mangola/MangolaPit.vue` (count, seed dots, accessible name, selectable / disabled / last-move /
  captured states), `MangolaBoard.vue` (4×8 grid, inner-row divider, path cues per the canvas choice, arrow
  keys, emits `sow(pit)`), `MangolaPlacementEditor.vue` (add / remove a seed, seeds-left counter, presets
  Checkerboard / Two per pit / Random / Clear, Confirm / Edit again, opponent "Arranging…" / "Ready",
  `draftOnlyEndless` warning).
- Locales `mangolaUi` for these texts.
- **Tests first** (Vitest): every pit shows its count and name; only `legalPits` are enabled; keyboard
  path; the editor never lets the counter go below 0 or the sum above 32, Confirm enabled at exactly 32;
  presets fill exactly 32; the opponent half is blank during hidden placement; 44 px targets (or the
  canvas's agreed alternative) at 375 px.

## N6 `feat: Mangola sowing animation` (M)

Per the canvas choice (spec item 2). Recommended: a hand token travelling pit to pit.

- `components/mangola/useSowingAnimation.ts`: plays a trace on a local copy of the previous board, tick time
  shrinking with the trace length (a cap on the total duration, about 4 s), Skip and click-to-finish,
  snaps to the DTO board at the end, queues states that arrive meanwhile and flushes to the newest version;
  `prefers-reduced-motion`: no travel, the result with the last move marked.
- Live region text for each move (seat, pit, relays, seeds captured) in `mangolaUi`.
- **Tests first** (Vitest with fake timers): a trace of E3 yields the intermediate boards of the spec in
  order and ends on the DTO board; Skip ends at once; a second state during an animation is queued; a
  newer version flushes; reduced motion shows the final board at once; a 600-drop trace ends within the cap.

## N7 `feat: play Mangola` (entry PR) (M)

Only entry PR open at the time (check `gh pr list` for the President entry PR first). Follows
`add-a-game.md` section 3.2 and section 4.

- `view/mangola/MangolaGameScreen.vue` (shared chrome: `GameTimer`, `RulesPanel`, `GameLobby`,
  `EndGameOverlay` with `result: 'draw'` for a repetition or endless draw, `DisconnectOverlay`,
  `ForfeitBanner`); placement screen then board.
- Shared files: `model/games.ts` (`define('mangola', { supportsBots: false, picker })`, flag flipped in N9),
  `games.test.ts`, `router.ts` (`mangola-room`), `router.test.ts`, `useGameRooms.ts` and its test, locales
  (`gamePicker.games.mangola`, `mangola` rules, `mangolaUi`, `options.mangola`), `en.test.ts`,
  `GamePickerView.test.ts` (counts), Cypress `game-picker`, `game-home`, `game-screens-homogeneity`,
  `cypress/support/stompPlayer.ts` (`'mangola'` in `ScriptedGameType`, plus the `arrange` / `confirmLayout`
  / `sow` helpers).
- Project `CLAUDE.md` game list and `context-map.md` "not covered yet" line.
- **Cypress first** (`test:` commit): `mangola-lobby.cy.ts` (create, invite, join, options `setup` and
  `firstPlayer` set by the host and read-only for the guest, Start, Leave); `mangola-game.cy.ts` (free
  placement with a preset, the scripted opponent confirms, the reveal, a full short game from a scripted
  position to the end screen, a capture visible on the board, the move announced in the live region).
  Waits on frames and DOM states, the animation skipped by a click.

## N8 `feat: Mangola bots with three levels` (L)

- `domain/bot/`: `MangolaBot` (port: `decideLayout`, `decideMove`), `RandomMangolaBot`, `GreedyMangolaBot`,
  `ExpertMangolaBot` (negamax, alpha-beta, iterative deepening, time budget and node cap, move ordering by
  captures, evaluation of the spec), `MangolaBotLevel` (`random|greedy|expert`, default `greedy`).
- `presentation/bot/MangolaBotCoordinator` (as `MorrisBotCoordinator`, but the decision is computed outside
  the monitor on a position snapshot, then applied under the monitor after the version check); the bot
  arranges and confirms in the placement phase.
- Factory `supportsBots() = true`, seat option `level` (`options.mangola.level`); `MangolaConfig`: broadcaster
  with `sessionService::botSeats`, `ThinkingDelay` (`mangola.bots.delay-min-ms` 700, `delay-max-ms` 1300),
  `mangola.bots.think-ms` 300, all 0 or minimal under `test` (`application-test.properties`).
- **Tests first:** `RandomMangolaBotTest` (always legal, layouts sum to 32), `GreedyMangolaBotTest` (takes the
  capture of E3 over a quiet move), `ExpertMangolaBotTest` (finds a forced win in 3 plies on a fixture; never
  picks an endless pit under `DRAW`; respects the node cap; deterministic with the constant generator;
  beats `greedy` in at least 70 % of 100 seeded games from random layouts, a statistical test kept fast by
  a small node cap), `MangolaBotCoordinatorTest` (manual scheduler: arranges and confirms; plays after the
  delay; drops a stale task; stops at the end; a new game after Play again gets a new table),
  lobby IT (add a bot, set `level`, start, the bot arranges and plays; Play again keeps it), IT asserting
  the bot seat receives no message and is never forfeited.
- Docs: `context-map.md` "Mangola bots".

## N9 `feat: play Mangola against a bot` (S)

- `model/games.ts`: `supportsBots: true` (and `games.test.ts`); locales `options.mangola.level`
  (Easy / Medium / Expert) and the rules-panel note "Expert is strong but not perfect".
- **Cypress first:** `mangola-bot.cy.ts`: add a bot, set Expert, start with `setup = checkerboard`, the bot
  answers each move (board state changes), the game reaches an end screen; Play again keeps the bot and
  its level. Manual check in the dev environment: the default delay and the animation queue read well.

## N10 `test: Mangola end-to-end coverage` (S-M)

- `mangola-placement.cy.ts`: hidden placement (the guest's frames never show the host's draft), Edit again,
  reveal on the second confirmation, reload during placement restores the draft.
- `mangola-endless.cy.ts`: from a scripted position (test seam, below) the endless pit is not offered
  (`ILLEGAL`) and the player with only endless moves loses.
- `mangola-rematch.cy.ts`, `mangola-presence.cy.ts`: Play again starts a new placement with the same options;
  a disconnection during placement and during play shows the shared overlays and the forfeit after grace.
- Test seam if needed: `presentation/MangolaTestPositionController` under `@Profile("test")`
  (`PUT /api/test/mangola/{gameId}/position`) with `MangolaFixedPositionAbsentInProductionIT`, as Kobo's
  `KoboTestDealController`. Added in N3 instead if N3's IT already needs it.

## TDD strategy

- **The JS is the oracle for the rules, the E-examples are the contract.** The ten hand-checked examples of
  the spec are written first, as exact boards and traces, before any engine code; the characterisation file
  generated from `ngola.js` then pins random games so a subtle difference (lap start after a capture, the
  empty-pit drop when the next pit is the start pit, a lap over 16 seeds) cannot slip in.
- **Invariants as property tests** (plain JUnit loops with a seeded generator, no new library): 64 seeds
  on the board after any move; the mover's half never loses seeds; the opponent's half never gains; every
  `legalPits` entry sows without exception and every other own pit is refused; the trace replayed from the
  previous board gives the new board (the same check the frontend animation relies on).
- **Behaviours of the JS not ported, each with a test that pins the new behaviour:** the random set-up's
  missing 16th pit; the draw on an endless loop (if Q1 is confirmed); the JS minimax.
- **Frontend:** pure helpers first (rotation, draft, trace replay), components with props in and events out,
  then the screen; Cypress written before the entry PR's code (`test:` commit first).

## Risks

- **Rule detail differs from the owner's memory** (captures, lap start, empty pit next to the start pit):
  the E-examples are reviewed by the owner with this plan; each point is a `MangolaRules` switch.
- **Endless sowings** in legal moves and bot search: Brent's check and the 100,000-drop cap bound every
  simulation; a test measures `legalPits` on the worst fixture.
- **Placement leaks** (hidden drafts): DTO test, STOMP IT on both seats' frames, Cypress N10.
- **Animation length and stale frames**: duration cap, Skip, queue flushed by `version` (#214 pattern).
- **Entry PR collision with President (#216)**: N7 waits for the other entry PR or goes first, never both.
- **Bot strength and time**: the expert's budget runs off the game monitor; node cap and zero delays under
  `test` keep Cypress deterministic.
- **Board at 375 px**: 8 columns of 44 px do not fit with gaps; the canvas decides (smaller pits with
  generous hit areas, or a layout change).
- **Name**: "Mangola" is the documented DR Congo name (chosen by the owner, 2026-10-15); the rules panel lists
  the other DR Congo names (Ngola, Mongola, Amungola, Angola) so players can find the game elsewhere.

## After the merges

- Tick the Mangola issue and roadmap #1 per merged PR; no status PR.
- Open an issue "Mangola variants" listing the spec's "Room for documented variants" (Mangola 1952, Omweso,
  Igisoro, Kiela, sequential placement) with the switches each needs.

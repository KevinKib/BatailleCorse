# Three Men's Morris v2: plan

Issue #147. Spec: `docs/specs/2026-10-14-three-men-morris-v2-design.md` (key decisions, variant rules with
sources, contract, room for Six/Nine Men's Morris, product questions). Written by Claude Opus 5.5.

**Goal:** play Three Men's Morris against a bot of three levels, in three variants (Tapatan, Achi, Picaria),
with draws (offer and threefold repetition) and a colour choice for the host, without changing what a v1
room does when the host touches nothing.

**Order:** backend first, each PR small and green on its own, nothing visible changes until the frontend
PRs: M1 (rules in data, no behaviour change) → M2 (variants) → M3 (colours) → M4 (draws) → M5 (bots). M2,
M3 and M4 only need M1, but they touch the same factory, aggregate and DTO, so they run in series to avoid
three-way conflicts. Frontend after the backend contract it uses, and after Kobo K3 (#186) is merged or by
rebasing on it: F1 (variants and colours on screen) needs M2 and M3; F2 (draws) needs M4 and F1; F3 (bots)
needs M5 and F1. F2 and F3 can run in parallel.

**Tests (every PR):** backend `cd backend && mvn -s ../deploy/docker/settings.xml test` (JUnit, `*IT`,
ArchUnit `ArchitectureTest` green; no new game package, so `ArchitectureRules.GAMES` is unchanged);
frontend `cd frontend && npm test && npx vite build`. Cypress (`docker compose -f docker-compose.e2e.yml`,
`npm run cy:run`, or a shifted stack with `--config baseUrl=...`) for F1, F2, F3; waits only on DOM states
(`should`), never `cy.wait(ms)`. Dev environment only for the frontend PRs (manual test).

**Conventions:** branches `refactor/...` and `feat/...`, conventional titles, one draft PR opened at the start
of each step with its real number; every new visible text through `locales/Messages.ts` and `locales/en.ts`;
backend paths relative to `backend/src/main/java/org/kevinkib/cardgames/threemenmorris/` unless stated,
frontend paths relative to `frontend/src/`.

**Cost scale:** S about half a day, M about a day, L about two days.

## Status

| PR | Title | Cost | Needs | State |
|----|-------|------|-------|-------|
| M1 | `refactor: Three Men's Morris rules as data` | M | - | [x] done (#190) |
| M2 | `feat: Achi and Picaria variants of Three Men's Morris` | M | M1 | [x] done (#194) |
| M3 | `feat: host colour option for Three Men's Morris` | S | M2 | [x] done (#198) |
| M4 | `feat: draw offers and threefold repetition in Three Men's Morris` | M | M3 | [x] done (#199) |
| M5 | `feat: Three Men's Morris bots with three levels` | L | M4 | [x] done, PR #202 (review pending) |
| F1 | `feat: Three Men's Morris variants and colours on screen` | M | M2, M3, #186 | [x] done, PR #205 |
| F2 | `feat: Three Men's Morris draw offer and draw end screen` | M | M4, F1 | [x] done, PR #210 |
| F3 | `feat: play Three Men's Morris against a bot` | S-M | M5, F1 | [x] done, PR #211 (review pending) |

Total about 6 to 7 days of work.

## Conflicts

| File | PRs | Handling |
|------|-----|----------|
| `domain/ThreeMenMorris.java`, `domain/ThreeMenMorrisFactory.java`, `presentation/dto/ThreeMenMorrisDto.java` | M1 to M5 | series |
| `config/ThreeMenMorrisConfig.java`, `resources/application-test.properties` | M3, M5 | series |
| `components/EndGameOverlay.vue` | F2 (and Kobo K3) | one optional prop, after #186 |
| `locales/Messages.ts`, `locales/en.ts` | F1, F2, F3 (and Kobo K3) | rebase; separate namespaces |
| `model/threemenmorris/MorrisState.ts`, `state/ThreeMenMorris.store.ts`, `view/threemenmorris/ThreeMenMorrisGameScreen.vue` | F1, F2, F3 | F1 first, then F2 and F3 rebase on each other |
| `model/games.ts` | F3 | one flag |

## M1 `refactor: Three Men's Morris rules as data` (M)

No behaviour, wire or test change for v1: every existing test passes unchanged.

- `domain/Geometry.java` (new record: point count, adjacency lists, lines; `isPoint`, `neighbours`,
  `areAdjacent`; constant `TAPATAN` built as today's `Board`). `Board.java` removed or reduced to a
  delegate of `Geometry.TAPATAN` for the v1 tests that use it.
- `domain/MorrisRules.java` (record: `geometry`, `piecesPerPlayer`, `PlacementRule`), constant `TAPATAN`.
- `domain/ThreeMenMorris.java`: constructor `(GameId, MorrisRules, PlayerId whiteSeat)`; the v1 constructor
  delegates with `(TAPATAN, seat 0)`; `fromLayout` keeps its signatures; `place`/`move`/lines/blocked read
  the rules; new queries `rules()`, `whiteSeat()`, `colourOf(seat)`, `legalPlacements(seat)`, `position()`.
- `domain/MorrisPosition.java` (immutable snapshot plus a `Key` of board and seat to move).
- `Move` and `MorrisEvent` validate points against the game's geometry (the aggregate checks, the records
  only refuse negatives) so a 16/24-point geometry fits later.
- Tests first: `GeometryTest` (Tapatan adjacency and lines equal v1's), `ThreeMenMorrisRulesTest` (the v1
  constructor equals `TAPATAN` with seat 0 white; `legalPlacements` equals the free points),
  `MorrisPositionTest` (keys equal for equal boards and side, differ otherwise).

## M2 `feat: Achi and Picaria variants of Three Men's Morris` (M)

- `domain/MorrisVariant.java` (`TAPATAN`, `ACHI`, `PICARIA`; option key, rules). `Geometry.PICARIA` (Tapatan
  plus 1-3, 3-7, 7-5, 5-1; **superseded 2026-10-18 by the 13-point board, PR #265**: grid 0..8 plus crossings 9..12, 16 lines). `MorrisRules.ACHI` (4 pieces), `MorrisRules.PICARIA` (3 pieces,
  `CENTRE_FORBIDDEN_DURING_PLACEMENT`).
- `ThreeMenMorris.place`: refuses the locked centre with a new `PointLockedException`; `legalPlacements`
  leaves it out.
- `ThreeMenMorrisFactory`: `optionDescriptors()` = `variant` (`tapatan|achi|picaria`, default `tapatan`, label
  `options.threeMenMorris.variant`); `create(id, n, options)` builds the variant.
- DTO: `variant`, `legalPlacements`.
- Tests first: `AchiTest` (4 placements each, then movement with one hole; White blocked right after the
  8th placement loses; line during placement wins), `PicariaTest` (centre refused during the whole placement, reachable by a move; short
  diagonal moves legal; no line along a short diagonal), `ThreeMenMorrisFactoryOptionsTest` (default
  `tapatan`, unknown variant refused by `validate`), `ThreeMenMorrisDtoTest` (new fields),
  `ThreeMenMorrisWebSocketControllerIT` (a room created with `variant=achi` starts with 4 pieces in hand).
- Docs: `docs/architecture/context-map.md` gains a Three Men's Morris paragraph (variants as data).

## M3 `feat: host colour option for Three Men's Morris` (S)

- `ThreeMenMorrisFactory(RandomGenerator)`: option `hostColour` (`white|black|random`, default `white`,
  label `options.threeMenMorris.hostColour`); `create` maps it to `whiteSeat` (one draw for `random`).
- `config/ThreeMenMorrisConfig.java`: a `RandomGenerator` bean for Morris, `ThreadLocalRandomGenerator` with
  `@Profile("!test")`, a constant generator (always 0, so "first candidate" and host White) with
  `@Profile("test")`; the factory takes it.
- DTO: `whiteSeat`, `players[].colour`.
- Tests first: factory test with a fixed generator (`white` gives seat 0, `black` seat 1 and Black's host
  waits for seat 1 to place first, `random` follows the generator), aggregate test (`current` starts on
  `whiteSeat`), `MorrisRandomAbsentInProductionIT` (production context holds `ThreadLocalRandomGenerator`),
  IT: a room with `hostColour=black` starts with seat 1 to play.

## M4 `feat: draw offers and threefold repetition in Three Men's Morris` (M)

- Domain: `offerDraw`, `acceptDraw`, `declineDraw` with the rules of the spec (movement phase, one pending
  offer, once between two own moves, cancelled by the next `place`/`move`); repetition count of
  movement-phase positions, draw at 3 after the line and block checks; `MorrisResult.Reason` gains
  `DRAW_AGREED`, `DRAW_REPETITION` with a null winner; `Action` gains `OFFER_DRAW`, `ACCEPT_DRAW`,
  `DECLINE_DRAW`; new exceptions; `drawOffer()`, `repetitions()`.
- Presentation: `ThreeMenMorrisGameActions.offerDraw/acceptDraw/declineDraw`; destinations
  `/three-men-morris/offerDraw`, `/acceptDraw`, `/declineDraw` (`GameActionPayload`); event types
  `DRAW_OFFERED`, `DRAW_DECLINED`, `GAME_OVER` with `action = ACCEPT_DRAW`; `ThreeMenMorrisEventData.from`
  covers the new domain events (new `MorrisEvent` records `DrawOffered`, `DrawDeclined`, `DrawAccepted`).
- DTO: `drawOffer`, `repetitions`, `Finished(null, reason)`.
- Deviation (kept the Tapatan wire byte for byte): the draw actions are not merged into `availableActions`
  (they would change every movement-phase view of the pinned contract). The domain has `drawActions(seat)`
  and the DTO an additive `drawActions`, written only when it is not the default of the phase (default:
  `["OFFER_DRAW"]` in the movement phase, nothing otherwise). `drawOffer` and `repetitions` (written from 2)
  are omitted at their default. Error events of the three commands carry `DRAW_OFFERED`, `DRAW_DECLINED`
  and `GAME_OVER` as their event type. New pinned file: `golden/three-men-morris-draws-wire.txt`.
- Tests first: `ThreeMenMorrisDrawOfferTest` (offer refused in placement, twice in a row, with one pending;
  accept finishes with no winner; decline clears it; a move by either side cancels it; version bumps; a
  forfeit with a pending offer finishes as a forfeit), `ThreeMenMorrisRepetitionTest` (a four-move cycle in
  Tapatan draws on the third occurrence; a line made by the repeating move wins instead; placement never
  counts), controller test and IT (two seats: offer, decline, offer after a move, accept, `GAME_OVER` to both
  seats with `outcome.winner = null`).

## M5 `feat: Three Men's Morris bots with three levels` (L)

- `domain/bot/`: `MorrisBot`, `MorrisDecision`, `RandomBot`, `GreedyBot`, `PerfectBot`, `MorrisSolver`
  (retrograde analysis per `MorrisRules`, lazily computed and cached), `MorrisBotLevel` (`random|greedy|perfect`,
  default `greedy`, `fromOption`). Draw answer rule: accept on a solved draw or loss.
- `presentation/bot/MorrisBotCoordinator.java` (listener and eviction listener; delay; version check under
  the monitor; reads `level` through `BotSeatOptions`; fallback to the first legal action; drops on end,
  eviction and rematch).
- Factory: `supportsBots() = true`, `seatOptionDescriptors()` = `level` (label `options.threeMenMorris.level`).
- `config/ThreeMenMorrisConfig.java`: broadcaster with `sessionService::botSeats`; `ThinkingDelay` from
  `threemenmorris.bots.delay-min-ms` (700) / `delay-max-ms` (1300); the coordinator bean registered on the
  broadcaster. `resources/application-test.properties`: both delays 0.
- Tests first: `MorrisSolverTest` (Tapatan and Achi are White wins, Picaria with the locked centre is a White win
  in 7 plies (the draw was the old rule), 3,602 / 4,512 / 2,545 positions, each solved under 1 s), `GreedyBotTest` (takes a line, blocks one), `PerfectBotTest` (never loses a
  solved draw; wins a solved win; with the constant generator its first moves are fixed), `RandomBotTest`
  (always legal), `MorrisBotCoordinatorTest` with a manual scheduler (acts after the delay; drops a stale task;
  answers a draw offer; stops on finish; a new game after Play again gets a new table; no action while the
  game is over), `SessionService`/lobby IT (add a bot, set `level`, start, the bot plays; Play again keeps the
  bot and its level), IT asserting the bot seat receives no message and is never forfeited.
- Docs: `docs/architecture/context-map.md` gains "Three Men's Morris bots" (ports, levels, solver).

## F1 `feat: Three Men's Morris variants and colours on screen` (M)

Start after #186 (Kobo K3) is merged, or rebase on it. Use the `ui-change` skill (board drawing changes).

- `model/threemenmorris/MorrisState.ts`: new fields, `winner: number | null`, new reasons.
- `model/threemenmorris/geometries.ts` (new): per variant, point coordinates, segments, point names keys,
  arrow navigation by direction; `board.ts` keeps the selection reducer, `selectable()` reads
  `legalPlacements`.
- `components/morris/MorrisBoard.vue`: `geometry` prop; points placed from coordinates; Picaria's diamond.
- Store and screen: `myColour`/`colourOf` from `players[].colour`; variant name on the table; last opponent
  move marked on the board.
- `RulesPanel` sections per variant; locales `options.threeMenMorris.variant` / `hostColour` and the new texts.
- Tests: Vitest (`geometries.test.ts`: each variant's points and segments, arrows; `board.test.ts` uses
  `legalPlacements`; `MorrisBoard.test.ts` draws the diamond for Picaria and keeps 44 px targets; store test:
  colour from the DTO when the host plays Black). Cypress `morris-variants.cy.ts`: the host picks Achi and
  Black in the lobby, the guest sees both read-only, the guest (White) places first, 4 pieces each; Picaria
  centre never offered in the placement phase. Existing `morris-game.cy.ts` and `morris-lobby.cy.ts` unchanged
  and green (defaults).

## F2 `feat: Three Men's Morris draw offer and draw end screen` (M)

- `components/EndGameOverlay.vue`: optional `result?: 'victory' | 'defeat' | 'draw'` (default from
  `didIWin`); draw title, no trophy; locale `endGame.draw` (shared namespace).
- Morris session/store: `offerDraw`, `acceptDraw`, `declineDraw`; `drawOffer` and `repetitions` from the state;
  events `DRAW_OFFERED`, `DRAW_DECLINED` for the live region.
- Screen: Offer button, pending label, incoming banner with Accept / Decline (`alertdialog`, keyboard), the
  repetition hint at 2, draw subtitles on the end screen.
- Tests: Vitest (`EndGameOverlay` draw state, the other games' snapshot unchanged; store and screen for each
  offer state; a move clears the banner). Cypress `morris-draw.cy.ts` with `stompPlayer` as the opponent:
  offer, decline, offer again after a move, accept, draw end screen on both sides; a repetition draw scripted
  in Tapatan (four moves shuffled twice).

## F3 `feat: play Three Men's Morris against a bot` (S-M)

- `model/games.ts`: `supportsBots: true` for `three-men-morris` (update `games.test.ts`).
- Locales `options.threeMenMorris.level` (Easy / Medium / Expert per [Q2]); bot move announcement.
- Tests: Vitest (lobby shows Add bot and the level select for Morris). Cypress `morris-bot.cy.ts` (zero delay,
  constant generator under `test`): add a bot, set Expert, start as White in Picaria (a solved draw) and as
  White in Tapatan; assert the bot answers each move (board state), declines a draw while it wins and accepts
  one in a drawn position; Play again keeps the bot and its level.
- Manual check in the dev environment: the default delay makes the bot's move visible (last-move mark).

## After the merges

- Tick #147 (or its follow-up issue) with the merged PRs; no status PR.
- Open an issue "Nine Men's Morris (Six and Nine)" with the spec's "Room for Six and Nine Men's Morris"
  section, the cost estimate (7 to 9 PRs) and the recommendation (separate picker game, same bounded context
  and engine). Not started by this plan.

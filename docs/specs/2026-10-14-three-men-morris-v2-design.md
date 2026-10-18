# Three Men's Morris v2: design

Issue #147 (follow-up of the v1 frontend). Plan: `docs/plans/2026-10-14-three-men-morris-v2-plan.md`.
Written by Claude Opus 5.5. The owner played v1 and asked for five changes: computer players with levels,
variants (Tapatan, Achi, Picaria) as a lobby option, draws (offer and repetition), a colour choice for the
host, and what follows from them for the screen, lobby, locales and end-to-end tests. Six and Nine Men's
Morris are **not** built here: this spec only says what v2 must leave room for, and what they would cost.

Legend: **[V]** verified by reading the code on `origin/main` at `a61377e` (2026-10-14), with `file:line`
(backend paths relative to `backend/src/main/java/org/kevinkib/cardgames/`, frontend paths relative to
`frontend/src/`); **[C]** computed by a throwaway retrograde analysis of the three variants (not committed;
the solver's unit tests in plan PR M5 re-check it; **corrected in M5**: the first draft computed Picaria with the
centre open from the first placement, see the Picaria row); **[H]** hypothesis to confirm in the PR that relies on it;
**[Q]** product question, listed at the end with a recommendation.

> **Update 2026-10-18 (owner decision, PR #265): Picaria is played on the 13-point board and the 9-point
> Picaria is removed** (Tapatan and Achi unchanged). Every statement below about Picaria's "nine points",
> "short diagonals" and its 7-ply / draw results describes the superseded 9-point board; the current board,
> rules and solver result are in the section "Picaria on the 13-point board" at the end of the design
> (before the product questions).

## Key decisions

1. **Rules become data, one engine.** The geometry (points, adjacency, winning lines) and the rule set
   (pieces per player, placement restriction) move into a `MorrisRules` value chosen per variant; the
   aggregate `ThreeMenMorris` runs any of them. Tapatan (today's game) is the default and behaves exactly as
   v1. Achi and Picaria are two more `MorrisRules` constants, not two more classes. The same engine is the
   seam for Six and Nine Men's Morris later (see "Room for Six and Nine Men's Morris").
2. **Every random or rule decision is server-side and in the domain.** The colour draw happens once, at the
   start, in `ThreeMenMorrisFactory.create` (the only place that already receives the lobby options,
   `sessionmanagement/core/application/SessionService.java:274`), through an injected `RandomGenerator`; the
   aggregate receives a fixed `whiteSeat` and stays deterministic. Draw offers, repetition and the bot's
   choice are pure domain code. The client only reads the DTO, which gains the facts it used to compute
   itself (colours, and the points where a piece may be placed, now that Picaria locks the centre).
3. **Bots reuse the existing seat-bot plumbing, with levels as a seat option.** `supportsBots()` becomes
   true, a seat option `level` (`random`, `greedy`, `perfect`, default `greedy`) is declared like Bataille
   Corse's `tier`, and a `MorrisBotCoordinator` follows the pattern of the two existing coordinators (listen
   to the state broadcaster, schedule after a delay, drop the task if the version moved, act through the same
   `ThreeMenMorrisGameActions` as a human). The perfect level reads a solved table: each variant has about
   2,500 to 4,500 reachable positions (counted from the seat to move, M5) so a full retrograde analysis takes
   milliseconds and is cached.
4. **Draws end a game with no winner.** A player may offer a draw (one pending offer at a time, cancelled by
   the next piece played, accepted or declined by the opponent); the third occurrence of the same position
   with the same player to move ends the game automatically. The outcome keeps its shape
   (`Finished(winner, reason)`) with `winner = null` and reasons `DRAW_AGREED` / `DRAW_REPETITION`; the
   shared end screen gains a draw state through one additive optional prop.
5. **Colour is a lobby option of the host, white always starts.** Option `hostColour` (`white`, `black`,
   `random`, default `white`, so v1 is unchanged). Seat 0 is the host (`SessionGame.HOST_SEAT`,
   `sessionmanagement/core/domain/SessionGame.java:35`); the draw is made at the start and the DTO gives each
   player's colour, so the frontend stops deriving it from the seat number.

## What exists today [V]

- **Domain.** `threemenmorris/domain/ThreeMenMorris.java`: seats 0 and 1 are hard-wired as White and Black
  (`:24-25`), White starts (`:30`), `place` (`:87-107`) and `move` (`:109-133`) check the turn, the phase and
  the point, then look for a line (`finishIfLine`, `:253-261`) and for a blocked opponent (`finishIfBlocked`,
  `:264-268`). The geometry is static in `Board.java` (`POINTS = 9`, the 8 `LINES` `:18-21`, adjacency =
  orthogonal neighbours plus the centre `:49-55`). `MorrisResult.Reason` is `LINE, BLOCKED, FORFEIT`
  (`MorrisResult.java:8`). `Action` is `PLACE, MOVE`. Every accepted command bumps `version()`.
- **Factory.** `ThreeMenMorrisFactory.java`: exactly 2 players, no option, no seat option, no bot
  (`supportsBots()` inherits `false`, `game/GameFactory.java:21-23`).
- **Shared option machinery.** `GameFactory` declares game options (`optionDescriptors`, `:31-33`), seat
  options (`seatOptionDescriptors`, `:36-38`), strict validation with defaults (`validate`, `:63-79`;
  `validateSeatOption`, `:46-54`) and `create(id, n, options)` (`:26-28`). `LobbyView` fills defaults and
  sets `canEditOptions` for the host (`sessionmanagement/core/application/LobbyView.java:63-65,79`).
  `/app/lobby/setOptions`, `/lobby/addBot`, `/lobby/setSeatOption` exist (`presentation/LobbyController.java:51,58,111`);
  `addBot` refuses a game without bots with `BotsNotSupportedException` (`SessionService.java:87-91`).
- **Bot plumbing.** Ports `BotSeats`, `BotSeatOptions`, `BotScheduler`, `ThinkingDelay` in
  `presentation/bot/`; `AppConfig` publishes `BotSeatOptions` and `BotScheduler` beans (`config/AppConfig.java:101-109`).
  Morris's broadcaster is built with `BotSeats.none()` (`config/ThreeMenMorrisConfig.java:28`), so a bot
  seat would today still receive messages and nobody would drive it. Bot levels exist only for Bataille
  Corse (`tier`, `bataillecorse/domain/BatailleCorseFactory.java:43-48`, read in
  `bataillecorse/presentation/bot/BatailleCorseBotCoordinator.java:167-173`); **Bullshit bots have no level**
  (no `seatOptionDescriptors` in `bullshit/domain/BullshitFactory.java`), only a thinking delay
  (`config/BullshitConfig.java:57-61`) zeroed by the `test` profile (`resources/application-test.properties`).
- **Rematch.** `SessionService.playAgain` (`SessionService.java:58-70`) keeps the game options and re-seats
  the bots (names and seat options) after seat 0 (`SessionGame.claimBotsOf`, `SessionGame.java:103-115`);
  the first human to call Play again takes seat 0 and becomes the host.
- **Deterministic e2e precedent.** Kobo's `ScriptedDeckSource` (`kobo/domain/ScriptedDeckSource.java`) is a
  `test`-profile bean behind a port (`config/KoboConfig.java:25-36`), driven by a `test`-only controller
  (`kobo/presentation/KoboTestDealController.java`) and proved absent in production by
  `KoboFixedDealAbsentInProductionIT`.
- **ArchUnit.** `architecture/ArchitectureRules.java` (test sources): a game's domain depends on no Spring,
  session or presentation (`:45-51`), **no game depends on another game** (`:54-58`, games listed `:17`).
- **Frontend.** The DTO type has `winner: number` (`model/threemenmorris/MorrisState.ts:26`) and three
  reasons (`:8`). The colour is derived from the seat (`state/ThreeMenMorris.store.ts:91-92`,
  `view/threemenmorris/ThreeMenMorrisGameScreen.vue:39`). The geometry is hard-coded twice: `board.ts`
  (`POINTS`, `LINES`, 3×3 arrow navigation, `:14-41`) and `components/morris/MorrisBoard.vue` (`:38-46`, CSS
  grid `:205`). The free points to place on are computed by the client from `board` (`board.ts:56-58`).
  The registry entry says `supportsBots: false` (`model/games.ts:72-75`). The lobby renders any game option
  (`components/lobby/LobbyGameOptions.vue`) and any seat option (`components/lobby/LobbySeatOptions.vue`) from
  the descriptors, labels from `locales/en.ts` under `options.<game>.<key>` (`en.ts:412-425`).
  `EndGameOverlay.vue` only knows victory or defeat (`didIWin`, `:3-5,36`).

## Rules of the variants

All three: 3×3 board numbered row by row (`0 1 2 / 3 4 5 / 6 7 8`), two players, White starts, placement
(one piece per turn on a free point) then movement (one piece one step along a line to a free adjacent point,
no jump, no pass), three in a row along one of the 8 lines (3 rows, 3 columns, 2 long diagonals) wins in
either phase, a player with no legal move loses (v1 assumption [A3] kept). Who starts is not given by the
sources: White starts, as in v1.

| | Tapatan (today, default) | Achi | Picaria |
|-|--------------------------|------|---------|
| Pieces each | 3 | **4** (8 on 9 points: one hole) | 3 |
| Adjacency | orthogonal + centre (the two long diagonals) | same as Tapatan | ~~Tapatan's plus the four short diagonals~~ **superseded: 13-point board, see "Picaria on the 13-point board"** |
| Winning lines | the 8 | the 8 | ~~the 8~~ **superseded: 16 lines on 13 points** |
| Placement restriction | none | none | **no piece may be placed on the centre (owner decision, whatdowedoallday.com/picaria); it is reached by a move** |
| Blocked player | practically unreachable (0 reachable blocked positions **[C]**) | **reachable** (12 reachable blocked positions **[C]**): the `BLOCKED` reason becomes a real ending | unreachable **[C]** |
| Perfect play from the start | **White wins in 9 plies [C]** | **White wins in 11 plies [C]** | ~~White wins in 7 plies (9-point board)~~ **superseded**: 13-point board, White wins in 7 plies with the lock and in 5 without it [C], see the comparison table |
| Source | v1 source (Locus Ludi PDF, board B) | Wikipedia "Achi (game)", which cites Claudia Zaslavsky, *Tic Tac Toe and Other Three-in-a-Row Games* (1982) **[H: book not read]** | Wikipedia "Picaria", citing Merilyn Simonds Mohr, *The Games Treasury* (1993), p. 28 **[H: book not read]** |

Details and choices:

- **Achi.** The sources agree on four pieces each, placement until all eight are down, then a step along a
  line to the adjacent empty point, three in a row wins, "if a player can't move, he loses". A variant
  without diagonal wins exists; not offered **[Q3]**. After the 8th placement Black has placed last, so White
  moves first; if White then has no piece next to the hole, White loses at once (the v1 code already checks
  the blocked rule right after the last placement, `ThreeMenMorris.java:101-104`).
- **Picaria.** *(Superseded 2026-10-18: Picaria now plays the 13-point board, see the dedicated section.)*
  Played on a 9-point or a 13-point board; this first version played the 9-point board. The source calls "a player cannot move" a draw *or* a loss by
  house rule; it is unreachable here **[C]**, so v2 keeps "blocked loses" for one rule everywhere. The
  centre lock does not remove a forced win here, it creates one (**corrected in M5**): with the centre
  closed for the whole placement phase White wins in 7 plies, the first move into the centre completing a
  diameter; with the centre open from the first placement Picaria is a draw **[C]** (extra short diagonals).
  The lock follows the owner's decision (#194); that it makes the variant a White win is a consequence the
  owner may want to know before keeping it **[Q11]**.
- **Names.** The game card stays "Three Men's Morris"; the variant picker says "Tapatan (3 pieces)",
  "Achi (4 pieces)", "Picaria (3 pieces, 13-point board)" **[Q1]**.

## Domain design

Package `threemenmorris/domain` (pure, ArchUnit unchanged).

- `Geometry` (record, replaces the static `Board`): `points` (count), `adjacency` (list of neighbour lists),
  `lines` (list of point lists). Constants `Geometry.TAPATAN` (today's `Board`, also used by Achi) and
  `Geometry.PICARIA` (the 13-point board: the 3x3 grid plus four crossings, see "Picaria on the 13-point board"). Point validation goes through the geometry
  (`isPoint`, `neighbours`, `areAdjacent`).
- `MorrisRules` (record): `geometry`, `piecesPerPlayer`, `PlacementRule` (`FREE` or
  `CENTRE_FORBIDDEN_DURING_PLACEMENT`). Constants `TAPATAN`, `ACHI`, `PICARIA`; `MorrisVariant` enum
  (`TAPATAN`, `ACHI`, `PICARIA`, with the option key `tapatan` / `achi` / `picaria` and its rules).
- `ThreeMenMorris(GameId, MorrisRules, PlayerId whiteSeat)`; the v1 constructor `ThreeMenMorris(GameId)`
  stays as `(id, TAPATAN, seat 0)` so every v1 test is untouched. New queries: `rules()`, `whiteSeat()`,
  `colourOf(seat)`, `legalPlacements(seat)` (free points, minus the centre while it is locked; empty outside
  the seat's turn or the placement phase), `drawOffer()`, `repetitions()` (occurrences of the current
  position), and `position()` (an immutable `MorrisPosition`: board, pieces in hand, seat to move, rules)
  used by the bots and by the repetition count.
- **Draw offer.** Commands `offerDraw(seat)`, `acceptDraw(seat)`, `declineDraw(seat)`. Rules: the game is
  in progress and in the movement phase **[Q4]**; no offer is pending; a player offers at most once between
  two of their own moves (no spam). Any seat may offer at any time (its turn or not), the opponent may
  answer at any time; the next accepted `place` or `move` by either player cancels a pending offer (a move by
  the offeree is an implicit refusal, a move by the offerer withdraws it). `acceptDraw` finishes with
  `MorrisResult(null, DRAW_AGREED)`. Each of the three commands bumps `version()` (the bot coordinator and the
  clients rely on it). New exceptions: `NoDrawOfferException`, `DrawOfferPendingException`,
  `DrawOfferNotAllowedException` (all `ThreeMenMorrisException`). `Action` gains `OFFER_DRAW`, `ACCEPT_DRAW`,
  `DECLINE_DRAW`; `availableActions(seat)` may now hold several actions and is no longer empty off-turn
  (an offeree can answer while it is not its turn).
- **Repetition.** A `Map<MorrisPosition.Key, Integer>` counts movement-phase positions (board plus seat to
  move; pieces in hand are zero by then). The placement phase never repeats a position (each placement adds
  a piece), so counting starts with the first movement position, which counts once. When a move makes a
  position reach **3** occurrences the game finishes with `MorrisResult(null, DRAW_REPETITION)`, unless the
  move made a line (a win is checked first). A line or a block always beats a repetition.
- `MorrisResult.Reason` gains `DRAW_AGREED`, `DRAW_REPETITION`; `winner` is null for both; a new
  `isDraw()`. `forfeit` is unchanged (a forfeit during a pending offer simply ends the game).
- **Colour.** `whiteSeat` is fixed at construction; `current` starts on it. `other(seat)` and the board
  owners stay by seat, so nothing else changes.

## Bots

- **Strategies** (`threemenmorris/domain/bot`, pure): `MorrisBot` port `decide(MorrisPosition, seat,
  pendingDrawOffer) -> MorrisDecision` (`Place(point)`, `Move(from, to)`, `AcceptDraw`, `DeclineDraw`).
  - `RandomBot`: a uniformly random legal placement or move.
  - `GreedyBot`: plays a winning line if it has one, else blocks the opponent's immediate line (a placement
    on the missing point, or in the movement phase a move that leaves the opponent no line next turn when one
    exists), else random.
  - `PerfectBot`: reads `MorrisSolver`: a retrograde analysis per `MorrisRules` (win / loss / draw for every
    reachable position, with distance to the end), cached once per variant (2,545 to 4,512 positions per
    variant, from the seat to move **[C]**; the computation runs in a few milliseconds, lazily on first use, thread-safe). It plays
    the fastest win, else a drawing move, else the slowest loss. A plain minimax would not terminate (the
    movement phase has cycles); the retrograde analysis handles them, and its draws match the threefold
    rule (a won position can always be won without repeating).
  - All choices among equal moves go through an injected `RandomGenerator`, so a game is reproducible in tests.
  - **Answering a draw offer (every level, one rule):** accept when the solver says the bot's position is a
    draw or a loss, decline when it is a win. Bots never offer a draw (the repetition rule ends endless
    games) **[Q6]**.
- **Coordinator** (`threemenmorris/presentation/bot/MorrisBotCoordinator`): `GameStateListener<ThreeMenMorris>`
  and `GameEvictionListener`, same shape as `BullshitBotCoordinator.java:109-137`: on each broadcast, if a bot
  seat has an action, schedule it after a thinking delay with the current version; under the game monitor,
  re-check the version and that the game is not finished, then act through `ThreeMenMorrisGameActions`. The
  level is read through `BotSeatOptions` (key `level`), as Bataille Corse reads `tier`. A failing strategy
  falls back to the first legal move. Dropped on game end, eviction, and when a new game replaces the old one
  under the same id (rematch). Not extracted into a shared driver: the three coordinators differ (Bataille
  Corse acts for every bot at once and grabs piles, Bullshit learns from events and hesitates before calling),
  so the rule of three of the mutualization spec does not apply; noted as a known near-duplication.
- **Delay:** uniform `threemenmorris.bots.delay-min-ms=700` to `delay-max-ms=1300` **[Q5]**, zero under the
  `test` profile like Bullshit's. The human sees the bot's move because the board marks the last opponent
  move (frontend, below).
- **Rematch:** bots and their level are kept by `claimBotsOf` (no new code). The human takes seat 0, so
  `hostColour` applies to the human.
- **Disconnection:** a bot seat never opens a connection, so presence never starts a grace period for it
  (`PresenceService` is driven by WebSocket connections and has no bot-specific code) **[H: asserted by the
  coordinator IT, as Bullshit bots already rely on it]**. If the human disconnects, the
  common grace period runs; the bot still plays a move that is its turn (then the human is to move, nothing
  more happens); after the grace the human forfeits and the bot wins (`FORFEIT`). A bot-only game cannot
  exist (2 seats, the host is always human).
- `ThreeMenMorrisConfig`: broadcaster built with `sessionService::botSeats` instead of `BotSeats.none()` (bot
  seats get no message), factory `supportsBots() = true`, seat option descriptor `level`.

## Colours

- Game option `hostColour`: `white`, `black`, `random`; default `white` **[Q7]**. Label: "Host plays".
- **Where the draw happens:** `ThreeMenMorrisFactory.create(id, 2, options)` maps the option to `whiteSeat`
  (seat 0 for `white`, seat 1 for `black`, one `RandomGenerator` draw for `random`). The domain receives a
  fixed seat and is tested without randomness; the factory is tested with a fixed generator.
- **Lobby before the start:** everybody sees the option ("Host plays: Random" for a guest, radios for the
  host). No colour is shown on the seats before the start; after the start every DTO gives each player's
  colour, and the table shows it as today ("You play White, White starts").
- **Rematch:** the options are kept (`SessionService.java:61-67`): `white` gives white to the new seat 0,
  which is whoever clicked Play again first between two humans; `random` draws again. No automatic
  alternation in v2 **[Q8]**: the lobby is shown again before every rematch, the host can change it.

## Network contract

Every addition is additive; no destination, payload or field is renamed or removed.

- **Lobby (`LobbyView`, unchanged shape):** `optionDescriptors` now holds `variant` (`tapatan|achi|picaria`,
  default `tapatan`, label key `options.threeMenMorris.variant`) and `hostColour` (`white|black|random`,
  default `white`, `options.threeMenMorris.hostColour`); `seatOptionDescriptors` holds `level`
  (`random|greedy|perfect`, default `greedy`, `options.threeMenMorris.level`). Edited through the existing
  `/app/lobby/setOptions` and `/app/lobby/setSeatOption`; bots through `/app/lobby/addBot` / `removeBot`.
- **New STOMP destinations** (payload `GameActionPayload {gameId, token}`, camelCase like `/kobo/swapDrawn`):
  `/app/three-men-morris/offerDraw`, `/acceptDraw`, `/declineDraw`. A refused one answers the sender alone with
  an `ErrorResponse` and its state (`SeatCommands`, unchanged).
- **New event types** (`ThreeMenMorrisEventType`): `DRAW_OFFERED`, `DRAW_DECLINED` (data `{seat, action}`);
  an accepted offer is announced as `GAME_OVER` with `action = "ACCEPT_DRAW"`; a repetition draw is the
  `GAME_OVER` of the `MOVE` that caused it. A cancelled offer needs no event: the next `PLACE`/`MOVE` state
  has `drawOffer = null`.
- **`ThreeMenMorrisDto` additions:** `variant` (`"tapatan"`...), `whiteSeat`, `players[].colour`
  (`"WHITE"`/`"BLACK"`), `legalPlacements` (viewer, placement phase only), `drawOffer` (`{seat}` or null),
  `repetitions` (occurrences of the current position, 1 or 2 while the game runs), new values in
  `availableActions` and in `outcome.reason`; `outcome.winner` is null for a draw (already `Integer`,
  `presentation/dto/ThreeMenMorrisDto.java:50`). `board` keeps 9 entries for the three variants.
- **Compatibility with games in progress:** state is in memory only, so a deployment ends every room and a
  restored tab gets 404 and leaves through the existing "game gone" path; no live migration exists to support.
  A lobby without the new options is completed with the defaults by `validate`/`LobbyView`, so v1 behaviour is
  what a room gets unless the host changes something. An old browser tab still holding the v1 bundle ignores
  the new fields; it would show a draw as a defeat until reloaded (accepted).

## Frontend

- **Types and store:** `MorrisState` gains the new fields; `MorrisOutcome.winner: number | null`; reasons
  `DRAW_AGREED`, `DRAW_REPETITION`. `myColour` and `colourOf` read `players[].colour`. `selectable()` uses
  `legalPlacements` instead of every free point (the server stays the only rule source).
- **Geometry by variant:** `model/threemenmorris/geometries.ts` maps `variant` to the drawing of the board
  (point coordinates in a 0-100 box, segments to draw, point names, arrow navigation computed from the
  coordinates: nearest point in the arrow's direction). `MorrisBoard.vue` takes a `geometry` prop instead of
  its constants and positions points absolutely from the coordinates rather than with a 3×3 grid. Picaria
  draws its 13-point board (grid, corner diagonals and the diamond; see the 13-point section). The client still knows no adjacency for play: legal moves come from the server.
- **Last move:** the opponent's (or bot's) last move is marked on the board (a faint ring on the origin and
  the destination) until the next state, so a quick bot move is visible; under reduced motion it is static.
- **Draw:** a "Offer a draw" button under my tag when `OFFER_DRAW` is available; a pending offer of mine
  shows "Draw offered" (disabled); an incoming offer shows a banner "Bob offers a draw" with Accept /
  Decline (`role="alertdialog"`, keyboard reachable, not modal: the board stays playable and a move declines).
  When `repetitions == 2` a hint reads "This position has occurred twice: once more and the game is drawn".
- **End screen:** `EndGameOverlay.vue` gets an optional `result?: 'victory' | 'defeat' | 'draw'` prop
  (defaults to `didIWin`, so Bataille Corse, Bullshit and Kobo are unchanged): title "DRAW", no trophy,
  neutral colour. Subtitles "Draw agreed" / "Draw by repetition: the same position occurred three times".
- **Lobby:** nothing new to build: the shared lobby renders `variant` and `hostColour` as radio groups and
  `level` as a select on each bot seat; "Add bot" appears once the registry says `supportsBots: true`. Only
  the locale labels are added.
- **Rules panel:** one text section per variant, the one in play shown first (the variant comes from the DTO).
- **Locales** (`locales/Messages.ts`, `locales/en.ts`): `options.threeMenMorris.{variant,hostColour,level}`
  with their values; `threeMenMorrisUi` gains the draw texts (offer, offered, incoming, accept, decline,
  repetition hint, end subtitles, draw title through `EndGameOverlay`), the colour line, the variant name on
  the table, and the last-move announcement for bots; rules sections for Achi and Picaria.

## End-to-end determinism

No test controller is needed. Under the `test` profile:
- the bot delay is zero (`application-test.properties`), as for Bullshit;
- the Morris `RandomGenerator` bean is a constant generator (always the first candidate), so a bot's choice
  among equal moves and the `random` colour draw are fixed; production gets `ThreadLocalRandomGenerator`.
  A `MorrisRandomAbsentInProductionIT` proves the production bean, like `KoboFixedDealAbsentInProductionIT`;
- the `greedy` and `perfect` bots are then fully deterministic, so a Cypress spec can script a whole game
  against a bot; the colour is set explicitly (`white` or `black`) wherever a spec depends on it.
If a later spec needs a fixed bot move independent of the strategy, a `ScriptedMorrisBot` like
`ScriptedDeckSource` can be added then; not now.

## Room for Six and Nine Men's Morris (not built)

What they add: a 16-point (Six: two concentric squares) or 24-point (Nine: three squares) board; 6 or 9
pieces each; a **mill** (three in a row on a line) does not win but lets the player **remove** an opponent
piece (not one inside a mill unless all are); Nine lets a player reduced to 3 pieces **fly** to any free
point; a player loses when reduced to 2 pieces or blocked; long games need a draw rule (repetition, plus a
"N moves without a mill" rule).

What v2 must prepare so that they do not need a rewrite (all cheap, all in the plan's PRs):
- **Rules in data** (decision 1): `Geometry` with any number of points, adjacency lists and lines;
  `MorrisRules` as a record that can later carry `onLine = WIN | REMOVE`, `flyingAt`, `loseBelow` without
  touching the three existing variants.
- **A state machine with room for a sub-step:** commands go through one place that checks phase and turn;
  `availableActions` is a list (v2 already puts several actions in it), so "must remove a piece" becomes an
  action `REMOVE` with a `legalRemovals` list and the turn not passing until it is done. v2 does not add it.
- **DTO per legal list:** v2 adds `legalPlacements` next to `legalMoves`, so a `legalRemovals` fits the
  same pattern; `board` is a list whose length is the geometry's; `variant` tells the client which board
  to draw.
- **A board component parameterised by geometry** (coordinates and segments, arrow navigation by direction).
- **A bot port on `MorrisPosition` + `MorrisRules`**, so a depth-limited search can plug in where the solver
  does not fit.

What is honestly harder:
- The rules engine is the **smaller** part: mills, removal, flying and the 2-piece loss are a few hundred
  lines of pure, well-tested domain code.
- The **board** is harder: 24 points at 375 px leave about 50 px between neighbours (7×7 lattice on
  ~320 px), which holds 44 px targets but leaves no room for labels; 18 pieces plus the pieces in hand need a
  new tag layout; the "remove a piece" step needs its own selection mode, announcements and keyboard path.
- The **capture flow** touches every layer (an extra sub-step per turn, events, the live region, the bot,
  the repetition key which then includes pieces in hand and the removal step).
- The **bots**: Nine Men's Morris has billions of positions (it was solved in 1996 with endgame databases;
  the result is a draw), so an exhaustive solver is out of reach in a request. It needs an alpha-beta search
  with iterative deepening under a time budget (about 300 ms) and a heuristic (pieces, mills, open mills,
  mobility, blocked pieces); levels become depth limits. Six Men's Morris is smaller but still beyond a
  millisecond table.

**Cost (estimate):** engine extension (removal sub-step, mills, flying, 2-piece loss, draw rule) 2 PRs M;
16/24-point geometry and DTO 1 PR S; frontend board and capture flow 2-3 PRs M; alpha-beta bot 1-2 PRs L;
e2e and rules texts 1 PR S. About **7 to 9 PRs**, roughly twice this v2.

**Recommendation: a separate game on the picker, in the same bounded context.** "Nine Men's Morris"
(variants Six and Nine) gets its own factory, game type, picker card, route and rules panel, because the
players see a different game (another board, captures, 20-minute games, a different bot); mixing it into
the 3×3 variant list would make the lobby option list hold two games. But it lives in the same package
`threemenmorris` (maybe renamed `morris` then) and runs the same engine: ArchUnit forbids a game from
depending on another game (`ArchitectureRules.java:54-58`), so a separate bounded context would have to
copy the engine. One context with two factories is allowed and shares everything.

## What we do not do

No Six or Nine Men's Morris, no capture, no flying; no 9-point Picaria (the 13-point one is the only Picaria since 2026-10-18); no Achi without diagonals; no bot
draw offers; no chat or takeback; no time control; no automatic colour alternation; no change to the session
core, the lobby components' logic, or Bataille Corse, Bullshit and Kobo (only the additive end-screen prop);
no extraction of a shared bot driver; no change to Kobo's in-flight frontend (PR #186).

## Risks

- **Kobo K3 (#186, open) on the same shared frontend files** (`EndGameOverlay.vue`, `locales/en.ts`,
  `locales/Messages.ts`, maybe `cypress/support/stompPlayer.ts`). Mitigation: the backend PRs go first; the
  frontend PRs start after #186 merges or rebase on it; the end-screen change is a single optional prop.
- **Perfect bot as White is unbeatable in all three variants [C].** That is the game, but it may feel unfair;
  the level names and the colour option let the player choose (see [Q2]).
- **Draw races** (an offer and a move at the same time): every command runs under the game monitor and bumps
  the version; the losing command gets an `ErrorResponse` with the current state.
- **Achi blocks are frequent [C]:** the `BLOCKED` ending, untested on screen in v1, becomes common; the e2e
  must cover it.
- **Changing `availableActions` semantics** (several actions, non-empty off-turn for an offeree): the
  frontend `myTurn()` (`board.ts:49-51`) and `isMyTurn` must keep using `currentSeat` plus `PLACE`/`MOVE`,
  not "any action"; covered by Vitest.
- **Cypress flakiness:** zero bot delay and a constant generator under `test`; every wait on a DOM state.

## Picaria on the 13-point board (owner decision 2026-10-18, PR #265)

The 9-point Picaria is removed; Picaria is the Zuni board of 13 points. Tapatan and Achi are unchanged.

**The board.** A Zuni Picaria board is a 3x3 grid (3 horizontal and 3 vertical lines), the 2 corner-to-corner
diagonals, and the 4 diagonals joining the midpoints of the sides (a diamond). Pieces sit on the 4 crossings
where the diamond's edges cut the corner diagonals, plus the 9 grid points: 13 points.

**Numbering.** The grid keeps its numbering (0 1 2 / 3 4 5 / 6 7 8, the centre is 4); the crossings are
9..12, clockwise from the top left: **9** between corner 0 and the centre, **10** between 2 and the centre,
**11** between 8 and the centre, **12** between 6 and the centre. The back end only reads `Geometry`, so the
`board` array of the DTO has 13 entries for Picaria (9 for Tapatan and Achi).

**Adjacency** is "consecutive along a drawn line" (no jump):
- grid rows and columns: 0-1, 1-2, 3-4, 4-5, 6-7, 7-8, 0-3, 3-6, 1-4, 4-7, 2-5, 5-8;
- corner diagonals 0-9-4-11-8 and 2-10-4-12-6: 0-9, 9-4, 4-11, 11-8, 2-10, 10-4, 4-12, 12-6 (a corner is no
  longer next to the centre: a crossing sits between);
- diamond edges: 1-9, 9-3, 1-10, 10-5, 5-11, 11-7, 7-12, 12-3.

The centre (4) has 8 neighbours (1, 3, 5, 7, 9, 10, 11, 12); a side middle has 5; a corner 3; a crossing 4.

**Winning lines (16):** the 3 rows and 3 columns of the grid; on each corner diagonal the 3 triplets of
consecutive points (0-9-4, 9-4-11, 4-11-8 and 2-10-4, 10-4-12, 4-12-6); and the 4 diamond edges
(1-9-3, 1-10-5, 5-11-7, 7-12-3). Three pieces that are not consecutive (9 and 11 without 4) do not count.

**Rules.** 3 pieces each; placement on any empty point except the centre (the lock is kept as is,
`CENTRE_FORBIDDEN_DURING_PLACEMENT`: 12 placeable points); a move is one step along a line to an adjacent
empty point; three in a row wins during placement or movement; a blocked player loses (existing rule).

**Solver result** (retrograde analysis, committed as `PicariaSolverResultTest`). Outcome and depth are for the
opening position, seen from White (the seat to move).

| | Tapatan | Achi | **Picaria 13, centre locked (the rule)** | Picaria 13, no lock | Picaria 9, locked (superseded) | Picaria 9, open (superseded) |
|-|---------|------|------|------|------|------|
| Points / pieces each | 9 / 3 | 9 / 4 | 13 / 3 | 13 / 3 | 9 / 3 | 9 / 3 |
| Perfect play | White wins | White wins | **White wins** | White wins | White wins | draw |
| Depth (plies) | 9 | 11 | **7** | 5 | 7 | n/a (draw) |
| Reachable positions (seat to move normalised) | 3,602 | 4,512 | **41,983** | 48,044 | 2,545 | not recorded |
| Blocked position reachable | no (0) | yes (12) | **no (0)** | no (0) | no | no |
| First placements that keep the win | centre | centre | **1, 3, 5, 7 (side middles)** | not computed | not recorded | n/a |

Findings:
- With the lock Picaria 13 is still a forced White win in 7 plies, like the old 9-point board: White opens on
  a side middle and the first move into the centre completes a line. Without the lock White wins faster, in
  5 plies (a double threat after three placements). Unlike the old 9-point board without lock (a draw), the
  13-point board is a White win either way: the crossings and the 16 lines give White more threats than
  Black can parry.
- Of the 12 placeable first points only the four side middles keep the win; a corner or a crossing opening
  does not (so the perfect bot with a fixed generator opens on point 1).
- A blocked position is not reachable by any legal play (BFS over every placement and move, locked or not):
  with 6 pieces on 13 points no move blocks the opponent without also making a line, so "blocked loses" never
  fires on this board; it stays as the common rule.
- Cost: the solver takes about 3.5 s to solve Picaria 13 from scratch (42k positions) against under 1 s for
  Tapatan and Achi. It runs once per JVM (cached per `MorrisRules`) on the first Picaria bot decision or draw
  answer. `eachVariantIsSolvedFromScratchInReasonableTime` now allows 15 s. If this is felt in play, warm the
  solver at start-up or at game creation; not done here.

**Frontend (pending: a design canvas is made separately).** The server sends `board` with 13 entries,
`legalPlacements` / `legalMoves` / `winningLine` with indices 0..12, and `Placed` / `Moved` events with points
up to 12. The client must (a) draw 13 points (the 9 grid points where they are today, plus crossings 9..12 at
the middle of the segment from each corner to the centre), (b) draw the segments: grid rows and columns, the
two corner diagonals through the crossings, the 4 diamond edges, (c) take its arrow-key navigation from the
new coordinates, and (d) update the Picaria Cypress specs (`morris-variants.cy.ts`, `morris-bot.cy.ts`) and
the rules text ("four short diagonals", "more diagonals"). Until then `geometries.ts` still draws 9 points, so
crossings cannot be seen or clicked.

## Product questions (owner) with a recommendation

- **Q1 Variant names in the picker.** Recommend "Tapatan (3 pieces)", "Achi (4 pieces)", "Picaria (3 pieces,
  13-point board)", with one sentence each in the rules panel; the game card keeps "Three Men's Morris".
- **Q2 Level names.** Recommend "Easy" (random), "Medium" (greedy, default), "Expert" (perfect), with a
  rules-panel note that White wins every variant with perfect play (Picaria with the centre locked: in 7 plies, also on the 13-point board).
- **Q3 Achi without diagonals.** Recommend not now (a fourth option for a rarely played variant).
- **Q4 When can a draw be offered?** Recommend from the movement phase only (placement lasts at most 8 plies
  and offering before any move is meaningless), once between two of one's own moves.
- **Q5 Bot delay.** Recommend 700 to 1300 ms plus the last-move mark; the same for every level (the expert
  does not "think longer").
- **Q6 Does the bot accept a draw?** Recommend: accept when its position is a draw or a loss by the solver,
  decline when it wins; never offer one itself.
- **Q7 Default colour.** Recommend `white` (v1 behaviour, existing e2e unchanged); `random` is one click away.
- **Q8 Colours on rematch.** Recommend no automatic alternation in v2; if wanted later, a fourth value
  `alternate` stored by the game, not by the session core.
- **Q9 Repetition threshold.** Recommend three occurrences of the same position with the same player to move,
  movement phase only (the placement phase cannot repeat), with a warning at two.
- **Q10 Show the colour on lobby seats?** Recommend no: colours exist only after the start (and `random` has
  none before); the option line says who plays what when it is fixed.
- **Q11 Picaria with the centre locked is a forced White win (M5, still true on the 13-point board).** Recommend asking the owner whether the lock
  (#194) should stay: with it, Picaria is the shortest and most lopsided variant (the first move into the centre
  wins); the old rule (centre open) gives a draw. Domain data only: a different `PlacementRule` would change it.

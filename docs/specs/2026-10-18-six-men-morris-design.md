# Six Men's Morris: design

Owner decision of 2026-10-18: the Picaria variant of Three Men's Morris is **removed** (White wins it with
perfect play, on the 13-point board too: solver result in `docs/specs/2026-10-14-three-men-morris-v2-design.md`,
section "Picaria on the 13-point board") and **replaced by Six Men's Morris**. Nine Men's Morris stays out of
scope. Plan: `docs/plans/2026-10-18-six-men-morris-plan.md`. Written by Claude Opus 5.5.

Legend: **[V]** verified by reading the code on `origin/main` at `08c86a7b` (2026-10-18), with `file:line`
(backend paths relative to `backend/src/main/java/org/kevinkib/cardgames/threemenmorris/`, frontend paths
relative to `frontend/src/`); **[S]** stated by a cited source; **[E]** estimated here (arithmetic in the
section "State space"); **[H]** hypothesis that the PR relying on it must confirm; **[Q]** product question,
listed at the end with a recommendation.

## Key decisions

1. **Six Men's Morris is a fourth variant of the same engine and lobby, in Picaria's place.** Option key
   `six` of the existing `variant` option; same game type (`three-men-morris`), package, factory, route and
   screen. Picaria is removed everywhere (backend, frontend, locales, Cypress, docs); the key `picaria` is
   **refused** as an unknown value by the existing strict validation, no alias. Rooms live in memory only, so
   no saved room exists to migrate.
2. **One rule set, the classic one (R. C. Bell, via Wikipedia):** two concentric squares joined at the
   middles of their sides (16 points, no diagonals), 6 pieces each, placement then movement, a **mill** (three
   in a row along a side) removes one opponent piece, never one inside a mill unless all of them are, one
   removal per move even for a double mill, **no flying**, a player reduced to 2 pieces or unable to move
   loses; draws by agreement, threefold repetition, and 50 movement plies without a removal [Q].
3. **The engine grows by data plus one sub-step.** `MorrisRules` gains a `LineRule` (`WIN` for the 3x3
   variants, `MILL` for Six), a `FlyingRule`, a `RemovalRule` and a quiet-move limit; `Geometry.SIX_MEN` is a
   new constant. A mill puts the game in a **pending removal** (the turn does not pass, the only action is
   `REMOVE`, the DTO lists `legalRemovals`). It is a sub-state of `PLACEMENT` or `MOVEMENT`, not a new
   `Phase` value, so the `phase` field and every existing client keep their meaning. Everything on the wire
   is additive.
4. **Bots stay modest: `random`, `greedy`, and for Six an `Expert` that searches three plies.** The seat option
   keeps its three keys; on Six the `perfect` key plays a depth-limited alpha-beta (three plies, node cap),
   because the owner does not want a very strong bot and because no solved table fits in the app: about **50
   million** positions (seat to move normalised) [E], **42,372,745** reachable [S, self-published]. The game
   is solved **offline once**, by an opt-in test, to report its perfect-play value (a published result says
   draw [S]); nothing of that table ships.
5. **The UI is designed on a Claude Design canvas first** (2 or 3 options on real positions, owner's choice)
   before the frontend chunk; this spec only fixes what the screen must show and the contract it reads.

## What exists today [V]

- **Engine.** `domain/ThreeMenMorris.java` runs any `MorrisRules` (`:51-63`). `place` (`:115-141`) and `move`
  (`:143-170`) check turn, phase and point, then `finishIfLine` (`:428-436`: any line of the mover wins),
  then pass the turn and look for a blocked opponent (`finishIfBlocked`, `:439-445`) and a threefold
  repetition (`:165-167`). `availableActions` holds `PLACE` or `MOVE` for the seat to move (`:350-355`);
  draw actions are a separate list (`drawActions`, `:339-348`). `legalPlacements` exists (`:296-307`).
- **Rules as data.** `domain/MorrisRules.java`: `record MorrisRules(Geometry geometry, int piecesPerPlayer,
  PlacementRule placement)` with `TAPATAN`, `ACHI`, `PICARIA` (`:20-26`); `PlacementRule.CENTRE_FORBIDDEN_DURING_PLACEMENT`
  exists only for Picaria (`:10-15`, used in `ThreeMenMorris.isLocked` `:405-408` and
  `bot/MorrisTransitions.isLocked` `:86-89`). `domain/Geometry.java`: `record Geometry(points, adjacency,
  lines)` with `TAPATAN` and `PICARIA` (`:19-36`); the constructor checks symmetric adjacency (`:38-66`).
  `domain/MorrisVariant.java` maps `tapatan|achi|picaria` to the rules (`:8-10`); `fromKey` throws on an
  unknown key (`:30-35`).
- **Factory and lobby.** `domain/ThreeMenMorrisFactory.java` declares `variant` from `MorrisVariant.values()`
  (`:82-90`), `hostColour`, and the seat option `level` (`:94-99`). `game/GameFactory.validate`
  (`game/GameFactory.java`, strict: an unknown value throws `InvalidOptionException`) completes defaults.
  Seat options cannot depend on game options: `seatOptionDescriptors()` takes no argument.
- **Position, transitions, solver, bots.** `domain/MorrisPosition.java` (`rules, board, inHand, toMove`; the
  repetition key is board plus seat to move). `bot/MorrisTransitions.java` restates the rules as a pure
  function (a line or a blocked opponent is a `Win`, `:63-84`). `bot/MorrisSolver.java` is a retrograde
  analysis over a `HashMap<MorrisPosition, Solution>` built lazily per rules and cached forever
  (`CACHE`, `forRules`); Picaria 13 (42k positions) already takes about 3.5 s. `bot/AbstractMorrisBot.answerDrawOffer`
  asks the solver for **every** level. `bot/PerfectBot` reads the solver, `bot/GreedyBot` takes a winning
  decision or one after which the opponent cannot win, `bot/MorrisBotLevel` maps `random|greedy|perfect`.
- **Tests that iterate every variant.** `@EnumSource(MorrisVariant.class)` in `MorrisSolverCrossCheckTest`,
  `MorrisTransitionsTest`, `PerfectBotTest` (twice), `RandomBotTest`, and a loop over
  `MorrisVariant.values()` in `MorrisSolverTest` (test sources, `domain/bot/`). Adding `SIX` to the enum
  without care would make them solve Six with the `HashMap` solver: tens of gigabytes.
- **Presentation.** `presentation/dto/ThreeMenMorrisDto.java`: additive fields are `@JsonInclude(NON_NULL)`
  and left out for Tapatan, so the v1 wire is byte-identical (golden files under
  `backend/src/test/resources/golden/`). `presentation/dto/event/ThreeMenMorrisEventData.java` is
  `{seat, action, point, from, to}`. STOMP destinations `/app/three-men-morris/{start,place,move,offerDraw,acceptDraw,declineDraw}`.
  `presentation/bot/MorrisBotCoordinator.java` decides **under the game monitor** (`:98-122`, `:164`), unlike
  Mangola's coordinator, which thinks outside it (`docs/architecture/context-map.md`, "Mangola bots").
- **Frontend.** `model/threemenmorris/geometries.ts` draws each variant from coordinates in a 0-100 box,
  segments, `pieces`, Picaria's `crossings`, `closedWhilePlacing` and the `refined` look (plaque, lit paths,
  marked chips); `VARIANT_KEYS = ['tapatan','achi','picaria']`. `components/morris/MorrisLobbyOptions.vue`
  shows only the keys that are both in `VARIANT_KEYS` and in the server's descriptor values, so a key the
  bundle does not know is never shown. `model/threemenmorris/board.ts` derives what is clickable from
  `availableActions`, `legalPlacements` and `legalMoves`; `myTurn` is "current seat and some action".
  Picaria appears in `MorrisBoard.vue` (crossings, padlock), `MorrisPlayerTag.vue` (`marked`),
  `ThreeMenMorrisGameScreen.vue` (`lockedPoints`, `:47-48`), `MorrisMiniBoard.vue`, `locales/Messages.ts` and
  `locales/en.ts`, the Vitest files and three Cypress specs (`morris-picaria.cy.ts`, `morris-variants.cy.ts`,
  `morris-bot.cy.ts`).

## Rules of Six Men's Morris

### Sources

- Wikipedia, "Nine men's morris", section on Six men's morris [S]: played on the board of Nine Men's Morris
  without its outer square, six pieces each, **flying is not permitted**; popular in Italy, France and England
  in the Middle Ages, obsolete by 1600; the same board serves Five Men's Morris; Seven Men's Morris adds a
  cross in the centre; only Twelve Men's Morris adds diagonals. Cited source: R. C. Bell, *Board and Table
  Games from Many Civilizations*, vol. 1 (1979), pp. 90-92 **[H: book not read]**.
- S. Leucci and M. Piccirilli, "Morris Games are (computationally) hard", ICTCS 2026 (CEUR-WS vol. 4269,
  paper 61) [S]: the common Morris rules (mill = three consecutive own stones along a straight line, one
  opponent stone removed per mill, "stones not involved in any mill have to be removed first", a player loses
  when reduced to two stones or left with no legal move), and footnote 2: "In Five and Six Men's Morris,
  flying is not allowed."
- Popular rule pages (gathertogethergames.com, "How to play Six Men's Morris"; a genjam.org rules sheet) [S]
  agree on the board, six pieces, one removal per mill, mills protected when possible, and the loss below
  three pieces or when blocked; **one sheet allows flying at three pieces**, which is the main contested point.
- B. H. Liou, `github.com/brianhliou/game-solver`, release "Morris tablebases v1" and the `solved-games`
  record (2026-06-18) [S, **self-published, not refereed**]: "Six men's morris is a draw under perfect play,
  over all 42,372,745 reachable positions" (no flying); the flying variant (42,383,945 positions) is a draw
  too. The page does not state its removal, double-mill or draw rules **[H]**. The author notes that the
  academic work (Gasser 1996 for Nine Men's Morris, a draw; Gévay and Danner 2014 for Nine, Lasker and
  Morabaraba) does not cover Six.

### The board

Two concentric squares; the middle of each side of the outer square is joined to the middle of the same side
of the inner square. 16 points: the 4 corners and 4 middles of each square. **No diagonals** (no source found
for diagonals on the 16-point board; diagonals belong to Twelve Men's Morris [S]) [Q9].

**Numbering** (the back end only reads `Geometry`; the DTO `board` has 16 entries for Six):

```
 0 ----------- 1 ----------- 2
 |             |             |
 |     8 ----- 9 ----- 10    |
 |     |               |     |
 7 --- 15             11 --- 3
 |     |               |     |
 |     14 ---- 13 ---- 12    |
 |             |             |
 6 ----------- 5 ----------- 4
```

Outer square 0..7 clockwise from the top left (corners 0, 2, 4, 6, middles 1, 3, 5, 7), inner square 8..15 in
the same order (point `i + 8` is the inner twin of `i`).

- **Adjacency** (consecutive along a drawn line, no jump): around each square `i - (i+1 mod 8)` (0-1, 1-2,
  ..., 7-0 and 8-9, ..., 15-8), and the four joins 1-9, 3-11, 5-13, 7-15. A corner has 2 neighbours, a middle
  3. 20 edges.
- **Lines of three (mills): 8**, the sides of the squares: 0-1-2, 2-3-4, 4-5-6, 6-7-0 and 8-9-10, 10-11-12,
  12-13-14, 14-15-8. A join has only two points, so it is never a mill. Each corner lies on two lines, so one
  placement or move can close two mills at once.
- **Symmetry:** the 8 symmetries of the square times the swap of the two squares (a graph automorphism that
  keeps the lines): 16. Only the offline solver uses it.

### The rule set we pick

1. Two players, White starts (as every variant here; the sources do not say) [Q10]. The host colour option
   applies unchanged.
2. **Placement:** each player holds 6 pieces and places one per turn on any free point, White, Black, White...
   until both hands are empty (Black places last). No piece moves while a hand is not empty (unlike Lasker
   Morris).
3. **Movement:** a turn moves one own piece one step along a line to a free adjacent point. No jump, no pass.
4. **Mill:** a placement or a move that completes a line of three own pieces (a line through the destination
   point) is a mill. Moving a piece out of a mill and back in forms the mill again (standard rule [S]). The
   mover then removes **one** opponent piece, even when the move closed two mills at once [Q4]. While the
   opponent has a piece outside every mill, a piece inside a mill cannot be taken; when all of them are in
   mills, any can be taken [Q3]. A removed piece leaves the game. The turn passes only after the removal.
5. **No flying** [Q2]: a player with three pieces still moves one step.
6. **End:** a player whose pieces (on the board plus in hand) fall to **two** loses (`REDUCED`). A player who
   must move in the movement phase and has no legal move loses (`BLOCKED`, the existing reason). A player
   with pieces in hand can always place (at most 12 pieces on 16 points). Lines do **not** win in Six.
7. **Draws:** agreement (existing: from the movement phase, one pending offer); threefold repetition of the
   same position with the same seat to move in the movement phase (existing rule); and **50 movement plies in
   a row without a removal** (`DRAW_MOVE_LIMIT`), counted from the first movement or the last removal,
   with a warning from 40 [Q5]. Repetitions only count positions where the turn has passed (never a pending
   removal) and the count is cleared by a removal (a position with more pieces cannot come back).
8. **A mill with no opponent piece on the board** removes nothing and the turn passes. It cannot happen with
   the alternation of placements (the opponent has at least one piece on the board whenever a mill can
   close) [E]; it is kept as a guard so the engine never waits for an impossible removal.

### Contested points and why we choose so

| Point | Choice | Other rule found | Why |
|-------|--------|------------------|-----|
| Diagonals on the 16-point board | none | none found for Six (diagonals are Twelve Men's Morris) | Wikipedia/Bell |
| Flying at three pieces | **no** | one popular rule sheet allows it | Wikipedia/Bell and Leucci and Piccirilli say no; both are draws by Liou, so neither is lopsided |
| Removing a piece in a mill | only when all are in mills | "any piece" in some casual rules | the near-universal Morris rule [S] |
| Double mill | one removal | some house rules give two | most sources; keeps the sub-step single |
| Mills during placement | they remove | none | every source |
| Loss | 2 pieces or blocked | none | every source |
| Draw rules | agreement, threefold, 50 quiet plies | none for Six; tournament Mühle has move limits | weak bots and humans can shuffle forever on 16 points; threefold alone may never fire |

### Who wins with perfect play

A published (self-archived) retrograde analysis says **draw**, with and without flying [S]. No source claims a
first-player forced win. The analysis does not state every rule we pick (protection of mills, one removal per
double mill), so the plan runs our own **offline solve once** (chunk C5) and writes the result here. If it found
a forced White win under our rules, that would be reported to the owner as a question before the frontend
chunk merges (the rules are data: flipping a rule is a one-line change).

## State space [E]

Positions counted from the seat to move (colours relabelled, as `MorrisSolver.normalised` does):

- **Movement phase** (hands empty, 3 to 6 pieces each on 16 points): sum over `w, b` in 3..6 of
  `C(16, w) * C(16 - w, b)` = **19,207,188** boards.
- **Placement phase** (hand of the mover `h` = 1..6, the opponent's hand `h` or `h - 1`, pieces on the board
  `<= 6 - hand`, at least three pieces each counting the hand): **30,779,235** states.
- **Pending removals** add the boards where the mover has just closed a mill (a subset of the above; at most a
  few million more).
- Total **about 50 million** (about 3 to 4 million under the 16 symmetries). Liou counts **42,372,745
  reachable** [S], consistent with this upper bound.

**Tractability.** The existing solver keeps one `MorrisPosition` record (two lists) per state in a `HashMap`:
about 200 to 300 bytes each, 10 to 15 GB for Six: **not tractable in the app, not even lazily.** A dedicated
solver with a dense index (combinatorial rank of the white set, of the black set among the free points, the
hands and the pending flag), byte arrays, and layers ordered by pieces left (a removal only goes to a smaller
layer, so each layer is solved after the smaller ones; inside a movement layer, repeated sweeps until nothing
changes; the placement phase is acyclic) needs about 50 to 150 MB and minutes of CPU **[H: measured by C5]**.
That is fine **offline**, not in a request. A 2-bit table of the result would be about 12 MB (1 MB with the
symmetries) and could ship later; it is not wanted now (decision 4).

## Domain design

All in `threemenmorris/domain` (pure; ArchUnit unchanged; no new game package).

### Removal of Picaria (chunk C1)

- `MorrisRules.PICARIA`, `Geometry.PICARIA` and `picaria()`, `MorrisVariant.PICARIA`, `PlacementRule`
  (only Picaria used `CENTRE_FORBIDDEN_DURING_PLACEMENT`; with one value left the enum is dead: the
  `placement` component goes), `MorrisRules.CENTRE`, `PointLockedException`, `isLocked` in the aggregate and in
  `MorrisTransitions`. `legalPlacements` stays (Achi already uses it on the wire; Six will too).
- Tests: `PicariaTest`, `PicariaSolverResultTest` and the Picaria cases of the other tests go; the Picaria
  results stay recorded in the v2 spec. `MorrisSolverTest` keeps Tapatan (3,602) and Achi (4,512).
- **Migration note.** State is in memory: a deployment ends every room. After it, the descriptor of
  `variant` is `tapatan|achi`; a request with `variant=picaria` (an old tab of the previous bundle) is refused
  by `GameFactory.validate` with `InvalidOptionException("Invalid value picaria for option variant")`, which
  the lobby already answers to the sender as an error and leaves the option unchanged. No alias: mapping
  `picaria` to `six` would silently change the game, to `tapatan` would hide a stale client [Q7]. A browser
  holding the old bundle shows no Picaria card any more (the card list is the intersection with the
  descriptor's values) and reloads on the next deploy as usual.

### Geometry and rules (chunk C2)

- `Geometry.SIX_MEN` (16 points, adjacency and the 8 lines above). `lines` keeps its meaning, "lines of
  three"; what a line does is in the rules.
- `MorrisRules` becomes `record MorrisRules(Geometry geometry, int piecesPerPlayer, LineRule lineRule,
  RemovalRule removal, FlyingRule flying, int quietPlyLimit)`:
  - `LineRule { WIN, MILL }`: `WIN` is today's rule (a line ends the game); `MILL` means a line removes an
    opponent piece and the game is won by `REDUCED` or `BLOCKED`.
  - `RemovalRule { PROTECT_MILLS, ANY }`: Six uses `PROTECT_MILLS`; `ANY` exists for [Q3] only if the owner
    asks (else not added: one value is enough, the record stays open).
  - `FlyingRule { NEVER, AT_THREE }`: Six uses `NEVER`. `AT_THREE` (a player with three pieces on the board
    and none in hand moves to any free point) is added and tested because [Q2] may flip it; it is a few lines
    in the move generator.
  - `quietPlyLimit`: 0 means none (Tapatan, Achi), 50 for Six.
  - The constructor rejects `MILL` with fewer than 3 pieces, and a quiet limit below 0.
  - `TAPATAN` and `ACHI` become `(geometry, n, WIN, PROTECT_MILLS, NEVER, 0)`; `SIX_MEN = (SIX_MEN, 6, MILL,
    PROTECT_MILLS, NEVER, 50)`. A convenience constructor `MorrisRules(Geometry, int)` keeps the 3x3 tests short.
  - `boolean solvable()`: true for `WIN` rules (the in-app solver may run), false for `MILL`.
- `MorrisPosition` gains `boolean removing` (the seat to move must remove a piece; it moved just before). The
  4-argument constructor stays (`removing = false`). `Key` is unchanged (repetitions never count a removing
  position).
- `Action` gains `REMOVE`; `MorrisResult.Reason` gains `REDUCED` and `DRAW_MOVE_LIMIT` (`isDraw` includes the
  latter).
- `MorrisVariant.SIX("six", MorrisRules.SIX_MEN)` is added in C3 with the wire, not in C2 (see the plan:
  adding it to the enum makes the `@EnumSource(MorrisVariant.class)` tests run on Six).

### The aggregate (chunk C3)

- **New state:** `pendingRemoval` (the seat that must remove, or null), `lastMill` (the points of the mill just
  closed, empty otherwise), `quietPlies` (movement plies since the last removal or since movement began).
- **`place` and `move`** (same checks as today, plus `RemovalPendingException` when a removal is pending):
  after the board update, `WIN` rules keep `finishIfLine`. `MILL` rules look for the lines **through the
  destination point** made of the mover's pieces. If there is one (or two) and the opponent has a piece on the
  board: `pendingRemoval = seat`, `lastMill` = that line (the first in geometry order if two), the turn does
  not pass, the version bumps, the event says `mill`. Otherwise the turn passes as today (placement end,
  blocked check, repetition count, quiet count, quiet limit).
- **`remove(seat, point)`**: requires the turn and a pending removal (`NoRemovalPendingException`), a point of
  the board (`InvalidPointException`), an opponent piece there (`NotOpponentPieceException`), and the
  protection rule (`PieceInMillException` when the piece is in a mill and some opponent piece is not). It
  removes the piece, clears `pendingRemoval`, `lastMill`, the repetition counts and `quietPlies`, cancels a
  pending draw offer (as a move does), bumps the version, then: opponent below 3 (board plus hand) finishes
  with `REDUCED` (winner the remover); else the turn passes and the usual end-of-turn checks run (end of
  placement, blocked opponent, repetition count). New event `MorrisEvent.Removed(seat, point)`.
- **Queries:** `pendingRemoval()`, `legalRemovals(seat)` (the opponent pieces the seat may take now, empty
  otherwise), `lastMill()`, `quietPlies()`, `piecesLeft(seat)` (board plus hand), `lost(seat)`
  (`piecesPerPlayer - piecesLeft`). `availableActions(seat)` is `[REMOVE]` during a pending removal.
  `legalMoves` is empty during a pending removal. `position()` fills `removing`.
- **Flying** (`AT_THREE` only): `movesOf` lists every free point for a seat with three pieces on the board and
  an empty hand.
- **Draw offers:** unchanged (movement phase); a removal cancels a pending offer like a move does.
- `fromLayout` gets an overload taking the rules, so Six positions can be written in tests (16 characters).
- New exceptions (all `ThreeMenMorrisException`): `RemovalPendingException`, `NoRemovalPendingException`,
  `NotOpponentPieceException`, `PieceInMillException`.

### Transitions, solver guard, bots (chunks C2 and C4)

- `MorrisTransitions.legal(position)`: during `removing`, one `MorrisDecision.Remove(point)` per legal
  removal; otherwise placements or steps (flying when the rules say so). `apply`: a placement or step that
  closes a mill under `MILL` rules gives `Next(position with removing = true, same seat to move)`; a `Remove`
  gives `Win(mover)` when the opponent falls below 3, else `Next` with the other seat (or `Win` when it is
  blocked). `MorrisTransitions` stays without history: the repetition and quiet-ply draws are not modelled
  (as today for repetition).
- `MorrisDecision.Remove(int point)`.
- **Solver guard (C2):** `MorrisSolver.forRules` and the constructor refuse rules that are not `solvable()`
  (`IllegalArgumentException`). `PerfectBot` and `answerDrawOffer` stop calling the solver for such rules (C4).
- **Levels on Six** (keys unchanged, labels unchanged: Easy, Medium, Expert) [Q6]:
  - `random` (Easy): a uniformly random legal decision, removals included.
  - `greedy` (Medium): closes a mill if it can; else blocks a line where the opponent has two pieces and a
    free third point it can reach next ply; else a decision after which the opponent cannot close a mill;
    else random. Removal: the opponent piece that belongs to a "two plus free third" threat, else the one with
    the most free neighbours, else random. One reply ahead, as today's greedy.
  - `perfect` (Expert): on solvable rules, today's `PerfectBot`; on Six, `SearchBot`: negamax with alpha-beta,
    **fixed depth 3 plies** (a removal belongs to the ply that closed the mill: the side to move does not
    change, the sign does not flip), a node cap (200,000) so a decision stays under about 50 ms **[H: C4
    measures]**, moves ordered mills first. Evaluation for the side to move: 100 x piece difference (board
    plus hand) + 20 x open threats difference (two own pieces and a free third on a line) + 10 x mills held
    difference + 2 x mobility difference; a won position is worth 10,000 minus the plies, a lost one the
    negative. Ties through the generator.
  - **Why so weak:** the owner asked for no very strong bot. Three plies see "I close a mill, you close one
    back" and simple traps, not the long manoeuvres of the movement phase; a careful human should beat it.
    A perfect level is not offered: no table fits in the app (see "State space"), and a perfect Six bot
    would be a drawing wall, not fun. An opt-in benchmark (C4) records search vs greedy and greedy vs random
    over 100 games each, like `ExpertStrengthBenchmark` for Mangola; expected: Expert beats Medium most of
    the time, Medium beats Easy nearly always **[H]**.
  - **Draw offers on Six:** a bot accepts when it is not ahead in pieces left (its count is at most the
    opponent's), declines otherwise; bots never offer [Q11]. The 3x3 variants keep the solver rule.
- **Coordinator (C6):** a bot that closes a mill gets a new broadcast with `REMOVE` as its action and is
  scheduled again after the thinking delay: the human sees the mill, then the removal. The decision moves out
  of the game monitor, as in Mangola (snapshot under the monitor, think, re-take the monitor, re-check the
  version, act), so a search never holds the game lock. Same delays (700 to 1300 ms, 0 under `test`).

### The offline solve (chunk C5)

An opt-in test in test sources, `SixMenMorrisSolveRunTest`, enabled only with
`-Dmorris.solveSix=true` (`@EnabledIfSystemProperty`; run with `mvn -s ../deploy/docker/settings.xml test
-Dtest=SixMenMorrisSolveRunTest -Dmorris.solveSix=true`) **[H: Surefire passes the user property to the
forked JVM, as Mangola's `-Dmangola.bench=true` does]**. Its own compact solver (bitboards of 16 bits, dense
index, layers by pieces left, sweeps inside movement layers) reports: the value of the opening (win, loss,
draw for White), the number of reachable positions, whether a blocked position and a `REDUCED` ending are
reachable, the time and memory. It cross-checks its move generator against `MorrisTransitions` on random
positions (needs C4; until then against hand-written cases). The result is written into this spec (section
"Who wins with perfect play") by the PR that runs it. The default `mvn test` skips it (CI unchanged).

## Network contract (additive)

- **Lobby:** `variant` values become `tapatan|achi|six` (C1 removes `picaria`, C3 adds `six`); default stays
  `tapatan` [Q8]. Labels `options.threeMenMorris.variant.six`. `level` unchanged.
- **New STOMP destination:** `/app/three-men-morris/remove`, payload `ThreeMenMorrisRemovePayload {gameId,
  token, point}`; a refusal answers the sender alone with an `ErrorResponse` and its state (`SeatCommands`).
- **Events:** new type `REMOVE` (data `{seat, action: "REMOVE", point}`); a removal that ends the game is a
  `GAME_OVER` with `action = "REMOVE"`. `ThreeMenMorrisEventData` gains `mill` (the three points, on the
  `PLACE`/`MOVE` that closed it) as a `NON_NULL` component, so existing events are byte-identical.
- **`ThreeMenMorrisDto` additions** (all `NON_NULL`, written only for `MILL` rules, so Tapatan and Achi wires
  are unchanged): `removal` (`{seat, mill: [a, b, c]}` while a removal is pending, else absent),
  `legalRemovals` (viewer, during its pending removal), `players[].lost` (pieces removed so far),
  `quietPlies` (only from 40, for the warning), and `quietPlyLimit` (50, so the client does not hard-code it).
  `board` has 16 entries; `availableActions` may hold `REMOVE`; `outcome.reason` may be `REDUCED` or
  `DRAW_MOVE_LIMIT`; `winningLine` stays empty for Six (no line wins). `variant` is `"six"`.
- **Compatibility:** an old bundle never shows Six (unknown key filtered); a new bundle reading a 3x3 game
  sees none of the new fields.

## UI (the canvas decides the look)

A **Claude Design canvas with 2 or 3 options** (Artifact `quickstart`, intent `design`), drawn on real
positions of Six (an opening, a pending removal with protected pieces, a 3 vs 4 endgame, the end screen), at
375 px and 1280 px, light and dark, comes **before** the frontend chunk; the owner chooses; the frontend chunk
then adds a short "UI" addendum to this spec. What every option must show:

- **Board:** 16 points from `geometries.ts` (outer square at 8/50/92, inner at 29/50/71 in the 0-100 box,
  the 8 sides cut at the middles plus the 4 joins: 20 segments), 44 px targets at 375 px (the closest points
  are about 21 units apart, 67 px on a 320 px board), arrow navigation from the coordinates (the existing
  `neighbourByArrow`; a Vitest proves every point is reachable by arrows from every other).
- **Mill highlighting:** the mill just closed lit (the existing winning-line drawing through
  `segmentsOnLine`) while the removal is pending, then a short fade.
- **Removal step:** a prompt "Mill! Take one of Bob's pieces" for the remover; only `legalRemovals` are
  selectable and marked (protected pieces visibly not), Enter/click removes; for the other player "Bob closed a
  mill and is taking one of your pieces"; announced in the live region; reduced motion respected.
- **Piece counts:** for each player, pieces in hand (existing reserve discs), on the board, and lost
  (`players[].lost`); a warning at three pieces left ("one more loss and Bob loses").
- **Last move and captured marker:** the existing last-move ring, plus a mark on the point where a piece was
  just removed until the next state; the quiet-ply warning from 40 ("10 moves without a capture before a
  draw") next to the repetition hint.
- **End screen:** subtitles for `REDUCED` ("Bob has only two pieces left") and `DRAW_MOVE_LIMIT`.
- **Lobby:** a `six` card with a mini board of 16 points; one-line rule "6 pieces each on two squares: a mill
  takes a piece; two pieces left loses". Rules panel: a Six section.
- Picaria's crossings, padlock and `closedWhilePlacing` go in C1; the `refined` look and the marked chips stay
  as generic geometry flags until the canvas says whether Six uses them (the frontend chunk deletes them if not).

## What we do not do

No Nine (or Five, Seven, Twelve) Men's Morris; no flying unless [Q2] says so; no lobby option for the rule
details (one rule set); no perfect bot and no shipped table for Six; no change to the session core, to the
lobby components' logic, to the other games; no rename of the game type, route or package; no alias for
`picaria`.

## Risks

- **A solver run on Six by mistake** (enum-driven tests, the draw rule of every bot, `PerfectBot`): the
  `solvable()` guard throws loudly (C2), and C3 excludes `SIX` from the solver tests when it adds the enum
  value.
- **Shuffling games:** the quiet-ply limit and threefold repetition end them; a forfeit and the draw offer
  remain.
- **Frontend `myTurn`** treats any action as "my turn": `REMOVE` is on the seat to move, so it stays right;
  `selectable` must return `legalRemovals` during a removal (covered by Vitest).
- **Canvas delay** blocks only the frontend chunk; the backend chunks do not depend on it.
- **The offline solve may disagree with Liou** (other rule details): reported to the owner, rules are data.
- **Six visible before its screen exists:** the server offers `six` from C3, but the frontend shows only keys
  it knows (`VARIANT_KEYS`), so nobody can pick it before the frontend chunk; a hand-crafted request could,
  and would get a 16-point game drawn as Tapatan: accepted for the few days between C3 and F1.

## Product questions (owner) with a recommendation

- **Q1 Where Six lives.** A fourth variant in the Three Men's Morris picker (as decided: it replaces Picaria)
  or a separate game card? Recommend the variant, and rename the card's display title to "Morris" (locale
  only; the game type `three-men-morris` stays).
- **Q2 Flying at three pieces?** Recommend no (Bell, Wikipedia, Leucci and Piccirilli; a draw either way).
- **Q3 Taking a piece from a mill?** Recommend only when every opponent piece is in a mill.
- **Q4 Double mill?** Recommend one removal.
- **Q5 Quiet-move draw?** Recommend 50 movement plies (25 moves each) without a removal, warning from 40.
- **Q6 Bot levels on Six.** Recommend the same three keys and labels; Expert on Six is a three-ply search
  (strong for a beginner, beatable), not perfect; the rules panel says so.
- **Q7 The `picaria` key.** Recommend refusing it (unknown value), no alias.
- **Q8 Default variant.** Recommend Tapatan stays the default.
- **Q9 Diagonals.** Recommend none.
- **Q10 Who starts.** Recommend White, as in the other variants (the host colour option decides who is White).
- **Q11 Bot and draw offers on Six.** Recommend: accept when not ahead in pieces left, never offer.

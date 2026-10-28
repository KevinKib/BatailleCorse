# Draughts (international, 10x10): design

A new two-player board game: international draughts ("jeu de dames"), game id `draughts`, played by the
rules that the French draughts federation (FFJD) publishes. Plan: `docs/plans/2026-10-28-draughts-plan.md`.
Written by Claude Opus 5.5 on 2026-10-28, from `origin/main` at `bb340ddd`.

Legend: **[S]** stated by a cited source (article numbers are those of the FMJD rules, see "Sources");
**[A]** assumption made here where the sources are silent or ambiguous, listed again under "Assumptions";
**[V]** verified in the code at `bb340ddd`; **[E]** estimated here; **[H]** hypothesis the PR relying on it
must confirm; **[Q]** product question for the owner, listed at the end with a recommendation.

## Key decisions

1. **One rule set, the FFJD's: international draughts as the FMJD official rules state them**, which the
   FFJD links as its detailed rules. 10x10 board, 20 men each, men move forward and capture both ways, flying
   kings, compulsory capture of the **largest number of pieces** (a king counts as one), pieces removed only
   after the whole capture, a man that only passes over the far row stays a man. A game is won when the
   opponent has no piece or no move, or resigns; it is drawn by agreement, threefold repetition, 25 moves of
   kings only, and the two short-endgame counts (16 and 5 moves). No lobby option for the rules.
2. **A new bounded context `draughts`, built like Six Men's Morris and Mangola**, not a variant of either: its
   own aggregate, a pure move generator over a fixed geometry of the 50 dark squares numbered 1 to 50 the FMJD
   way, the shared seat machinery (`SeatCommands`, `SeatGameRest`, `SeatStateBroadcaster`,
   `SeatLifecycleBroadcaster`), resignation through the shared `/app/forfeit`. The server sends each seat its
   **legal moves** (from, to, captured squares, path); the client never computes a rule.
3. **Bots: three levels, `random` / `greedy` / `expert`, the strongest kept modest.** Expert is an
   alpha-beta search with iterative deepening under a time budget **and a depth cap of 6 plies** (forced
   captures extend the search a little), thinking outside the game lock like Mangola's. Perfect play is out of
   reach (about 10^20 positions [S]); a strong engine is not wanted. Bots answer draw offers by material and
   never offer one.
4. **No clock in v1.** The FMJD clock rules are for competitions; the app has no per-player clock today. A
   Fischer clock is described as a later, optional lobby option with proposed numbers, not built [Q3].
5. **The board, the pieces, the player plates and the lobby layout go through a Claude Design canvas first**
   (2 or 3 options on real positions, owner's choice) before the components chunk; this spec only fixes what
   the screen must show and the contract it reads. Every text is in `locales/Messages.ts` and `locales/en.ts`
   (and the French bundle if it has landed by then).

## Sources

- **FFJD, "Règles du Jeu de Dames International"**, `https://www.ffjd.fr/Web/index.php?page=reglesdujeu`
  (read 2026-10-28) [S]: the simplified rules, with sections Le matériel, La marche des pièces, La prise,
  Les irrégularités, La fin de partie égale, Le résultat. It says itself: "Les règles ci-dessous sont des règles
  simplifiées. Les règles détaillées se trouvent à la page des règlements."
- **FFJD, page "Règlements"** (`?page=reglements`) links **"Annexe 1 - Règlement officiel du jeu de dames
  international"** (`fichiers/reglements/1.annexe_1_regl off_nov2015.pdf`, FMJD, version of November 2015, in
  French) [S] and **"Annexe 3 - Règlement officiel en cours de compétition"** (FMJD, November 2017) [S]. The
  article numbers below (2.6, 4.13, 6.2...) are those of Annexe 1 unless "A3" is written.
- Where the simplified page and Annexe 1 disagree (only on the "two kings against one king" ending, see
  "Draws"), this spec follows **Annexe 1**, the text the FFJD calls the detailed rules [Q1].
- The state-space figure in decision 3 is general knowledge (international draughts is not solved; the 10x10
  game has on the order of 10^20 positions or more) [S, not re-checked here: it only supports "no table"].

## Rules we implement

### Board and set-up

- 10x10 board, play on the 50 dark squares (2.1, 2.2). Each player has a dark corner on their left (2.4).
- **Numbering** (2.6): the dark squares are numbered 1 to 50, left to right, row by row, from the top row as
  seen from White's side. Black's base row is 1-5, White's is 46-50; the corners of the long diagonal are 5 and
  46 (2.6.1 to 2.6.3).
- Black men on 1-20, White men on 31-50, 21-30 empty (2.8). **White moves first** (3.3).
- Coordinates (used by the code and the client, never on the wire): square `n` is on row `r = (n - 1) / 5`
  (0 at the top) and column `c = 2 * ((n - 1) % 5) + (r even ? 1 : 0)` (0 at the left). So 1 is (0,1), 5 is
  (0,9), 46 is (9,0), 50 is (9,8).

```
       col 0  1  2  3  4  5  6  7  8  9
  row 0     .  1  .  2  .  3  .  4  .  5      Black base row
  row 1     6  .  7  .  8  .  9  . 10  .
  row 2     . 11  . 12  . 13  . 14  . 15
  ...
  row 8     . 41  . 42  . 43  . 44  . 45
  row 9    46  . 47  . 48  . 49  . 50  .      White base row
```

### Moves

- **A man** moves one square diagonally **forward** to a free square (3.4): White towards row 0, Black towards
  row 9.
- **Promotion** (3.5): a man that **ends its move** on the opponent's base row (1-5 for White, 46-50 for
  Black) becomes a king. Crowning is automatic here (3.6, 3.7 concern a physical board).
- **A king** moves any number of free squares along a diagonal, forwards or backwards (3.9). The rule "a new
  king waits for the opponent to play once" (3.8) holds by construction: promotion ends the move.
- **Touch-move** (3.11, 3.12) and "j'adoube" (3.13) are physical-board rules: online, selecting a piece is not
  a commitment until a move is sent [A1].

### Captures

- **Capture is compulsory**, forwards and backwards, for men and kings (4.1). Capturing one's own piece is not
  allowed (4.1).
- **A man** captures an adjacent opponent piece diagonally when the square right behind it is free, landing
  there (4.2). **A king** captures an opponent piece on the same diagonal at any distance, when every square
  between is free and at least one square behind it is free, and lands on any free square behind it on that
  diagonal (4.3), before any further piece.
- **Multiple capture ("rafle")** (4.5, 4.6): after a jump, if the capturing piece can jump again it must, and
  so on; a king may turn onto another diagonal to continue. The whole sequence is one move (4.1).
- **No piece is jumped twice; a free square may be crossed several times** (4.8). Jumping one's own pieces is
  forbidden (4.7).
- **Captured pieces leave the board only after the whole sequence** (4.11). So, during a sequence, a captured
  piece still blocks (it cannot be jumped again and the capturing piece cannot land on or pass through its
  square) [S by 4.8 and 4.11; the consequence, the "Turkish strike", is spelled out here as A2].
- The square the capturing piece started from is **free** during the sequence (it left it), so the piece may
  cross it or land on it [A3].
- **Majority rule** (4.13): among all legal capture sequences, the player must play one that takes the
  **largest number of pieces**; a king counts as one piece and gives no priority (capturing kings is not
  preferred, capturing with a king is not required). With several sequences of that maximal count, the player
  chooses freely, with a man or a king (4.14).
- **A man that passes over the far row during a capture and does not end there stays a man** (4.15).
- 4.16 (removing a single captured man before jumping) is a physical-board convenience, irrelevant online.

### End of the game

- **Win** (7.2) when the opponent: resigns (7.2.1); **has no legal move on their turn** (7.2.2: all pieces
  blocked); **has no piece left** (7.2.3). 7.2.4 (refusing the rules) cannot happen online.
- **Draw** (7.3): by agreement (7.3.1); by the rules of article 6 (7.3.2):
  - **6.1 threefold repetition**: the same position (same pieces on the same squares, men and kings told
    apart) appears for the third time **with the same player to move**.
  - **6.2 kings only**: "during 25 successive moves only kings have been moved, without moving a man or
    capturing". We read **25 moves of each player, 50 plies** [A4]: the count of consecutive plies with no
    man move and no capture reaches 50.
  - **6.3 three-against-one endings**: when only 3 kings, 2 kings and 1 man, or 1 king and 2 men remain
    against **a lone king**, the game is a draw once each player has played **16 more moves** (32 plies).
  - **6.4 two-against-one and one-against-one endings** (Annexe 1): 2 kings, 1 king and 1 man, or 1 king,
    against a lone king: a draw once each player has played **5 more moves** (10 plies) [Q1: the simplified
    page declares 2 kings against 1 and 1 king against 1 drawn at once "pour autant qu'il n'y ait pas de phase
    de jeu en cours"].
  - Counting for 6.3 and 6.4 [A5]: the count starts at the first position where the material matches, a
    capture that changes the material starts the count of the new case (6.4 after 6.3), and a man move does
    not reset it (the text says "encore joué... au maximum", a hard cap). The 6.2 count keeps running in
    parallel; whichever limit is reached first ends the game. A repetition can end it earlier.
- A game ended by a disconnection or a Leave is a forfeit, as in every game (presence context).
- **Draw offer** (online form of 7.3.1, the competition procedure is A3 5.3): a player may offer a draw
  **on their turn or not**, at most one offer pending, not again before their own next move (the Morris rule
  [V]); a move by the offerer's opponent declines it (A3 5.3: "le fait de jouer équivaut à une réponse
  négative") [A6]. The FMJD's "at least 40 moves played before an offer" (A3 5.3) is a competition rule: not
  applied [Q4]. A lobby option can switch offers off (see "Lobby").
- Irregularities (section 5) cannot be played: the server refuses an illegal move, so there is nothing to
  rectify.

### Assumptions (flagged)

| # | Point | What we do | Why |
|---|-------|------------|-----|
| A1 | Touch-move, "j'adoube" | Not applied: a selection is free until a move is sent | physical-board rules (3.11-3.13) |
| A2 | Captured pieces during a sequence | Stay as obstacles until the end (Turkish strike) | consequence of 4.8 and 4.11, standard in international draughts |
| A3 | Start square during a capture | Free: may be crossed or landed on | the piece has left it; standard practice, not written |
| A4 | "25 coups" of 6.2 | 25 moves of each player (50 plies) | "coup" is a move of one piece (3.2) and the draw rules of 6.3 and 6.4 say "chacun"; the usual reading of 6.2; the stricter reading (25 plies) is a one-number change |
| A5 | Start and reset of the 6.3 / 6.4 counts | Start when the material first matches; a new material case starts its own count; man moves do not reset | the text gives a maximum, not a reset rule |
| A6 | Draw offer timing | Any time during play, one pending, not repeated before one's own next move; the opponent's move declines it | A3 5.3, minus the 40-move threshold and the clock steps |
| A7 | Position identity for 6.1 | Squares of white men, white kings, black men, black kings, plus the seat to move | 6.1 says "la même position... avec le même joueur ayant le trait" |
| A8 | Ambiguous captures | Two sequences with the same start, end and captured set are the same move (same result); same start and end with different captured sets are different moves | a move is identified by what it does; the path is only a picture |
| A9 | Bot material values | man 1, king 3 (Expert: 100 and 300) | common engine heuristic, not a rule; tuned by the benchmark |

## Who wins with perfect play

Unknown: international draughts is not solved (only 8x8 English checkers is, a draw, Schaeffer 2007) [S]. The
first-move advantage is small; the app does not need to know. Nothing is solved offline (unlike Six Men's
Morris): the state space rules it out.

## Domain design (`org.kevinkib.cardgames.draughts.domain`, pure)

Names from `docs/architecture/game-conventions.md`: id `draughts`, package `draughts`, Pascal `Draughts`,
camel `draughts`. Display name "Draughts" ([Q6]: "International draughts" on the picker card subtitle).

### Geometry

- `Square` (an `int` 1..50 in the API; validated) and `BoardGeometry` (static tables built once): row and
  column of each square, the **four diagonal rays** of each square (the squares met going north-west,
  north-east, south-west, south-east, in order, up to the edge), the neighbour in each direction, and the
  promotion row of each colour. Every other class reads the rays; nothing re-derives coordinates.
- Tests pin the numbering of 2.6 (corners 5 and 46, bands 6-16-26-36-46 and 5-15-25-35-45, base rows) and the
  17 diagonals of 2.3 (the rays form exactly 17 maximal diagonals, the long one 46-41-37-32-28-23-19-14-10-5).

### Position and moves

- `Piece { WHITE_MAN, WHITE_KING, BLACK_MAN, BLACK_KING }`, `Colour { WHITE, BLACK }`.
- `DraughtsPosition` (immutable record): the 50 squares (four `long` bit sets or a `Piece[]`, an implementation
  detail hidden behind `pieceAt(square)`), the colour to move. Its `key()` is the repetition key (A7). Factory
  methods: `initial()`, and `fromLayout(String)` (50 characters `w W b B .`, plus the colour to move), used by
  tests and by the test-only seam.
- `DraughtsMove` (record): `from`, `to`, `path` (the landing squares after `from`, ending with `to`; one entry
  for a plain move), `captures` (the captured squares, in capture order), `promotes` (a man ending on its
  promotion row). `isCapture()` is `!captures.isEmpty()`. Notation `from-to` or `fromxto` (8.2).
- `MoveGenerator` (pure, stateless): `legalMoves(position)` returns every legal move for the colour to move:
  1. find every capture sequence by depth-first search from each own piece (men: the four short jumps; kings:
     along each ray, skip free squares, one opponent piece not yet captured, then each free landing square
     behind it before the next piece), tracking the captured set (never jumped twice, still blocking, A2) and
     treating the start square as free (A3);
  2. keep the sequences with the maximal number of captures (4.13); if none exists, every plain move (men
     forward one square, kings any distance);
  3. de-duplicate by `(from, to, captured set)` (A8), keeping one representative path;
  4. `promotes` only for a man whose sequence **ends** on its promotion row (4.15).
  Order of the result: by `from`, then `to`, then captured squares, so bots and tests are deterministic.
- `apply(position, move)` returns the next position (pieces removed after the move, promotion, colour to
  move swapped). `MoveGenerator` and `apply` are what the bots and the aggregate share; there is no second
  copy of the rules (Morris needed `MorrisTransitions` because its aggregate predated it; here the aggregate
  calls the generator).
- **Cost** [E]: a position has at most a few dozen plain moves; a king capture tree can branch (each landing
  square), but the captured set bounds the depth to the number of opponent pieces (at most 20). Tests include
  the classic worst cases (a king with many landing choices and a long rafle) and assert a time bound (under
  1 ms per call on the CI runner [H]).

### The aggregate `Draughts` (implements `game.Game`)

- **State**: `id`, `whiteSeat` (seat 0 or 1), `position`, `phase` (`PLAY`, `FINISHED`), `version`, `result`
  (`DraughtsResult(winner, reason)`), the repetition counts (`Map<Key, Integer>`), `kingOnlyPlies` (6.2),
  `endgameCase` and `endgamePlies` (6.3 / 6.4), the draw offer (`drawOffer` seat or null, and
  `offeredSinceOwnMove[2]`), `lastMove`, `history` (the notation of every move, for the move list).
- **Commands** (all `synchronized`, version bumped on every accepted command, like Morris):
  - `move(seat, from, to, captures)`: checks the game is running, the seat is to move, then finds the legal
    move with that `from`, `to` and captured **set** (order free). None: `IllegalMoveException`. If
    `captures` is empty and several legal moves share `from` and `to` (different captured sets),
    `AmbiguousMoveException` (the client always sends the set; this guards hand-made commands). Applies it,
    clears a pending draw offer made by the opponent (a move declines it, A6), records the event, then runs
    the end checks in this order: opponent without pieces (`NO_PIECES`), opponent without a legal move
    (`BLOCKED`), threefold repetition (`DRAW_REPETITION`), 6.3/6.4 count (`DRAW_ENDGAME`), 6.2 count
    (`DRAW_KINGS_ONLY`).
  - `offerDraw(seat)`, `acceptDraw(seat)`, `declineDraw(seat)`: the Morris contract [V] (`DrawOfferNotAllowed`
    when already made since one's own move, `DrawOfferPending`, `NoDrawOffer`), with
    `DRAW_AGREED`.
  - `forfeit(loser)`: `FORFEIT` (the shared presence context calls it on resignation, disconnection, Leave).
- **Queries**: `position()`, `currentSeat()`, `colourOf(seat)`, `legalMoves(seat)` (empty when it is not that
  seat's turn), `captureRequired(seat)`, `availableActions(seat)` (`MOVE` on its turn; `OFFER_DRAW`,
  `ACCEPT_DRAW`, `DECLINE_DRAW` as allowed), `counts(seat)` (men and kings), `drawCounters()` (the three
  counts and their limits), `repetitions()`, `lastMove()`, `history()`, `result()`.
- **Exceptions** (all `DraughtsException`): `NotPlayersTurnException`, `FinishedGameException`,
  `InvalidSquareException`, `IllegalMoveException`, `AmbiguousMoveException`, the three draw-offer exceptions.
- **Events** (`DraughtsEvent`, sealed): `Moved(seat, move)`, `DrawOffered(seat)`, `DrawDeclined(seat)`,
  `DrawAccepted(seat)`, `Finished(result)` folded into the presentation's `GAME_OVER` as in Morris.
- `fromPosition(id, whiteSeat, position, options)` builds a game from a layout (tests, the test seam).

### Factory `DraughtsFactory`

- `GAME_TYPE = "draughts"`, 2 players exactly, `supportsBots() = true`.
- Lobby options (strict, `GameFactory.validate`):
  - `hostColour` = `white|black|random`, default **`random`** (the Morris default since 2026-10-18 [V]),
    label `options.draughts.hostColour`; the generator is drawn once per game (constant under `test`).
  - No draw-offers option (decided 2026-10-28): offers are always allowed. A legacy `drawOffers` key sent by a
    client is dropped by `DraughtsFactory.validate` instead of being refused [Q4].
- Seat option `level` = `random|greedy|expert`, default `greedy`, label `options.draughts.level` (the Mangola
  keys [V]; displayed Easy, Medium, Expert).

## Bots (`draughts.domain.bot`, pure; `draughts.presentation.bot`)

- **Port** `DraughtsBot.decide(position, seat, drawOfferPending)` returns a `DraughtsDecision` (`Play(move)`,
  `AcceptDraw`, `DeclineDraw`). Candidates always come from `MoveGenerator.legalMoves`, so a bot cannot play
  an illegal move. Ties go through an injected `RandomGenerator` (constant under the `test` profile).
- **`random` (Easy)**: a uniformly random legal move.
- **`greedy` (Medium)**: two plies by material: for each move, the opponent's best reply (the largest capture
  it then must or can make); keep the moves with the best material balance after that reply; among them prefer
  a promotion, then advancing men that stay defended; else random. Material: man 1, king 3 [A9, a common
  heuristic].
- **`expert` (Expert)**: negamax with alpha-beta, iterative deepening, best move of the previous depth first,
  captures first. **Time budget 300 ms and depth cap 6 plies** (the first depth always completes, so it always
  answers). A position where the side to move must capture is not a leaf (the search goes on, at most 4 extra
  plies) so it does not stop in the middle of an exchange. Evaluation for the side to move [A9]: material
  (man 100, king 300), advancement of men (rows gained, small), centre squares, back-row men kept; a won position is worth 100,000 minus the plies. Node cap 2 million as a safety net.
- **Why so modest** [Q2]: the owner does not want very strong bots for board games. Six plies see simple
  shots (a sacrifice followed by a double capture) and avoid hanging pieces; they miss the long combinations and
  the endgame technique of a club player. An opt-in benchmark (`DraughtsStrengthBenchmark`,
  `-Ddraughts.bench=true`, 100 games per pairing, colours alternating, draw at 200 plies) records Expert
  against Medium and Medium against Easy in this spec; expected Expert beats Medium most of the time and Medium
  beats Easy nearly always [H]. If Expert turns out stronger than "beatable by a careful club player", the depth
  cap goes down (a one-number change).
  - **Measured (B3, `DraughtsStrengthBenchmark`, `-Ddraughts.bench=true -Dtest=DraughtsStrengthBenchmark`, 100
    games per pairing, colours alternating, draw after 200 plies, real Expert budget, JDK 22 on the developer
    machine):** Expert against Medium: 100 wins, 0 losses, 0 draws; Medium against Easy: 100 wins, 0 losses, 0
    draws. Expert decision time: mean 33 ms, worst 300 ms (the time budget; the node cap of 2 million was never
    the limit). Medium decided in under 1 ms and Easy instantly. The results are one-sided because Medium only
    looks two plies ahead by material, so a clean sweep says Expert is clearly stronger than Medium, not that it
    is strong: six unrefined plies of search with a plain material evaluation stay far from a club player. The
    depth cap stays at 6 and the budget at 300 ms; if the owner finds Expert too hard in play, the cap goes
    down (a one-number change in `ExpertDraughtsBot.Budget.DEFAULT`).
  - **Implementation notes (B3):** a position with a capture to make is extended at the horizon by up to 4
    plies (so the shot "sacrifice, forced capture, double capture" is seen even at depth 1); the first depth
    ignores the time and node budgets, so a decision always exists; the root of each iteration uses a window of
    one point below the best score so far, which keeps every move equal to the best for the random tie-break.
- **Draw offers**: a bot never accepts a draw immediately [owner decision]. Before ply 20 (`DraughtsDrawContext.MIN_PLIES_TO_ACCEPT_DRAW`,
  plies = moves played by both sides) it declines every offer; from ply 20 it accepts when its material (man 1, king 3)
  is not ahead of the opponent's, declines otherwise. While a draw counter is running (a position seen twice, the
  kings-only count shown from 30 plies, a 6.3 / 6.4 short ending) it answers by material even before ply 20. The
  coordinator passes the situation through `DraughtsBot.decide(position, seat, offerPending, drawContext)` (the
  three-argument form stays, with no early restriction). Bots never offer (the Six rule [V]). With offers off,
  nothing to answer.
- **Coordinator** `DraughtsBotCoordinator` (a `GameStateListener` and a `GameEvictionListener`, over the shared
  `BotScheduler` and `ThinkingDelay` [V]): after each broadcast, one task per bot seat that must move or
  answer an offer, after the thinking delay (700 to 1300 ms, 0 under `test`). **The decision is computed outside
  the game monitor** (the Mangola and Morris pattern [V]): snapshot under the monitor, think, re-take the monitor,
  re-check the version, act through `DraughtsGameActions`; a stale decision is dropped; a failing strategy falls
  back to the first legal move. Properties `draughts.bots.delay-min-ms`, `delay-max-ms`, `think-ms` (300), `depth`
  (6), all 0 or minimal under `test` (the Expert then completes depth 1 only).

## Network contract

- **REST** `/api/draughts/game/{id}`, `/join`, `/play-again` (`SeatGameRest`).
- **STOMP** `/app/draughts/start` (game-type check first), `/app/draughts/move`
  (`DraughtsMovePayload {gameId, token, from, to, captures}`), `/app/draughts/offerDraw`, `/acceptDraw`,
  `/declineDraw` (`{gameId, token}`). Resignation is the shared `/app/forfeit` (`ForfeitReason.RESIGNED` [V]),
  shown by the existing forfeit UI. A refusal answers the sender alone (`SeatCommands`) with an error **code**
  (`draughts.errors.illegalMove`, `ambiguousMove`, `notYourTurn`...), never a server message shown to the player
  (French translation spec, D3).
- **Test-only seam** (profile `test` only): `PUT /api/test/draughts/{gameId}/position` with
  `{layout: "<50 chars>", toMove: "white|black"}` replaces the position of a started game (the Kobo deal seam
  pattern [V]); an IT proves it is absent outside `test`. It lets the end-to-end spec play a short ending
  instead of a 50-move game.
- **`DraughtsDto`** (per seat, `forViewer`; nothing is hidden in draughts, the projection only scopes the
  viewer's actions and moves):
  - `started`, `id`, `gameType`, `version`, `phase` (`PLAY`, `FINISHED`), `currentSeat`, `whiteSeat`;
  - `board`: 50 entries, index `square - 1`, each `"w" | "W" | "b" | "B" | null` (man, king; colour);
  - `players[]`: `seat`, `name` (null for a bot or a nameless human, per the translation spec), `colour`,
    `current`, `men`, `kings`, `captured` (opponent pieces taken so far), `bot`;
  - `availableActions` (viewer): `MOVE`, `OFFER_DRAW`, `ACCEPT_DRAW`, `DECLINE_DRAW`;
  - `legalMoves` (viewer, on its turn): `[{from, to, path, captures, promotes}]`;
  - `captureRequired` (viewer, on its turn) and `maxCaptures` (the count of the majority rule), for the hint;
  - `lastMove`: `{seat, from, to, path, captures, promotes}` or absent;
  - `drawOffer`: `{by: seat}` or absent;
  - `drawCounters`: `{kingOnlyPlies, kingOnlyLimit (50), endgame: {case: "SIXTEEN" | "FIVE", plies, limit}}`,
    each part present only once it matters (king-only plies from 30, the endgame case as soon as it applies), so
    the client never hard-codes a limit;
  - `repetitions` (from 2);
  - `history`: the notation of every move (`"32-28"`, `"19x30"`), oldest first [Q5];
  - `outcome`: `ONGOING` or `FINISHED{winner, reason}` with `reason` in `NO_PIECES`, `BLOCKED`, `FORFEIT`,
    `DRAW_AGREED`, `DRAW_REPETITION`, `DRAW_KINGS_ONLY`, `DRAW_ENDGAME`.
- **Events** (`DraughtsEventType`): `START`, `MOVE` (data `{seat, from, to, path, captures, promotes}`),
  `DRAW_OFFERED`, `DRAW_DECLINED`, `GAME_OVER` (the command that ended the game, with the same data when it was
  a move).
- **Size** [E]: about 1.5 KB at the start (White has 9 legal moves on its first turn), up to a few KB
  with a long `history` (100 moves of 6 characters is 0.7 KB): fine for a per-move broadcast.

## Board interaction model (frontend; the canvas decides the look)

- **Orientation**: each player sees their own men at the bottom (Black's view is the board turned by 180
  degrees); square numbers are optional, a canvas choice (small, in the corner of each dark square, off by
  default) [Q7].
- **Selecting**: on my turn, only my pieces that have a legal move are buttons. When a capture is compulsory,
  only the pieces that can make a maximal capture are, and a hint says so ("You must capture: 2 pieces").
  Selecting a piece highlights the `to` squares of its legal moves; selecting another of my pieces switches;
  Escape or a tap on the selected piece clears it.
- **Playing**: a tap on a highlighted destination sends the move when exactly one legal move of that piece
  ends there. When several do (same start and end, different captured sets, A8), the board shows the
  candidates: the pieces each one would take are outlined, and a tap on a piece that belongs to only one of
  them picks it (a small chooser list "takes 23, 28, 33" / "takes 24, 29, 33" if no single piece separates
  them) [H: rare; a Vitest covers it with a hand-made position].
- **Multi-jump preview**: while a destination of a capture is hovered or focused, its path is drawn (the
  landing squares joined) and the pieces it takes are marked.
- **Last move**: from and to marked, the path drawn for a capture, captured squares shown briefly; a move is
  animated along `path` (a piece glides square to square, the captured pieces then disappear in capture order,
  4.11), reduced motion respected (no glide, an instant change).
- **Keyboard**: the dark squares are a 10x5 grid for focus; arrows move the focus diagonally on the visible
  board, Enter or Space selects or plays, Escape clears; announcements in a live region ("Bob played 19x30,
  taking 2 pieces").
- **Tap targets**: a 10-column board at 375 px gives squares of about 34 px (343 px board), under the 44 px the
  project used for Morris. The squares touch each other, so the target is the whole square; WCAG 2.5.8
  (AA, 24 px) holds, 2.5.5 (AAA, 44 px) does not. The canvas must show the 375 px board and may propose a
  mitigation (full-bleed board, larger at 390-430 px phones) [Q8].
- **Player plates**: name, colour, men and kings left, pieces captured, turn indicator, bot level; the draw
  offer controls (offer, accept, decline) and the Resign button (shared confirm dialog); warnings for the draw
  counts ("10 moves left before a draw: kings only" and the 6.3 / 6.4 counts); the repetition hint (from 2).
- **End screen**: the shared `EndGameOverlay` with subtitles per reason (no pieces left, blocked, resigned or
  left, agreed draw, threefold repetition, 25 moves of kings, short ending).
- **Lobby**: the generic options mechanism (`hostColour`) and the seat `level`; a mini board on
  the lobby card is a canvas choice.
- **Move list** (if [Q5] says yes): a collapsible list of the notation, newest last.

### What the canvas must cover (D in the plan)

2 or 3 options on real positions, at 375 px and 1280 px (the app is dark-only [V, Six addendum]): the opening
position (White to move), a compulsory capture with the hint and two capturing pieces, a king's multi-jump with
its path preview and an ambiguous destination, a kings-only ending with the draw counter, the end screen. Board
look (wood, flat, felt), piece and king look (the crown mark must not depend on colour alone), plates layout,
the move list, and the lobby. The owner chooses; the components chunk adds a "UI addendum" to this spec.

### UI addendum (F2)

The owner chose option A, "Felt" (canvas: https://claude.ai/artifact/4oJCE9drXcPU5xzmhUHFiC), dark theme only.

- Look: green felt board with a darker playing square, ivory and ebony pieces, a gold ring on selectable pieces, a crown
  mark on kings (the king is never told by colour alone). No square numbers on the board; the notation appears in the
  move list, the live region and the end summary.
- Plates: opponent above the board, me below (my name is "You"); each shows colour, men, kings and pieces taken; the
  side to move has a gold ring.
- Notes block of fixed height above the board (capture hint with the count, what to do, colour hint before the first
  move, draw counters, repetition warning); a draw offer for me replaces it while it lasts, so the board never resizes.
- Squares are about 34 px at 375 px wide (the whole square is the target, spec Q8); the board takes what the fixed rows
  leave, using container units.
- Ambiguous destination: a bottom sheet lists the routes ("Takes 37, 28"), and the pieces of each route are numbered on
  the board; either choice sends the captured set. Cancel is a 44 px icon button.
- Move list: a side panel (240 px) on wide screens, open by default; a sheet over the bottom on phones, closed by default.
- Lobby: the shared lobby with a small starting-position board and a one-line summary in the options slot (GameLobby has
  no header slot).
- Actions row: Offer a draw (hidden when the host switched offers off), Resign (same confirmation as leaving), Moves.

## Rules panel text (draft, `locales/en.ts` `draughts: RulesMessages`, kind `text` sections)

- **Goal**: "Take all your opponent's pieces, or leave them without a move on their turn."
- **The board**: "A 10 by 10 board; only the 50 dark squares are used. Each player starts with 20 men on the
  four rows nearest them. White moves first."
- **Moving**: "A man moves one square diagonally forward to a free square. A king moves any number of free
  squares along a diagonal, forwards or backwards."
- **Capturing**: "Capturing is compulsory. A man jumps over an adjacent opponent piece to the free square right
  behind it, forwards or backwards. A king captures from a distance along a diagonal and may land on any free
  square behind the piece. If the capturing piece can jump again, it must: the whole chain is one move."
- **The majority rule**: "You must make the capture that takes the most pieces. A king counts as one piece. If
  several captures take the same number, you choose. Captured pieces are removed at the end of the move, and no
  piece can be jumped twice."
- **Kings**: "A man that ends its move on the far row becomes a king. A man that only passes over the far row
  during a capture stays a man."
- **End of the game**: "You win when your opponent has no pieces left or cannot move. Draws: agreed draws, the
  same position three times with the same player to move, 25 moves each with only kings moving and no capture,
  and short endings with one king alone against at most three pieces (16 or 5 more moves each)."
- **Bots**: "Easy plays at random, Medium looks one reply ahead, Expert looks a few moves ahead. None plays
  perfectly."

## Clock (optional, not in v1) [Q3]

The FMJD plays with a Fischer clock (A3 6.1: 1 h 20 + 1 min per move for competitions). The app has no
per-player clock; a disconnection is handled by the presence context (grace delay, then forfeit). If wanted
later, a lobby option `clock` with values below, a `TIME` loss reason, the clock in the DTO (remaining
milliseconds per seat plus the server time of the last switch) and a server-side timeout task (like the
President exchange-view timeout [V]). Proposed values:

| Option value | Current | Proposed | Note |
|--------------|---------|----------|------|
| `off` | (no clock) | default | **changes nothing** |
| `blitz` | - | 5 min + 3 s per move | new |
| `rapid` | - | 15 min + 10 s per move | new |
| `classic` | - | 30 min + 30 s per move | new; A3 6.1 asks 30 s minimum per move for slow games |

## Numbers (current / proposed)

Nothing exists for draughts; "current" gives the comparable value of the closest game, for reference. Every row
is new (**all proposed**).

| Parameter | Current (closest game) | Proposed | Where |
|-----------|------------------------|----------|-------|
| Bot thinking delay | 700-1300 ms (Morris, Mangola) | 700-1300 ms | `draughts.bots.delay-*-ms` |
| Expert time budget per move | 300 ms (Mangola `think-ms`) | **300 ms** | `draughts.bots.think-ms` |
| Expert depth cap | none for Mangola; 3 plies on Six | **6 plies** (+ up to 4 plies of forced captures) | `draughts.bots.depth` |
| Expert node cap | 5,000,000 (Mangola), 200,000 (Six) | 2,000,000 | constant |
| Medium look-ahead | 1 reply (Morris greedy) | 1 reply (2 plies) | `GreedyDraughtsBot` |
| Material for bots | - | man 1, king 3 (draw offers, Medium); man 100, king 300 (Expert) | bot code |
| Kings-only draw (6.2) | 50 quiet plies (Six) | **50 plies** (25 moves each) | domain constant |
| Three-against-one count (6.3) | - | **32 plies** (16 moves each) | domain constant |
| Two-against-one count (6.4) | - | **10 plies** (5 moves each) | domain constant |
| Repetition draw | threefold (Morris, Mangola) | threefold | domain |
| Warning for the kings-only count | from 40 of 50 (Six) | from 30 of 50 (10 moves each left) | DTO threshold |
| Draw offers | on, one pending (Morris) | always allowed (no lobby option) | factory |
| Host colour default | `random` (Morris) | `random` | factory |

## What we do not do

No other draughts variant (no 8x8 English or Brazilian, no Frisian, no flying-king-off); no clock in v1; no
lobby option for rule details; no move takeback; no analysis board or engine evaluation shown; no perfect or
strong bot; no saved games or PDN export (the `history` notation could feed one later); no change to the
session core, to the other games, or to the shared lobby logic.

## Risks

- **Capture generation bugs** (the majority rule, kings turning, the Turkish strike): the domain chunk is test
  first with the classic positions of the FMJD text (the "three men not two kings" diagram of 4.13 on the FFJD
  page) and hand-made rafles; a property test checks invariants on random play (pieces never jumped twice,
  every move of maximal count, counts of pieces consistent).
- **Bot latency**: the search runs outside the game lock; the depth cap and node cap bound it; the benchmark
  records the worst decision time.
- **Small squares on phones** (Q8): the canvas decides; Cypress clicks by `data-test`, not by coordinates.
- **Parallel new games**: the entry PR touches the same shared files as any other game entering the app
  (`games.ts`, router, locales, `PerGameConfigTest` counts, `ArchitectureRules`, picker tests); one entry PR at a
  time (`docs/architecture/add-a-game.md`), rebased after the previous one.
- **French translation in flight** (`docs/specs/2026-10-28-french-translation-design.md`): if the French bundle
  and its parity test land before the entry PR, every new key also needs its French text in `locales/fr/`; the
  game name "Dames" and the terms (pion, dame, rafle, prise majoritaire) go in its glossary.

## Product questions (owner) with a recommendation

- **Q1 Two kings against one king.** Annexe 1 (6.4): draw after 5 more moves each; the simplified FFJD page:
  drawn at once unless a combination is in progress. Recommend **Annexe 1 (5 moves each)**: it is the detailed
  text the FFJD points to, it is mechanical (the "phase de jeu en cours" test is a judgement), and 5 moves let a
  player cash an immediate shot.
- **Q2 Bot strength.** Recommend three levels with Expert capped at 6 plies and 300 ms (modest, beatable by a
  careful player), as the owner asked for board games; confirm, or ask for weaker (4 plies).
- **Q3 Clock.** Recommend **no clock in v1**; the values above if wanted later, as an optional lobby option.
- **Q4 Draw offers.** Offers are always allowed (owner decision, no lobby switch), and there is no
  40-move threshold (competition rule).
- **Q5 Move list.** Recommend sending `history` (cheap) and letting the canvas decide whether and where to show
  it.
- **Q6 Name.** Recommend "Draughts" as the card title with "International draughts, 10x10" in its description
  (French: "Dames", "Jeu de dames international"); id `draughts` (permanent).
- **Q7 Square numbers on the board.** Recommend off by default, a canvas option to show them small.
- **Q8 Small squares at 375 px.** Recommend accepting about 34 px squares (whole-square targets, no gaps) and
  letting the canvas show it on a real phone width before deciding.

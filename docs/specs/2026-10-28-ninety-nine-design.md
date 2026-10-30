# Ninety-Nine (99): design

Scope: a seventh game, **99**, a multi-player counting card game with lives, from the domain to the
bots, delivered as a series of PRs (plan: `docs/plans/2026-10-28-ninety-nine-plan.md`). Written by Claude Opus 5.5
on `origin/main` at `bb340ddd`. No code here; names follow `docs/architecture/game-conventions.md` and the checklist
`docs/architecture/add-a-game.md`. The model for a multi-player card game with options, a determinism seam and
bots is President (`docs/specs/2026-10-15-president-design.md`).

Labels:

- **[O]** the owner's reference rules (main source, section 12), taken as they are;
- **[S2]** the secondary source (zpag.net), used only where the main source is silent;
- **[U]** not written in either source, chosen here; every [U] that changes play is an owner question (section 13);
- **[D]** a design choice where no rule applies (online play, data model, UI).

## 1. Goal

Add 99 as a seat game hosted by the session core (rooms, seats, tokens, lobby, options, presence, forfeit, play
again, bots as flagged seats, public game list, usage statistics), every rule in a pure `ninetynine/domain`
aggregate. 2 to 12 players each start with 3 lives (option 2 to 4). In each **round** a running total starts at 0;
players in turn play one card from a 3-card hand, which changes the total, and draw a replacement. The player who
takes the total **over 99** loses a life and the round ends. The last player with lives left wins; in **quick
mode** the game ends at the first elimination and the others are ranked by lives left.

The game is strictly turn-based: no out-of-turn play, no race between players.

## 2. Identity

| Form | Value | Why |
|------|-------|-----|
| `<id>` | `ninety-nine` | `99` cannot be a Java package nor start a TypeScript identifier; the spelled form reads well in `/ninety-nine/room/…`; it is the prefix of no other id |
| `<pkg>` | `ninetynine` | |
| `<Pascal>` | `NinetyNine` | |
| `<camel>` | `ninetyNine` | |
| Display name (picker, titles) | **99** | the name players use; "Ninety-Nine" in the rules text and in accessible names where a bare number reads badly [D, owner question Q10] |

## 3. Rules

### 3.1 Base game

- **Players**: 2 to 12 [O]. `minPlayers() = 2`, `maxPlayers() = 12` (the largest table of the site so far:
  President stops at 8; see 9.3 for what this costs the lobby and the table).
- **Deck**: one 52-card French deck, no joker [U, Q2]. With 12 players 36 cards are in hands and 16 in the stock.
- **Lives**: each player starts with `lives` lives (option, 2 to 4, default 3) [O].
- **Round start** [O]: every player still alive gets **3 new cards** from a freshly shuffled full deck; the total is
  0; the direction is reset to seat order [U, Q7].
- **First player** [O]: round 1, a seat drawn at random (through the determinism seam) [U, Q6]; later rounds, the
  player who lost the previous round, or, when that loss eliminated them, the next live seat after them **in seat
  order** [O; "the next player" read as seat order, U, Q6].
- **A turn**: the current player plays **one** card face up on the pile, the total changes by the card's effect,
  then the server draws a replacement into their hand automatically (no draw command; the hand is back to 3 while
  the stock lasts) [O]. The turn passes to the next live seat in the current direction.
- **Stock exhausted** [S2]: when a draw finds the stock empty, the pile except its top card is shuffled into a new
  stock (through the seam). One deck and at most 36 cards in hands means the stock can always be refilled.

### 3.2 Card effects [O, except the 5]

| Rank | Effect on the total | Note |
|------|---------------------|------|
| A | +1 or +11, the player chooses | +11 is offered only if it keeps the total ≤ 99 (with `freePlay = off`; by default both values are always offered, 3.3) |
| 2, 3, 5, 6, 7, 8 | + face value | **5 is missing from the main source**: the secondary source counts 2 to 8 at face value; proposed +5 [S2, Q1] |
| 4 | +0, **reverses the direction** | with 2 players a reversal changes nothing (the other player is next either way) [U, Q8] |
| 9 | +0 | "pass" card |
| 10 | −10 | below 10 the total stops at **0**, never negative [U, Q3] |
| J, Q | +10 | |
| K | the total **becomes 99** | always playable |

The secondary source differs on four cards (section 12.2): J skips the next player, 9 reverses, 4 counts +4,
Q counts +10 and the Ace only +1. The main source wins everywhere it speaks.

### 3.3 Going over 99, the stuck hand, the end of a round

- A card is **safe** when one of its values keeps the total ≤ 99. A total of exactly 99 is safe [O: "dépasse 99"].
  4, 9, 10 and K are always safe; a hand can only be stuck at a total of 92 or more without any of them.
- **Free play** [owner change, 30 Oct 2026, supersedes Q4's "a player who holds a safe card must play a safe card"]:
  by default (`freePlay = on`, section 4.1) the player is **allowed to make the mistake**, to force attention. The
  domain accepts any card of the hand, and an Ace at +11 even when +11 goes over; a play over 99 is paid exactly as
  a stuck seat pays it (the `Played` event, then `Busted`, no replacement draw, the round ends, a life is lost).
  The server reports every card as playable and both Ace values as allowed, so the client neither dims a card nor
  says "over 99" beforehand; the stuck seat sees what it saw before (`stuck: true`, the hint, an Ace sent as 1).
  With `freePlay = off` the old guard applies [U, Q4]: the domain refuses an unsafe card (`UnsafeCardException`)
  when a safe one is in hand, and an Ace at +11 when +11 goes over (`AceValueException`); `playable` then marks the
  unsafe cards `safe: false`. Bots are unaffected: they still pick a safe card whenever they hold one.
- **Stuck** [owner change, 28 Oct 2026, after playing; supersedes the automatic bust of the first version]: when
  the turn reaches a seat whose cards are all unsafe, the seat is **not** busted: it stays on turn (`stuck: true` in
  the view, public) and **must play one of its cards**, which takes the total over 99. That play is a normal
  `Played` event, shown to everyone with the card and the total; the same command then records `Busted(seat, total
  over 99, the hand it still holds)`, the seat loses one life and the round ends. Any card is accepted when stuck;
  an Ace counts 1 or 11 (both go over; the client sends 1). The stuck seat draws no replacement (the round is
  over). A forfeit that moves the turn to a stuck seat leaves it stuck on turn. Bots of all levels play the card
  that goes the least over 99. The round pause is 4 s (was 2.5 s) and the client holds the bust overlay 1.5 s so
  the table shows the busting card first.
- **Round end**: the round enters `ROUND_OVER`, shows who lost a life (and who is eliminated), then the next round
  is dealt after a pause (`ninetynine.round-pause-ms`, section 10) [D, Q9]; no ready barrier, so 12 players never
  wait for the slowest click.
- **Elimination** [O]: a player at 0 lives is out: no more cards, skipped in turn order, still shown at the table
  with their place.

### 3.4 End of the game and ranking

- **Normal mode** (`quick = off`, default) [O]: the game ends when one player has lives left; that player is 1st;
  the others are ranked by elimination, the later one higher. Two players are never eliminated by the same round
  (one loss per round), so there are no ties in normal mode.
- **Quick mode** (`quick = on`) [O]: the game ends as soon as a player loses their last life. That player is
  **last**; every other player is ranked by **lives left**, more is better. Players with the same number of lives
  **share** the place (1st, 1st, 3rd…: standard competition ranking) [U, Q5]; the end screen names every winner.
- `NinetyNineResult`: places per seat, reason `LAST_STANDING`, `QUICK` or `FORFEIT`.

### 3.5 Out of scope

| Variant | Source | Why not |
|---------|--------|---------|
| J skips the next player, 9 reverses, 4 counts +4, Ace only +1 | zpag (S2) | conflicts with the owner's card table |
| Several decks by player count (2 decks for 3 players, 3 for 4-5, 4 for 6-7) | zpag (S2) | one deck with reshuffle serves 12 players; a `decks` option can come later without model change (Q2) |
| First dealer by "first Jack dealt", cut, eldest hand | zpag (S2) | dealing is automatic online |
| A player who forgets to draw plays with 2 cards; picking up cards before the deal ends | zpag (S2) | the server draws; nothing to forget |
| Five strikes as the only end ("coincé 5 fois") | zpag (S2) | the owner's lives (2 to 4) replace it |
| Announcing the total aloud | zpag (S2) | the table always shows it |

## 4. Options

### 4.1 Host options (game level)

`lives` and `quick` are `OptionType.CHOICE` through the existing lobby mechanism (`optionDescriptors()`, `validate` parsing a
`NinetyNineOptions` record strictly, Bullshit/President pattern). Label keys `options.ninetyNine.<key>`.

| Key | Values (first = default) | Meaning |
|-----|--------------------------|---------|
| `lives` | `3`, `2`, `4` | lives per player [O] |
| `quick` | `off`, `on` | quick mode (3.4) [O] |
| `freePlay` | `on`, `off` | **hidden**: not in `optionDescriptors()`, so the lobby shows no group for it; accepted by the strict parse, hence settable by the host through the options API (`PUT` options / `SessionService.updateOptions`), so a later tutorial can switch the guard back on. Default `on` (3.3). Always present in the full option map (`options` of the view and the lobby) |

### 4.2 Seat options (bots)

`level` = `easy|medium|hard`, default `medium`, label `options.ninetyNine.level` (Kobo / President pattern).

## 5. Numeric parameters

"Main source" is the owner's text; a row flagged **changed** is a value this spec proposes where the main source
gives another value or none. Nothing here is implemented before the owner agrees to this table.

| Parameter | Main source | Secondary (zpag) | Proposed | |
|-----------|-------------|------------------|----------|-|
| Players | 2 to 12 | 3 or more | 2 to 12 | |
| Lives | 3, option 2 to 4 | 5 strikes | default 3, choices 2 / 3 / 4 | |
| Hand size | 3 | 3 | 3 | |
| Decks | not given | 2 to 4 by player count | 1 deck of 52, discard reshuffled | **changed** (new value, Q2) |
| Value of the 5 | not given | +5 | +5 | **changed** (new value, Q1) |
| Floor of the 10 | not given | not given | total never below 0 | **changed** (new value, Q3) |
| Round pause before the next deal | not given | — | 2,500 ms (0 under `test`) | **changed** (new value, Q9) |
| Bot thinking delay | — | — | 700 to 1,300 ms (President's), 0 under `test` | **changed** (new value) |
| Bot `hard` samples per decision | — | — | 200 sampled deals (cap 20 ms a decision) | **changed** (new value) |
| Bot `easy` random play | — | — | 100% random among safe cards | **changed** (new value) |
| Bot `medium` mistakes | — | — | none: deterministic heuristic | **changed** (new value) |

## 6. Domain model (`ninetynine/domain`)

One aggregate root `NinetyNine implements Game`, President/Kobo style: every mutating method `synchronized`,
validates first then mutates, bumps `version()`, returns a small outcome; a refused command never mutates.
Package-private constructor with explicit hands, stock and total for tests (`NinetyNineBuilder`).

| Type | Role |
|------|------|
| `NinetyNineOptions` (+ `Lives`, `QuickMode`) | typed options, `from` lenient / `parseStrict` strict, `toGameOptions` |
| `CardEffect` (pure) | `values(card)`: the possible totals effects (`Add(n)`, `Subtract(10)`, `SetTo(99)`, `Reverse`); `apply(total, card, aceValue)`; `isSafe(total, card)`; `safeCards(total, hand)`. Shared by the aggregate, the DTO hints and the bots, so the client never computes a rule |
| `Direction` | `CLOCKWISE` (seat order) / `COUNTER_CLOCKWISE` |
| `Round` | number, hands, stock, pile, total, direction, current seat, starter |
| `Phase` | `PLAYING`, `ROUND_OVER`, `FINISHED` |
| `DeckSource` (port) | `shuffledDeck()` for a deal and `reshuffle(List<Card>)` for the stock; `RandomDeckSource` default, `ScriptedDeckSource` under `test`; the first seat of round 1 also comes from it (`firstSeat(n)`) |
| `NinetyNineEvent` (sealed) | `RoundDealt(round, starter)`, `Played(seat, card, aceValue, totalAfter, reversed)`, `Busted(seat, totalBefore, hand, livesLeft)`, `Eliminated(seat, place)`, `StockReshuffled(size)`, `GameFinished(result)`, `Forfeited(seat)` |
| Exceptions (checked `NinetyNineException`) | `FinishedGameException`, `WrongPhaseException`, `NotPlayersTurnException`, `CardNotInHandException`, `UnsafeCardException` (a safe card exists, `freePlay` off only), `AceValueException` (missing, not 1/11, or with `freePlay` off 11 over 99), `NotEliminatedException` |

### 6.1 Commands and queries

Commands: `play(seat, Card, Integer aceValue)`, `nextRound()` (called by the presentation after the pause; refused
outside `ROUND_OVER`), `forfeit(PlayerId)`.

`play` order: phase and turn checks; card in hand; ace value valid; safety (6.2); then apply the effect, put the
card on the pile, draw (reshuffle if the stock is empty), move to the next live seat in the direction; then, if
that seat is **stuck**, it stays on turn (3.3) and its play may go over 99 (any card, an Ace as 1 or 11). A play over 99 busts the seat: lives −1, elimination and place, end check
(3.4), else `ROUND_OVER`.

Queries: `phase()`, `round()`, `version()`, `currentSeat()`, `direction()`, `total()`, `hand(seat)`,
`handSizes()`, `lives()`, `pileTop()`, `pileSinceReshuffle()` (public: the face-up pile), `stockSize()`,
`lastBust()`, `places()`, `safeCards(seat)`, `availableActions(seat)` (`PLAY` only; nothing for the others),
`options()`, `result()`.

### 6.2 Worked examples (test contract of the domain chunk)

Three players A, B, C in seat order, 3 lives, one parameterised test row each.

1. Total 90, A plays K: total 99. B holds 9: plays it, total 99, C next.
2. Total 95, A holds 7, 8, J: stuck. When C's play passes the turn to A, A stays on turn, plays the 7 (total 102) and busts with the hand 8, J shown:
   A has 2 lives, `ROUND_OVER`, A starts the next round.
3. Total 89, A holds A, 2, 4: plays A; with `freePlay` off `aceValue = 11` is refused (100) (accepted by default, and the seat loses a life), `aceValue = 1` gives 90. A missing ace value
   is refused for an Ace, ignored for other cards [D].
4. Total 95, A holds 8, 4: playing 8 is refused (`UnsafeCardException`, the 4 is safe); A plays 4: total 95, the
   direction reverses, C is next.
5. Total 6, A plays 10: total 0.
6. Two players, A plays 4: direction reverses, B is still next.
7. B at 1 life busts and is eliminated (place 3 of 3); the next round starts with C (next live seat after B).
8. Quick mode, lives 3: A busts to 0 → game over; B has 3, C has 2 → B 1st, C 2nd, A 3rd; with B and C at 2, both 1st.
9. Stock empty with 5 cards on the pile: the 4 under the top are shuffled into a new stock, the draw happens.
10. A forfeits on their turn: eliminated, hand removed, the turn goes to the next live seat; one live player left →
    `FINISHED` (`FORFEIT`).

## 7. Disconnect, forfeit, rematch

- **Disconnect**: the existing 60 s grace; the table waits for a disconnected player whose turn it is (no turn
  timer, as everywhere else) [D].
- **Forfeit** [U, Q5b]: the player is eliminated at once and placed below every live player (several forfeiters:
  the later one higher, like eliminations). If it was their turn, the turn moves on; the total and the round are
  unchanged. A forfeit **does not** trigger the quick-mode end (a leaver should not end everybody's game); the game
  ends with `FORFEIT` only when one live player is left.
- **Rematch**: `FINISHED`, then `POST /api/ninety-nine/game/{id}/play-again`; a new game at full lives, options and
  bots kept by the session core.

## 8. Presentation

### 8.1 WebSocket (`/app/ninety-nine/...`, through `SeatCommands`)

| Destination | Payload | Effect |
|-------------|---------|--------|
| `/ninety-nine/start` | `GameActionPayload` | host starts (game-type check first: "This room is not a 99 game") |
| `/ninety-nine/play` | `NinetyNinePlayPayload(gameId, token, card: CardDto, aceValue: Integer?)` | play a card |
| `/presence`, `/forfeit` | existing | unchanged |

`NinetyNineEventType`: `START`, `ROUND_START`, `PLAY`, `RESHUFFLE`, `BUST`, `ELIMINATED`, `GAME_OVER`. One
`SuccessResponse` per human seat through `SeatStateBroadcaster<NinetyNine, NinetyNineDto>`; refusals to the sender
alone with a code (French translation design D3: the frontend never renders a server `message`).

`NinetyNineRoundPause` (presentation, President's `PresidentExchangeViewTimeout` pattern): on `ROUND_OVER`,
schedules `nextRound()` after `ninetynine.round-pause-ms`, keyed on `version()`, dropped when stale.

### 8.2 REST (`/api/ninety-nine`) over `SeatGameRest`

`GET /game/{id}?token=`, `POST /game/{id}/join`, `POST /game/{id}/play-again`. Test seam under `@Profile("test")`:
`NinetyNineTestDealController`, `PUT`/`DELETE /api/test/ninety-nine/{gameId}/deal` (Kobo/President pattern), with
the deals, reshuffles and first seat it scripts.

### 8.3 Per-seat DTO (`NinetyNineDto.forViewer(game, viewer, names, bots)`)

Public: `version`, `round`, `phase`, `total`, `direction`, `currentSeat`, `pileTop`, `pileCount`, `stockSize`,
`options`, per seat `{seat, name, bot?, level?, lives, handSize, eliminated, place?}`, `lastPlay {seat, card,
aceValue, totalBefore, totalAfter, reversed}`, `lastBust {seat, totalBefore, hand, livesLeft, eliminated}` (the
stuck hand is public: it proves the bust), `outcome` (places, reason).

Private (viewer only): `hand`, `playable` (per card of the hand: safe or not, and the allowed ace values, from the
domain's `CardEffect`), `availableActions`.

Never in another seat's DTO: a card of a live hand, the stock order.

Names follow the French translation design D3 (bots and unnamed players sent as `name: null`, the client names
them).

### 8.4 Configuration

`config/NinetyNineConfig.java`: `ninetyNineFactory`, `ninetyNineStateBroadcaster`, `ninetyNineGameActions`,
`ninetyNineLifecycleBroadcaster`, the `DeckSource` by profile, `NinetyNineRoundPause`, the bot coordinator.
`AppConfig` is not edited. Shared backend tests per `add-a-game.md` §2.2. The public game list and the usage
statistics need nothing: both key on the game type the session core already carries (`GameDefinition.serverType`
equals the key for a new game).

## 9. Frontend

### 9.1 Files (per `game-conventions.md` §4)

`model/ninetynine/NinetyNineState.ts` (types of 8.3, helpers: `cardLabel`, `effectLabel` codes, `totalDanger`),
`model/ninetynine/tempo.ts` and `motion.ts` (anchors `NinetyNineAnchor`: seats, pile, stock, my hand; plans per
event), `application/NinetyNineSession.ts` (`NINETY_NINE_SESSION_CONFIG`, command `play(card, aceValue?)`),
`state/NinetyNine.store.ts`, `composables/useNinetyNineBootstrap.ts`, `components/ninetynine/` (table, seat plate
with lives, centre counter with direction, hand, ace chooser, bust banner, lobby options, round and final
standings), `view/ninetynine/NinetyNineGameScreen.vue`. Texts: rules at `ninetyNine`, screen at `ninetyNineUi`,
options at `options.ninetyNine`. Registry: `define('ninety-nine', { supportsBots: true, picker })`.

The client has no rule: which card is playable and which ace values are allowed come from `playable`.

### 9.2 Reuse

The shared table pieces (`model/table/seatRing.ts`, `opponentOrder.ts`, `displayName.ts`,
`components/table/TotalsTable.vue`), the card animation module (`src/animation` barrel: flights from a seat to the
pile and from the stock to my hand, rings, speed button, reduced motion; recipe in `src/animation/README.md`), the
Kobo/President felt and room, the shared chrome (`GameTimer`, `RulesPanel`, `GameLobby`, `EndGameOverlay`,
`DisconnectOverlay`, `ForfeitBanner`). Card faces from the existing card components (President/Kobo).

### 9.3 UI decisions: Claude Design canvas required (not designed here)

Every layout below goes through a Claude Design canvas (Artifact `quickstart`, intent `design`) with **2 or 3
options on real 99 data** (a 12-player table at total 94 with a reversed direction, a stuck bust with the revealed
hand, quick-mode final ranking with shared places), at 375 px and 1280 px, light and dark, one recommendation,
before chunk F2 starts. The owner chooses; the choice goes into a "UI addendum" of this spec.

1. **Table for 2 to 12 seats**: whether the President ring (`seatRing.ts`) still reads with 11 opponents at 375 px
   (name, lives, card count, eliminated, current turn), or another arrangement.
2. **Centre**: the running total (and its danger as it nears 99), the direction of play, the pile top, the stock.
3. **Hand and the Ace**: how the player picks +1 or +11 (two buttons after tapping the Ace, or two targets), how
   unsafe cards look (disabled, with the reason).
4. **Lives** on the seat plates (pips, hearts, a number) and the elimination state.
5. **Bust and round end**: the banner with the stuck hand, the life lost, the pause before the next deal.
6. **Lobby options** (`lives`, `quick`) inside the existing `GameLobby` / `LobbyGameOptions` pattern, and the 12-seat
   lobby list.
7. **End screen**: places, shared places in quick mode.

## 10. Bots

Three levels, as in the other games: the choice is real in 99 (which safe card, when to spend a K, a 10 or a 4,
the Ace value, who is next), so three levels are meaningful.

- **Domain** (`ninetynine/domain/bot`, pure): `NinetyNineBotView.forSeat(game, seat)` (exactly what the seat's DTO
  shows, plus the public pile since the last reshuffle; a parity test pins it), `NinetyNineBotStrategy` (port:
  `decide(view) → Play(card, aceValue)`), `NinetyNineBotLevel`, `NinetyNineBotTuning`.
- **Candidates** always come from the domain's `CardEffect.safeCards`, so a bot never plays an illegal card.
- `easy` (**random**): a uniformly random safe card, a random allowed Ace value.
- `medium` (**greedy**, deterministic): keep the escape cards (K, 10, 4, 9) while a plain card is safe; among plain
  cards play the one that brings the total **highest** without going over (pushes the next player towards 99); when
  no plain card is safe, spend a 9, then a 4, then a 10, and a K last.
- `hard` (**strongest feasible**: determinised Monte Carlo): for each candidate, sample 200 deals of the unseen
  cards (the deck minus its own hand, the pile since the reshuffle) into the other hands; score = probability that
  the next live player in the resulting direction is stuck, minus the probability that the bot itself is stuck on
  its next turn after one round of plays by greedy opponents, weighted by lives (hurting the leader is worth more).
  Hidden information makes an exact solve pointless; the cap is 20 ms a decision.
- **Strength benchmark** (C2, opt-in: `mvn test -Dtest=NinetyNineBotStrengthBenchmark -Dninetynine.bench=true`,
  200 games of 4 players, seats rotated, default options; a fair seat wins 25%, one run so about ±3 points of noise;
  the three same-level seats are individual seats, hence their spread):

  | Table | Focus level wins | Others |
  |-------|------------------|--------|
  | 1 hard, 3 medium | hard 31.5% | medium 16.0 / 23.0 / 29.5% |
  | 1 hard, 3 easy | hard 71.5% | easy 8.5 / 7.5 / 12.5% |
  | 1 medium, 3 easy | medium 63.5% | easy 15.5 / 10.5 / 10.5% |
  | 1 hard, 1 medium, 2 easy | hard 45.5% | medium 42.5%, easy 6.5 / 5.5% |

  Hard beats medium by a modest margin and both beat easy clearly, as intended: the strongest level stays beatable.
- **Coordinator** (`presentation/bot/NinetyNineBotCoordinator`, President/Morris pattern): after each broadcast,
  one task for the bot on turn, after `ThinkingDelay` (`ninetynine.bots.delay-min-ms` / `delay-max-ms`, 0 under
  `test`), keyed on `version()`; a failing strategy falls back to the first safe card (Ace at +1).

## 11. Tests

- Strict TDD at domain level: `CardEffectTest` (every rank, the Ace, the 10 floor, the K, safety), the worked
  examples of 6.2 as parameterised rows, `NinetyNineRoundTest` (deal, starter, reshuffle), `NinetyNineEndTest`
  (normal and quick ranking, shared places), `NinetyNineForfeitTest`, `NinetyNineFactoryTest` (type, bounds,
  options strict parse), bots (`easy` only safe cards over 1,000 random positions; `medium` picks the documented
  card in hand-written positions; `hard` beats `medium` in an opt-in benchmark and stays under its time cap).
- Presentation: `NinetyNineDtoTest` (no foreign hand), `NinetyNineGameActionsTest`, `NinetyNineRestAndLifecycleTest`,
  `NinetyNineWebSocketControllerTest`, `NinetyNineWebSocketControllerIT` (STOMP: create, join, start, plays, a
  refused unsafe card answered to the sender alone, a bust, the end), the seam ITs (present under `test`, absent
  otherwise), the round pause and the coordinator with a scheduler stub.
- Frontend: Vitest per file (model, session, store, bootstrap, components, screen).
- **Cypress: nominal only, one spec**: `cypress/specs/ninety-nine-game.cy.ts`, written in the integration chunk: a
  host creates a room with `lives = 2`, a scripted second player (`joinAsScriptedPlayer(gameId, name,
  'ninety-nine')`) joins, Start, a fixed deal leads to a play, a bust with its banner, a second round, and the end
  screen. No lobby-only spec, no bot spec, no quick-mode spec (Vitest and backend tests cover them); the shared
  picker specs get the new game in their lists.

## 12. Sources

### 12.1 Main source (the owner's text, verbatim)

> Objectif: Soyez le dernier joueur à avoir des vies restantes.
> Partie: Chaque joueur commence la partie avec 3 vies. À chaque tour, les joueurs reçoivent 3 nouvelles cartes et
> le total de points commence à 0. Les joueurs jouent à tour de rôle une carte de leur main et en piochent une de
> remplacement. Lorsqu'un joueur dépasse 99 points, il perd une vie et la manche se termine. Le tour suivant
> commence avec le joueur qui a perdu le tour précédent, ou en cas d'élimination, le joueur suivant.
> Cartes: 1, 2, 3, 6, 7, 8 = ajouter la valeur de la carte; A = ajouter 1 ou 11; V, D = ajouter 10; 4 = changer le
> sens du tour de jeu; 9 = ne rien ajouter; 10 = retirer 10; R = fixer le total de points à 99.
> Options: 2-12 joueurs; 2-4 vies.
> Mode rapide: désactivé = quand un joueur perd toutes ses vies, il est éliminé et la partie continue; activé =
> quand un joueur perd toutes ses vies, tous les joueurs restants sont classés en fonction du nombre de vies
> restantes.

("1" in the card list is read as a slip: the Ace has its own line.)

### 12.2 Secondary source: zpag.net, "Le 99" (fetched 2026-10-28)

https://zpag.net/Jeux/Cartes/le_99.html. Differences with the main source:

| Point | Main source | zpag | Spec |
|-------|-------------|------|------|
| Players | 2 to 12 | 3 or more | main |
| Decks | not given | 2 decks for 3 players, 3 for 4-5, 4 for 6-7 ("one more deck each time the count turns even") | one deck (Q2) |
| Ace | +1 or +11 | +1 | main |
| 4 | reverses | +4 | main |
| 5 | **not listed** | +5 (face value, 2 to 8) | +5 (Q1) |
| 9 | +0 | reverses the direction | main |
| 10 | −10 | −10 | same |
| Jack | +10 | the next player loses their turn (no value given) | main |
| Queen | +10 | +10 | same |
| King | total = 99 | total = 99 | same |
| Losing | going over 99 costs a life | being "stuck" at 99 with no K, J, 10 or 9 costs a strike | main (a stuck hand is the only way to go over, 3.3) |
| End | last player with lives; quick mode | a player stuck 5 times loses and the game ends | main |
| Deal, first dealer | not given | cards dealt face up until a Jack names the dealer; 3 cards each; a turned card starts the pile and the count | automatic deal, random first seat (Q6) |
| Stock empty | not given | the pile is shuffled into a new stock | zpag |
| Forgot to draw | — | the player keeps 2 cards | not applicable (the server draws) |

## 13. Questions for the owner (recommendation first)

| # | Question | Recommendation | Alternative |
|---|----------|----------------|-------------|
| Q1 | The 5 is missing from your card list: what does it do? | +5 (face value, as zpag and the 2-8 sequence) | another effect (e.g. +0) |
| Q2 | Deck for up to 12 players | one 52-card deck, the pile reshuffled into the stock when it runs out | 2 decks from 7 players |
| Q3 | 10 when the total is under 10 | the total stops at 0 | negative totals allowed |
| Q4 | A hand with no safe card | the seat busts at once when its turn comes, its hand shown; a seat with a safe card cannot play an unsafe one | the stuck player must click a card that goes over; or unsafe plays always allowed |
| Q5 | Quick mode, equal lives | shared place (1st, 1st, 3rd) | tie broken by seat order or by fewer busts |
| Q5b | A forfeit in quick mode | eliminates the leaver only; the game goes on | it ends the game like a last life lost |
| Q6 | First player of round 1; "the next player" after an elimination | a random seat; then the next live seat in seat order | the host; the next seat in the direction in force |
| Q7 | Direction at the start of a round | reset to seat order | kept from the previous round |
| Q8 | The 4 with two players | reverses (no visible effect) | acts as "play again" (Uno-style) |
| Q9 | Between rounds | 2.5 s pause then automatic deal | a ready barrier (Kobo/President) |
| Q10 | Display name | "99" in the picker and titles | "Ninety-Nine" |

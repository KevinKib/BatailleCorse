# Blackjack: design

Scope: a new game, **Blackjack**, played solo against an automated dealer or as a chip tournament between 2 to 6
players (humans and bots) who all play against the same dealer. From the domain to the bots and the entry PR,
delivered as a series of PRs (plan: `docs/plans/2026-10-28-blackjack-plan.md`). Written by Claude Opus 5.5 on
`origin/main` at `8f7a36d1`. No code here; names follow `docs/architecture/game-conventions.md` and the checklist
`docs/architecture/add-a-game.md`. The closest model is 99 (`docs/specs/2026-10-28-ninety-nine-design.md`: a
multi-player card game with automatic deals, a deck seam, three bot levels and one Cypress spec).

Status: implemented; the entry PR (chunk E) makes it playable.

**The owner delegated every decision for this game** ("decide autonomously, I will test the finished game and give
feedback"). There are no owner questions and no Claude Design canvas: every rule and the UI layout are decided
here, and each decision is listed in section 2 with its reason and the rejected alternative, so the owner can
overturn any of them after playing. The plan is executable end to end without further input.

Labels:

- **[O]** the owner's requirement (section 14.1), taken as it is;
- **[C]** standard casino blackjack, as the sources of section 14.2 describe it;
- **[T]** tournament blackjack practice (section 14.2);
- **[A]** decided autonomously here (section 2 lists them all);
- **[D]** a design choice where no rule applies (data model, wire, UI).

## 1. Goal

Add Blackjack as a seat game hosted by the session core (rooms, seats, tokens, lobby, options, presence, forfeit,
play again, bots as flagged seats, public game list, usage statistics), every rule in a pure `blackjack/domain`
aggregate.

- **Solo** (one seat): one human against the dealer, 100 points, a fixed number of hands; the game ends after the
  last hand or when the player is broke. The end screen shows the final stack and the gain or loss.
- **Tournament** (2 to 6 seats, humans and bots): every player starts with 100 points and plays the same hands
  against the same automated dealer; a player at 0 is eliminated; after the last hand (or when one player is
  left), the player with the most points wins.

Solo and tournament are **the same game**: the mode follows from the number of seats when the host presses Start
(1 seat = solo), not from an option.

## 2. Decisions taken autonomously

Each row can be overturned by the owner after playing; the "Change cost" column says where.

| # | Decision | Reason | Rejected alternative | Change cost |
|---|----------|--------|----------------------|-------------|
| A1 | Id `blackjack`, display name "Blackjack" in both languages | the word is used as is in French; one word, prefix of no other id | `black-jack`, "Vingt-et-un" | permanent (URLs, tokens) |
| A2 | Solo is a 1-seat game, not an option (`minPlayers = 1`) | no extra option, the lobby already has Start and Add bot; the session core accepts 1 (it compares claimed seats to `minPlayers`) | a `mode` option | factory bound |
| A3 | Up to **6** seats | a 6-seat table still fits a 375 px screen as a 2-column grid of plates; real tables have 5 to 7 spots | 7 seats | one constant + layout |
| A4 | **Neutral automated dealer** for everyone, in both modes | the dealer has no decisions (draws to 17), so a player-banker only moves chips between players and must cover up to 5 bets of 50 with 100 points; the tournament standard is "everyone against the house, compare chips" [T] | a rotating player-banker (the owner's alternative) | large (new settlement model) |
| A5 | Players compete through their **stacks**: the ranking is by points; each hand also names its **best hand** (the largest gain of the hand) as information only | this is tournament blackjack [T]; it answers the owner's "compared with the bank and between them" without a second payout system | a pot for the best hand of each round | small (settlement) |
| A6 | 100 points each, fixed, shown in the lobby but not an option [O] | the owner fixed 100 | a stake option | one constant |
| A7 | Number of hands: option `hands`, values 10 (default), 5, 15, 20 [O "10 or 15", default 10] | short and long games; 20 is the length of a short real tournament round [T] | a free number | option values |
| A8 | Bets: whole points, **minimum 1**, maximum = option `maxBet` 50 (default), 25, 100, capped by the stack | min 1 (1% of 100, the tournament ratio) means a player with points left can always bet, so "eliminated" only ever means 0 [O]; 100 allows all-in | even bets only; a minimum of 10 | option values |
| A9 | Blackjack pays **3:2 rounded down** to a whole point (a bet of 5 wins 7) [C] | 3:2 is the standard payout; casinos round odd payouts down; whole points keep the domain and the display simple | 6:5; half points | one function |
| A10 | Shoe of **6 decks, shuffled fresh before every hand** (continuous shuffle) | 6 decks is the common shoe [C]; a fresh shuffle each hand makes card counting pointless (fair for bots and humans) and gives the test seam one shoe per hand | a cut card and a reshuffle point | the shoe source |
| A11 | Dealer **stands on all 17s** (S17) [C] | the more player-friendly common rule; one sentence in the rules panel | hits soft 17 | one line |
| A12 | Dealer takes a **hole card and peeks** when the up card is an Ace or a 10-value card; a dealer blackjack ends the hand at once, players lose only their original bet (a player blackjack pushes) [C] | no doubles or splits lost to a hidden blackjack (the European no-hole-card rule feels unfair online) | no hole card (European) | the deal routine |
| A13 | **Double** on any first two cards, once, one card then stand; also after a split (DAS), except split Aces [C] | standard and cheap: the same logic per hand | doubles only on 9 to 11 | one rule |
| A14 | **Split** once (two hands at most), on two cards of the same **value** (a K and a 10 can split); split Aces get one card each and stand; 21 after a split is not a blackjack (pays 1:1) [C] | splitting is half of the blackjack fun and costs only "a seat holds one or two hands"; one split keeps the phone layout to two hands | no split; resplits up to 4 hands | moderate (hand list) |
| A15 | **No insurance, no even money, no surrender** | insurance is a bad side bet the sources advise against [C] and adds a prompt to every hand with an Ace up; surrender adds a button and a half-point refund; both slow the phone flow | insurance offered at 2:1 | a new phase and action each |
| A16 | No double or split for less: doubling or splitting needs a stack of at least the bet | simple and common | "double for less" | one check |
| A17 | **Sealed, simultaneous bets**: every live player bets at once; bets are hidden from the others until the deal | fastest on a phone (no waiting for each other's bet in turn); hidden bets stop the last bettor from always reacting to the others, which a turn order would have to fix with a rotating button [T] | bets in turn order with a rotating first bettor | betting phase |
| A18 | Play in **seat order from a rotating first seat** (the "button" moves one live seat each hand, hand 1 starts at seat 0) [T] | real tournament practice; acting first or last alternates fairly | always seat 0 first; simultaneous play | one index |
| A19 | **No pause and no ready barrier between hands**: after the settlement the next hand's betting opens at once, and the last hand's cards and results stay on the table until the next deal | the betting phase is already a natural barrier; a timer would only delay | a 3 s pause (99 style); a ready button | DTO + screen |
| A20 | A player at **0 points is eliminated** after the settlement; no rebuy [O "sauf recave autorisée": not allowed] | the owner's rule; a rebuy would make the tournament endless | a one-time rebuy option | an option + chips |
| A21 | The tournament ends after the last hand, or **as soon as one player has points left**; the solo game ends after the last hand or when the player is broke | nothing is left to decide with one player | play the remaining hands alone | end check |
| A22 | Ranking: live players by stack, more is better, equal stacks **share** the place (1st, 1st, 3rd); eliminated players below, the later elimination higher, eliminated on the same hand share; forfeiters below everyone | standard competition ranking, as 99's quick mode | tie broken by seat order | ranking function |
| A23 | Solo outcome: no place; the end screen shows the final stack and the net gain (`+23`, `−100`); "won" when the final stack is above 100 | a ranking of one is meaningless | a fixed target (double your stack) | end screen |
| A24 | Forfeit (and the 60 s presence grace, unchanged): the seat is eliminated at once, a placed bet is lost, its hands leave the table, the turn moves on | same as 99 | the hand plays itself out (auto stand) | forfeit routine |
| A25 | Three bot levels: `easy` random, `medium` basic strategy with a flat bet, `hard` basic strategy plus **tournament betting** (section 10) | play decisions against a dealer have one best answer (basic strategy); with a fresh shuffle each hand, the only room for a stronger bot is the bet sizing and the last-hand risk relative to the other stacks | counting cards (pointless with A10); a Monte Carlo bot | bots chunk |
| A26 | No remappable shortcuts in v1 (no gear), the actions are buttons | phone first; other card games without shortcuts do the same | H/S/D/P hotkeys | registry + settings |
| A27 | Layout (no canvas, owner override): dealer strip on top, opponents as compact plates in a 2-column grid (one row at 1280 px), my seat and a sticky action bar at the bottom; betting panel with chip buttons and a one-tap "Bet N" prefilled with my last bet | thumb reach on a phone, one tap to repeat a bet; the 99 ring is built for a single shared pile, not for per-seat hands | a seat ring around an oval felt (Kobo/99) | components chunk |
| A28 | Picker card: icon `pi pi-wallet`, severity `success` (green felt) | a betting game; colours are already shared between games | `pi pi-money-bill` | one line |
| A29 | Actions on the active hand need no hand index on the wire: a command acts on the seat's active hand, and a stale click is refused by the turn and phase checks | the server knows the active hand; fewer payload fields | `handIndex` in every payload | payload |

## 3. Rules

### 3.1 Players, stacks, hands

- **Players**: 1 to 6 (`minPlayers() = 1`, `maxPlayers() = 6`) [A2, A3]. One seat is solo; two or more is a
  tournament.
- **Stack**: every player starts with **100 points** [O, A6].
- **Hands**: the game lasts `hands` hands (option, section 4) [O, A7]. A hand is: bets, deal, dealer peek, players'
  turns, dealer's turn, settlement.

### 3.2 Card values [C]

2 to 10 at face value; J, Q, K count 10; an Ace counts 11 unless that takes the hand over 21, then 1. A hand
counting an Ace as 11 is **soft**. A **blackjack** is an Ace and a 10-value card as the first two cards of a hand
that does not come from a split. Over 21 is a **bust**.

### 3.3 Betting [A8, A17]

- Phase `BETTING`: every live player places one bet, a whole number from 1 to `min(maxBet, stack)`. The bet leaves
  the stack at once (the plate shows stack and bet apart).
- Bets are sealed: the others see "bet placed", not the amount, until the deal.
- The deal happens as soon as every live player has bet. A seat that has bet cannot change its bet [D].

### 3.4 The deal [C, A10, A18]

- A fresh shoe of 6 × 52 cards is shuffled for every hand (through the seam, section 6).
- The **first seat** (button) is seat 0 on hand 1, then the next live seat after the previous button each hand.
- Deal order, from the shoe top: one card face up to each live seat in seat order starting at the button, one card
  face up to the dealer (the up card), a second card face up to each seat in the same order, then the dealer's
  **hole card** face down. This order is the contract of the scripted shoe (section 8.2).

### 3.5 Dealer peek [C, A12]

If the up card is an Ace or a 10-value card, the dealer checks the hole card. With a dealer blackjack, the hole card
is shown, the hand is settled at once (player blackjacks push, every other hand loses its bet), and no player acts.

### 3.6 Players' turns [C, A13, A14, A16]

- Turns go seat by seat from the button. A seat with a blackjack has nothing to do and is skipped.
- On its turn a seat plays its **active hand**: `HIT` (one card), `STAND`, `DOUBLE` (first two cards only, stack ≥
  bet: the bet doubles, one card, the hand stands), `SPLIT` (first two cards of the same value, no split yet,
  stack ≥ bet: two hands with one card each and a bet each).
- A hand that reaches **21** stands by itself; a hand that busts is finished (its bet is lost at the settlement).
- **Split**: the first hand gets its second card at once and is played first; the second hand gets its second card
  when the first is finished, then is played. Split Aces get one card each and both stand at once (no action). A
  21 on a split hand is a plain 21. Double after split is allowed except on split Aces. No resplit.
- `availableActions(seat)` is the single source of what a seat may do (the client shows exactly these buttons).

### 3.7 Dealer's turn [C, A11]

After the last seat, the hole card is shown. If every live hand is busted or a blackjack, the dealer draws nothing.
Otherwise the dealer draws until the total is 17 or more and **stands on every 17, soft or hard**.

### 3.8 Settlement [C, A9]

Per hand (a split seat has two), with `bet` the hand's bet (doubled if it doubled):

| Case | Net for the player |
|------|--------------------|
| Player busts (whatever the dealer does) | −bet |
| Player blackjack, dealer no blackjack | +⌊3 × bet / 2⌋ |
| Player blackjack, dealer blackjack | 0 (push) |
| Dealer blackjack, player no blackjack | −bet (only the original bet: nobody doubled or split, section 3.5) |
| Dealer busts, player did not | +bet |
| Higher total than the dealer | +bet |
| Equal totals | 0 (push) |
| Lower total | −bet |

The **best hand** of the hand [A5]: the seat(s) with the largest positive total net over their hands; none if
nobody gained. Information only (a badge in the summary).

### 3.9 Elimination, end and ranking [O, A20 to A23]

- After the settlement, a live player with a stack of 0 is **eliminated** (no rebuy).
- **End**: after the settlement of hand `hands`; or, in a tournament, as soon as at most one live player is left;
  or, in solo, when the player is eliminated. Otherwise the next hand's `BETTING` opens at once (A19).
- **Tournament ranking**: live players by stack (more is better, equal stacks share the place); then eliminated
  players, the later elimination higher, the same hand sharing; then forfeiters, the later forfeit higher.
- **Solo outcome**: the final stack and the net (`final − 100`).
- `BlackjackResult`: places per seat (empty in solo), final stacks, reason `HANDS_PLAYED`, `LAST_STANDING`, `BROKE`
  (solo at 0) or `FORFEIT`.

### 3.10 Out of scope

| Variant | Why not |
|---------|---------|
| Insurance, even money, surrender (late or early) | A15 |
| Resplitting, hitting split Aces, double for less | A14, A16 |
| A rotating player-banker | A4 |
| Rebuy ("recave") | A20 |
| Visible bets in turn order, a hidden "secret bet" per round (Ultimate Blackjack Tour) | A17 |
| Elimination of the lowest stack at fixed hands (Elimination Blackjack) | the owner's rule eliminates at 0 only |
| Side bets (Perfect Pairs, 21+3) | not blackjack |
| Card counting help, a cut card | A10 |

## 4. Options

### 4.1 Host options (game level)

`OptionType.CHOICE` through the existing lobby mechanism (`optionDescriptors()`, `validate` parsing a
`BlackjackOptions` record strictly, the 99 pattern). Label keys `options.blackjack.<key>`.

| Key | Values (first = default) | Meaning |
|-----|--------------------------|---------|
| `hands` | `10`, `5`, `15`, `20` | number of hands [O, A7] |
| `maxBet` | `50`, `25`, `100` | largest bet of a hand, also capped by the stack [A8] |

The starting stack (100) and the minimum bet (1) are fixed: the lobby options card shows them as a read-only line
("Each player starts with 100 points; bets from 1 to the maximum").

### 4.2 Seat options (bots)

`level` = `easy|medium|hard`, default `medium`, label `options.blackjack.level` (the 99 pattern).

## 5. Numeric parameters (current / proposed)

"Owner" is the owner's text (section 14.1); a row flagged **changed** is a value decided here where the owner
gave another value or none. The owner delegated the decisions: these are implemented as proposed, and stay
easy to change (one constant or option list each).

| Parameter | Owner | Proposed | |
|-----------|-------|----------|-|
| Starting stack | 100 points ("ici 100, PAS 1000") | 100, fixed | |
| Number of hands | fixed, "par exemple 10 ou 15", default 10 | option 10 (default) / 5 / 15 / 20 | **changed** (5 and 20 added) |
| Players | solo; 2+ in tournament | 1 to 6 | **changed** (max 6, new value) |
| Minimum bet | not given ("bet limits") | 1 | **changed** (new value) |
| Maximum bet | not given ("bet limits") | option 50 (default) / 25 / 100, capped by the stack | **changed** (new value) |
| Decks | "decide" | 6, fresh shuffle each hand | **changed** (new value) |
| Dealer rule | "stands on 17" | stands on all 17 (soft and hard) | |
| Blackjack payout | "3:2 or similar" | 3:2, rounded down to a whole point | |
| Win payout | not given | 1:1 | |
| Split | "if cheap enough" | once, same value, split Aces one card each | **changed** (new value) |
| Double | yes | first two cards, after split too (not split Aces) | |
| Insurance | "optional, decide" | none | **changed** (new value) |
| Rebuy | "sauf recave autorisée" | none | |
| Pause between hands | not given | none (betting is the barrier) | **changed** (new value) |
| Bot thinking delay | not given | 700 to 1,300 ms per decision (99's), 0 under `test` | **changed** (new value) |
| Bot `medium` flat bet | — | 10 (capped by the stack and `maxBet`) | **changed** (new value) |
| Bot `hard` bet sizing | — | rules of section 10.3 | **changed** (new value) |
| Dealer card pace on screen | — | 450 ms per dealer card, 0 with reduced motion (`tempo.ts`) | **changed** (new value) |

## 6. Domain model (`blackjack/domain`)

One aggregate root `Blackjack implements Game`, 99 style: every mutating method `synchronized`, validates first
then mutates, bumps `version()`, returns a small outcome; a refused command never mutates. A package-private
constructor with an explicit shoe, stacks and hand number for tests (`BlackjackBuilder` in the test tree).

| Type | Role |
|------|------|
| `BlackjackOptions` (+ `Hands`, `MaxBet` enums) | typed options, `from` lenient / `parseStrict` strict, `toGameOptions`, `defaults()` |
| `HandValue` (pure record) | `of(List<Card>, boolean fromSplit)`: `total`, `soft`, `blackjack`, `bust`; the only place that counts cards (DTO and bots use it, the client never counts) |
| `DealerRule` (pure) | `mustDraw(HandValue)`: below 17 → true; 17 and above, soft or hard → false |
| `Settlement` (pure) | `net(PlayerHand, DealerHand)` per the table of 3.8; `bestHands(Map<seat, net>)` |
| `PlayerHand` | cards, bet, `doubled`, `fromSplit`, `splitAces`, status `ACTIVE`, `STOOD`, `BUST`, `BLACKJACK`, `WAITING` (second split hand before its card) |
| `SeatState` | stack, bet placed or not, hands (0 to 2), active hand index, eliminated (hand number), forfeited, place |
| `Shoe` | the cards of the current hand, `draw()` |
| `ShoeSource` (port) | `shuffledShoe(GameId, int handNumber)`: 312 cards, the first dealt first; `RandomShoeSource` (6 French decks from the `frenchcards` `CardsService`, shuffled), `ScriptedShoeSource` under `test` (a scripted prefix per hand, the rest of the 312 cards in a fixed order) |
| `Action` | `BET`, `HIT`, `STAND`, `DOUBLE`, `SPLIT` |
| `Phase` | `BETTING`, `PLAYING`, `FINISHED` |
| `BlackjackEvent` (sealed, `domain/event`) | `BetPlaced(seat)`, `HandDealt(handNumber, button, bets, upCard, seatCards)`, `DealerBlackjack(holeCard)`, `CardDrawn(seat, handIndex, card, value)`, `Stood(seat, handIndex)`, `Doubled(seat, handIndex, card)`, `SplitHand(seat, cards)`, `Busted(seat, handIndex)`, `DealerPlayed(holeCard, drawn, value)`, `HandSettled(handNumber, nets, stacks, bestSeats)`, `Eliminated(seat, handNumber)`, `GameFinished(result)`, `Forfeited(seat)` |
| `BlackjackResult` | places (empty in solo), final stacks, reason (`HANDS_PLAYED`, `LAST_STANDING`, `BROKE`, `FORFEIT`) |
| Exceptions (checked `BlackjackException`) | `FinishedGameException`, `WrongPhaseException`, `NotPlayersTurnException`, `AlreadyBetException`, `InvalidBetException` (below 1, above the cap, not a whole number), `ActionNotAllowedException` (double or split not available), `EliminatedSeatException` |

### 6.1 Commands and queries

Commands: `bet(seat, int amount)`, `hit(seat)`, `stand(seat)`, `doubleDown(seat)`, `split(seat)`,
`forfeit(PlayerId)`.

`bet`: phase `BETTING`; live seat; not yet bet; `1 ≤ amount ≤ min(maxBet, stack)`; take the amount from the stack;
when every live seat has bet: deal (3.4), peek (3.5), skip blackjacks, set the current seat to the first seat with
an action, or play the dealer and settle when none has one.

`hit` / `stand` / `doubleDown` / `split`: phase `PLAYING`; the seat on turn; the action in `availableActions`;
apply; when the active hand is finished, move to the seat's next hand, else to the next seat with an action, else
the dealer's turn (3.7) and the settlement (3.8), elimination and end (3.9); else open the next hand's betting
(new shoe, button moved, bets cleared, last hand kept for the DTO).

Queries: `phase()`, `version()`, `handNumber()`, `handsTotal()`, `button()`, `currentSeat()`,
`activeHandIndex()`, `seat(n)` (stack, bet, hands, eliminated, place), `dealerUpCard()`, `dealerCards()` (the hole
card only once shown), `holeCardShown()`, `availableActions(seat)` (with the bet limits during `BETTING`),
`lastHand()` (dealer cards, per-seat nets, best seats), `isSolo()`, `options()`, `result()`.

### 6.2 Worked examples (test contract of the domain chunk)

Seats A, B, C, default options, the shoe scripted; one parameterised test row each.

1. A bets 10, B bets 0 → refused (`InvalidBetException`); B bets 60 with `maxBet = 50` → refused; B bets 50: B's
   stack is 50; C bets: the deal happens.
2. Deal order (A, B, C, dealer, A, B, C, hole): the shoe `A♠ K♣ Q♦ 5♥ 9♠ 7♣ 6♦ 10♥` with the button on A gives A
   `A♠ 9♠`, B `K♣ 7♣`, C `Q♦ 6♦`, dealer up `5♥`, hole `10♥`; with the button on B it gives B `A♠ 9♠`, C `K♣ 7♣`,
   A `Q♦ 6♦` (the test pins this order).
3. A has `A♠ K♠` (blackjack), dealer up `9`: A is skipped; at the settlement a bet of 10 wins 15, a bet of 5 wins
   7.
4. Dealer up `A`, hole `K`: dealer blackjack shown at once; A with blackjack pushes; B and C lose their bets; no
   `PLAYING` phase; the next hand's `BETTING` opens.
5. Dealer up `10`, hole `7`: peek finds nothing; play goes on (the hole card stays hidden in the DTO).
6. B `8♣ 8♦` splits: stack −bet; hand 1 `8♣ 3♥` (11) doubles, gets `K`: 21, stands; hand 2 gets its second card
   only now; refused second split on `8 8` again.
7. C `A♥ A♦` splits: each gets one card and both stand; `A♥ K♣` counts 21, not blackjack: it pays 1:1.
8. A hits to 21: the hand stands by itself; B busts: the turn moves on; when every live hand has busted or has a
   blackjack, the dealer draws nothing.
9. Dealer `6 A` (soft 17) stands; dealer `6 10` (16) draws `5`: 21.
10. A with stack 10 bets 10 and loses: eliminated at the settlement; with B and C left the game goes on; B then
    loses everything: one live player left → `FINISHED` (`LAST_STANDING`), C 1st, B 2nd, A 3rd.
11. Hand 10 of 10 settled: C 140, A 140, B 60 → A and C share 1st, B 3rd (`HANDS_PLAYED`).
12. Solo: A bets 100 and loses → `FINISHED` (`BROKE`), net −100; after 10 hands at 123 → `HANDS_PLAYED`, net +23.
13. Double with a stack smaller than the bet: `DOUBLE` not in `availableActions`, the command refused; same for
    `SPLIT`.
14. B forfeits on its turn: eliminated, bet lost, the turn moves to C; A forfeits during `BETTING` before betting:
    the deal no longer waits for A.
15. Button: hand 1 seat 0, hand 2 seat 1; with seat 1 eliminated, hand 3 starts at seat 2.

## 7. Disconnect, forfeit, rematch

- **Disconnect**: the existing 60 s grace; the table waits for a disconnected player who has not bet or whose turn
  it is (no turn timer, as everywhere else) [D].
- **Forfeit** [A24]: eliminated at once and ranked below every live and eliminated player (several forfeiters: the
  later one higher); a placed bet is lost; its hands leave the table; if it was its turn, the turn moves on; if its
  bet was the last one awaited, the deal happens. A forfeit that leaves one live player in a tournament ends the
  game (`FORFEIT`); in solo it ends the game.
- **Rematch**: `FINISHED`, then `POST /api/blackjack/game/{id}/play-again`; a new game with 100 points each,
  options and bots kept by the session core.

## 8. Presentation

### 8.1 WebSocket (`/app/blackjack/...`, through `SeatCommands`)

| Destination | Payload | Effect |
|-------------|---------|--------|
| `/blackjack/start` | `GameActionPayload` | host starts (game-type check first: "This room is not a Blackjack game") |
| `/blackjack/bet` | `BlackjackBetPayload(gameId, token, amount: int)` | place the bet |
| `/blackjack/hit`, `/stand`, `/double`, `/split` | `GameActionPayload` | act on the active hand (A29) |
| `/presence`, `/forfeit` | existing | unchanged |

`BlackjackEventType`: `START`, `BET`, `DEAL`, `DEALER_BLACKJACK`, `HIT`, `STAND`, `DOUBLE`, `SPLIT`, `BUST`,
`DEALER_PLAY`, `SETTLE`, `ELIMINATED`, `GAME_OVER`. One `SuccessResponse` per human seat through
`SeatStateBroadcaster<Blackjack, BlackjackDto>`; refusals to the sender alone with a code (French translation
design D3: the frontend never renders a server `message`). Codes: `NOT_YOUR_TURN`, `WRONG_PHASE`,
`ALREADY_BET`, `INVALID_BET`, `ACTION_NOT_ALLOWED`, `GAME_FINISHED`.

No pause bean: nothing is scheduled between hands (A19).

### 8.2 REST (`/api/blackjack`) over `SeatGameRest`

`GET /game/{id}?token=`, `POST /game/{id}/join`, `POST /game/{id}/play-again`.

Test seam under `@Profile("test")`: `BlackjackTestShoeController`, `PUT /api/test/blackjack/{gameId}/deal` with
`{ "hands": [["AS","KC",...], ...] }` (one scripted shoe prefix per hand, in the deal order of 3.4, then the hit,
split and dealer cards in draw order) and `DELETE` to clear it (the Kobo/99 pattern; card codes as the 99 seam).

### 8.3 Per-seat DTO (`BlackjackDto.forViewer(game, viewer, names, bots)`)

Public: `version`, `phase`, `handNumber`, `handsTotal`, `solo`, `button`, `currentSeat`, `activeHandIndex`,
`options` (`hands`, `maxBet`, plus the fixed `startingStack` 100 and `minBet` 1), `dealer {upCard, cards (the hole
card only once shown), holeHidden, total (of the shown cards), soft, blackjack, bust}`, per seat `{seat, name, bot?,
level?, stack, betPlaced, bet (null for another seat during BETTING), hands [{cards, total, soft, status, doubled,
fromSplit, net?}], eliminated, forfeited, place?, standing (current rank by stack)}`, `lastHand {handNumber, dealer
{cards, total, bust, blackjack}, seats [{seat, hands [{cards, total, status, net}], net}], bestSeats}` (kept during
the next `BETTING`, A19), `outcome {places, stacks, reason, net (solo)}`.

Private (viewer only): `availableActions`, `betLimits {min, max}` during `BETTING`, `lastBet` (the viewer's
previous bet, for the one-tap "Bet N").

Never in a DTO: the hole card before it is shown, another seat's bet amount during `BETTING`, the shoe order.

Names follow the French translation design D3 (bots and unnamed players sent as `name: null`, the client names
them). Totals and soft flags come from `HandValue`: the client has no rule.

### 8.4 Configuration

`config/BlackjackConfig.java`: `blackjackFactory` (with the `ShoeSource` by profile), `blackjackStateBroadcaster`
(`sessionService::botSeats`), `blackjackGameActions`, `blackjackLifecycleBroadcaster`, the bot coordinator.
`AppConfig` is not edited. Properties `blackjack.bots.delay-min-ms` / `delay-max-ms` in `application.properties`
(700 / 1300), 0 in `application-test.properties`. Shared backend tests per `add-a-game.md` §2.2. The public game
list and the usage statistics need nothing: both key on the game type the session core already carries.

## 9. Frontend

### 9.1 Files (per `game-conventions.md` §4)

- `model/blackjack/BlackjackState.ts` (types of 8.3), `fixtures.ts` (P0), `betting.ts` (pure: clamp a bet to the
  limits, chip increments, the "Bet N" default = last bet clamped, else 10 clamped), `standings.ts` (ordering and
  shared places for the plates and the end screen), `tempo.ts` and `motion.ts` (anchors `BlackjackAnchor`: shoe,
  dealer, each seat's hand slots; plans per event: deal in the order of 3.4, hit, double, split, dealer play).
- `application/BlackjackSession.ts` (`BLACKJACK_SESSION_CONFIG`, `bet(amount)`, `hit()`, `stand()`,
  `doubleDown()`, `split()`), `state/Blackjack.store.ts` (`blackjack-store`, `useSeatRoom`),
  `composables/useBlackjackBootstrap.ts`.
- `components/blackjack/`: `BlackjackTable.vue`, `BlackjackDealer.vue`, `BlackjackSeatPlate.vue` (opponent),
  `BlackjackMySeat.vue`, `BlackjackHand.vue` (cards, total, soft mark, status, double mark, net chip),
  `BlackjackBetPanel.vue`, `BlackjackActionBar.vue`, `BlackjackHandSummary.vue` (last hand: nets, best hand
  badge), `BlackjackStandings.vue` (end screen body), `BlackjackLobbyOptions.vue`.
- `view/blackjack/BlackjackGameScreen.vue`.
- Texts: rules at `blackjack`, screen at `blackjackUi`, options at `options.blackjack`, picker at
  `gamePicker.games.blackjack`; English in `locales/en.ts`, French in `locales/fr/blackjack.ts` (wired in
  `fr/index.ts`), the parity test's allowlist for the leaves equal in both languages (the name "Blackjack", the
  bot label, digits).
- Registry: `define('blackjack', { supportsBots: true, picker: { icon: 'pi pi-wallet', severity: 'success' } })`.

### 9.2 Reuse

`PlayingCard.vue` (card faces), the shared animation barrel `src/animation` (flights from the shoe to a hand, the
dealer reveal; speed button; reduced motion), `model/table/displayName.ts`, the felt and room of the Kobo/99
screens, the shared chrome (`GameTimer`, `RulesPanel`, `GameLobby` + `LobbyGameOptions`, `EndGameOverlay`,
`DisconnectOverlay`, `ForfeitBanner`, `YourTurnIndicator`).

### 9.3 Layout (decided here, A27; no canvas)

Mobile first (375 px), the same order at 1280 px with more room:

1. **Header** (shared chrome): Back, rules, timer, "Hand 3 / 10".
2. **Dealer strip**: the dealer's cards (hole card face down until shown), the total of the shown cards ("Dealer
   17"), "Blackjack" / "Bust" tags.
3. **Opponents**: compact plates in a 2-column grid at 375 px (up to 5 plates, 3 rows), one row at 1280 px. A plate:
   name (bot level), stack, standing (`#1`), bet ("Bet placed" during betting, the amount after the deal), the
   hand(s) as small cards with the total, status tags (Blackjack, Bust, Double, Split), the turn highlight,
   eliminated (greyed, place), disconnect badge. In solo there are no plates.
4. **My seat** (bottom, larger): my stack and standing, my hand or my two hands side by side (the active one
   outlined), totals with a "soft" mark (e.g. "7 / 17"), net chips after the settlement.
5. **Sticky bottom bar** (thumb zone), one of:
   - `BETTING`: chip buttons `+1`, `+5`, `+10`, `+25`, `Max`, `Clear`, and the main button **"Bet N"** prefilled
     with my last bet (first hand: 10), so repeating a bet is one tap; disabled beyond the limits.
   - `PLAYING`, my turn: `Hit`, `Stand`, then `Double` and `Split` only when in `availableActions`; buttons at
     least 48 px high.
   - Otherwise: a status line ("Waiting for Bob", "Dealer plays").
6. **Last hand summary** (between dealer strip and opponents during betting): "Dealer 20 · You +10 · Best hand:
   Alice (+15)".
7. **Lobby options**: the two choices (`hands`, `maxBet`) in the existing `LobbyGameOptions` pattern, and the fixed
   line (100 points, minimum bet 1). A hint under Start in a 1-seat room: "Start alone to play solo against the
   dealer".
8. **End screen** (`EndGameOverlay`): tournament standings with stacks and shared places; solo: the final stack and
   the net ("You finish with 123 points (+23)").

Colours: the existing felt and theme tokens; results use the existing win / loss tones with a text label (never
colour alone). Screen-reader live region: each event read once ("Alice hits: 7, total 18"; "Dealer busts with 24";
"You win 15").

## 10. Bots

Three levels, as in the other games (A25). Against a dealer, each play decision has one best answer (basic
strategy) and a fresh shuffle each hand removes card counting; what separates a strong tournament player is the
**bet sizing and the risk taken relative to the other stacks** near the end. Hence:

- **Domain** (`blackjack/domain/bot`, pure): `BlackjackBotView.forSeat(game, seat)` (exactly what the seat's DTO
  shows: own hands, the dealer up card, every public stack, bets after the deal, hands left; a parity test pins it),
  `BlackjackBotStrategy` (port: `decideBet(view) → int`, `decidePlay(view) → Action`), `BlackjackBotLevel`,
  `BasicStrategy` (pure table).
- Candidates always come from the domain's `availableActions` and bet limits: a bot never sends an illegal command.

### 10.1 `easy` (random)

A uniformly random bet in `[1, min(maxBet, stack)]`; a uniformly random action among the available ones.

### 10.2 `medium` (basic strategy, flat bet)

Bet 10 (capped). Play the basic strategy for 6 decks, S17, double after split, no surrender, dealer peek
(Wizard of Odds, section 14.2), with "double if allowed, else hit" (and "else stand" for soft 18):

Hard totals (rows) against the dealer up card (2 to 10, A):

| Hand | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | A |
|------|---|---|---|---|---|---|---|---|----|---|
| 8 or less | H | H | H | H | H | H | H | H | H | H |
| 9 | H | D | D | D | D | H | H | H | H | H |
| 10 | D | D | D | D | D | D | D | D | H | H |
| 11 | D | D | D | D | D | D | D | D | D | H |
| 12 | H | H | S | S | S | H | H | H | H | H |
| 13 to 16 | S | S | S | S | S | H | H | H | H | H |
| 17+ | S | S | S | S | S | S | S | S | S | S |

Soft totals:

| Hand | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | A |
|------|---|---|---|---|---|---|---|---|----|---|
| A,2 / A,3 | H | H | H | D | D | H | H | H | H | H |
| A,4 / A,5 | H | H | D | D | D | H | H | H | H | H |
| A,6 | H | D | D | D | D | H | H | H | H | H |
| A,7 | S | Ds | Ds | Ds | Ds | S | S | H | H | H |
| A,8+ | S | S | S | S | S | S | S | S | S | S |

Pairs (split when `P`, else play the hard or soft total):

| Pair | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | A |
|------|---|---|---|---|---|---|---|---|----|---|
| A,A / 8,8 | P | P | P | P | P | P | P | P | P | P |
| 2,2 / 3,3 | P | P | P | P | P | P | H | H | H | H |
| 4,4 | H | H | H | P | P | H | H | H | H | H |
| 5,5 | as hard 10 | | | | | | | | | |
| 6,6 | P | P | P | P | P | H | H | H | H | H |
| 7,7 | P | P | P | P | P | P | H | H | H | H |
| 9,9 | P | P | P | P | P | S | P | P | S | S |
| 10,10 | S | S | S | S | S | S | S | S | S | S |

(D = double if allowed, else hit; Ds = double if allowed, else stand.)

### 10.3 `hard` (basic strategy and tournament betting)

Plays the same basic strategy, except on the **last hand** (below). Bets with the stacks in view, with `lead` = my
stack minus the best other live stack (negative when behind), `left` = hands left including this one, `cap` =
`min(maxBet, stack)`:

| Situation | Bet |
|-----------|-----|
| Solo | the `medium` flat bet (no opponent to play against) |
| Leading by more than `cap × left` (nobody can catch up) | 1 |
| Leading, `left ≤ 2` | `secondCap` = `min(maxBet, second's stack)`; if `lead > 2 × secondCap`, 1 (the second cannot pass even if it wins and I lose); else `min(cap, secondCap)` (match the second's largest bet: if both win or both lose, the lead holds) |
| Behind, `left ≤ 3` | `cap` (all the risk now: a flat bet cannot catch up) |
| Behind by more than `maxBet × (left − 1)` | `cap` |
| Otherwise | 10, plus 5 per 25 points behind (capped) |

Last hand, still behind after the bets are shown: if only a **doubled** win overtakes the leader, double any
first two cards totalling 11 or less and every soft total (an aggressive deviation); if even a win does not
overtake, play to maximise the chance of a win (hit hard 12 to 16 against any dealer card when standing cannot
reach first place). Leading on the last hand: stand on any 12 or more when the dealer shows 2 to 6 (protect the
lead).

The exact rules are pinned by hand-written views in the bots chunk; the numbers are constants in
`BlackjackBotTuning`.

### 10.4 Benchmark and coordinator

- **Strength benchmark** (opt-in: `mvn test -Dtest=BlackjackBotStrengthBenchmark -Dblackjack.bench=true`): 1,000
  tournaments of 4 seats, 10 hands, default options, seats rotated; a fair seat wins 25%. Expected: `hard` wins
  clearly more than 25% against three `medium`; `medium` beats `easy`. The results are written into this section
  by the bots chunk (as 99 did). If `hard` does not beat `medium`, the bots chunk tunes `BlackjackBotTuning` and
  says so in its PR.
- **Measured** (bots chunk, 1,000 tournaments of 4 seats, 10 hands, default options, seats rotated, on the test-tree
  simulator of the rules because the aggregate was not yet on `main`; a tie for first splits the win; the standard
  error is about 1.4 points): 1 `hard` vs 3 `medium`: `hard` 36.5%, `medium` 21.2% per seat; 1 `medium` vs 3
  `easy`: `medium` 66.9%, `easy` 11.1%; `hard` + `medium` + 2 `easy`: 43.5% / 39.8% / 8.4%; 4 `medium` (control):
  25.0%. No tuning of `BlackjackBotTuning` was needed. Re-run on the real aggregate (entry chunk, opt-in `BlackjackBotAggregateBenchmark`, same scenarios, bots deciding from `BlackjackBotView.forSeat`): 1 `hard` vs 3 `medium`: `hard` 36.2%, `medium` 21.3%; 1 `medium` vs 3 `easy`: `medium` 63.0%, `easy` 12.3%; `hard` + `medium` + 2 `easy`: 42.4% / 40.9% / 8.4%; 4 `medium`: 25.0%. Same ordering and magnitudes as the simulator, so the figures above stand.
- **Coordinator** (`presentation/bot/BlackjackBotCoordinator`, the 99 pattern): after each broadcast, during
  `BETTING` one task per bot seat that has not bet, during `PLAYING` one task for the bot on turn; each after
  `ThinkingDelay` (0 under `test`), keyed on `version()` and dropped when stale; a failing strategy falls back to a
  bet of 1 and to `STAND`.

## 11. Rules panel text (draft for `blackjack: RulesMessages`, English; French by the locales chunk)

1. **Goal.** Solo: finish the hands with as many points as you can. Tournament: finish with more points than
   everyone else. Everyone plays against the dealer, not against each other.
2. **Stacks and bets.** Each player starts with 100 points. Before each hand, every player bets from 1 point to the
   maximum (25, 50 or 100, set by the host), never more than they have. Bets stay hidden until the cards are dealt.
3. **Card values.** 2 to 10 count their value, J, Q and K count 10, an Ace counts 11 or 1. A hand with an Ace
   counted as 11 is "soft". An Ace and a 10-value card as your first two cards is a Blackjack.
4. **Your turn.** Hit (take a card), Stand, Double (your first two cards: double the bet, take exactly one card),
   Split (two cards of the same value: play them as two hands, each with its own bet; split Aces get one card each).
   Over 21, you bust and lose your bet. At 21, you stand automatically.
5. **The dealer.** The dealer has one card face up and one face down. With an Ace or a 10 showing, the dealer checks
   for Blackjack first. After the players, the dealer draws until 17 or more and stands on every 17.
6. **Payouts.** Blackjack pays 3 to 2 (rounded down), a win pays 1 to 1, an equal total is a push (you keep your
   bet). No insurance, no surrender.
7. **The end.** After the last hand, or when you have no points left. In a tournament, a player with 0 points is
   out, and the game ends early when only one player is left. Equal stacks share the place.

## 12. Tests

- Strict TDD at domain level: `HandValueTest` (every rank, soft totals, several Aces, blackjack vs split 21, bust),
  `DealerRuleTest`, `SettlementTest` (every row of 3.8, the rounding of 3:2), the worked examples of 6.2 as
  parameterised rows in `BlackjackDealTest`, `BlackjackPlayTest`, `BlackjackSplitDoubleTest`,
  `BlackjackEndTest` (ranking with shared places, early end, solo outcomes), `BlackjackForfeitTest`,
  `BlackjackOptionsTest`, `BlackjackFactoryTest` (type, bounds 1 to 6, strict options); version bumps on every
  accepted command, a refused command never mutates.
- Bots: every level only sends legal bets and actions on 1,000 random views; `BasicStrategyTest` (every cell of
  the three tables of 10.2); `hard` picks the documented bet on hand-written views; opt-in benchmark.
- Presentation: `BlackjackDtoTest` (hole card hidden until shown, other bets hidden during `BETTING`,
  `availableActions` and `betLimits` for the viewer only), bot view parity, `BlackjackGameActionsTest`,
  `BlackjackRestAndLifecycleTest`, `BlackjackWebSocketControllerTest`, `BlackjackWebSocketControllerIT` (STOMP:
  create, join, start, bets, a hit, a refused out-of-turn action answered to the sender alone, a settlement, the
  end), `BlackjackFixedShoeIT` and `BlackjackFixedShoeAbsentInProductionIT`, coordinator with a scheduler stub.
- Frontend: Vitest per file (model, session, store, bootstrap, components, screen), French parity (FAILS without
  the French texts).
- **Cypress: nominal only, one spec**: `cypress/specs/blackjack-game.cy.ts`, written in the entry chunk and added
  to `cypress/smoke-specs.json`:
  1. Tournament: a host creates a room with `maxBet = 100`, a scripted second player
     (`joinAsScriptedPlayer(gameId, name, 'blackjack')`) joins, the shoe is scripted, Start; both bet (the host
     with the chip buttons and "Bet N", the scripted player all-in 100); the host hits once and stands; the
     scripted player busts; the dealer plays; the summary shows the nets; the scripted player is eliminated and
     the end screen names the host 1st.
  2. Solo: the host starts alone, bets 100 with `Max`, the scripted shoe gives the dealer a blackjack: the end
     screen shows the final stack 0 and the net −100.
  No lobby-only spec, no bot spec, no split spec (Vitest and backend tests cover them); the shared picker specs
  get the new game in their lists.

## 13. Risks

| Risk | Handling |
|------|----------|
| A slow human blocks the betting of 5 others | the bet panel is one tap; the presence grace and forfeit already exist; a betting timer can come later |
| `hard` barely beats `medium` (tournament edges are small) | the benchmark decides; tuning constants; the levels still differ visibly (random vs sensible) |
| Two split hands on a 375 px plate | small cards overlap within a hand; my seat shows both hands side by side, the opponent plates stack them |
| French texts longer than English on the action bar | `length.test.ts` lists them; short French verbs chosen (Carte, Rester, Doubler, Séparer) |
| `minPlayers = 1` is new for the session core | verified: `startGame` compares claimed seats to `minPlayers`; the lobby computes "players needed" from it; C3 adds an IT that starts a 1-seat room |

## 14. Sources

### 14.1 The owner's requirement (verbatim)

> Au Blackjack traditionnel, les joueurs ne s'affrontent pas entre eux, mais jouent tous contre la banque (le
> croupier). Cependant, il est possible d'adapter les règles pour créer une compétition directe entre les joueurs :
> partie « tous contre tous ». Dans cette version, un joueur (ou un croupier neutre) distribue les cartes, mais
> l'objectif n'est plus seulement de battre le croupier : avoir un meilleur score que les autres joueurs sans
> dépasser 21, ou éliminer ses adversaires. Mode « Tournoi éliminatoire » : dotation identique pour chaque joueur
> (ici 100 points, PAS 1000) ; un nombre fixe de mains est joué (par exemple 10 ou 15) ; un croupier tourne à chaque
> manche, ou un joueur fait office de banque à tour de rôle ; à la fin de chaque main, on compare les mains de
> chaque joueur à celle de la banque et entre elles ; le vainqueur est celui qui a le plus de jetons à la fin du
> nombre de manches ; un joueur tombé à 0 est éliminé (sauf recave autorisée).

Plus the brief: a solo mode against an automated dealer (house rules fixed and documented) with the 100-point
stake; a multiplayer tournament (N hands, default 10, elimination at 0, winner = most chips); bots filling seats;
lobby options (hands, bet limits); a fast betting UI on a phone.

### 14.2 Rules references (fetched 2026-10-28)

- pagat.com, "Blackjack" (https://pagat.com/banking/blackjack.html): shoe of 1 to 8 decks, deal order (players,
  dealer up card, players, hole card), peek on an Ace or 10, dealer draws to 17 with a soft-17 variant, 3:2
  blackjack, doubling, splitting (same rank or any 10-values, split Aces one card, split 21 not a blackjack),
  insurance at 2:1 (advised against), late surrender optional. → A9 to A16.
- pagat.com, "Tournament blackjack" (https://pagat.com/banking/tournament_blackjack.html): equal starting chips, a
  fixed number of hands, minimum and maximum bets, the starting position passes to the left each hand, the most
  chips at the end wins; some formats hide one bet. → A5, A7, A8, A17, A18.
- Wikipedia, "Elimination Blackjack" (https://en.wikipedia.org/wiki/Elimination_Blackjack): eliminations of the
  lowest stacks at fixed hands (not used, 3.10).
- Wizard of Odds, basic strategy for 4 to 8 decks (https://wizardofodds.com/games/blackjack/strategy/4-decks/),
  dealer stands on soft 17, double after split, peek; surrender plays replaced by hit (no surrender). → the tables
  of 10.2.

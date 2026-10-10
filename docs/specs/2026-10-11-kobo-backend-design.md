# Kobo backend — design

Scope: the backend of a new game, Kobo (rules source: http://animahsite.free.fr/index.php?page=kobo,
validated with the product owner). Frontend and bots are out of scope. Items marked **[H]** are
hypotheses of this spec that the rules text does not settle; they are collected in "Open questions".

## Goal

Add Kobo as a third game next to Bataille Corse and Bullshit, hosted by the existing session core
(rooms, seats, tokens, lobby, presence, forfeit, play-again), with the rules living in a pure
`kobo/domain` aggregate. 2 to 6 players, 52 cards, played in rounds until a player's total exceeds
100. Kobo mixes turn-based play with a real-time race (anyone may discard a matching card at any
time), which is handled like Bataille Corse's slap: the server processes messages one at a time and
the first one processed wins.

## Rules

### Cards and points

52 cards (`DeckType.FRENCH`, no jokers). Points: Ace 1, 2 to 10 face value, Jack 11, Queen 12,
black King 13, red King 0 (hearts and diamonds are red). The point table is a domain concept
(`CardPoints`), not read from `FrenchRank.getValue()` (which has no colour).

### Round set-up

Shuffle, deal 4 face-down cards to each player, laid out as a square: cards 1 and 2 at the bottom,
3 and 4 at the top. The rest is the draw pile; the discard pile starts empty **[H1]**. Each player
looks at their cards 3 and 4 (the server sends those two values to that player only; remembering
them is the client's job). Play starts when every player has said "ready" (see Memorisation
barrier) **[H10]**. The first player of round 1 is seat 0, then the start rotates to the next
remaining seat each round **[H16]**.

### A turn

1. The player draws a card and sees it alone.
2. Then exactly one of:
   - swap it with one of their own cards (that card goes face up on the discard pile; the drawn card
     takes its place face down). No power applies after a swap;
   - discard it directly. Its power may then be used (always optional):

| Card | Power |
|------|-------|
| Ace to 6 | none |
| 7, 8 | look at one of your own cards |
| 9, 10 | look at one card of another player |
| Jack, Queen | swap one of your cards with one card of another player, blind |
| King (black or red) | look at one card of another player, then optionally swap it with one of your own |

3. The turn is over only when the player ends it (`END_TURN`), or announces Kobo instead (see below).

### Matching discard (the race)

At any time, including out of turn and while another player's power is pending, any player may try
to put a card of any player's tableau (their own or another's) on the discard pile. The server does
not check what the player "knows". It only compares the **rank** of the targeted card with the rank
of the card on top of the discard pile (colour is irrelevant: red and black kings answer each other).

- Same rank, own card: the card goes on the discard pile; the player has one card less.
- Same rank, another player's card: the card goes on the discard pile and the acting player gives
  one of their own cards (their choice, sent with the same message) to the targeted player, face
  down, into the slot just emptied. If the acting player has no card left, nothing is given and the
  slot stays empty **[H17]**.
- Different rank: the acting player receives one extra card from the draw pile, face down, as a
  penalty.
- A player may drop to 0 cards (0 points); they can then announce Kobo like anyone else.

### End of a round

After playing their turn, a player who thinks they have the fewest points announces Kobo instead of
ending the turn. They do not play again. Each other player plays exactly one more turn, then all
cards are revealed and counted.

- The announcer has the fewest points (a tie with the lowest counts): they score 0 and give 10
  points to another player of their choice **[H7]**; the others score the value of their hand.
- Otherwise the announcer scores their hand plus 20; the others score their hand.

Order of computation, always: (1) round points, (2) the 10 given points, (3) every player whose
total is exactly 75 or exactly 100 drops to 50. Points accumulate across rounds.

### End of the game

When, after a round, a player's total is strictly above 100. The winner is the player with the
fewest total points; players tied at the lowest total all win **[H8]**. A rematch is then possible
the same way as in Bullshit.

### Empty draw pile

When a card must be drawn and the pile is empty, the discard pile except its top card is shuffled
to become the draw pile **[H2]**. If there is still nothing to draw (only the top card exists), the
draw yields nothing: a turn's draw is skipped and the turn goes straight to its end step, and a
penalty is not given **[H14]**.

## Existing bricks reused

| Need | Reused brick |
|------|--------------|
| Rooms, seats, tokens, host, lobby view, play-again | `SessionService.createRoom/joinRoom/startGame/playAgain`, `SessionGame`, `LobbyView` (game-agnostic, nothing to add) |
| Game plug-in | `Game` / `GameFactory` / `GameOptions` from the shared kernel `game`; `GameFactories` list in `AppConfig` |
| Private channel per seat | `GameMessagingService.sendToSeat` (token-addressed topic) and `SeatSubscriptionInterceptor` |
| Lobby events | `LobbyBroadcaster` |
| Disconnect, 60 s grace, forfeit, rematch departure | `PresenceService`, `LifecycleController` (`/presence`, `/forfeit`), `GameLifecycleBroadcaster` port (a `KoboLifecycleBroadcaster` implements it) |
| Cards | FrenchCards `Card`, `FrenchRank`, `FrenchSuit` (`getColor()`), `CardsService.createDeck(DeckType.FRENCH, ...)`, `Deck` (shuffle, seed for tests) |
| Use-case shape | `BullshitGameActions` (domain call, `touch`, one broadcast), `BullshitStateBroadcaster` (one DTO per seat), `BullshitRestController` (state rehydration, join, play-again), `BullshitWebSocketController` (create, start) |
| Race semantics | Bataille Corse's slap: first message processed wins; Bullshit's `synchronized` aggregate and `version()` counter |

Nothing in `sessionmanagement` changes: the session core never learns about Kobo, and `kobo` knows
neither sessions nor tokens nor transport.

## Domain model (`kobo/domain`)

One aggregate root, `Kobo implements Game`, in the style of `Bullshit`: every mutating method is
`synchronized`, validates, mutates, bumps `version`, and returns a small outcome record. The
aggregate owns the whole match (rounds and totals), not a single round, because rematch, forfeit and
the end-of-game condition span rounds. A package-private constructor takes explicit piles for tests.

### Tableau and slots

Each player owns a `Tableau`: an ordered list of slots with **stable positions**. Slot 0 and 1 are
the bottom row, 2 and 3 the top row, further slots (penalties) are appended. A slot holds a card or
is empty; emptied slots are never renumbered or reused, except by a card given in a matching
discard on another player's card (it lands in the slot just emptied). Every slot has a `revision`
counter, incremented whenever the card in it changes (swap, gift, removal, replacement). Two roles:

- every command addresses cards as `SlotRef(seat, slot)`, so the server never needs to know what a
  player remembers and never trusts a card value sent by a client;
- clients key their memory by `(seat, slot, revision)`: a change of revision means "forget what you
  knew", which is exactly what makes the memory game fair without the server storing memory.

### Phases

`Phase` is an enum on the aggregate; `currentSeat` is the seat whose turn it is.

| Phase | Meaning | Leaves with |
|-------|---------|-------------|
| `MEMORISING` | cards dealt, players look at slots 2 and 3 | `READY` from every remaining player |
| `DRAW` | current player must draw | `DRAW` |
| `DECISION` | current player holds the drawn card | `SWAP_DRAWN` or `DISCARD_DRAWN` |
| `POWER` | direct discard of a 7-Queen, power pending (`Power`: `PEEK_OWN`, `PEEK_OTHER`, `BLIND_SWAP`; or king step 1) | the power command or `SKIP_POWER` |
| `KING_DECISION` | king peeked at a card; may swap it with one of their own | `KING_SWAP` or `SKIP_POWER` |
| `TURN_END` | turn played; may end it or announce Kobo | `END_TURN` or `ANNOUNCE_KOBO` |
| `GIFT` | round revealed, announcer won and must give 10 points | `GIVE_TEN` |
| `ROUND_OVER` | round scored and shown, waiting to continue | `READY` from every remaining player |
| `FINISHED` | some total is above 100 | rematch (outside the aggregate) |

Cards with no power (Ace to 6) and swaps go from `DECISION` straight to `TURN_END`. A power that has
no legal target (for example peek another player's card when nobody else has one) leaves the player
free to `SKIP_POWER`; the server never auto-skips.

### Commands (aggregate API)

`ready(seat)`, `draw(seat)`, `swapDrawn(seat, slot)`, `discardDrawn(seat)`, `skipPower(seat)`,
`peek(seat, SlotRef)` (own card for 7/8, another player's for 9/10 and the king),
`blindSwap(seat, ownSlot, SlotRef)`, `kingSwap(seat, ownSlot)`, `endTurn(seat)`,
`announceKobo(seat)`, `giveTen(seat, target)`, `matchDiscard(seat, SlotRef, Integer giveSlot)`,
`forfeit(PlayerId)` (the `Game` contract). Plus queries: `phase()`, `currentSeat()`, `version()`,
`round()`, `drawPileSize()`, `discardTop()`, `tableau(seat)`, `totals()`, `lastRoundResult()`,
`result()`, `availableActions(seat)`, `privateView(seat)`.

Rules enforced in the domain, never in controllers or the DTO: turn ownership, phase, slot
occupancy, ownership of targets (own vs other), power eligibility by rank, the matching rule, the
scoring, the 75/100 reset, the end condition, the pile reshuffle.

### Value objects and rules

- `CardPoints.of(Card)`: the point table above. `Power.of(Card)`: the table of powers by rank.
- `SlotRef(PlayerId seat, int slot)`; `Tableau` / `Slot`.
- `RoundScoring` (pure function, no state): from the hands, the announcer (or none) and the gift
  target, returns per-player round points, applies the gift and the 75/100 rule on running totals.
- `PrivateReveal(SlotRef, Card, revision)`: what a seat has just looked at.
- `RoundResult`: per player hand value, round points, gift (from, to), totals before/after, reset
  flags, announcer verdict. `KoboResult`: winners (list) or ongoing, with a reason (`SCORE` or
  `FORFEIT`).

### Outcomes (domain events returned by commands)

Sealed `KoboEvent`, mapped to WebSocket events by the presentation layer, so the domain stays
transport-free: `RoundStarted`, `Ready`, `Drawn(seat)`, `DrawSkipped`, `Swapped(seat, slot, revision)`,
`DiscardedDrawn(seat, card)`, `Peeked(seat, SlotRef)` (no value), `BlindSwapped(seat, ownRef, otherRef)`,
`KingSwapped(...)`, `PowerSkipped`, `TurnEnded`, `KoboAnnounced(seat)`, `MatchSucceeded(actor, target,
card, given)`, `MatchPenalised(actor, drawnPenalty)`, `RoundScored(RoundResult)`, `TenGiven(from, to)`,
`GameFinished(KoboResult)`, `PlayerForfeited(seat)`.

### Exceptions

All extend a `KoboException` (checked, like Bullshit's): `FinishedGameException`,
`NotPlayersTurnException`, `WrongPhaseException(expected, actual)`, `InvalidSlotException`
(unknown seat/slot, or own vs other violated), `EmptySlotException`, `NothingToMatchException`
(empty discard pile), `InvalidGiveSlotException`, `StaleSlotException`, `InvalidGiftTargetException`,
`AlreadyReadyException`. A rejected command never mutates the game and never bumps `version`.

## Turn flow in detail

- `draw`: current player in `DRAW`. Takes the top of the draw pile (reshuffling if empty, see
  above), keeps it as `drawnCard` (private to the player), phase `DECISION`.
- `swapDrawn(slot)`: slot must be occupied (a drawn card cannot fill an empty slot **[H9]**). The old
  card is put on the discard pile (public). Slot revision changes. Phase `TURN_END`.
- `discardDrawn`: the card is put on the discard pile (public). Power by rank: none, then `TURN_END`;
  otherwise `POWER`.
- Power commands are valid only in `POWER` with the matching `Power`, then `TURN_END`:
  `peek` (own slot for 7/8, another seat for 9/10), `blindSwap(ownSlot, other)` (both occupied, other
  seat is not self; both revisions change), king: `peek(other)` in `POWER` then `KING_DECISION`,
  where `kingSwap(ownSlot)` swaps the peeked card with an own occupied slot, or `skipPower` keeps it.
  The peek result is stored as a `PrivateReveal` for that seat only. If the peeked slot is emptied
  or changed (by the race) before the king decides, `kingSwap` is rejected: it compares the slot
  revision recorded at peek time and throws `EmptySlotException` (slot emptied) or
  `StaleSlotException` (card replaced) when it moved; the king may still `skipPower`.
- `endTurn`: allowed in `TURN_END` for the current player. Moves to the next remaining seat in
  `DRAW`. In final turns (after a Kobo) removes the player from the pending list and, when the list
  is empty, scores the round.
- `announceKobo`: only the current player, only in `TURN_END`, only once per round, not in final
  turns. The announcement during a pending decision or power is rejected as `WrongPhaseException`:
  the player must first resolve it or `SKIP_POWER`, then announce **[H6]**. Effect: the announcer
  is stored, the other remaining seats are queued in seat order after the announcer, the turn passes
  to the first of them (`DRAW`).
- Round resolution (queue empty after the last `endTurn`): all cards revealed (the DTO flags the
  round as revealed, phase `GIFT` or `ROUND_OVER`). If the announcer won, phase `GIFT`: only the
  announcer may `giveTen(target)` (target is another remaining seat) **[H7]**; then the round is
  finalised. Otherwise it is finalised at once. Finalising applies `RoundScoring`, stores the
  `RoundResult`, then either `FINISHED` (a total above 100) or `ROUND_OVER`.
- `ROUND_OVER` -> new round: when every remaining player is `READY`, the aggregate starts the next
  round (fresh 52 cards, new deal, rotated start seat, phase `MEMORISING`). Totals are kept.

## Memorisation and barriers

Two barriers share one command, `ready`: at `MEMORISING` (the player has looked at slots 2 and 3)
and at `ROUND_OVER` (the player has seen the scores). The aggregate keeps a `readySeats` set (public:
it lets clients show who is still looking), cleared when the barrier opens. There is no timeout:
absent players are removed by the existing 60 s disconnect forfeit, and a removal re-evaluates the
barrier. A remaining player cannot be `ready` twice (`AlreadyReadyException`, harmless).

## Matching discard in detail

`matchDiscard(actor, target, giveSlot)`, valid in any phase of a live round except `MEMORISING`,
`GIFT`, `ROUND_OVER`, `FINISHED`, whoever the actor is (current player or not). Order of checks, all
before any mutation unless stated:

1. game not finished, phase allows it, `actor` is a remaining player;
2. `target.seat` is a remaining player and `target.slot` exists, otherwise `InvalidSlotException`;
3. the target slot is occupied, otherwise `EmptySlotException` and **no penalty** **[H3]**: the
   usual cause is a lost race (someone matched that card a moment earlier), not a mistake;
4. the discard pile has a top card, otherwise `NothingToMatchException`, no penalty **[H1]**;
5. rank of the target card vs rank of the top card, at processing time **[H4]**;
6. rank differs: the actor draws one card from the draw pile (reshuffle rule applies) and appends it
   to their own tableau face down (new slot, never reusing an empty one **[H12]**). Outcome `MatchPenalised`. The targeted card is not
   revealed to anyone (only "the attempt failed" is public);
7. rank equal, own card: the card is removed from the slot (revision++), put on the discard pile
   (it becomes the new, public top). `MatchSucceeded`;
8. rank equal, another player's card: `giveSlot` must be an occupied slot of the actor, otherwise
   `InvalidGiveSlotException` before mutation (and no penalty: the rank was right). If the actor has
   no card at all, `giveSlot` is ignored and the slot stays empty. The target card goes on the
   discard pile; the given card moves into the target slot of the targeted player (revision++ on both
   slots touched).

A match does not change the phase or the turn. If it removes or changes a slot a pending command
refers to, that command fails cleanly with `EmptySlotException` or the revision check and the player
chooses again.

## What each player sees (per-seat views)

`KoboDto.forViewer(game, viewer, names)` is the only projection (like `BullshitDto.forViewer`), sent
on the seat's private topic for every broadcast and for REST rehydration. Public information is
identical for all seats; private information is only in the viewer's own DTO.

Public, same for everyone:
- `version`, `round`, `phase`, `currentSeat`, `readySeats`, `announcer`, `finalTurnsRemaining`
  (seats still to play after a Kobo), `drawPileSize`;
- discard pile: top card (full card), size, and the card put on top by each action (events);
- per player: seat, name, total points, and a slot list `[{slot, occupied, revision}]`. Never a card
  value of a face-down slot;
- after the round is revealed (`GIFT`, `ROUND_OVER`, `FINISHED`): every slot also carries its card,
  and the `RoundResult` is present;
- `outcome`: ongoing or finished with winners.

Private, only in the viewer's DTO:
- `startingCards`: the viewer's slots 2 and 3 with values, present while the round has not seen a
  first draw (so during `MEMORISING` and until the first player draws) **[H11]**, so a reload or a
  reconnection before play still shows them;
- `drawnCard`: present only for the current player in `DECISION` (others get `hasDrawn: true` only);
- `reveal`: the last `PrivateReveal` of the viewer (peek, king peek), cleared when the viewer's next
  command is accepted or their turn ends. It is state, not a message, so a reconnection mid-power
  recovers it;
- `availableActions` (enum names, like `Bullshit`): `READY`, `DRAW`, `SWAP_DRAWN`, `DISCARD_DRAWN`,
  `PEEK`, `BLIND_SWAP`, `KING_SWAP`, `SKIP_POWER`, `END_TURN`, `ANNOUNCE_KOBO`, `GIVE_TEN`, and
  `MATCH_DISCARD` whenever it is currently legal.

Public events never leak a value: a peek is announced as "seat A looked at (seat B, slot s)", a
blind swap as "seat A swapped (A, i) and (B, j)" (both revisions move), a failed match as "seat A
failed a match" (the targeted card stays hidden), a successful match shows the card because it is
now the public top of the discard pile. The drawn card is revealed publicly only when it is
discarded or swapped out (it then is the top of the discard pile).

## Concurrency

- Every state change goes through a `synchronized` method of the aggregate: one monitor, one message
  at a time. The first message processed wins; STOMP's inbound thread pool decides arrival order, as
  for Bataille Corse's `slap`. No timestamp or client ordering is trusted.
- Losing a race is not an error for the loser: matching an already-removed card is rejected without
  penalty (step 3), and a stale pending power fails cleanly. Matching with a different rank, judged
  at processing time, is penalised even if the client saw a matching top when it clicked **[H4]**.
- Broadcasts must keep the order of state changes. `KoboGameActions` does the domain call, `touch`
  and the broadcast inside `synchronized (game)` (the monitor is reentrant), so two seats' events are
  never interleaved out of order, and each DTO carries `version` so a client can drop a stale one.
- A rejected command answers only the sender, with an `ErrorResponse` carrying that seat's current
  state, as in Bullshit; nothing is broadcast.

## Session, lobby, WebSocket and REST integration

### Registration

- `KoboFactory implements GameFactory`: `gameType() = "kobo"`, `minPlayers() = 2`, `maxPlayers() = 6`,
  `create(id, nbPlayers)` and the options overload both build a `Kobo`. A bean in `AppConfig` and an
  entry in the `GameFactories` list. This is the only change in the shared wiring.
- Player ids are `0..n-1` like Bullshit (the session core deals to contiguous seats).

### WebSocket (prefix `/app/kobo/...`, all token-authenticated)

Kobo uses its own prefix for every game command, since Bullshit already owns `/discard`.

| Destination | Payload | Effect |
|-------------|---------|--------|
| `/kobo/create` | `KoboCreatePayload(name)` | create room, answered on `/topic/game` with `{gameId, gameType, tokens{0: hostToken}}` |
| `/kobo/start` | `GameActionPayload` | host starts; broadcast `START` + first states |
| `/kobo/ready` | `GameActionPayload` | memorisation / next-round barrier |
| `/kobo/draw`, `/kobo/discardDrawn`, `/kobo/skipPower`, `/kobo/endTurn`, `/kobo/announce` | `GameActionPayload` | turn commands |
| `/kobo/swapDrawn` | `KoboSlotPayload(gameId, token, slot)` | swap the drawn card with an own slot |
| `/kobo/peek` | `KoboTargetPayload(gameId, token, seat, slot)` | power peek |
| `/kobo/blindSwap` | `KoboSwapPayload(gameId, token, ownSlot, seat, slot)` | jack / queen |
| `/kobo/kingSwap` | `KoboSlotPayload` | king swap with own slot |
| `/kobo/match` | `KoboMatchPayload(gameId, token, seat, slot, giveSlot?)` | matching discard (the race) |
| `/kobo/giveTen` | `KoboGiftPayload(gameId, token, seat)` | announcer's gift |
| `/presence`, `/forfeit` | existing | unchanged |

Event types (`KoboEventType`): `START`, `ROUND_START`, `READY`, `DRAW`, `SWAP`, `DISCARD_DRAWN`,
`PEEK`, `BLIND_SWAP`, `KING_SWAP`, `SKIP_POWER`, `END_TURN`, `KOBO`, `MATCH`, `ROUND_END`,
`GIVE_TEN`, `GAME_OVER`. Each is sent as a `SuccessResponse(eventType, eventData, message, state)`
to every human seat (one DTO per seat). Lifecycle events reuse `LifecycleEventType`
(`OPPONENT_DISCONNECTED`, `OPPONENT_RECONNECTED`, `FORFEIT`, `JOIN`).

Handlers resolve the seat from the token, call `KoboGameActions`, and on any exception reply only to
that seat with `ErrorResponse(eventType, message, state)`.

### REST (`/api/kobo`)

Copy of `BullshitRestController` restricted to `gameType == "kobo"`: `GET /game/{id}?token=` (the
viewer's `KoboDto`, or the `LobbyView` before the start), `POST /game/{id}/join`,
`POST /game/{id}/play-again`.

### Lifecycle

`KoboLifecycleBroadcaster implements GameLifecycleBroadcaster` (supports `Kobo`; `disconnected`,
`reconnected`, `forfeited` broadcast the state with the existing lifecycle events). Without it
`GameLifecycleBroadcasters.broadcasterFor` throws for Kobo.

## Options

None in v1: `KoboFactory` ignores `GameOptions` (the `GameFactory` default does it). The constants
(point table, 4 cards, thresholds 75/100/50, end above 100, gift 10, penalty 20) live in one
`KoboRules` class so a future `KoboOptions` (target score, extra reset values) is a small change in
the style of `BullshitOptions`.

## Disconnection, forfeit, rematch

- Disconnect: `PresenceService` starts the 60 s grace and calls `broadcasterFor(game).disconnected`.
  Nothing is Kobo-specific; the table waits for the player during the grace.
- Forfeit (timer or `/forfeit`): `Kobo.forfeit(seat)` removes the player. Their cards go under the
  draw pile **[H13]**. If they were the current player, any held drawn card also goes under the draw
  pile and the turn passes to the next remaining seat in `DRAW`; they are removed from the final-turn
  queue and from the ready set (which may open a barrier or score the round). If they were the
  announcer, the Kobo is void: pending players still finish their final turn and the round is
  scored with hand values only, no bonus, no penalty, no gift. If they owed a gift, none is given.
  A forfeiter's total is dropped from the standings. One player left: `FINISHED`, that player wins,
  reason `FORFEIT`. The session core keeps the forfeiter's seat claimed but absent from
  `getPlayerIds()`, as Bullshit does.
- Rematch: identical to Bullshit. Once `FINISHED` (`isFinished()` true), clients call
  `POST /api/kobo/game/{id}/play-again` (reopen room), presence turns a timed-out departure into
  `leaveRematch`. `SessionService.rematch` builds a new `Kobo` through the factory. The rematch is
  not a rule of the aggregate.

## Edge cases (all covered by tests)

1. Empty draw pile on `draw`: discard pile (minus its top) reshuffled; the top stays.
2. Empty draw pile and nothing to reshuffle: draw skipped, turn goes to `TURN_END`; a penalty card
   that cannot be drawn is simply not given.
3. A player at 0 cards: 0 points, may announce Kobo, may still match (it is only their own
   tableau that is empty; targets are other players' cards); `swapDrawn` and own-card powers are not
   possible (no occupied slot), `SKIP_POWER` is.
4. Announcing Kobo while a decision or power is pending: rejected with `WrongPhaseException`.
   Announcing out of turn, twice, or during final turns: rejected.
5. A match on a card already removed (lost race): `EmptySlotException`, no penalty.
6. Two simultaneous matches on the same card: the first processed wins, the second gets case 5.
   Two simultaneous matches on different cards of the same rank: both succeed in processing order.
7. A match between a current player's `draw` and `discardDrawn` that changes the top rank: later
   attempts are judged against the new top.
8. Power target changed by a match while pending: the power command is rejected; the player picks
   another target or skips.
9. Match with no top card (start of a round): `NothingToMatchException`, no penalty.
10. Match on another player's card without a valid `giveSlot`: rejected before any mutation, no
    penalty.
11. Match attempts in `MEMORISING`, `GIFT`, `ROUND_OVER`, `FINISHED`: rejected.
12. Total exactly 75 or 100 after the gift: set to 50 (so exactly 100 never ends the game; 101 does).
13. The gift pushes a player over 100 or onto 75/100: the reset test runs after the gift, the end
    test after the reset.
14. Tie at the lowest total in a Kobo: the announcer wins the round. Tie at the lowest total at the
    end of the game: all tied players win **[H8]**.
15. Disconnect during `MEMORISING`, `ROUND_OVER`, `GIFT`, a pending power, or a final turn: see
    Forfeit above; each case has a test.
16. Reload or reconnect at any phase: REST `GET /game/{id}` returns the viewer's DTO with private
    parts (starting cards while applicable, drawn card, reveal).
17. Rematch after a forfeit-finished game, and with fewer players: same session-core behaviour as
    Bullshit.

## Testing strategy (TDD)

- Domain: JUnit 5 + Hamcrest, no Mockito. A `KoboBuilder` fixture builds a game in a given phase
  with explicit tableaux, draw pile and discard pile (like `BullshitBuilder`); `KoboFixtures` for
  cards. Random is seeded or bypassed by explicit piles. Scoring tested as a pure function.
- Concurrency: a domain test hammers the same card with N threads (`ExecutorService` + latch) and
  asserts exactly one success, the rest `EmptySlotException`, no penalty, consistent totals of cards.
- Presentation: DTO projection tests assert privacy (no hidden value in any other seat's DTO), and
  a "total cards stay 52 across a scripted whole game" invariant test.
- Integration: `KoboWebSocketControllerIT`-style test over a real STOMP connection in the project's
  existing style, plus a whole-game test through the real session core.
- Suite: `cd backend && mvn -s ../deploy/docker/settings.xml test`.

## Out of scope

- Bots (a Kobo strategy, coordinator, scheduler); the generic `addBot` of the session core exists
  but no Kobo endpoint exposes it, and the broadcaster does not filter bot seats yet. Later bots
  need a state listener hook like `BullshitStateListener` and automatic `ready`.
- Frontend, locales, animations, memory of the client (the client mirrors slot revisions as above).
- Options (target score etc.), spectators, in-game chat, timers on turns.
- Persistence: state is in memory like the other games.

## Open questions

Hypotheses to confirm or change before implementation (the recommended answer is what the spec and
plan assume).

- **H1** The discard pile starts empty; a match attempt before the first discard is rejected without
  penalty. Alternative: flip the first card of the draw pile.
- **H2** Empty draw pile: reshuffle the discard pile except its top card (proposed, not contradicted).
- **H3** Matching a slot that has just been emptied by another player is rejected with no penalty.
  Alternative: treat it as an error and penalise.
- **H4** The rank is compared with the top at processing time; a player whose click was based on a
  stale top is penalised. Alternative: the client sends the expected top rank and the server rejects
  without penalty when it moved.
- **H6** Announcing Kobo needs an explicit `END_TURN`/`ANNOUNCE_KOBO` choice after the turn is played
  and is refused while a power is pending. Alternative: no explicit end step; the last action of the
  turn carries an optional `announceKobo` flag.
- **H7** The 10 points go to another player (never to the announcer), chosen by the announcer, and
  the choice is mandatory.
- **H8** Several players tied at the lowest total all win.
- **H9** A swap or a blind swap needs occupied slots on both sides; with 0 cards a player can only
  discard directly or skip the power.
- **H10** A `ready` barrier before each round (and after scoring), with no timeout.
- **H11** The two starting cards are resent in the viewer's DTO until the first draw of the round;
  later the client memory is the only source.
- **H12** A penalty card is appended as a new slot at the end of the tableau (positions stay stable
  for the client), never put into an empty slot.
- **H13** A forfeiter's cards go under the draw pile; a forfeiting announcer voids the Kobo.
- **H14** An unavoidable empty draw simply skips the draw / the penalty.
- **H16** The first player rotates by one remaining seat each round.
- **H17** A player with no card who matches another player's card gives nothing and the slot stays
  empty.

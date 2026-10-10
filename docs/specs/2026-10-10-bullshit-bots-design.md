# Bullshit bot players — design

Roadmap: issue #80, track B (B1 = this spec and its plan; B2 = backend; B3 = lobby UI).

## Goal

Let the host fill empty seats of a Bullshit lobby with computer players, so a game can be played
alone or with fewer humans than the table needs. Bots play the real rules through the same code
path as humans, see exactly what a human at their seat sees, and "think" for about 1-2 seconds
before acting.

Decisions already taken with the product owner:

1. The host adds and removes bots from the lobby. Games are mixed (humans and bots).
2. A single difficulty level.
3. Probabilistic rule-based strategy, no LLM. The bot counts the cards it can see (its own hand,
   the cards it knows are in the pile, the cards revealed by a challenge) to estimate how
   plausible a claim is, and challenges with some randomness. When it lacks the target cards it
   bluffs with a simple rule.
4. A short delay (about 1-2 s) before every bot action.

## Current behaviour that the design relies on

- An action arrives as a STOMP message (`/app/discard`, `/app/callBullshit`, `/app/forfeit`,
  `/app/bullshit/start`). `BullshitWebSocketController` resolves the seat from the `SessionToken`,
  calls the `Bullshit` aggregate (`discard`, `callBullshit`, both `synchronized`), then
  `sessionService.touch(gameId)` and `BullshitStateBroadcaster.broadcast(...)`. The broadcaster
  sends one `BullshitDto.forViewer(game, seat)` per seat on the seat's private topic
  (`/topic/game/{id}/seat/{token}`).
- Any non-claimant can call Bullshit while a claim is on the table, not only the next player.
  If the claimant emptied their hand (`pendingWinner`), the next player must call Bullshit or the
  claim stands and the claimant wins (a `discard` by that player is a decline).
- The session core (`SessionGame`, `SessionPlayer`) pre-allocates `maxPlayers` seats with a token
  each, claims seats with `claimNextFreeSeat`, and starts the game with
  `create(id, claimedCount, options)`: the game's `PlayerId`s are `0..claimed-1`, so claimed seats
  must be contiguous from 0.
- Presence (`PresenceService`) is keyed on a STOMP connection bound to a `Seat`. A dropped
  connection starts a 60 s forfeit timer; `forfeit` removes the player from `Bullshit`, and the
  last remaining player wins.
- Rematch for Bullshit is "reopen the room": `SessionService.playAgain` removes the game and
  recreates an empty lobby; the first caller takes seat 0 and becomes host.
- `BullshitDto` is the only per-seat view: own hand, hand counts, current target, pile size, last
  claim (claimant, target label, count, never the actual cards), pending winner, outcome.
  Revealed cards are public only in the `CALL_BULLSHIT` event.

## Key decisions

### D1. A bot is a seat flagged `bot` in the session core, with no token exposed

`SessionPlayer` gets a `bot` flag. `SessionGame.claimBot(name)` claims the lowest free seat as a
bot; `removeBot(seat)` frees it. The session core stays game-agnostic: it knows "a seat can be
bot-controlled", never how a bot plays.

- No token is handed out. The seat still holds the internal `SessionToken` (the record's invariant
  is that every seat has one), but it never leaves the backend: `LobbyView` and `SeatView` carry
  no tokens, and `SessionGame.findPlayerByToken` ignores bot seats, so a bot seat can never be
  impersonated even if a token leaked.
- No presence, no disconnect, no forfeit timer: nothing binds a STOMP connection to a bot seat, so
  `PresenceService` never sees one. Nothing subscribes to a bot seat's topic, so the state
  broadcaster skips bot seats instead of publishing into the void.
- Lobby projection: `LobbyView.LobbyPlayer` and `SeatView` gain `bot: boolean`. A bot counts as
  `joined`, so `claimedCount`, `canStart` (min 2 players) and `maxPlayers` work unchanged. A host
  alone with one bot can start.
- Default name `Bot 1`, `Bot 2`, ... (numbering over bots only). The frontend may localise the
  label from the `bot` flag; the name is plain data.
- Only the host can add or remove a bot, only before the game starts, only while a seat is free
  (add) or the target seat is a bot (remove). Reuses `NotHostException`, `RoomFullException`,
  `GameAlreadyStartedException`.

**Seat contiguity.** Because the game is dealt `0..claimed-1`, the lobby must not be started with a
hole. A human joining takes the lowest free seat, so a hole can appear only when a bot is removed
below a seat taken by someone else. Rule: `removeBot(seat)` is accepted only if no claimed seat
above it is a human (bots above it are renumbered down; they carry no identity). Otherwise it is
rejected with a clear error and `LobbyView` exposes `removableBotSeats` so the UI shows the
remove button only where it works. This avoids renumbering humans, whose seat index is stored
client-side. See open question Q2.

### D2. The decision logic is a pure domain port; the scheduler is an adapter

```
bullshit.domain.bot            pure rules, no Spring, no time, no session
  BotStrategy                  port: BotDecision decide(BotObservation)
  BotObservation               the bot's view of one seat (see D3)
  BotDecision                  sealed: Discard(cards) | CallBullshit | Pass
  BotMemory                    what the bot has learned (D4)
  PlausibilityEstimator        P(claim is true) from counts
  ProbabilisticBotStrategy     the single difficulty level; takes a RandomGenerator

bullshit.presentation          orchestration at the edge, like the controllers
  BullshitGameActions          discard / callBullshit use cases shared by controller and bots
  bot.BullshitBotCoordinator   decides when a bot must think; owns BotMemory per (game, seat)
  bot.BotScheduler             port: schedule(Duration, Runnable) -> cancellable handle
  bot.ThinkingDelay            port: Duration next()  (random 1000-2000 ms in production)

config / infrastructure
  TaskSchedulerBotScheduler    adapter on the existing Spring TaskScheduler
```

Why the coordinator is in `presentation`, not in `bullshit.domain`: it needs to know which seats
are bots (a session fact) and to broadcast (transport). A game knows neither sessions nor
transport (CLAUDE.md), and `sessionmanagement` must not know a concrete game. The presentation
layer already is the only place that sees both, and that is where `BullshitWebSocketController`
lives. The strategy stays pure and is reachable only through the `BotStrategy` port.

`BullshitGameActions` is extracted from the controller so a bot goes through the same path as a
human: domain call, `touch`, event data, broadcast. The controller keeps token-to-seat resolution
and error replies and delegates the rest. No behaviour change for humans (covered by the existing
controller tests, which must stay green).

### D3. A bot never sees more than a human at its seat

The strategy receives a `BotObservation`, never the `Bullshit` aggregate. It is built by
`BotObservation.forSeat(game, seat)` and holds exactly what `BullshitDto.forViewer(game, seat)`
shows: own hand, hand count per seat, current target, pile size, last claim (claimant, claimed
target, count) without the actual cards, pending winner, available actions, and the claim mode's
matching rule through the claim target. It has no accessor for another seat's cards.

Enforced by tests, not by convention: a parity test builds both the DTO and the observation from
the same game and asserts they agree on every shared fact; a structural test asserts the
observation record has no component of type `Hand`, `Player`, or `Discard` (whose `actualCards`
are secret).

### D4. Strategy: counting plus a challenge probability, and a simple bluff rule

Knowledge (`BotMemory`, per bot per game, fed only by public information):

- own hand (from the observation);
- cards the bot put in the pile and that nobody has picked up yet (it knows their identity);
- cards revealed by a `CALL_BULLSHIT` event, noted as held by the player who picked the pile up.
  This knowledge is dropped for that player when they next discard (the bot cannot tell which
  cards left).

**Challenge decision** (for any bot that is not the claimant, when a claim is on the table):

1. Let `n` = claimed count, target `T`, `M` = unseen cards matching `T`: total matching cards in
   the deck (4 per rank, 13 per suit) minus those in the bot's hand, minus its known pile cards.
   Revealed cards known to be with the claimant count as certain matches `k`.
2. Unseen population `U` = 52 minus own hand minus known pile cards. The claimant held
   `H = handCount + n` cards before discarding.
3. `P(true)` = probability that the claimant held at least `n - k` of the `M - k` unseen matches,
   a hypergeometric tail over `(U, M - k, H)`. If `n > M`, `P(true) = 0` (impossible claim).
4. Call probability `pCall = clamp(1 - P(true), 0.05, 0.95)`, then the bot calls if
   `random < pCall`. The clamp keeps a bot from being perfectly predictable in both directions.
5. Overrides: if the claimant emptied their hand (`pendingWinner`) and the bot is the next
   player, `pCall = 1.0` (declining ends the game). A bot never calls on its own claim (the
   observation exposes no `CALL_BULLSHIT` then).

**Play decision** (the bot whose turn it is, after deciding not to call):

- If it holds cards matching `T`: with probability 0.85 play all matching cards (up to 4);
  with probability 0.15 add one non-matching card (a light bluff that also sheds a card).
  If it holds matching cards for its whole hand and the count is at most 4, play them all
  (winning move, no bluff).
- If it holds none: it must lie. Number of cards: 1 with 0.7, 2 with 0.25, 3 with 0.05 (capped
  by hand size). Which cards: those whose own target will come up the latest (simulated with
  `ClaimMode.next`), keeping the cards it will be able to play honestly soon.
- Both branches go through `ClaimMode.matches` for "matches the target", so rank mode and suit
  mode need no special case in the strategy.

All constants live in one `BotTuning` record so they can be changed without touching the logic,
and tests can pin them.

### D5. Time, order and races

- After each state change, `BullshitBotCoordinator.onStateChanged(game)` is called by
  `BullshitStateBroadcaster` (a listener hook added to it, so every path that changes state,
  including forfeit and start, triggers it). For each bot seat that has an available action it
  schedules one task after `ThinkingDelay.next()`. It cancels the tasks of the previous state first.
- Every task captures a `game version` (a counter incremented by each mutating `Bullshit` method,
  new in B2). When the task fires it takes the game monitor (`synchronized (game)`; the aggregate's
  methods are `synchronized` on the same monitor, so this is reentrant and atomic), compares the
  version, and drops the task if the state moved on. This prevents a bot from challenging a claim
  that is no longer the one it reasoned about, and from two bots both acting on one claim (the
  second would otherwise challenge the next claim).
- Several bots can think about the same claim. The first to fire wins; the others see a changed
  version and drop out.
- A bot whose strategy returns `Pass` (decided not to challenge, not its turn) schedules nothing
  more for that state; it is re-evaluated at the next state change.
- If a bot action throws (domain exception, unexpected state), the coordinator logs it and falls
  back to discarding its single lowest card (or doing nothing when no action is available), so the
  table never stalls on a bot.

### D6. Determinism for tests

- `ProbabilisticBotStrategy` takes a `java.util.random.RandomGenerator`; the coordinator takes a
  `BotScheduler` and a `ThinkingDelay`. Production wires `ThreadLocalRandom`-backed generators and
  the `TaskScheduler` adapter.
- Tests use a seeded `Random` or a scripted generator to force a branch, a `ManualBotScheduler`
  (records tasks, runs them on demand, exposes the requested delays) and a fixed `ThinkingDelay`.
  No test sleeps, none depends on wall-clock time.
- Games under test are built from `BullshitBuilder` with known hands, as in `BullshitTest`.

### D7. Presence, forfeit, end of game and humans leaving

- Bots are never forfeited by presence. Human forfeit works as today (the aggregate removes the
  seat). If a single player remains, the aggregate declares them winner, bot or human.
- If **no human remains** in a live game (all humans forfeited, the last survivors are bots), the
  coordinator stops scheduling and cancels pending tasks. The game is not finished by a rule (a
  bot-only table has no winner to announce to anyone); it is left unreferenced and evicted by the
  existing idle TTL (`GameCleanupService`, 30 min), which also releases the room. Rationale: no
  new eviction path, no CPU spent on a game nobody watches.
- A disconnected human still has the standard 60 s grace. During it bots keep acting, except that
  the table naturally waits when it is the disconnected human's turn.
- When the game finishes (win or forfeit), the coordinator cancels all tasks for that game.

### D8. Rematch

`playAgain` reopens an empty lobby. Bots are therefore **not** carried over: the host re-adds
them. This keeps the "rematch = normal join then start" invariant of the reopen design and avoids
mixing bot seats into the contiguity rule at reopen time. Bullshit has no unanimous-rematch
tally (that is Bataille Corse only), so bots never need to "click Play Again". The coordinator
drops its memory and tasks for the old game when it finishes. Carrying bots over is a candidate
follow-up (open question Q1).

## `BullshitCreatePayload` clean-up (must land before B2)

`BullshitCreatePayload(Integer nbPlayers, GameMode mode, String name, String claimMode)`: the
frontend publishes only `{ name, claimMode }` (`BullshitSession.create`), and
`BullshitWebSocketController.createGame` reads only `name()` and `claimMode()`. `nbPlayers` and
`mode` are never read. Remove them (and the `GameMode` import). This changes the record's
canonical constructor, so the three calls in `BullshitWebSocketControllerTest`
(`new BullshitCreatePayload(null, null, "Alice", null)`) are updated at the same time. The new
bot endpoints use their own payloads, so B2 must not touch `BullshitCreatePayload` again; hence
the clean-up is a standalone first task (task 0 of the plan), mergeable on its own.

## Host actions and API (B2)

- `/app/bullshit/addBot` with `GameActionPayload { gameId, token }`; `/app/bullshit/removeBot`
  with `{ gameId, token, seat }` (a small `BullshitBotPayload`). Pattern identical to
  `/bullshit/start`: resolve the actor from the token, call `SessionService.addBot` /
  `removeBot` (host check inside), then `LobbyBroadcaster.broadcast` so every seat gets a fresh
  `LobbyView`. Errors go back on the host's seat topic as `ErrorResponse`.
- Event type: reuse `LifecycleEventType.JOIN` for add and add a `LEAVE` value for remove only if
  the frontend branches on the event type; B2 checks how the lobby store consumes the event and
  prefers reusing the existing refresh path.
- `SessionService.startGame` needs no change: bots are claimed seats. It does need to record who
  is a bot for the coordinator: `SessionService.botSeats(GameId): Set<PlayerId>`, read through a
  small `BotSeats` port so the bullshit presentation package depends on an interface, not on the
  whole service.

## Testing (TDD)

Backend, unit (no Spring):

- `PlausibilityEstimatorTest`: impossible claim gives 0; claim of 1 with all unseen cards
  unseen; known revealed cards raise the lower bound; probabilities against hand-computed
  hypergeometric values.
- `ProbabilisticBotStrategyTest`: forced challenge when pending winner; challenge on an
  impossible claim with the generator pinned to the cap; no challenge with the generator pinned
  above `pCall`; honest play keeps matching cards; lie when no match picks the latest-needed
  card; works in rank mode and suit mode; same seed gives the same decisions; a 1000-round
  statistical test with a seeded generator checks the empirical challenge rate of a certain lie is
  in `[0.9, 0.97]` and of an almost-certain truth in `[0.04, 0.15]`.
- `BotObservationTest`: parity with `BullshitDto.forViewer` for every shared fact; structural test
  that no secret type is exposed; observation of a non-participating seat is rejected.
- `BotMemoryTest`: own pile cards tracked until the pile is taken; reveal recorded for the picker
  and dropped on their next discard.
- `SessionGameTest` (bots): `claimBot` takes the lowest free seat and flags it; `removeBot` frees
  it, refuses a human seat, refuses when a human sits above (contiguity); bot seats ignored by
  `findPlayerByToken`; bots count in `claimedCount`.
- `SessionServiceTest`: host-only add/remove, not after start, full room, host alone plus one bot
  can start, `lobbyViews` flags bots and exposes no token; `playAgain` yields a lobby without bots.
- `BullshitBotCoordinatorTest` with `ManualBotScheduler`: schedules one task per bot with the
  injected delay; a bot challenges and the state advances; a stale task is dropped after another
  action; two bots on one claim give one action; no task when no human remains; tasks cancelled
  on game end; fallback discard on a throwing strategy; broadcaster skips bot seats.
- `BullshitStateBroadcasterTest`: listener invoked after each broadcast; bot seats get no message.
- Existing tests stay green: `BullshitWebSocketControllerTest`, `BullshitTest`, presence tests.

Backend, integration (`*IT`, Spring context, no Docker): a full game of 1 human (scripted client
calls on `BullshitGameActions`) against 2 bots with a `ManualBotScheduler`: the game reaches a
winner without exceptions in at most N steps, over several seeds.

Frontend (B3): Vitest on the lobby component (Add bot / Remove bot visibility by host, bot badge,
disabled when full), store handling of the new `bot` flag. Cypress e2e of bots belongs to the
Bullshit e2e work (C2) and depends on the testing profile pinning the delay to 0.

## Out of scope

- Several difficulty levels, bot personalities or names chosen by the host.
- Carrying bots over a rematch (Q1), bots replacing a forfeited human mid-game.
- Learning from past games, any LLM, any persistence.
- Bots in Bataille Corse.
- Spectating a bot-only table.

## Open questions

- Q1. Should a rematch keep the same bots? Recommended: no in v1 (see D8); a cheap follow-up
  is to store the bot count in the room and re-add after the host's first `play-again`.
- Q2. Contiguity rule for removing a bot below a human (D1): accept "remove only where no human
  sits above" (recommended) or renumber seats at start (larger change touching client-side seat
  storage).
- Q3. Delay range: a flat random 1000-2000 ms is proposed; should an out-of-turn challenge be
  slightly slower than playing, to leave humans a window to challenge first?
- Q4. Bot names: `Bot 1..n` as data, localised label in the frontend; or a fixed list of
  friendly names?

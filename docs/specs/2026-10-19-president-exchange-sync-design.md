# President exchange synchronisation: design

Written by Claude Opus 5.5 on `origin/main` at `44927da` (2026-10-19). Plan:
`docs/plans/2026-10-19-president-exchange-sync-plan.md`. Builds on the President spec
(`docs/specs/2026-10-15-president-design.md`, sections 3.2, 6, 7 and 8) and on chunk 2 of the animations plan
(`docs/plans/2026-10-18-president-animations-plan.md`: `model/president/exchangeMotion.ts`,
`PresidentExchangeRecap`).

## 1. Owner decision and goal

Owner, 2026-10-19: the game must **not** continue after the card exchange (the Scum and Vice-Scum give their best
cards, the President and Vice-President give cards back) until **every human player has seen it**: the flights of
the exchange and its summary. Chosen design: **synchronised, server-side**. The server waits for an acknowledgement
from each human seat (bots acknowledge on their own, at once), with a maximum timeout, so that a stalled or
disconnected player cannot block the table.

Today the phase leaves `EXCHANGE` with the last give back (at the deal under `giveBack = lowest`), and the first
lead can play at once: a bot leader plays 0.7 to 1.3 s later, while the humans' screens are still flying the cards
of the exchange and have not shown the summary yet.

## 2. Key decisions

1. **The barrier lives in the President domain**, as a new phase `EXCHANGE_VIEW` between `EXCHANGE` and
   `PLAYING`, with per-seat acknowledgements, like the `ROUND_OVER` barrier and its `ready`. The domain waits for
   **every active seat**: it never learns which seats are bots, or whether anyone is connected. The application
   layer acknowledges for the bots (the bot coordinator, with no delay) and closes the view on a timeout (a new
   listener with a timer). Time, bots and connections stay out of the domain; the rule ("no trick before the
   table has seen the exchange, and what a close does") stays in it.
2. **New command `/app/president/seeExchange`** with `{gameId, token, round}`, authenticated by the seat token
   through `SeatCommands` like every other action. An acknowledgement that is late (another round, the view
   already closed) or repeated changes nothing and answers nothing: no error, no broadcast.
3. **Timeout of 20 s**, counted from the moment the view opens (property `president.exchange-view.timeout`,
   default `PT20S`). On timeout the late seats are treated as having seen the exchange and the first trick starts.
   The late client still plays the flights and shows the summary, since both are keyed on the round and the
   pairs, not on the phase.
4. **When a client acknowledges**: once the flights of the state that opened the view have landed **and** the
   summary has been on screen for 3 s (scaled by the speed: Fast 1.5 s, Instant at once). Closing the summary with
   its cross acknowledges at once. A reload during the view shows the summary without flights and acknowledges
   after the same 3 s.
5. **Wire contract, additive**: phase value `EXCHANGE_VIEW`; action `SEE_EXCHANGE`; `players[].sawExchange`
   (boolean, like `ready`); event types `EXCHANGE_SEEN` and `EXCHANGE_CLOSED`. No deadline on the wire: no
   countdown is shown (sections 7.4 and 8), and the timer stays a server concern.

## 3. Options considered for where the barrier lives

| Option | Description | Verdict |
|--------|-------------|---------|
| A. Domain phase, every active seat acknowledges (chosen) | `President` holds the set of seats that saw the exchange; `seeExchange(seat, round)` and `closeExchangeView(round)` are domain commands; bots acknowledge through the coordinator; a presentation listener runs the timer | Same shape as the `ready` barrier: the state that the DTO shows comes from the aggregate and moves its `version`; domain tests cover the barrier with forfeits; no knowledge of bots or time in the domain |
| B. Domain phase, application-side acknowledgements | The domain has only the phase and `endExchangeView(round)`; who saw what is kept by a presentation registry that knows bots and humans | Fewer broadcasts (no bot acknowledgement), but the "who has seen" state lives outside the aggregate, does not move `version`, needs its own eviction and rematch handling, and the projection would read two sources |
| C. No new phase: `EXCHANGE` lasts until every acknowledgement | `EXCHANGE` with no pending return but acknowledgements missing | Changes the meaning of a phase the frontend and the bots already read (`isExchange` shows the give-back panel); harder to read than a phase of its own |
| D. Session layer (`sessionmanagement`) | A generic "seen" barrier in the session core | The session core must not know a game's phases (`sessionmanagement` depends only on the `game` kernel); no other game needs it |

Option A costs one extra broadcast per bot seat that acknowledges (at most 7 per exchange round, sent at once
after the view opens): small next to a trick, and every one of them is a plain state the screens already handle.

## 4. Domain (`president/domain`)

### 4.1 Phase

`Phase` gains `EXCHANGE_VIEW`: "round 2 or later with an exchange, every card has changed hands, the table is
shown the exchange; no trick yet". Order: `EXCHANGE`, `EXCHANGE_VIEW`, `PLAYING`, `ROUND_OVER`, `FINISHED`.

| Phase | Enters | Leaves with |
|-------|--------|-------------|
| `EXCHANGE` | the deal of a round with an exchange, under `giveBack = free` | the last return (a `giveBack`, or a forfeit that settles it): to `EXCHANGE_VIEW` |
| `EXCHANGE_VIEW` (new) | the end of `EXCHANGE`, or the deal itself under `giveBack = lowest` | the last active seat's `seeExchange`, `closeExchangeView`, or a forfeit after which every remaining seat has seen it: to `PLAYING`; a forfeit that leaves one player: `FINISHED` |

No view when nothing was exchanged: round 1, `exchange = none`, or an exchange plan with no pair. Those rounds go
straight to `PLAYING`, as today. A round whose returns were all cancelled by forfeits still opens the view: the
best cards moved at the deal.

`Round` gains `showExchange()` (phase `EXCHANGE_VIEW`); `beginPlay()` stays the way into `PLAYING`. `Round.forfeit`
treats `EXCHANGE_VIEW` like `EXCHANGE`: the hand is set aside and, if the leaver was the first lead, the lead passes
to the next seat with cards (the existing `passLeadFrom`).

### 4.2 Commands

```java
/** The seat has seen this round's exchange; the last active seat to say so starts the first trick. */
public synchronized List<PresidentEvent> seeExchange(PlayerId seat, int round) throws PresidentException

/** The table stops waiting (the timeout): every seat that has not seen the exchange is counted as having seen it. */
public synchronized List<PresidentEvent> closeExchangeView(int round)
```

`seeExchange`:
- a seat that is not active: `NotPlayersTurnException` (as `ready`);
- the match is over, `round` is not the current round, the phase is not `EXCHANGE_VIEW`, or the seat has already
  seen it: **no event, no version change** (an acknowledgement is a fact about a screen, never a mistake worth an
  error: an error reply would show "move impossible" on that screen);
- otherwise the seat joins `sawExchange`, event `ExchangeSeen(seat, playBegins)`; when every active seat has seen
  it, `round.beginPlay()` and `playBegins = true`; version + 1.

`closeExchangeView`: no event and no change unless the phase is `EXCHANGE_VIEW` of that `round`; otherwise event
`ExchangeViewClosed(round, late)` (the active seats that had not seen it, in seat order), every active seat joins
`sawExchange`, `round.beginPlay()`, version + 1.

Opening the view (one private helper, called where `round.beginPlay()` follows an exchange today: `giveBack` after
the last return, `startNextRound` under `lowest`, `forfeit` in `EXCHANGE` once no return is pending): clear
`sawExchange`, `round.showExchange()`. `startNextRound` also clears `sawExchange`.

`forfeit` in `EXCHANGE_VIEW`: the leaver leaves `sawExchange` with the rest of their state; then, unless one player
is left (`FINISHED`, as today), play begins if every remaining seat has seen the exchange.

`play`, `pass` and `giveBack` in `EXCHANGE_VIEW` are refused as in any other wrong phase (`round.play` and
`round.pass` check `PLAYING`; `giveBack` checks `EXCHANGE`).

### 4.3 Queries and actions

- `exchangeSeen()`: the seats that have seen this round's exchange (empty outside `EXCHANGE_VIEW`, filled during
  it; cleared at the next deal).
- `Action.SEE_EXCHANGE`: `availableActions(seat)` in `EXCHANGE_VIEW` is `[SEE_EXCHANGE]` until the seat has seen it,
  then `[]`. No `PLAY`, `PASS` or `COMPLETE` during the view.
- `currentSeat()` during the view is already the first lead (unchanged rule: the Scum, or the President under
  `nextLead = president`); it only acts once the phase is `PLAYING`.

### 4.4 Events

`ExchangeSeen(PlayerId seat, boolean playBegins)` and `ExchangeViewClosed(int round, List<PlayerId> late)`, added
to the sealed `PresidentEvent`. Neither carries a card.

### 4.5 Bots (pure part)

`PresidentBotDecision` gains `SeeExchange()`. `HeuristicPresidentBot.decide` answers `SeeExchange` in
`EXCHANGE_VIEW` (after `require(view, SEE_EXCHANGE)`), as it answers `Ready` at the barrier. `PresidentBotView`
already carries the phase, the round and the actions, which is all a bot needs; whether it also mirrors
`sawExchange` is decided by `PresidentBotViewParityTest` (mirror it if the test compares the players field for
field, as it does for `ready`).

## 5. Presentation (`president/presentation`)

### 5.1 Command

| Destination | Payload | Effect |
|-------------|---------|--------|
| `/app/president/seeExchange` | `PresidentRoundPayload(gameId, token, round)` (new record in `presentation/api`; a missing `round` is refused with "Missing field: round") | `PresidentGameActions.seeExchange(id, seat, round)` |

`PresidentGameActions`:
- `seeExchange(id, seat, round)`: the common `run`, type `EXCHANGE_SEEN`, data `PresidentEventData` with the seat,
  `action = "EXCHANGE_SEEN"` and `barrierOpened = playBegins` (the existing field: the barrier opened). **`run`
  skips the touch and the broadcast when the domain returns no event** (duplicate or late acknowledgement); today
  every command returns at least one event, so nothing else changes.
- `closeExchangeView(id, round)`: under the game's monitor, `game.closeExchangeView(round)`; when it returns
  events, touch and one broadcast, type `EXCHANGE_CLOSED`, data `PresidentExchangeClosedData(round, late)` (new
  `EventData` record in `presentation/dto/event`, `late` as seat numbers).

`PresidentEventType` gains `EXCHANGE_SEEN` and `EXCHANGE_CLOSED`.

### 5.2 Timer

`PresidentExchangeViewTimeout` (new, `president/presentation`): a `GameStateListener<President>` and a
`GameEvictionListener`, registered on `presidentStateBroadcaster` next to the bot coordinator. Every state change of
a President game goes through that broadcaster (commands, bot moves, forfeits through `SeatLifecycleBroadcaster`),
so the listener sees every way into and out of the view.

- On a broadcast, under the game's monitor: phase `EXCHANGE_VIEW` and no task armed for this game object and this
  round: schedule `actions.closeExchangeView(id, round)` after the timeout; any other phase (or another round, or
  another game object under the same id after Play again): cancel the task.
- On eviction: cancel. A task that fires late finds a stale round and the domain does nothing.
- Scheduler: the existing `BotScheduler` port and its `TaskSchedulerBotScheduler` bean (a one-shot delayed task
  with cancel; the name is historical, renaming it is out of scope).
- `president.exchange-view.timeout` (`Duration`, default `PT20S`), read in `PresidentConfig`. The `test` profile
  keeps the default; the backend tests drive the timer with a manual scheduler.

**Why 20 s.** The longest exchange picture at the Normal speed is the deal under `giveBack = lowest` with 8 players
and `exchange = scaled` (3, 2 and 1 cards, both ways: 12 flights): about 2.6 s of flights, plus up to 3.5 s of
tempo-queue lag, plus the 3 s summary: about 9 s. 20 s leaves more than twice that for a slow device or a tab in
the background (whose timers are throttled), and costs a stalled table at most 20 s, a third of the 60 s
disconnection grace the table already waits for a missing return or a missing `ready`.

### 5.3 Bot coordinator

- `SEE_EXCHANGE` is a step like `READY`: keyed on the phase and the round, so one human's acknowledgement does not
  reschedule anything.
- **No thinking delay** for this step (`Duration.ZERO`): the bots acknowledge right after the broadcast that opens
  the view, each through `actions.seeExchange(id, seat, view.round())` (one broadcast each).
- `apply` gets an explicit `SeeExchange` branch (today the last `else` sends `ready`); the fallback acknowledges in
  `EXCHANGE_VIEW`.
- The first lead, when it is a bot, is scheduled from the broadcast that starts the play, with its usual thinking
  delay (0.7 to 1.3 s): the humans see the view close, then the first play.
- Unchanged: a table with no human left is not driven (`drop`), so a bot-only table never waits; the speed of bot
  play is the same as today once the play has started.

### 5.4 DTO (`PresidentDto`)

- `phase`: may be `EXCHANGE_VIEW`.
- `players[].sawExchange`: `true` once that seat has seen this round's exchange (bots included, after their
  acknowledgement); `false` otherwise and outside the view. Plain boolean, always present, like `ready`.
- `availableActions`: `SEE_EXCHANGE` while the viewer still has to acknowledge.
- `exchange`, `received`, `given`, `toGiveBack`: unchanged (the pairs are all `done`, `toGiveBack = 0`).
- No deadline field (decision 5).

`PresidentBotViewParityTest` keeps the view equal to the DTO in the new phase.

### 5.5 JSON diff (the whole wire change)

```text
state.phase                  + "EXCHANGE_VIEW"
state.players[].sawExchange  + boolean
state.availableActions[]     + "SEE_EXCHANGE"
response.eventType           + "EXCHANGE_SEEN" (data: PresidentEventData, barrierOpened = play begins)
                             + "EXCHANGE_CLOSED" (data: {round, late: number[]})
/app/president/seeExchange   + {gameId, token, round}
```

President has no wire golden file (unlike Three Men's Morris); `PresidentDtoTest` and
`PresidentWebSocketControllerTest` pin the new fields and the destination.

## 6. Presence, forfeit, reconnection

- **Disconnected when the view opens**: nothing special; the timeout closes the view after 20 s at most (the
  disconnection grace is 60 s). Acknowledging on behalf of disconnected seats would need presence to tell the
  President presentation who is connected: not worth it for 20 s, left out.
- **Forfeit during the view** (timer or `/forfeit`): the seat leaves the match; if every remaining seat has seen
  the exchange, play begins in the same command (the forfeit broadcast shows `PLAYING`); a lone player left wins
  (`FINISHED`); the timer cancels on the next broadcast.
- **Forfeit during `EXCHANGE` that settles the last return**: the view opens in the forfeit itself; the timer and
  the bots see it through the forfeit broadcast.
- **Reconnection or reload during the view**: the REST state is in `EXCHANGE_VIEW` with my `sawExchange = false`:
  no flights (the store has no previous state), the summary shows at once and the client acknowledges 3 s later
  (decision 4). Reconnected after the view closed: plain `PLAYING`, the summary still shows (it already does
  during the play).
- **A human replaced by a bot**: the session has no in-game replacement today. Should one come, the new bot sees
  `SEE_EXCHANGE` in its actions at the next broadcast and acknowledges.

## 7. Client (`frontend/src`)

### 7.1 Model and session

- `PresidentPhase` gains `'EXCHANGE_VIEW'`, `PresidentAction` gains `'SEE_EXCHANGE'`, `PresidentPlayer` gains
  `sawExchange: boolean`, `PresidentEventType` gains `'EXCHANGE_SEEN' | 'EXCHANGE_CLOSED'`; the fixtures follow.
- `PresidentSession.seeExchange(round)` publishes `/app/president/seeExchange` with `{round}`.
- `model/president/exchangeView.ts` (new, pure):
  - `waitingFor(state, mySeat)`: the human seats (`!bot`) other than mine that have not seen the exchange, in seat
    order; empty outside `EXCHANGE_VIEW`;
  - `ackDelayMs({ flightsEndMs, factor })`: `flightsEndMs + 3000 * factor` (`factor` from `speedFactor`: 1, 0.5,
    0 for Instant, which acknowledges at once); `flightsEndMs` is 0 after a reload.
- `exchangeRecap` shows in `EXCHANGE_VIEW` as well as in `PLAYING` (it requires `PLAYING` today).

### 7.2 Store

- `seeExchange()`: sends once per round, when `can('SEE_EXCHANGE')`; it does **not** take the `busy` lock (nothing
  else is possible during the view, and a lost frame is retried by the reload after a reconnection, which shows a
  state still waiting for me).
- `EXCHANGE_SEEN` and `EXCHANGE_CLOSED` make no log entry and no movement (handled like a state change only).

### 7.3 Screen (`PresidentGameScreen.vue`)

- `playExchange` already knows when the flights of a change end (`admission.startInMs + endMs`); on the change that
  enters `EXCHANGE_VIEW`, it arms the acknowledgement at `ackDelayMs`. Without such a change (reload), the
  acknowledgement is armed when the summary shows. One timer per round, cleared on unmount and when the phase
  leaves the view.
- The summary's cross acknowledges at once (and closes it as today).
- **Reduced motion** at the Normal speed: still rings instead of flights (`markReducedMs` as `flightsEndMs`), then
  the 3 s of summary: reduced motion lowers movement, not reading time. Instant acknowledges at once (the player
  chose not to wait).
- The phone banner still closes on its own after 8 s; the acknowledgement (3 s) comes well before that.

### 7.4 What the table shows while waiting

No new layout: the action bar stays in place with its phase line (`data-test="phase-text"`), Play and Pass
disabled (no `PLAY` in the actions), the summary over it as today.

| State | Phase line (new keys under `presidentUi.exchangeView`) |
|-------|---------------------------------------------------------|
| I have not acknowledged yet | `The exchange` (`watching`) |
| I have, one human left | `Waiting for {player} to see the exchange` (`waitingOne`) |
| I have, two humans left | `Waiting for {a} and {b}` (`waitingTwo`) |
| I have, three or more left | `Waiting for {n} players` (`waitingMany`) |
| Nobody left (the next state starts the play) | `The first trick starts` (`starting`) |

Existing tokens and styles only (the phase line's own). The turn ring stays off during the view (the screen
already rings the current seat in `PLAYING` only).

**Needs a Claude Design canvas before it is built (not in this work):**
- a per-seat "has seen" marker on the plates (like the Ready marker on the fan of backs);
- a countdown of the timeout;
- an explicit "Continue" button instead of the automatic acknowledgement.

## 8. Rejected alternatives

- **Acknowledge after the flights only**: faster (about 3 s less), but the summary would come in after the first
  play, which the owner asked to avoid.
- **Acknowledge when the summary closes**: on a computer the summary stays until its cross is pressed; the table
  would wait for the timeout every round.
- **A deadline in the DTO**: the first broadcast of the view leaves before the listener arms the timer, so the
  projection would have to arm it; clock skew between server and client; nothing on screen uses it.
- **A countdown or a seen marker** without a canvas: UI decisions (section 7.4).

## 9. Tests

| Level | What |
|-------|------|
| Domain | `ExchangeViewTest` (new): the last free return opens `EXCHANGE_VIEW`; `lowest` opens it at the deal; round 1 and `exchange = none` go to `PLAYING`; the last acknowledgement begins play with the lead unchanged (Scum, and President under `nextLead = president`); a repeated, late (other round) or out-of-phase acknowledgement returns nothing and keeps the version; an inactive seat is refused; `availableActions` is `[SEE_EXCHANGE]` then `[]`; `play` and `pass` are refused during the view; `closeExchangeView` begins play and names the late seats, a stale close does nothing; forfeit during the view (the rest have seen it: play begins; the first lead leaves: the lead moves; one player left: finished); a forfeit that settles the last return opens the view. Existing tests that go through an exchange (`ExchangeFlowTest`, `FirstLeadTest`, `NextLeadTest`, `PresidentForfeitTest`, `PresidentAvailableActionsTest`, `PresidentMatchTest`) and the drivers (`PresidentDriver`, `PresidentRandomGamesTest`, `PresidentMatchInvariantTest`, `PresidentBotSelfPlayTest`) acknowledge for every seat. `HeuristicPresidentBotTest`: `SeeExchange` in the view. |
| Presentation | `PresidentGameActionsTest`: an acknowledgement broadcasts `EXCHANGE_SEEN`, a repeated one broadcasts nothing, a close broadcasts `EXCHANGE_CLOSED`. `PresidentWebSocketControllerTest` and `IT`: the destination, token authentication, a missing round. `PresidentExchangeViewTimeoutTest` (new, manual scheduler): armed once per round on entering the view, fires the close, cancelled when the play begins or on eviction, a new game under the same id is a new table. `PresidentBotCoordinatorTest`: the bots acknowledge with no delay and never play during the view; the first bot lead plays after the view with its delay. `PresidentBotGameIT`: one human and bots pass round 2 when the human acknowledges, and through the timeout when they do not. `PresidentDtoTest`, `PresidentBotViewParityTest`: the phase and `sawExchange`. |
| Frontend | `exchangeView.test.ts` (waiting list, delays per speed, reload); `exchangeRecap.test.ts` (shown in the view); `President.store.test.ts` (one send per round, not when already seen, no `busy` lock, no log entry for the two events); `PresidentSession.test.ts` (destination and payload); `PresidentGameScreen.test.ts` (phase lines, the cross acknowledges, Play disabled, the acknowledgement timer per speed with fake timers). |
| Cypress | The scripted STOMP guests of `support/` are human seats: they acknowledge by default (an option holds them back). New `president-exchange-sync.cy.ts` with two humans (the browser and a scripted guest held back, a bot third): after the flights and the summary the browser reads "Waiting for Bob to see the exchange" and cannot play; once Bob acknowledges, the first trick starts; at the Instant speed the browser acknowledges at once. Every existing `president-*` spec stays green with no change but the support. The timeout is covered by the backend tests, not by Cypress (a short timeout under the `test` profile would race with the normal acknowledgements). |

## 10. Edge cases

| Case | Behaviour |
|------|-----------|
| No human in any pair | Every human still waits for the view: they see backs fly and the counts in the summary |
| One human, the rest bots | The human's acknowledgement starts the play; the bots acknowledged at once |
| A human leaves (forfeit) during the view | Removed from the wait; play begins if the rest have seen it |
| Every human leaves | No human left: the coordinator stops driving the table, as today |
| Round with no exchange | No view; unchanged |
| Every return cancelled by forfeits | The view still opens (the best cards moved at the deal) |
| The match ends during the view (forfeit down to one player) | `FINISHED`; the timer cancels |
| An acknowledgement after the timeout | Late round or phase: nothing happens, no error |
| Two acknowledgements from one screen | The second changes nothing, no broadcast |
| A tab in the background | Its timer may fire late; the timeout bounds the wait |
| Play again under the same id | A new game object: the timer and the coordinator start fresh |

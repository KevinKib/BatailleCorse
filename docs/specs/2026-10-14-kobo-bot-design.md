# Kobo bot: design

Scope: computer players for Kobo, at three levels, seated through the shared lobby like the Bullshit and Three
Men's Morris bots. The owner asked for "a bot for Kobo, it does not need to be perfect": the goal is a table
companion that plays sensibly, takes part in the matching race and never cheats, not a strong player. Bots
were out of scope of the Kobo backend spec (`docs/specs/2026-10-11-kobo-backend-design.md`, "Out of scope")
and of the Kobo frontend spec. Plan: `docs/plans/2026-10-14-kobo-bot-plan.md`. Written by Claude Opus 5.5.

## Key decisions

1. **The bot sees exactly what a human sees at its seat, and remembers it itself.** A domain view
   `KoboBotView.forSeat(game, seat)` mirrors `KoboDto.forViewer` (no face-down value except the seat's own
   private parts); a per-seat `KoboBotMemory` keyed by `(seat, slot, revision)` is fed by that view and by
   the public moves of each broadcast. The strategy never receives the `Kobo` aggregate.
2. **One heuristic strategy, three levels as tuning.** `HeuristicKoboBot` plus a `KoboBotTuning` record per
   level (`easy`, `medium`, `hard`): match reaction time, forgetting, whether it follows public swaps and
   matches opponents' cards, how bold its Kobo call is. No search, no card counting.
3. **The match race has its own task, keyed on the discard top, not on the game version.** A bot that knows
   a card of the top's rank schedules a match after a random reaction delay; that task survives unrelated
   state changes and is dropped only when the top changes. The domain guards (`expectedTopRank`,
   `expectedRevision`) settle every contention without penalty; a bot only ever matches a card it knows, so
   it is never penalised.
4. **The coordinator lives in `kobo/presentation/bot`**, next to `MorrisBotCoordinator`, reusing the shared
   ports (`BotSeats`, `BotSeatOptions`, `BotScheduler`, `ThinkingDelay`); the strategy and memory live in
   `kobo/domain/bot`, pure (no Spring, session or transport), so ArchUnit is unchanged.
5. **Three small PRs:** B1 domain (view, memory, strategy, self-play test), B2 table (coordinator, factory,
   broadcaster filtering, DTO `bot` flag, wiring), B3 frontend (`supportsBots`, level labels, Cypress).

## What exists today (read from the code)

| Fact | Where |
|------|-------|
| The Kobo broadcaster is built with `BotSeats.none()`: it would send a state to a bot seat. This is the "no bot filtering (out of scope)" of the backend plan | `config/KoboConfig.java` `koboStateBroadcaster` |
| The projection lambda already receives the bot set and ignores it: `(game, viewer, names, bots) -> KoboDto.forViewer(game, viewer, names)` | same |
| `KoboDto.PlayerDto(seat, name, current, total, handSize, slots)` has **no** bot flag (the backend plan listed one, the code never got it). Bot seats are already named "Bot N" by the session core (`SessionGame.claimBot`), so names show without it | `kobo/presentation/dto/KoboDto.java` |
| `KoboFactory` does not override `supportsBots()` (default false), so `SessionService.addBot` refuses Kobo; it has no `seatOptionDescriptors()` | `kobo/domain/KoboFactory.java`, `game/GameFactory.java` |
| Listeners get `onBroadcast(game, eventType, EventData)`: for Kobo the data is `KoboEventData` (presentation), never the domain `KoboEvent` | `presentation/state/SeatStateBroadcaster.java`, `kobo/presentation/KoboGameActions.java` |
| Every command and its broadcast run under `synchronized (game)`; listeners are called on the same thread, inside that monitor | `KoboGameActions.run` |
| `matchDiscard(seat, target, giveSlot, expectedTopRank, expectedRevision)`: a moved top (`StaleDiscardException`), a changed slot (`StaleSlotException`) or an emptied one (`EmptySlotException`) are rejected without penalty; only a wrong rank against the current top draws a penalty card | `Kobo.matchDiscard` |
| A new round builds fresh tableaux: revisions restart at 0 | `Kobo.deal`, `Tableau` |
| Private per-seat queries: `startingCards(seat)` (slots 2 and 3, until the round's first draw), `drawnCard(seat)` (current seat in `DECISION`), `revealFor(seat)` (last peek, cleared by the seat's next accepted command) | `Kobo.java` |
| `Kobo.slots(seat)` returns `Tableau.Slot` **with the card**: anything built for a bot must strip it | `Kobo.slots`, `Tableau.Slot` |
| Patterns to follow: Bullshit `BotObservation.forSeat` (domain view, never the aggregate), `BotMemory` (public facts only), `BotTuning` (constants in a record), `BullshitBotCoordinator` (`CallDecisionDelay`: a second delay that leaves humans a window); Morris `MorrisBotCoordinator` (per-seat task, version check under the monitor, level from the `level` seat option, fallback to a legal action, drop on finish, eviction and rematch) | `bullshit/domain/bot`, `bullshit/presentation/bot`, `threemenmorris/presentation/bot` |
| ArchUnit: a game's domain depends on no Spring, session, presentation or config; no game depends on another; the shared `presentation` knows no game | `architecture/ArchitectureRules.java` |

Consequence of the last line: Bullshit's `BotMemory` and `BotTuning` cannot be reused from `kobo` (one game
may not depend on another), and they would not fit anyway (claims and piles, not slots). Kobo gets its own
small `kobo/domain/bot` package; only the game-agnostic ports in `presentation/bot` are shared.

## 1. Hidden information: what the bot may know

### Rule

A bot knows what a human sitting at its seat, with a perfect (or, by level, imperfect) memory, could know:

- its own starting cards (slots 2 and 3) while the server shows them;
- the card it drew, while it holds it;
- every card it looked at with a power (7/8 own, 9/10 and king others);
- every card that becomes public: the discard top, each swapped-out or discarded card, each matched card;
- where cards go in public moves: a blind swap, a king swap or a match gift moves a card it knows from one
  slot to another, and the event says which two slots (a human can follow it);
- public facts: phases, whose turn, slot occupancy and revisions, totals, the announcer, the provisional
  scores in `GIFT`.

It never knows a face-down card it has not seen, never the draw pile order, never another seat's private
parts.

### How the memory is fed

`KoboBotMemory` is one mutable object per bot seat, owned by the coordinator. An entry is
`(SlotRef, revision) -> Card`. Three inputs, in this order, on every broadcast, for every bot of the table:

1. **Public moves** from the broadcast's `KoboEventData`, translated by the coordinator into domain calls:
   `BLIND_SWAP` / `KING_SWAP` become two `onMoved(from, to)`; a successful `MATCH` with a `givenSlot` becomes
   `onMoved((actor, givenSlot), (targetSeat, targetSlot))`; the bot's own `SWAP` becomes
   `onOwnDrawnPlaced(slot)` (the card it held is now in that slot). A moved entry is relocated and marked
   "to re-stamp".
2. **The seat view** `KoboBotView.forSeat(game, bot)`: `observe(view)` re-stamps relocated entries with the
   destination's current revision, drops every other entry whose revision no longer matches (the card
   changed in a way the bot cannot follow), records the private parts (starting cards, drawn card, reveal)
   at their current revision, and clears everything when `round` changed (revisions restart at 0).
3. **Forgetting**, at the bot's own `END_TURN` or `KOBO`: each entry is dropped with the level's
   probability (one `nextDouble()` per entry, so tests can script it).

Because an entry is only ever created from a card actually seen, relocated by an exact public move, or
dropped, the memory is **never wrong**, only incomplete. That is what makes "only match a known card" safe.

`KoboBotView` is a record built under the game's monitor from the aggregate's per-seat queries, field for
field what `KoboDto.forViewer` shows that seat: phase, round, current seat, available actions, discard top,
draw pile size, pending power, announcer, pending final turns, totals, per-seat slots as
`(slot, occupied, revision)` (cards only once the round is revealed), starting cards, drawn card, reveal,
provisional or last round result. A presentation test asserts the parity with `KoboDto.forViewer` in each
phase, and a domain test asserts that no other card appears.

### Alternatives rejected

- **The strategy reads the `Kobo` aggregate** (as the Morris bot reads a `MorrisPosition`): Morris has no
  hidden information; in Kobo it would see every face-down card. Rejected for fairness.
- **Build the observation from the seat's `KoboDto`** (strictest parity: literally the human's JSON): the
  domain cannot depend on presentation, so the coordinator would have to parse `CardDto` labels back into
  cards for a second model. The Bullshit precedent (`BotObservation.forSeat` in the domain, documented as
  equal to the DTO) plus a parity test gives the same guarantee with less code.
- **Feed the memory from domain `KoboEvent`s**: the listener port only carries the presentation
  `EventData`; changing `GameStateListener` touches four games for one consumer. The translation from
  `KoboEventData` (four cases) stays in the coordinator.
- **No memory, only the view**: the bot would forget its starting cards at the first draw and every peek at
  its next command; it could never match or call Kobo sensibly.
- **Card counting** (estimating unknown cards from the public discards): better estimates, more code and
  tests, not needed for "not perfect". An unknown card is worth the deck average, 6.5 points (338 points in
  52 cards, computed from `CardPoints`, not hard-coded).

## 2. Strategy

`HeuristicKoboBot implements KoboBotStrategy` with
`KoboBotDecision decide(KoboBotView view, KoboBotMemory memory)` for the bot's own turn, and
`Optional<KoboBotDecision.Match> matchFor(KoboBotView view, KoboBotMemory memory)` for the race. Values used
below: `known(slot)` the remembered card's points, `est(slot)` = `known` or 6.5 when unknown,
`estimate(seat)` = sum of `est` over the seat's occupied slots.

| Phase (action offered) | Decision |
|------------------------|----------|
| `MEMORISING`, `ROUND_OVER` (`READY`) | `Ready` (after the thinking delay, so a human sees the scores before the bot's tick) |
| `DRAW` | `Draw` |
| `DECISION`, drawn card `d` | For each own occupied slot, `gain = est(slot) - points(d)`; best slot by gain, a known slot first on ties (certain gain). `SwapDrawn(best)` when `gain >= 1`, else `DiscardDrawn` (so a red King is always kept, a 7 to King with nothing worse in hand is discarded for its power) |
| `POWER` `PEEK_OWN` (7, 8) | `Peek` the lowest own unknown slot; none: `SkipPower` |
| `POWER` `PEEK_OTHER` (9, 10) | `Peek` an unknown slot of the opponent with the lowest `estimate` (the likeliest Kobo); none: `SkipPower` |
| `POWER` `BLIND_SWAP` (J, Q) | Own slot with the highest `est` against the opponent slot with the lowest `est` (lowest-`estimate` opponent on ties); `BlindSwap` when the difference is at least 3, else `SkipPower` |
| `POWER` `KING` | `Peek` an opponent slot known to be worth 3 or less, else an unknown slot of the lowest-`estimate` opponent; none: `SkipPower` |
| `KING_DECISION`, peeked `p` | `KingSwap(worst own slot)` when `est(worst) - points(p) >= 2`, else `SkipPower` |
| `TURN_END` | `AnnounceKobo` when the rule below holds, else `EndTurn` |
| `GIFT` | `GiveTen(target)`, rule below |

**When to announce Kobo.** With `E = estimate(me)` and `u` the number of own unknown cards, announce when all
of: `ANNOUNCE_KOBO` is offered; `u <= tuning.maxUnknownAtAnnounce`; `E <= tuning.announceAt + pressure`;
`E < estimate(opponent) - tuning.announceMargin` for every opponent. `pressure` grows by 1 every 3 own
turns past the 6th of the round: the longer a round lasts the bolder the bot, which also guarantees that a
bots-only round ends (see tests). A failed Kobo costs 20 points; the margin and the unknown cap are what keep
that risk low at `medium` and `hard`, and `easy` is deliberately reckless. A hand of 0 cards (estimate 0)
always qualifies: a tie with the lowest wins.

**Who gets the 10 points.** From the provisional totals of `GIFT` (`lastRound` in the view, see
`Kobo.lastRoundResult`): (1) an opponent pushed strictly above 100 by the 10 points while the bot then has the
lowest total (the game ends and the bot wins); else (2) the opponent with the lowest provisional total; else (3) the first other seat.
(The 75/100 reset, which used to exclude some targets here, was removed on 2026-10-18.)

**The match (any live phase).** If the discard top has rank `r`: an own remembered card of rank `r` first
(no gift needed, the hand shrinks), else, when the level allows it, an opponent's remembered card of rank `r`,
giving the own card with the highest `est` (a known high card, else an unknown one; no `giveSlot` when the
bot has no card). The decision carries `expectedTopRank = r` and `expectedRevision` = the remembered
revision. Never a guess: no remembered card of rank `r`, no match.

**Fallback.** A strategy exception or a refused decision falls back to a fixed legal action per phase:
`READY`, `DRAW`, `DISCARD_DRAWN` (in `DECISION`), `SKIP_POWER` (in `POWER` and `KING_DECISION`),
`END_TURN`, `GIVE_TEN` to the first other seat. Each phase has one, so the table never stalls. A refused match has no fallback: losing the
race is normal.

### Alternatives rejected

- **Search or simulation** (Monte Carlo over the unknown cards): far beyond "not perfect", slow to test.
- **Always the cheapest legal action** (draw, discard, end turn): legal but pointless; the bot would never
  call Kobo, so a bots-only round would never end and a human would have no opponent.
- **Deliberate mistakes at `easy`** (wrong matches, absurd swaps): they look like bugs to players. `easy`
  is weak by forgetting, slowness and recklessness instead.

## 3. The match race

**Scheduling.** On every broadcast the coordinator compares the discard top (card and pile size) with the one
it saw last. When it changed, every pending match task of the table is cancelled. Then, for each bot without
a pending match task, `matchFor(view, memory)` is asked; a candidate is scheduled after
`reactionDelay(level)`, a uniform draw within the level's bounds (scaled by a property, 0 under the `test`
profile). New knowledge (a peek that reveals a card of the top's rank) therefore also triggers a match.

**Why not the game version.** The turn tasks of both existing coordinators are cancelled on every version
change. A match task keyed the same way would restart its reaction delay each time anybody acts (a draw, a
failed match), so a fast table would starve the bot. Keyed on the top, it fires unless the top itself moves,
which is exactly when the match would become stale.

**Firing.** Under the game's monitor the task re-checks that the game is live, the top is unchanged and the
memory still holds the entry at the same revision, then calls `KoboGameActions.match(...)` with the expected
values. Contention with humans or other bots is settled by the domain: the first processed wins; the loser
gets `StaleDiscardException`, `StaleSlotException` or `EmptySlotException`, without penalty, which the task
logs at debug level and ignores. Two bots knowing the same card: same thing. A match by the bot bumps the
version, so its own pending turn task is rescheduled, as after any other change.

**Opponents' cards.** Only remembered ones (a 9/10/king peek, or a card followed through a public move), and
only at `medium` and `hard`. The gift is chosen in the same decision; if the chosen give slot changed in the
meantime the domain rejects it (`InvalidGiveSlotException`, no penalty).

**Penalty.** A wrong rank is penalised by the domain, but a bot can only produce one through a memory bug;
the self-play test asserts that no bot is ever penalised.

### Alternatives rejected

- **Instant match** (no reaction delay): the bot would win every race, humans could never match first.
- **Second hesitation delay like Bullshit's `CallDecisionDelay`**: the reaction delay already is that
  window; one delay per level is simpler.
- **Matching unknown cards by probability**: costs penalties, looks like cheating when it works.

## 4. Levels

Seat option `level` (`easy`, `medium`, `hard`, default `medium`, label `options.kobo.level`), read through
`BotSeatOptions` like Morris; `KoboBotLevel.fromOption` maps a missing or unknown value to the default. One
strategy class; the level only picks the `KoboBotTuning`:

| Tuning | easy | medium | hard |
|--------|------|--------|------|
| Match reaction (ms, uniform) | 2500-4000 | 1300-2500 | 700-1300 |
| Forget each entry at own turn end | 0.25 | 0.08 | 0 |
| Follows public moves (blind/king swaps, match gifts) | no | yes | yes |
| Matches opponents' remembered cards | no | yes | yes |
| `announceAt` (max estimate) | 9 | 6 | 5 |
| `maxUnknownAtAnnounce` | 2 | 1 | 1 |
| `announceMargin` over every opponent | none | 0 | 2 |

The numbers are first guesses, pinned by tests on the record and tunable without touching the strategy.
Alternatives rejected: separate strategy classes per level (three times the tests for the same rules), and
the nine reaction tiers of Bataille Corse (Kobo's skill is memory and judgement, not only speed).

## 5. Broadcaster and DTO

- `KoboConfig.koboStateBroadcaster` takes `sessionService::botSeats` instead of `BotSeats.none()`: a bot seat
  gets no message (nothing subscribes to it) and the listener gets the bot set.
- `KoboDto.PlayerDto` gains `boolean bot` (additive), from the projection's `bots` argument:
  `KoboDto.forViewer(game, viewer, names, bots)`, the three-argument overloads delegating with no bots.
  The frontend uses it for the "Bot N" label (B3), as Bullshit does, rather than trusting a typed name.

## 6. Coordinator

`KoboBotCoordinator implements GameStateListener<Kobo>, GameEvictionListener` in `kobo/presentation/bot`,
shaped like `MorrisBotCoordinator`:

- **Table per game**: the `Kobo` instance, one `KoboBotMemory` per bot seat, `turnTasks`, `matchTasks`,
  `scheduledVersion`, the last seen discard top. A new `Kobo` under the same id (Play again) gets a fresh
  table; `onEvicted` drops it.
- **On broadcast** (under `synchronized (game)`): drop the table when there is no bot, the game is finished,
  or no human is left (all remaining seats are bots: the game is left to the idle eviction, as Bullshit and
  Morris do). Otherwise feed every memory (moves, then view), then: if the version changed, cancel the turn
  tasks and schedule one per bot that has a turn action (`READY` while not ready, or the current seat's
  action, or `GIVE_TEN`), after `ThinkingDelay`; then the match scheduling of section 3.
- **Think** (under the monitor): drop if the table is gone, the game finished or the version moved; else
  `decide`, apply through `KoboGameActions` (the same use cases as a human, so broadcasts and session touch
  are identical), fallback on any exception.
- **Delays**: thinking delay per step `kobo.bots.delay-min-ms` / `delay-max-ms` (600 / 1200) so each step
  of a bot turn (draw, decide, power, end) is visible; reaction delays from the tuning multiplied by
  `kobo.bots.reaction-scale` (1.0). `application-test.properties`: both delays 0 and the scale 0.
- **Randomness**: a `koboBotRandom` `RandomGenerator` bean, `ThreadLocalRandomGenerator` in production and a
  fixed-seed generator under the `test` profile (not a constant one: a constant 0 would make every
  `nextDouble() < p` true and erase a `medium` bot's memory).
- **Stop**: game finished, bot removed (forfeit cannot happen to a bot: it never connects, so presence never
  starts a grace for it), eviction, rematch; all-humans-gone as above.
- `KoboFactory`: `supportsBots() = true`, `seatOptionDescriptors()` = `level`.

Where it lives: `kobo/presentation/bot` may depend on the shared `presentation` ports and on
`sessionmanagement.core.application.GameEvictionListener`, exactly like the Morris coordinator; the domain
package `kobo/domain/bot` depends only on `kobo/domain` and the `game` kernel. `ArchitectureRules.GAMES` is
unchanged. Alternative rejected: a generic coordinator in the shared `presentation` (it would have to know
Kobo's phases and race; the shared package knows no game).

## 7. Tests

- **Domain, pure (B1)**, JUnit 5 + Hamcrest, no Mockito, positions from `KoboBuilder` / the package-private
  constructor and deals from `ScriptedDeckSource`:
  - `KoboBotViewTest`: no card of another seat in any phase before the reveal; the seat's own private parts
    present exactly when `KoboDto` shows them.
  - `KoboBotMemoryTest`: starting cards kept after the first draw; a peek kept after the next command; a
    revision change forgets; a blind swap, a king swap and a match gift relocate (and do not when the level
    does not follow moves); a new round clears; forgetting with a scripted generator.
  - `HeuristicKoboBotTest`: one test per row of the decision table and per announce/gift rule, including red
    King kept, 75/100 avoided in the gift, push-over-100 win, no match without a remembered card, own match
    before an opponent's, highest card given.
  - `KoboBotTuningTest` / `KoboBotLevelTest`: values per level, `fromOption` defaults.
  - `KoboBotSelfPlayTest`: 2 to 6 bots per level mix, seeded, driven by a small test harness that asks each
    bot for its turn decision and its match in turn: every game finishes within a step bound, the 52-card
    invariant holds at each step (`cardsInPlay`), no bot is ever penalised, no strategy exception.
- **Presentation (B2)**:
  - `KoboBotViewParityTest` (presentation test package): the view equals `KoboDto.forViewer` field for field
    in each phase.
  - `KoboBotCoordinatorTest` with a manual scheduler (as `MorrisBotCoordinatorTest`): acts after the delay;
    drops a stale turn task; readies at both barriers; a match task survives an unrelated action and is
    cancelled when the top changes; a lost race is ignored without fallback; stops on finish, eviction and
    rematch; nothing scheduled when only bots remain; fallback on a throwing strategy.
  - `KoboDtoTest`: `bot` flag; broadcaster test: no message to a bot seat.
  - `KoboBotGameIT`: through `SessionService`, two scripted humans and one bot (zero delays): the bot readies,
    plays its turns, matches a card it knows when a human discards that rank (fixed deal), and the game reaches
    `ROUND_OVER`; Play again keeps the bot and its level; the bot seat receives no message.
  - `KoboFactory` test: `supportsBots`, the `level` descriptor and its validation.
- **Frontend (B3)**: Vitest (`games.test.ts`, the lobby shows Add bot and the level select for Kobo, the
  bot label); Cypress `kobo-bot.cy.ts` on the e2e stack (zero delays, fixed deal through the existing test
  deal endpoint): add a bot, set Hard, start, both memorise, the bot plays its turn, the human ends the round
  with Kobo and sees the bot in the scores, Play again keeps the bot. Existing assertions "no Add bot" for
  Kobo are updated.

## 8. Delivery

| PR | Content | Needs | Size |
|----|---------|-------|------|
| B1 `feat: Kobo bot strategy and memory` | `kobo/domain/bot` only, no wiring, nothing visible | - | M-L |
| B2 `feat: Kobo bots at the table` | coordinator, factory, broadcaster, DTO flag, config, properties, context map | B1 | M-L |
| B3 `feat: play Kobo against bots` | `games.ts`, locales, Kobo label, Cypress | B2; after #206 (K7b) or rebased on it | S-M |

Shared files: `model/games.ts` and `games.test.ts` (Morris F3 flips its own flag), `locales/Messages.ts` and
`en.ts` (Morris F1-F3, Kobo K7b; separate `options.kobo` namespace), `view/kobo/KoboGameScreen.vue` and
`state/Kobo.store.ts` (K7b #206). The backend PRs touch only Kobo files plus
`application-test.properties` (one block, Morris's is already merged) and `docs/architecture/context-map.md`.

## Decisions to confirm

- **Q1 Levels**: three levels `easy` / `medium` / `hard`, default `medium`, labelled Easy / Medium / Hard.
  Recommended. Alternative: a single level like Bullshit (simpler lobby, less tuning).
- **Q2 Bots match opponents' cards** (only remembered ones, `medium` and `hard`). Recommended: it is part of
  the game and gives the bot a real presence in the race. Alternative: own cards only (gentler).
- **Q3 Pace**: 600-1200 ms per step of a bot turn (about 3 to 5 s for a whole turn with a power).
  Recommended. Alternative: one delay per turn and the steps chained quickly (faster table, harder to
  follow).
- **Q4 Barriers**: a bot says ready after its thinking delay, it never waits for the humans. Recommended (the
  barrier still waits for every human). Alternative: the bot readies last.

## Out of scope

Card counting, opponent modelling, bluff-like play, bots offered in the frontend before B3, a bot taking
over a disconnected human's seat, any change to `sessionmanagement`, the shared listener port or another
game.

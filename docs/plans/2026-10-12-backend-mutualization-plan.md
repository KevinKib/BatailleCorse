# Backend mutualization: plan

> **For agentic workers:** one PR per section, in the order and waves below. Each PR is TDD
> (characterization or red test first), leaves every suite green on its own and changes no observable
> behaviour unless its "Observable change" line says otherwise.

**Spec:** `docs/specs/2026-10-12-backend-mutualization-design.md` (findings D1-D15 and I1-I13, target,
what we do not do). **Plan written by:** Claude Opus 5.5.

## Owner decisions (2026-10-12)

These replace the open questions of the first draft.

1. **ArchUnit** (`com.tngtech.archunit:archunit-junit5`, test scope) is added for the architecture test in
   PR0. No home-made scan of `import` lines.
2. **`/lobby/addBot` is refused for a game that has no bot.** X4 leaves the deferred list and becomes
   planned work: PR6a (below), red test first, the refusal goes to the sender alone. Consequence for I5:
   a bot can no longer be added to a Kobo or Morris room, so the broadcast no longer meets a bot seat
   there. The defensive fix of `sendToSeat` is kept only if PR6a's red test shows another path to a bot
   seat; otherwise it is dropped.
3. **The sender's state is attached to the error reply everywhere when a game runs** (as Kobo and Morris
   do today), made in PR4 (which introduces `SeatCommands`) with a contract test that pins the before and
   after, and with a check that the frontend clients accept an enriched error reply (PR4, "Frontend client
   check"). **Reality verified by PR4 (#163):** `BullshitWebSocketController` already joined the sender's
   state to the `ErrorResponse` of `/discard` and `/callBullshit`; the only gap was `/bullshit/start`
   refused while the game runs (`null` before, the sender's state after). A start refused on a lobby stays
   without state. Bataille Corse still sends `null` until it adopts `SeatCommands` in PR9; its client check
   was done in PR4 so PR9 knows what to change.
4. **One `OutcomeDto` is postponed** (X3): reconsider when the Kobo (#149) and Three Men's Morris (#147)
   frontends are planned.
5. **After lobby PR9, Bataille Corse errors go to the sender alone** (PR9).

**Goal:** remove the presentation and wiring code copied between Bataille Corse, Bullshit, Kobo and
Three Men's Morris, and align the behaviours that differ for no product reason, without touching the
wire contract or the dependency rules of `docs/architecture/context-map.md`.

**Test commands:**
- One class: `cd backend && mvn -s ../deploy/docker/settings.xml -Dtest=KoboRestAndLifecycleTest test`
- Whole backend (unit tests and `*IT`): `cd backend && mvn -s ../deploy/docker/settings.xml test`
- Frontend untouched; the global rule still asks for `cd frontend && npm test && npx vite build` before
  each PR.
- No Docker and no e2e: no PR here has a visible effect (rule of the project `CLAUDE.md`).

**Conventions:** paths relative to `backend/src/main/java/org/kevinkib/cardgames/` (tests under
`backend/src/test/java/org/kevinkib/cardgames/`). Branches `refactor/...` or `test/...`, titles
`refactor: ...`. Every PR description lists the classes deleted and, when there is one, the observable
change with its test.

## Status (2026-10-12)

| PR | State |
|----|-------|
| 0 | [x] merged, #159 |
| 1 | [x] merged, #162 |
| 2 | [x] merged, #160 |
| 3 | [x] merged, #161 |
| 4 | [x] merged, #163 |
| 5 | [x] merged, #164 |
| 6a | [ ] in progress, draft PR #168 |
| 6, 7, 8 | [ ] to do |
| 9 | [ ] done in draft PR #175 (BC keeps its public-topic state and lifecycle broadcasters; dead solo and rematch code removed) |

## Overview and waves

```
Wave 1 (parallel)   PR0 architecture test | PR1 test kit | PR2 config per game | PR3 card DTO
Wave 2 (in order)   PR4 seat commands  ->  PR5 typed start
Wave 3              PR6a addBot refused without a bot | PR6 seat state + lifecycle broadcasters  ->  PR7 REST support | PR8 actions under the monitor
Wave 4 (after lobby PR9)  PR9 Bataille Corse adopts the helpers
Deferred            X1 bot coordinator skeleton, X2 lifecycle under the lock, X3 outcome DTO (postponed)
```

| PR | Title | Cost | Depends on | Parallel with |
|----|-------|------|-----------|---------------|
| 0 | `test: lock the context-map dependency rules` | S | - | 1, 2, 3 |
| 1 | `test: share the messaging double, STOMP client and session table` | M | - | 0, 2, 3 |
| 2 | `refactor: one Spring configuration per game` | S | - | 0, 1, 3 |
| 3 | `refactor: one card DTO and card label parser` | S | - | 0, 1, 2 |
| 4 | `refactor: one seat-command handler` | M | 1 | - |
| 5 | `refactor: typed startGame that checks the game type first` | S | 4 | - |
| 6a | `fix: refuse /lobby/addBot for a game without a bot` | S | 4 | 6 |
| 6 | `refactor: generic per-seat state and lifecycle broadcasters` | M | 2, 4 | 6a |
| 7 | `refactor: shared REST support for seat games` | S | 6 | 8 |
| 8 | `refactor: Bullshit and Bataille Corse broadcast under the game monitor` | M | 6 | 7 |
| 9 | `refactor: Bataille Corse adopts the shared seat helpers` | M | 4-7 and lobby PR9 | - |

S: half a day or less; M: about a day. Total about 7 to 8 days of agent time. Estimated net effect
(**[H]**, counted from the files read): about 1 100 lines of main code and 900 lines of test code
removed, about 450 lines of shared code and tests added.

## Conflict map

| File | PRs | Risk |
|------|-----|------|
| `config/AppConfig.java` | 2 (split), then 6, 8 edit the per-game config classes | Low after PR2; merge PR2 first in wave 1 |
| `bullshit/presentation/BullshitWebSocketController.java` | 3 (import), 4, 5, 6 | Sequential waves |
| Kobo and Morris `*WebSocketController.java` | 4, 5, 6 | Sequential waves |
| `*RestController.java` (Bullshit, Kobo, Morris) | 4 (token), 6 (types), 7 | Sequential |
| `bataillecorse/presentation/*` | 3, 8, 9 and lobby PR9 | PR9 waits for lobby PR9; PR3 and PR8 touch only `GameActions` and DTO imports |
| Bullshit/Kobo/Morris tests | 1, then every later PR | PR1 merged first in wave 1 |
| `backend/pom.xml` | 0 (test dependency) | Low |

Any session that adds a game or a bot in the meantime should rebase on PR2 (per-game configuration)
and, from wave 3, use `SeatStateBroadcaster` instead of copying `KoboStateBroadcaster`.

---

## PR 0: `test: lock the context-map dependency rules`

- Add ArchUnit (`com.tngtech.archunit:archunit-junit5`, test scope; owner decision 1, no home-made scan) and
  `ArchitectureTest` in the test root package, with the rules of the context map:
  - `..sessionmanagement..` and `..game..` depend on no `..bataillecorse..`, `..bullshit..`,
    `..kobo..`, `..threemenmorris..`, `..presentation..`, `..config..`;
  - every `..<game>.domain..` depends on no `..presentation..`, `..sessionmanagement..`,
    `..config..`, `org.springframework..` (the shared kernel `..game..` is allowed);
  - no game depends on another game;
  - `..presentation..` (shared package, not a game's `presentation`) depends on no concrete game.
- Main code only (`ImportOption.DoNotIncludeTests`): test packages break the first rule today (I12),
  cleaned later or never.
- Red first: write each rule against a deliberately wrong temporary import, see it fail, remove it.
- Observable change: none. Risk: the shared `presentation` rule may catch an existing import; grep showed
  none **[V]**.

## PR 1: `test: share the messaging double, STOMP client and session table`

Test code only. New package `presentation/support` under the test root:

- `RecordingMessaging extends GameMessagingService`: records `sendToSeat` (seat, payload),
  `sendToGame` (payload) and `sendToToken` (resolved to the seat through an optional
  `SessionService`, as `TokenResolvingMessaging` does), with `last()`, `toSeat(seat)`, `clear()`.
  Replaces the 15 local doubles (D12) and `TokenResolvingMessaging`.
- `StompTestClient` (port, `ObjectMapper`, `SimpleBrokerMessageHandler`): `connect()`, `subscribe`,
  `subscribeSeat`, `send`, `body(gameId, token, ...)`, `disconnectAll()`, and the `Frames` class
  (`await`, `awaitAny`, `drain`). Replaces D13 in the six STOMP ITs.
- `SessionTable`: `InMemorySessionRepository` + `SessionService` over given factories +
  `RecordingMessaging` + `install(game, gameType)` + `token(game, seat)`. `KoboTable`,
  `ThreeMenMorrisTable` and `BotGameIntegrationTest.Table` keep their game wiring on top of it (D14).
- Order: migrate one test class at a time, run it, then delete its local double. Test counts before and
  after must match (write them in the PR).
- `PresenceServiceTest` (sessionmanagement) keeps its local double: moving it to presentation support
  would add a second test import of presentation (I12); left as is.
- Observable change: none. Risk: timing of `Frames.await` changes if a copy diverged; compare the copies
  before merging them (the Kobo one has `awaitAny`, the lobby ones may not).

## PR 2: `refactor: one Spring configuration per game`

- `AppConfig` keeps the shared beans (clock, repository, `SessionService`, schedulers, presence,
  messaging, lobby, bot ports, `GameLifecycleBroadcasters`, cleanup).
- New `config/BatailleCorseConfig`, `BullshitConfig`, `KoboConfig`, `ThreeMenMorrisConfig`: factory,
  state broadcaster, actions, lifecycle broadcaster, bot coordinator and bot properties of that game,
  moved as they are (same bean names, same `@ConditionalOnProperty`, same `@Value` keys).
- `GameFactories` is built from `List<GameFactory>` (every factory bean) instead of the explicit list;
  `GameFactories.factoryFor` filters by type, so order does not matter **[V]**.
- The `sessionService()` calls between `@Bean` methods become injected parameters in the per-game classes.
- Tests first: `ApplicationContextTest` and `BotWiringTest` already cover the wiring; add one assertion
  that `GameFactories` knows the four types (`bataille-corse`, `bullshit`, `kobo`, `three-men-morris`)
  and that `GameLifecycleBroadcasters.broadcasterFor` answers for each game. Run them before the move,
  green, then after.
- Observable change: none. Risk: a bean created twice or a missing proxy call (`@Configuration` without
  `proxyBeanMethods`); the context test catches both.

## PR 3: `refactor: one card DTO and card label parser`

- `presentation/dto/CardDto` (record `rank`, `suit`, `name`, null-safe `from` as Kobo's) replaces
  the three copies (D7). `presentation/dto/CardLabels` (`rank(String)`, `suit(String)`,
  `card(CardDto)`) replaces the maps of `BullshitCardMapper` and `KoboRankMapper` (D8); both mappers are
  deleted or reduced to calls if many tests use them.
- Characterization first: a JSON test per game that serializes a card through the current DTO (BC
  `BatailleCorseDto`, Bullshit `CallBullshitEventData`, Kobo `KoboEventData` and `KoboDto`) and
  pins the exact JSON; same tests green after the swap. Error messages of the mappers kept verbatim
  ("Unknown card: rank=... suit=...", "Unknown rank: ...").
- `presentation` already depends on FrenchCards through the games; Morris does not use it.
- Observable change: none (property order of the BC JSON may change; JSON clients do not depend on
  it). Risk: low.

## PR 4: `refactor: one seat-command handler`

- No session-core change: since #155 a malformed token is an unknown token (tolerant approach,
  spec I4; the strict alternative, a domain exception mapped to 403, is set aside). The
  `catch (IllegalArgumentException)` kept around token lookups only for malformed tokens become dead
  and go with the handler; `MalformedTokenIT` must stay green unchanged.
- `presentation/command/SeatCommands` (one SLF4J logger):
  - `void handle(String rawGameId, String token, String eventType, SeatAction action, StateOnError state)`
    where `SeatAction.run(GameId, PlayerId) throws Exception` and
    `StateOnError.stateFor(GameId, PlayerId)` may return null;
  - unknown or malformed id, unknown token: nothing sent, logged at debug;
  - refused command: `ErrorResponse(eventType, e.getMessage(), state)` to the sender alone, logged
    at debug for domain exceptions and at warn for unexpected ones (`RuntimeException` other than
    `IllegalArgumentException`/`IllegalStateException`);
  - the state is attached whenever a game runs, for every game (owner decision 3); `null` only when no
    game runs yet (refused start of a lobby).
- Adopt in `KoboWebSocketController`, `ThreeMenMorrisWebSocketController`,
  `BullshitWebSocketController` (I2: authenticate before loading the game). Each controller keeps its
  mappings and its `required(...)` checks. `LifecycleController` and `LobbyController` replace
  `System.err.println` by the logger (I8); `LobbyController` sends through `LobbyBroadcaster` (D10).
- Tests first: the existing controller tests of the three games stay green; add for Bullshit
  "a malformed game id on `/discard` sends nothing and throws nothing" (red today: it throws) and the
  same for `/callBullshit` and `/bullshit/start`. One `SeatCommandsTest` with `FakeGame`.
- Contract test (red first): per game, pin the `ErrorResponse` of a refused command before the change
  (Bullshit: `state` is `null`; Kobo and Morris: the sender's state) and after (the sender's state for
  all, when a game runs). The Bullshit test is red today; the Kobo and Morris ones stay green.
- Frontend client check (verification task, no frontend change planned in this PR; code read on
  `origin/main`, to be redone when the PR starts):
  - `frontend/src/application/BullshitSession.ts` `onResponse` already handles `success === false`: it
    applies `response.state` when present, then emits the `error` event. An error reply carrying the
    sender's state is therefore accepted as is (the comment there already describes this case). Add a
    Vitest case in `BullshitSession.test.ts` if none covers "refused with a state" so the contract is
    locked on the client side.
  - `frontend/src/application/GameSession.ts` (Bataille Corse) `processEvent` does not look at
    `success`: it always builds `BatailleCorse.fromJSON(response.state)` and emits `state-update`. A
    reply with an enriched state is processed as a normal state update (harmless: BC state is public
    and current), and a `null` state would fail in `fromJSON`. So the enriched reply is safe, but BC
    shows no error to the player. Not changed here (BC is untouched until PR9); PR9 decides whether
    `GameSession` gains a `success === false` branch that surfaces the error and skips the
    state-update.
  - `frontend/src/service/WebSocketService.ts` subscribes to `/topic/game` and to the seat queue and
    only does `JSON.parse(message.body)` before delegating to the stores; it does not inspect `state`,
    so it needs no change.
  - Verdict to write in the PR: Bullshit client accepts the enriched reply without change; Bataille
    Corse client needs no change for the enrichment, and a possible error branch is a PR9 item.
- BC untouched in this PR (its handler is legacy, see PR9).
- Observable change (as merged in #163): a `/bullshit/start` refused while the game runs now carries the
  sender's state in its `ErrorResponse` (it was `null`); `/discard` and `/callBullshit` already did, as
  Kobo and Morris. From PR9, a refused Bataille Corse command will too. A malformed game id on Bullshit
  commands is now silent instead of an exception in the server log.
- Risk: medium; messages of `ErrorResponse` are shown to players (Bullshit lobby errors), so they
  must not change: assert them in the tests.

## PR 5: `refactor: typed startGame that checks the game type first`

- `SessionService.<T extends Game> T startGame(GameId id, String hostToken, Class<T> type)`: starts
  and returns the typed game (`type.cast`), like the existing `getGame(id, Class<T>)` (I10).
  `sessionmanagement` still knows no concrete game.
- The type check stays in each `start` handler, **before** the call, with its current message
  ("This room is not a Kobo game", "... a Three Men's Morris game", "... a Bataille Corse game"), so no
  player-visible text moves into the session core. Bullshit gains the same check
  ("This room is not a Bullshit game", I3). The four casts go.
- Red tests: "a `/bullshit/start` on a Kobo room does not start it" (was red), and the
  existing "not a Kobo game" tests stay green.
- Observable change (merged, #164): `/bullshit/start` on a room of another game is refused with "This room
  is not a Bullshit game" without starting it. I3 was a real bug: the call started the other game, then
  failed on the cast.
- Risk: low.

## PR 6a: `fix: refuse /lobby/addBot for a game without a bot` (in progress, draft #168)

Planned work (was X4; owner decision 2). Independent of PR6 but merged before it, so PR6 no longer needs
a bot-seat case for Kobo and Morris.

- Kernel: `GameFactory.supportsBots()` (default `false`); `BatailleCorseGameFactory` and
  `BullshitGameFactory` return `true`, Kobo and Morris keep the default. `SessionService.addBot` /
  `addBotViews` refuse the call for a room whose factory does not support bots, with a domain exception
  of the session core (for example `BotsNotSupportedException`); `sessionmanagement` still knows no
  concrete game.
- `LobbyController.addBot` catches it and sends an `ErrorResponse` to the sender alone (through the
  lobby error path / `SeatCommands`, no broadcast to the other seats); message in English, asserted in
  the test.
- Red first: `SessionServiceTest` with `FakeGame` whose factory has no bots ("addBot on such a room
  throws and adds no seat"), `LobbyControllerTest` ("the refusal goes to the sender only, the other seats
  receive nothing"), and a Kobo and a Morris case. The Bataille Corse and Bullshit add-bot tests stay
  green unchanged. Also a test that `supportsBots()` is `true` exactly for those two factories.
- Consequence for I5: the Kobo or Morris room can no longer hold a bot seat, so the broadcast no longer
  hits `sendToSeat` of a bot. The defensive change of `sendToSeat` (do not throw on a bot seat) is kept
  only if a test finds another way to reach a bot seat (for example a seat converted to a bot by
  presence forfeit); otherwise it is not made.
- Frontend: check that Kobo/Morris UI does not offer an "add bot" button (they have no frontend yet,
  #149 and #147); nothing to change.
- Observable change (deliberate): `/lobby/addBot` on a Kobo or Morris room is refused instead of adding
  a seat nobody plays. Risk: low.

## PR 6: `refactor: generic per-seat state and lifecycle broadcasters`

- Move `SeatNames` to `presentation` (I9); Kobo and Morris take it instead of a `Function`.
- `presentation/state/GameStateListener<G extends Game>` replaces `BullshitStateListener` and
  `BatailleCorseStateListener` (both coordinators implement the generic one).
- `presentation/state/SeatProjection<G, D>`: `D forViewer(G game, PlayerId viewer,
  Map<PlayerId, String> names, Set<PlayerId> bots)`; one per game as a method reference
  (`BullshitDto::forViewer`, and an adapter ignoring `bots` for Kobo and Morris).
- `presentation/state/SeatStateBroadcaster<G extends Game, D>`: `stateFor`, `addListener`,
  `broadcast(game, eventType, eventData, message)` to every human seat (bot seats skipped), then the
  listeners, each in its own `try`, logged as today (copy of `BullshitStateBroadcaster:54-70`).
- `presentation/state/SeatLifecycleBroadcaster<G extends Game>` (`Class<G>`, `SeatStateBroadcaster<G, ?>`):
  the three methods of D2 with the same event data and messages.
- Delete `BullshitStateBroadcaster`, `KoboStateBroadcaster`, `ThreeMenMorrisStateBroadcaster`,
  `BullshitLifecycleBroadcaster`, `KoboLifecycleBroadcaster`, `ThreeMenMorrisLifecycleBroadcaster`,
  `BullshitStateListener`; beans typed `SeatStateBroadcaster<Kobo, KoboDto>` and so on in the per-game
  configs (Spring resolves generic types for injection).
- BC keeps `BatailleCorseStateBroadcaster` (public topic, forfeit reasons) and its lifecycle
  broadcaster; it only implements `GameStateListener<BatailleCorse>`.
- Tests first: generic tests with `FakeGame` (human seats only, listener failure isolated, lifecycle
  messages). The existing per-game broadcaster and lifecycle tests are moved onto the generic classes
  and must stay green with the same assertions. No Kobo or Morris bot-seat test: after PR6a such a room
  cannot hold a bot (I5 is handled there). The generic broadcaster still skips bot seats, for Bullshit.
- Observable change: none.
- Risk: medium (generic Spring beans: check that each injection point names its full generic type).

## PR 7: `refactor: shared REST support for seat games`

- `presentation/rest/SeatGameRest` (built with `SessionService` and `LobbyBroadcaster`):
  - `state(String gameType, String id, String token, BiFunction<Game, PlayerId, Object> stateFor)`:
    403 without token or with an unknown one, 404 for an unknown id or another game type, the state
    when the game runs, the `LobbyView` otherwise;
  - `join(gameType, id, JoinGamePayload)` and `playAgain(...)`: 404 / 409 as today, `touch`, JOIN
    lobby broadcast with the message `"Player " + (seat + 1) + " joined."` (D9).
- `BullshitRestController`, `KoboRestController`, `ThreeMenMorrisRestController` keep their
  `@RequestMapping` and three one-line handlers.
- Tests first: the three REST test classes stay green unchanged; one `SeatGameRestTest` with `FakeGame`
  covers every status. The 409 list differs today: `join` catches `GameAlreadyStartedException` and
  `RoomFullException`, `playAgain` only `RoomFullException`; keep exactly that.
- Observable change: none. Risk: low.

## PR 8: `refactor: Bullshit and Bataille Corse broadcast under the game monitor`

- `BullshitGameActions` and `BatailleCorseGameActions` run the domain call, `touch` and the broadcast
  inside `synchronized (game)`, as Kobo and Morris do (I1). The bot paths already hold that monitor
  (reentrant), so only the human paths change.
- Lock order stays "game, then session" as documented in `SessionService.leaveRematch`: check with a
  grep that no `synchronized` method of `SessionService` or `PresenceService` calls a game method that
  takes the game monitor (`Kobo.getPlayerIds`/`isFinished` and `ThreeMenMorris.isFinished` are
  `synchronized`; `SessionService` reads `Game::isFinished` outside its monitor on purpose).
- Tests first: a concurrency test per game (two threads, many commands, the recorded broadcasts carry
  increasing `version` values per seat). It may pass today by luck; run it 50 times before and after.
- Observable change: none intended (broadcast order now always follows state order).
- Risk: medium (deadlock if the lock-order check is wrong); keep this PR alone so it can be reverted
  alone.

## PR 9: `refactor: Bataille Corse adopts the shared seat helpers`

Only after lobby PR9 (`feat: Bataille Corse adopts the shared lobby; solo is a bot`) has deleted
`/create`, `/rematch`, the seat-1 join, the tokenless `GET`, `SessionRestController` and `GameMode`.

- `BatailleCorseWebSocketController`: `/bataillecorse/start`, `/send`, `/slap`, `/grab` through
  `SeatCommands`; errors go to the sender alone (owner decision 5), no longer to the game topic, and
  carry the sender's state (owner decision 3). Apply the finding of PR4's frontend check: give
  `GameSession.processEvent` a `success === false` branch that surfaces the error and skips the
  `state-update`, with a Vitest case, if the client must show the error; the frontend also has to stop
  relying on the game-topic error, which is a frontend change in this PR.
- `BatailleCorseRestController`: `join` and `play-again` through `SeatGameRest`.
- `BatailleCorseLifecycleBroadcaster` stays (forfeit reasons in the public DTO; `rematchStarted`
  goes if the in-place rematch is deleted by lobby PR9).
- Observable change (deliberate): BC errors reach the sender alone, with the sender's state. Risk: medium; needs the BC e2e run of lobby PR9 as a
  baseline.

## Deferred (not planned, with their trigger)

- **X1 Bot coordinator skeleton** (D15): extract a `BotTables<G>` (per-game task table, version gate,
  `drop`, eviction) into `presentation/bot` when a third game gets bots. Align I11 at that time.
- **X2 Lifecycle broadcasts under the game lock** (I13): needs an ordering decision for disconnect and
  forfeit events; not a refactor.
- **X3 One outcome DTO** (`winners`, `reason`): wire change, frontend work, product decision.
  Postponed by the owner (decision 4); reconsider when the Kobo (#149) and Three Men's Morris (#147)
  frontends are planned.
- X4 (`/lobby/addBot` refused without a bot) is no longer deferred: see PR6a.

## Risks

1. Wire drift hidden by a refactor (field order, `null` versus absent, message text): every PR that
   touches a DTO or an `ErrorResponse` adds a JSON or message characterization test first.
2. Generic Spring beans (PR6): an ambiguous injection fails at startup; `ApplicationContextTest` catches
   it, but each game's IT must also run.
3. Lock ordering (PR8): see the grep check; the PR stays alone.
4. Parallel game work: sessions adding a game or a bot copy the pre-refactor pattern. Mitigation: merge
   wave 1 quickly; PR0 stops cross-context imports; the context map points to the shared helpers
   after PR6.
5. Hypotheses I3 and I5 may be wrong: each is a red test first; if it is green, the PR records it and
   drops the "bug fix" line.
6. Enriched error replies (decision 3) reach clients that may not expect a state: mitigated by the
   frontend client check of PR4 and the before/after contract test.

## Open questions for the owner

None left: the five questions of the first draft are answered in "Owner decisions (2026-10-12)".

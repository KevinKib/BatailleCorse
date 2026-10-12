# Backend mutualization: design

Scope: the backend presentation and wiring code that four games (`bataillecorse`, `bullshit`, `kobo`,
`threemenmorris`) copied from one another to ship quickly, as listed in PRs #115, #142 and #146.
No new feature. The wire contract is frozen except the deliberate, tested changes listed under
principle 1 (owner decisions of 2026-10-12). The PR-by-PR plan is
`docs/plans/2026-10-12-backend-mutualization-plan.md`.

Every finding below was read in the code on `origin/main` at `5dba71a` (rechecked at `4dc26ca`, after #154 and #155) and is marked **[V]**
(verified by reading) or **[H]** (inferred from the code, not reproduced by a test or a run).

## 1. Findings

### 1.1 Duplication

| # | What | Where | Size |
|---|------|-------|------|
| D1 | Per-seat state broadcaster: `stateFor` + loop `sendToSeat` over `getPlayerIds()` | `KoboStateBroadcaster:30-39`, `ThreeMenMorrisStateBroadcaster:31-40` (identical); `BullshitStateBroadcaster:46-70` (same plus bot skip and listeners) | 3 x 40 lines **[V]** |
| D2 | Lifecycle broadcaster: `supports` by `instanceof`, three broadcasts with the same event data and messages | `BullshitLifecycleBroadcaster:21-48`, `KoboLifecycleBroadcaster:21-48`, `ThreeMenMorrisLifecycleBroadcaster:21-48` (identical but for the cast); `BatailleCorseLifecycleBroadcaster` differs (forfeit reasons in the DTO, `rematchStarted`) | 3 x 49 lines **[V]** |
| D3 | REST controller: `GET /game/{id}?token=` (state or `LobbyView`), `POST /join`, `POST /play-again`, game-type check | `BullshitRestController:46-128`, `KoboRestController:45-112`, `ThreeMenMorrisRestController:45-112` (Kobo and Morris identical but for names); BC lobby branch `BatailleCorseRestController:110-116` | 3 x 110 lines **[V]** |
| D4 | WebSocket `handle`: parse id, resolve token, ignore unknown, run, reply `ErrorResponse` with state to the sender only | `KoboWebSocketController:138-163`, `ThreeMenMorrisWebSocketController:79-104` (identical); `BullshitWebSocketController:57-102` and `BatailleCorseWebSocketController:124-138` (variants) | 4 x 25 lines **[V]** |
| D5 | Start: type check, `(G) startGame`, `touch`, broadcast `START` / `EmptyEventData` / `"Game started."` | `KoboWebSocketController:51-61`, `ThreeMenMorrisWebSocketController:47-58`, `BullshitWebSocketController:39-55`, `BatailleCorseWebSocketController:72-98` | 4 x 12 lines **[V]** |
| D6 | `*GameActions.run`: `getGame(type)`, `synchronized (game)`, command, `touch`, broadcast | `KoboGameActions:85-94`, `ThreeMenMorrisGameActions:40-51` | small; the event-type choice is per game **[V]** |
| D7 | `CardDto {rank, suit, name}`, same JSON in three games | `bataillecorse/.../dto/CardDto` (class), `bullshit/.../dto/CardDto` (record), `kobo/.../dto/CardDto` (record, null-safe) | 3 copies **[V]** |
| D8 | Card label parsing (`FrenchRank::toString` map) | `BullshitCardMapper:16-19`, `KoboRankMapper:14-15` | 2 copies **[V]** |
| D9 | Lobby join / play-again use case: `joinRoom`/`playAgain`, `touch`, `LobbyBroadcaster.send(JOIN, "Player n+1 joined.")` | the three REST controllers of D3, plus `BatailleCorseRestController:111-115` | 5 copies **[V]** |
| D10 | `LobbyController.handle:108-111` re-implements `LobbyBroadcaster.send:22-25` | `presentation` | 1 copy **[V]** |
| D11 | Per-game wiring (factory, state broadcaster, actions, lifecycle broadcaster) in one `AppConfig` and the explicit `GameFactories` list | `AppConfig:76-100, 141-283` | 4 games in one file, every game PR edits it **[V]** |
| D12 | Recording messaging doubles: 15 classes extending `GameMessagingService` or `TokenResolvingMessaging` | e.g. `KoboTable:23`, `ThreeMenMorrisTable:22`, `BullshitGameActionsTest:37`, `BatailleCorseRestControllerTest:33`, `LobbyControllerTest:39`, `PresenceServiceTest:56` | **[V]** (grep) |
| D13 | STOMP IT helpers (`connect`, `Frames`, `subscribe`, `awaitBrokerSubscription`, `send`, `body`) | `KoboWebSocketControllerIT:90-190`, `ThreeMenMorrisWebSocketControllerIT:162-240`, `LobbyControllerIT:65-130`, `LobbyLeaveIT:67-130`, `LobbyOptionsIT:64-114`, `BatailleCorseWebSocketControllerIT:195-230` | 6 copies **[V]** |
| D14 | Session + game test fixture ("table": repository, `SessionService`, messaging, broadcaster, actions, controllers, `install`) | `KoboTable`, `ThreeMenMorrisTable`, `BotGameIntegrationTest.Table:46`, `ThreeMenMorrisWebSocketControllerIT.Table:79` | 4 copies **[V]** |
| D15 | Bot coordinator skeleton (per-game `Table`, `cancelPending`, version gate, `botsIn`, `drop`, eviction) | `BullshitBotCoordinator:80-165`, `BatailleCorseBotCoordinator:84-154` | about 60 lines in 2 classes **[V]** |

### 1.2 Inconsistencies (behaviour differs between games for no product reason)

| # | Finding | Status |
|---|---------|--------|
| I1 | Kobo and Morris run call, `touch` and broadcast under the game monitor (`KoboGameActions:87`, `ThreeMenMorrisGameActions:42`); Bullshit (`BullshitGameActions:38-66`) and BC (`BatailleCorseGameActions:35-76`) do not, so two human commands may broadcast out of order. The bot paths already hold the monitor (`BullshitBotCoordinator:213`, `BatailleCorseBotCoordinator:178`). | [V] code; ordering effect [H] |
| I2 | Bullshit `/discard` and `/callBullshit` load the game before authenticating and parse the id outside any `try` (`BullshitWebSocketController:59-60, 83-84`; `/bullshit/start:41`): an unknown or malformed id escapes to Spring as an exception. | [V] |
| I3 | `/bullshit/start` does not check the room's game type (`BullshitWebSocketController:47`); Kobo (`:54`), Morris (`:50`) and BC (`:88`) do. A `/bullshit/start` on another game's room would start it, then fail the cast. | [V] code; effect [H] |
| I4 | **Fixed by #155.** A malformed token used to make `new SessionToken(String)` throw (`UUID.fromString`), so the REST `GET` of Bullshit, Kobo and Morris answered 500. Since #155 the approach is *tolerant*: an invalid value becomes a name-based (version 3) UUID that no seat holds, so it is an unknown token everywhere (REST 403, WebSocket commands ignored), covered by `SessionTokenTest` and `MalformedTokenIT` (160 cases). The *strict* alternative (a domain exception translated to 403 at the edge) is set aside for now. Nothing left to do here; the remaining catches of `IllegalArgumentException` around token lookups become dead code, removed in PR4. | [V] |
| I5 | `/lobby/addBot` is game-agnostic (`LobbyController:50-54`), but the Kobo and Morris broadcasters send to every seat (`KoboStateBroadcaster:35`, `ThreeMenMorrisStateBroadcaster:36`) and `sendToSeat` of a bot seat throws (`InMemorySessionRepository:48-51`, bots have no resolvable token). A Kobo or Morris room started with a bot would stop broadcasting at the bot seat, and no bot would play. **Decision (2026-10-12): `/lobby/addBot` is refused for a game that has no bot (X4, planned in PR6a), so a bot can no longer be added to a Kobo or Morris room and the broadcast cannot meet a bot seat there. The defensive fix in `sendToSeat` is kept only if the red test of PR6a shows another path to a bot seat.** | [V] code; effect [H] |
| I6 | Error state of a refused command: Kobo and Morris attach the sender's state when a game runs, Bullshit and BC send `null` (`BullshitWebSocketController:53`, `BatailleCorseWebSocketController:96`). **Decision (2026-10-12): the state is attached everywhere when a game runs. This is a deliberate observable change for Bullshit and Bataille Corse, made in PR4 (`SeatCommands`) with a before/after contract test and a check of the frontend clients.** | [V] |
| I7 | BC errors go to the whole game topic, including those caused by an invalid token (`BatailleCorseWebSocketController:124-137`); the others answer the sender alone. Legacy path, deleted or reshaped by lobby PR9. **Decision (2026-10-12): after lobby PR9, BC errors go to the sender alone.** | [V] |
| I8 | Logging: `System.err.println` at 16 sites in 6 classes (four game WebSocket controllers, `LifecycleController`, `LobbyController`); SLF4J in 5 others. Broad `catch (Exception)` around every handler. | [V] |
| I9 | `SeatNames` port lives in `bullshit.presentation.bot`; Kobo and Morris take a raw `Function<GameId, Map<PlayerId, String>>` for the same session fact. | [V] |
| I10 | Casts on `startGame` (`(Kobo) sessionService.startGame(...)`, same in the three others) because `SessionService.startGame` returns `Game`, although `getGame(id, Class<T>)` exists. | [V] |
| I11 | The Bullshit bot task does not check that its game is still the room's live game (`BullshitBotCoordinator:215`); BC does (`BatailleCorseBotCoordinator:210-217`). The `isFinished` check probably covers it. | [V] code; harmless [H] |
| I12 | Tests of `sessionmanagement` import presentation (`PresenceServiceTest:12-23`) and concrete games (`GameFactoriesTest`, `SessionService*Test`) although `FakeGame` exists. Main code respects the rules (grep: no game or presentation import in `sessionmanagement` or `game`, no presentation or session import in any `*/domain`). | [V] |
| I13 | Lifecycle broadcasts run outside the game lock (`PresenceService:130-133`), known as M5 in PR #115. | [V] |

### 1.3 Not duplication (differences that are real)

- **Outcome DTOs** have three wire shapes: Bullshit `Won(winnerId: String)`, Kobo `Finished(winners: List<Integer>, reason)`, Morris `Finished(winner: Integer, reason)`; BC uses `winner: PlayerIdDto`. Unifying them is a contract change for the frontend. **[V]**
- **BC state** is one public DTO on `/topic/game/{id}`; the others are per-seat private DTOs. **[V]**
- **Domain exceptions, `Action` enums, event types, `version()` counters** are each game's own language.
- **Bot strategies** and their timing (tiers and auto-grab for BC, memory and call delay for Bullshit).

## 2. Principles

1. **Wire contract frozen.** Destinations, payloads, JSON shapes, HTTP statuses and messages stay as
   they are, except the deliberate changes named in the plan, each called out in its PR with a test:
   I3 (start checks the game type), I5/X4 (`/lobby/addBot` refused for a game without bot), I6 (state
   attached to error replies everywhere) and I7 (BC errors to the sender alone, after lobby PR9).
2. **Dependency rules unchanged and enforced.** `sessionmanagement` and `game` know no game and no
   presentation; a game's `domain` knows no session, transport or Spring. Shared transport code goes
   to `org.kevinkib.cardgames.presentation`, never to `sessionmanagement`. An architecture test
   locks it before any move.
3. **Composition over inheritance.** Shared helpers are plain objects that per-game classes call.
   No abstract controller: each game keeps its own controllers with explicit `@MessageMapping` and
   `@RequestMapping`, so a destination is still found by a grep.
4. **Rule of three.** Extract only what at least three games share identically today. Two copies wait
   for the third (bots).
5. **Bataille Corse after lobby PR9.** Its legacy paths (`/create`, `/rematch`, seat-1 join, tokenless
   GET, `SessionRestController`, `GameMode`) are deleted by PR9 of the shared-lobby plan; they are not
   refactored here.
6. **Rules stay in the domain.** Event-type choice per command (for example Kobo's `ROUND_END`) stays
   in each game's `*GameActions`.

## 3. Target

All new types live in `org.kevinkib.cardgames.presentation` (sub-packages named below), next to
`GameMessagingService` and `LobbyBroadcaster`.

| Target | Replaces | Games |
|--------|----------|-------|
| `presentation.SeatNames` (moved from `bullshit.presentation.bot`) | I9 | Bullshit, Kobo, Morris |
| `presentation.state.GameStateListener<G extends Game>` | `BullshitStateListener`, `BatailleCorseStateListener` | all four |
| `presentation.state.SeatStateBroadcaster<G extends Game, D>`: `stateFor(game, viewer)`, `broadcast(...)` to human seats only, then listeners. Built with `GameMessagingService`, `BotSeats`, `SeatNames` and a `SeatProjection<G, D>` (`forViewer(game, viewer, names, bots)`) | D1, I5 | Bullshit, Kobo, Morris |
| `presentation.state.SeatLifecycleBroadcaster<G>`: implements `GameLifecycleBroadcaster` for a `Class<G>` over a `SeatStateBroadcaster` | D2 | Bullshit, Kobo, Morris |
| `presentation.command.SeatCommands`: authenticates a seat (unknown or malformed id, unknown token: ignored, logged at debug; a malformed token is already an unknown one since #155), runs the action, answers the sender alone with `ErrorResponse(type, message, state)`, the sender's state being attached whenever a game runs (I6 decision); one SLF4J logger | D4, I2, I8 | Bullshit, Kobo, Morris (BC after PR9) |
| `SessionService.startGame(GameId, String token, Class<T>)` returning `T` (no cast in controllers); every `start` handler checks the room's game type **before** starting, Bullshit included | D5, I3, I10 | all four |
| `presentation.rest.SeatGameRest`: `state(gameType, id, token, stateFor)`, `join(gameType, id, payload)`, `playAgain(gameType, id, payload)` returning `ResponseEntity`, with the lobby JOIN broadcast | D3, D9 | Bullshit, Kobo, Morris (BC join/play-again after PR9) |
| `presentation.dto.CardDto` (record, null-safe) and `presentation.dto.CardLabels` (rank and suit from their published labels) | D7, D8 | BC, Bullshit, Kobo |
| Per-game `@Configuration` classes in `config` (`BatailleCorseConfig`, `BullshitConfig`, `KoboConfig`, `ThreeMenMorrisConfig`), `GameFactories` built from every `GameFactory` bean | D11 | all four |
| Test kit in `src/test/.../presentation/support`: `RecordingMessaging`, `StompTestClient` (with `Frames`), `SessionTable` | D12, D13, D14 | all tests |
| `ArchitectureTest` (dependency rules of the context map), written with ArchUnit (`archunit-junit5`, test scope; decided, no home-made scan) | guards principle 2 | all |
| `GameFactory.supportsBots()` (kernel) and the refusal of `/lobby/addBot` for a game without bot | X4, I5 | session core, Kobo, Morris |

Each game keeps: its controllers (thin, explicit mappings, payload mapping, `required(...)` checks),
its `*GameActions` (command, event type, message), its DTO projection, its outcome DTO, its bots.

## 4. What we do not do

- **No generic `GameActions` or command bus.** The shared part is five lines; the event-type rules are
  the game's.
- **No shared `OutcomeDto`.** Three wire shapes; merging them needs a frontend change and a product
  decision. Postponed (owner, 2026-10-12): reconsider when the Kobo (#149) and Three Men's Morris
  (#147) frontends are planned.
- **No abstract base controller and no `/{gameType}/...` dynamic destinations.** They hide routes and
  tie games together through Spring inheritance.
- **No `GameModule` registry object.** The per-game `@Configuration` class is the registration.
- **No `version()` in the `Game` kernel** and no shared bot coordinator until a third game has bots.
- **No shared domain exceptions** (`FinishedGameException`, `NotPlayersTurnException` stay per game).
- **No change to BC's public-topic broadcast** nor to its lifecycle broadcaster (forfeit reasons,
  `rematchStarted`) before PR9 decides the BC flow.
- **No move of lifecycle broadcasts under the game lock** (I13) without a dedicated decision.

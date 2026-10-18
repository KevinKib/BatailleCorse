# Backend Context Map

Four game contexts, a session context, a shared kernel and a shared presentation layer, under
`backend/src/main/java/org/kevinkib/cardgames/`. Naming rules: `game-conventions.md`; how to add a game:
`add-a-game.md`.

| Package | Role |
|---------|------|
| `game` | Shared kernel: `Game`, `GameFactory`, `GameId`, `PlayerId`, `GameOptions`, `OptionDescriptor`. Knows no concrete game. |
| `sessionmanagement` | `core` (seats, tokens, create / join / start / play again, bots as flagged seats) and `presence` (connection, grace period, forfeit). Depends only on `game`. |
| `bataillecorse`, `bullshit`, `kobo`, `threemenmorris` | One bounded context per game: `domain` (pure rules) and `presentation` (controllers, use cases, DTOs, bots). No game knows another. |
| `presentation` | Shared driving adapters: lobby, presence, session REST, WebSocket configuration, `SeatCommands`, `SeatGameRest`, the seat broadcasters, bot ports. Knows no concrete game. |
| `config` | `AppConfig` (shared beans) and one `<Game>Config` per game. |

```mermaid
graph TB
    subgraph Pres["🌐 presentation (shared driving adapters)"]
        LOBBY["LobbyRestController · LobbyController<br/>LobbyBroadcaster · LifecycleController"]
        SEAT["SeatCommands · SeatGameRest<br/>SeatStateBroadcaster · SeatLifecycleBroadcaster"]
    end

    subgraph Games["♟️ game contexts (upstream): domain + presentation"]
        BC["bataillecorse"]
        BS["bullshit"]
        KO["kobo"]
        TM["threemenmorris"]
    end

    subgraph SessionMgmt["📦 sessionmanagement (core + presence)"]
        SS["SessionService · GameFactories"]
        PS["PresenceService · GameLifecycleBroadcaster (port)"]
        REPO["SessionRepository (port) → InMemorySessionRepository"]
        SS --> REPO
    end

    subgraph Kernel["🔩 game (shared kernel)"]
        K["Game · GameFactory · GameId · PlayerId"]
    end

    CFG["⚙️ config: AppConfig + one Config class per game"]

    LOBBY --> SS
    Games -->|controllers call| SS
    Games -->|reuse| SEAT
    Games -->|implement| K
    SessionMgmt --> K
    CFG -. "GameFactory and GameLifecycleBroadcaster beans" .-> SS & PS

    style Games fill:#1a3a2a,stroke:#4ade80,color:#fff
    style SessionMgmt fill:#1a2a3a,stroke:#60a5fa,color:#fff
    style Pres fill:#2a1a3a,stroke:#c084fc,color:#fff
    style Kernel fill:#3a3a1a,stroke:#facc15,color:#fff
```

The ArchUnit test `ArchitectureTest` locks these arrows; see "Shared backend building blocks".

## Bounded Contexts

**BatailleCorse** — upstream, pure game rules. `BatailleCorse` is the aggregate root, `BatailleCorseFactory` its factory (type `bataille-corse`). No knowledge of sessions, tokens, or transport. It is the legacy outlier: its REST base (`/api/game/{id}`) and commands (`/app/send`, `/app/slap`, `/app/grab`) carry no game segment, and it keeps its own `BatailleCorseStateBroadcaster` (one public state for the room) and `BatailleCorseLifecycleBroadcaster` instead of the seat broadcasters; its controllers do use `SeatCommands` and `SeatGameRest`.

**BatailleCorse computer seats** — a room seat held by a bot is played by the server. The pure part lives in `bataillecorse/domain/bot` (`BatailleCorseBotTier`: the nine tiers of the former local AI; `BatailleCorseBotStrategy`: slap, send or nothing, read from the rules in force). `BatailleCorseBotCoordinator` (`presentation/bot`) listens to the `BatailleCorseStateBroadcaster`, schedules the bot after its tier reaction time (seat option `tier`, 0 to 8, default 1, plus `bataillecorse.bots.extra-delay-ms`), re-checks `BatailleCorse.version()` under the game monitor before acting, and collects a won pile for the bot after 1500 ms (`autoGrabPlayer()`). It uses the same `BatailleCorseGameActions` as a human, starts on START, stops on game end, forfeit or eviction, and restarts on the next START after Play again. `bataillecorse.bots.enabled=false` switches it off.

**Bullshit** — upstream game rules (`org.kevinkib.cardgames.bullshit`), depending only on the `game` kernel and `frenchcards`. `Bullshit` is the aggregate root; the claim/match mechanic sits behind a pluggable `ClaimMode` strategy (ascending-rank over the deck in play, or a suit variant that draws a random starting suit each round through an injected `RandomGenerator`); the deck (`DeckSize`: 32 cards from 7 to Ace, or 52) is a creation option. Played through `BullshitWebSocketController` (`/app/bullshit/start`, then the unprefixed legacy commands `/app/discard` and `/app/callBullshit`) and `BullshitRestController` (`/api/bullshit`), with bots (next section).

**Session Management** — downstream of the kernel only: it never learns a concrete game. Two sub-contexts:
- *core* — `SessionService` creates rooms through `GameFactories` (built from every `GameFactory` bean), and `SessionGame` ties a game ID to its seats (`SessionPlayer`, humans or bots); `SessionToken` maps each seat to a secret token that authenticates actions. `SessionRepository` is a port, `InMemorySessionRepository` the adapter.
- *presence* — `PresenceService` tracks connections, the grace period and forfeits, and reaches each game through the `GameLifecycleBroadcaster` port (one bean per game, collected by `GameLifecycleBroadcasters`).

**presentation** — the shared driving adapters (lobby, presence, session REST, WebSocket configuration, `SeatCommands`, `SeatGameRest`, the seat broadcasters, bot ports). No domain logic and no concrete game; each game adds its own controllers in `<game>.presentation`.

## Bullshit bot players

Spec: `docs/specs/2026-10-10-bullshit-bots-design.md`. A bot is a flagged seat of the game-agnostic session core; how it plays lives in the Bullshit context behind a port, and the glue that knows both sits in the Bullshit presentation layer, like the WebSocket controller.

```mermaid
graph TB
    subgraph Pres["🌐 bullshit.presentation"]
        CTRL["BullshitWebSocketController<br/>(discard, call, addBot, removeBot)"]
        ACT["BullshitGameActions<br/>(shared use cases)"]
        BCAST["SeatStateBroadcaster<Bullshit, BullshitDto><br/>(skips bot seats, notifies listeners)"]
        COORD["bot.BullshitBotCoordinator<br/>(thinks after a delay, drops stale tasks)"]
        SCHED["bot.BotScheduler / ThinkingDelay /<br/>BotSeats (ports)"]
        CTRL & COORD --> ACT --> BCAST
        BCAST -. "onBroadcast" .-> COORD
        COORD --> SCHED
    end

    subgraph BS["🃏 bullshit.domain.bot (pure rules)"]
        STRAT["BotStrategy (port)<br/>ProbabilisticBotStrategy"]
        OBS["BotObservation<br/>(what a seat sees, nothing more)"]
        MEM["BotMemory · PlausibilityEstimator"]
        STRAT --> OBS & MEM
    end

    subgraph Sess["📦 sessionmanagement.core"]
        SG2["SessionGame / SessionPlayer<br/>(bot flag, claimBot, removeBot)"]
        SS2["SessionService<br/>(addBot, removeBot, botSeats)"]
        SS2 --> SG2
    end

    COORD --> STRAT
    CTRL --> SS2
    SCHED -. "BotSeats = SessionService::botSeats" .-> SS2

    style Pres fill:#2a1a3a,stroke:#c084fc,color:#fff
    style BS fill:#1a3a2a,stroke:#4ade80,color:#fff
    style Sess fill:#1a2a3a,stroke:#60a5fa,color:#fff
```

- **Session core**: a bot seat counts as joined, has no presence and no forfeit timer, and its internal token never resolves (`findPlayerByToken`, `findTokenByPlayer`) nor appears in a lobby view. Only the host adds or removes bots, before the game starts, and only for a game whose factory `supportsBots()`; a bot can be removed only where no human sits after it (`LobbyView.removableBotSeats`), so the dealt seats stay contiguous. Play again reopens the lobby with the former bots re-seated in order right after seat 0, with their names and seat options; humans come back one by one and the first caller is the host.
- **Bullshit domain**: the strategy only ever receives a `BotObservation`, built to carry exactly what `BullshitDto.forViewer` shows (a parity test and a structural test enforce it). `Bullshit.version()` lets asynchronous bot tasks detect that the table moved on.
- **Dependencies**: `sessionmanagement` never learns a game; the coordinator reaches the session core only through the `BotSeats` port (`SessionService::botSeats`) and the eviction listener.


## Kobo

Spec: `docs/specs/2026-10-11-kobo-backend-design.md`. A third upstream game-rules context (`org.kevinkib.cardgames.kobo`), plugged into the session core through a `KoboFactory` collected by `GameFactories`; nothing changes in `sessionmanagement`.

**Fixed deal for e2e** — the deal comes from the `DeckSource` port of `kobo/domain` (`RandomDeckSource` by default). Under the Spring `test` profile only, `ScriptedDeckSource` and `KoboTestDealController` (`PUT`/`DELETE /api/test/kobo/{gameId}/deal`, `{"round":1,"cards":["KH","10S",...]}` in deal order, seat 0 first) let a test choose the leading cards of a round before `start`; the route does not exist in any other profile. This is the model for any random game (`add-a-game.md`, Determinism).

```mermaid
graph TB
    subgraph KP["🌐 kobo.presentation"]
        KWS["KoboWebSocketController (/kobo/...)"]
        KREST["KoboRestController (/api/kobo)"]
        KACT["KoboGameActions<br/>(call + touch + broadcast under the game monitor)"]
        KBC["SeatStateBroadcaster<Kobo, KoboDto><br/>(one KoboDto per seat)"]
        KLC["SeatLifecycleBroadcaster<Kobo>"]
        KWS --> KACT --> KBC
        KREST --> KBC
        KLC --> KBC
    end

    subgraph KD["🂡 kobo.domain (pure rules)"]
        KOBO["Kobo (aggregate, synchronized)<br/>phases · tableaux with slot revisions"]
        SCORE["RoundScoring · CardPoints · Power"]
        KOBO --> SCORE
    end

    subgraph SM["📦 sessionmanagement"]
        SS3["SessionService · PresenceService"]
    end

    KACT --> KOBO
    KACT --> SS3
    KLC -. "GameLifecycleBroadcaster port" .-> SS3

    style KP fill:#2a1a3a,stroke:#c084fc,color:#fff
    style KD fill:#1a3a2a,stroke:#4ade80,color:#fff
    style SM fill:#1a2a3a,stroke:#60a5fa,color:#fff
```

- **Slots and revisions**: every command addresses a card as `SlotRef(seat, slot)`; each slot has a `revision` bumped when its card changes. The server never learns what a player remembers; clients drop what they knew of a slot whose revision moved.
- **The race**: any player may try to discard a card (own or another's) that matches the rank on top of the discard pile. Commands go through the `synchronized` aggregate, so the first message processed wins. A losing attempt (slot already emptied or refilled, or the pile moved) is rejected without penalty (`expectedTopRank`, `expectedRevision`); only a real rank error costs a penalty card.
- **Privacy**: `KoboDto.forViewer` is the only projection. Face-down values appear only in the viewer own DTO (starting cards before the first draw, the drawn card while deciding, the last peek) and in everyone's DTO once the round is revealed.
- **Frontend**: registered in the game selection, with its lobby and game screen (`KoboSession`, `Kobo.store`, `view/kobo/KoboGameScreen.vue`); the table itself is in progress (issue #149). Bots: see "Kobo bots" below.

### Kobo bots

Spec: `docs/specs/2026-10-14-kobo-bot-design.md`. `KoboFactory.supportsBots()` is true and declares the seat option `level` = `easy|medium|hard` (default `medium`, label `options.kobo.level`). The strategy side is pure, in `kobo.domain.bot` (B1): `KoboBotView.forSeat(game, seat)` (what a human at that seat sees, in domain types; a presentation test, `KoboBotViewParityTest`, pins that it equals that seat's `KoboDto`), `KoboBotMemory` (per seat, `(slot, revision) -> card`, never wrong, only incomplete), `KoboBotStrategy` (`decide`, `matchFor`), `HeuristicKoboBot` and `KoboBotLevel` / `KoboBotTuning` (the level only picks the constants).

`kobo.presentation.bot.KoboBotCoordinator` (a `GameStateListener` and a `GameEvictionListener`, like the Morris one) owns, per game, one memory per bot seat and two kinds of tasks, all handled under the game monitor:
- **Memory feed**, on every state change and in this order: the public moves of the broadcast's `KoboEventData` (`BLIND_SWAP` and `KING_SWAP` call `onSwapped`, a successful `MATCH` with a gift calls `onMoved`, the bot's own `SWAP` calls `onOwnDrawnPlaced`, its own `END_TURN` or `KOBO` makes it forget), then `observe(view)`.
- **Turn tasks** (a barrier to ready, the seat's turn step, or the gift), after the `ThinkingDelay` (`kobo.bots.delay-min-ms` / `delay-max-ms`, 600 to 1200 ms per step); keyed on the game version and dropped if the state moved. A strategy failure or a refused decision falls back to one fixed legal action per phase (`READY`, `DRAW`, `DISCARD_DRAWN`, `SKIP_POWER`, `END_TURN`, `GIVE_TEN`); a King swap refused because the peeked card moved (`StaleSlotException`, `EmptySlotException`) falls back to `SKIP_POWER`.
- **Match tasks** (the race), keyed on the discard top and not on the version: a bot that remembers a card of the top's rank reacts after the `ReactionDelay` of its level (`UniformReactionDelay`, the tuning's bounds times `kobo.bots.reaction-scale`); an unrelated action (a draw, a failed match) does not restart it, only a new top cancels it. At firing it re-checks the top and that its memory still holds the card at the same revision; a refusal by the game is a lost race, ignored without fallback or penalty.
- A table is dropped when the game finishes, is evicted, is reopened under the same id (Play again, fresh memories), or has no human left.

`KoboConfig` gives the broadcaster `sessionService::botSeats` (a bot seat gets no message; `KoboDto.PlayerDto.bot` is true for bot seats and omitted from the JSON otherwise), the `koboBotRandom` generator (`ThreadLocalRandomGenerator`; a fixed-seed `Random` under the `test` profile, not a constant, so a medium bot's forgetting still varies) and registers the coordinator. Under the `test` profile both delays and the reaction scale are 0.

## Three Men's Morris

Spec: `docs/specs/2026-10-11-three-men-morris-backend-design.md`. A fourth upstream game-rules context (`org.kevinkib.cardgames.threemenmorris`), two players, plugged into the session core through a `ThreeMenMorrisFactory` (type `three-men-morris`, lobby option `variant` = `tapatan|achi|picaria`, default `tapatan`) collected by `GameFactories`; nothing changes in `sessionmanagement`. `ThreeMenMorrisWebSocketController` (`/app/three-men-morris/start|place|move`, over `SeatCommands`) and `ThreeMenMorrisRestController` (`/api/three-men-morris`, over `SeatGameRest`) authenticate by seat token and delegate to `ThreeMenMorrisGameActions` (call, touch, one broadcast of a `ThreeMenMorrisDto` per seat through a `SeatStateBroadcaster<ThreeMenMorris, ThreeMenMorrisDto>`); a `SeatLifecycleBroadcaster<ThreeMenMorris>` serves presence and forfeit. Both are beans of `config/ThreeMenMorrisConfig.java`. The rules (board, placement, movement, lines, blocked player) live in the `ThreeMenMorris` aggregate. The variants are data, not classes: `MorrisVariant` maps an option key to a `MorrisRules` (`Geometry`, pieces per player, `PlacementRule`): Tapatan (3 pieces), Achi (4 pieces, one hole) and Picaria (3 pieces, four short diagonals, centre closed during the whole placement phase, reachable by a move). The DTO adds `variant` and `legalPlacements` only for Achi and Picaria, so the Tapatan wire is byte-identical to v1. Frontend: registered and playable (`docs/specs/2026-10-13-three-men-morris-frontend-design.md`). Bots: see "Three Men's Morris bots" below (v2: `docs/specs/2026-10-14-three-men-morris-v2-design.md`, issue #147).

### Three Men's Morris bots

Same pattern as the Bullshit bots, with levels as a seat option. `ThreeMenMorrisFactory.supportsBots()` is true and declares the seat option `level` = `random|greedy|perfect` (default `greedy`, label `options.threeMenMorris.level`). In `threemenmorris.domain.bot` (pure): `MorrisBot` (port: `decide(position, seat, drawOfferPending)` returns a `MorrisDecision`: `Place`, `Step`, `AcceptDraw`, `DeclineDraw`), `RandomBot`, `GreedyBot` (takes a line, else blocks the opponent's next one), `PerfectBot` (reads the solver), `MorrisBotLevel` (option value to strategy), `MorrisTransitions` (the rules as a pure function of a `MorrisPosition`, checked against the aggregate by random games) and `MorrisSolver` (retrograde analysis per `MorrisRules`: win, loss or draw with a depth for every reachable position, cached per rules, a few milliseconds). Every level answers a draw offer with one rule: accept when the solver says the position is a draw or a loss for the bot, decline when it is a win; bots never offer a draw. In `threemenmorris.presentation.bot`: `MorrisBotCoordinator` (a `GameStateListener` and a `GameEvictionListener`): after each broadcast it schedules one task per bot seat that must answer an offer or play, after the `ThinkingDelay` (`threemenmorris.bots.delay-min-ms` / `delay-max-ms`, 700 to 1300 ms, 0 under the `test` profile); the task re-checks the game version under the game monitor and acts through `ThreeMenMorrisGameActions`; a failing strategy falls back to the first legal action. `ThreeMenMorrisConfig` gives the broadcaster `sessionService::botSeats` (a bot seat gets no message) and registers the coordinator. Under the `test` profile the Morris generator is constant, so `greedy` and `perfect` are deterministic.

## President

Spec: `docs/specs/2026-10-15-president-design.md`, plan: `docs/plans/2026-10-15-president-plan.md`. An upstream game-rules context (`org.kevinkib.cardgames.president`), 3 to 8 players, plugged into the session core through a `PresidentFactory` (type `president`, computer seats with a `level`) collected by `GameFactories`; nothing changes in `sessionmanagement`. Lobby options (strict parse, unknown keys refused): `afterPass`, `firstLead` (default `kingOfHearts`), `exchange`, `giveBack`, `nextLead`, `scoring`, `length` (`rounds-5` by default, `endless` never ends). `equalPlay` (V1) and the V2 trick options are declared to the lobby: `forbiddenFinish` (`lastPlace|off`, default `lastPlace`) and `mustPlay` (`off|on`); the other variant options (runs, revolution, ...) exist in `PresidentOptions` as off values but are not declared until their PRs. `PresidentWebSocketController` (`/app/president/start|play|pass|giveBack|ready`, over `SeatCommands`) and `PresidentRestController` (`/api/president`, over `SeatGameRest`) authenticate by seat token and delegate to `PresidentGameActions` (call, touch, one broadcast of a `PresidentDto` per seat through a `SeatStateBroadcaster<President, PresidentDto>`); a `SeatLifecycleBroadcaster<President>` serves presence and forfeit. All are beans of `config/PresidentConfig.java`. The DTO carries the viewer's own hand, exchange cards and legal plays only; other hands, and the cards of another pair's exchange, appear nowhere. The first player out closes the trick (`TrickEndReason.FIRST_OUT`). Deal seam: `DeckSource` is a `RandomDeckSource` in production; under the `test` profile it is a `ScriptedDeckSource` driven by `PresidentTestDealController` (`PUT|DELETE /api/test/president/{gameId}/deal`), not mapped in any other profile. Frontend: entry PR F2 (registry, lobby with the Classic card and Customise through the shared options slot, table, minimal exchange and round-over placeholders). Bot strategy (`president/domain/bot`, pure; wired to the table by B2, see "President bots"): `PresidentBotView.forSeat(game, seat)` (own hand, other hands as sizes, public trick, legal plays), `PresidentBotMemory` (cards seen played, exchange cards; emptied when a new round is dealt), `PresidentBotStrategy.decide(view, memory)` returning a sealed `PresidentBotDecision` (`Play`, `Pass`, `GiveBack`, `Ready`), `HeuristicPresidentBot` and the `easy|medium|hard` `PresidentBotLevel` (`DEFAULT = MEDIUM`) with their `PresidentBotTuning`. Candidates come from the domain's legal plays, so a rule change cannot make a bot play an illegal card. The frontend offers bots (Add bot, seat option `level`: Easy, Medium, Hard, `options.president.level`; bots are labelled "Bot N" through the shared `seatDisplayName`). Not covered yet: the variant rules (V3 and V4).

### President bots

`PresidentFactory.supportsBots()` is true and declares the seat option `level` = `easy|medium|hard` (default `medium`, label `options.president.level`). The lobby refuses variant options, so a bot only plays the Classic rules. `PresidentDto.PlayerDto.bot` (true for a computer seat, omitted from the JSON otherwise) is the only change to the wire contract. A presentation test, `PresidentBotViewParityTest`, pins that `PresidentBotView` equals the seat's `PresidentDto` field for field in every phase.

`president.presentation.bot.PresidentBotCoordinator` (a `GameStateListener` and a `GameEvictionListener`, like the Kobo one) owns, per game, one strategy and one memory per bot seat, all handled under the game monitor:
- **Memory feed**, on every state change: the cards named by the broadcast's `PresidentEventData` first (the last play of a closed trick is already in the discard and no longer in the trick, so the seat view alone would miss it; `PresidentBotMemory.learnPlayed`), then `observe(view)`.
- **One task per bot seat that has a step**, after the `ThinkingDelay` (`president.bots.delay-min-ms` / `delay-max-ms`, 700 to 1300 ms): its turn (play or pass), the cards it owes back in the exchange, or `ready` at the barrier. A task is keyed on its step: during play the game version (any move makes the plan stale), during the exchange and at the barrier the phase and the round only, so one bot's give back or `ready` does not restart the thinking of the others. The next broadcast cancels the tasks whose step is gone; a task that fires on another state (the state moved without a broadcast) does nothing.
- **Decision and application in one go under the monitor** (the heuristic is cheap, unlike the Mangola search), so a decision is never stale. A strategy that throws, or a decision the game refuses, falls back to one fixed legal action: the lowest legal set when leading, a pass when following, the lowest cards in the exchange, `ready` at the barrier.
- A table is dropped when the match finishes, is evicted, is reopened under the same id (Play again: fresh memories, same seat and level), or has no human left among the active players (a forfeited human leaves `getPlayerIds()`).

`PresidentConfig` gives the broadcaster `sessionService::botSeats` (a bot seat gets no message), the `presidentBotRandom` generator (`ThreadLocalRandomGenerator`; a fixed-seed `Random` under the `test` profile, not a constant, so an easy bot still varies) and registers the coordinator. Under the `test` profile both delays are 0.

### President equal play and the first out-of-turn command

Equal play is a fixed rule (it was the `equalPlay` option until 2026-10-18; the king of hearts lead of round 1 and the forbidden finish are fixed the same way, and the strict parse refuses `equalPlay`, `firstLead` and `forbiddenFinish` like any unknown key). An equal set (same rank and count) is a legal answer and binds the next player (`Constraint.MATCH_OR_PASS`, repeated along the chain; the first pass ends it); the four cards of a rank played in succession close the trick (`TrickEndReason.QUAD`) and the last player leads; a quad played in a single play closes the trick the same way (`QUAD`, or `FIRST_OUT` when it takes 1st place, the next leader being the seat right after the player who still holds cards; a fixed rule, with no option). `/app/president/play` is the game's first command that a seat may send when it is **not** on turn: `Round.play` accepts it only as the completion of the open chain (`Completion`, offered by `President.completion(seat)` to the holder of the missing cards). The window opens with the second play of a chain (a lone play never opens it) and closes with the next play or the end of the trick; a later or wrong attempt raises `CompletionClosedException` (any other out-of-turn play stays `NotPlayersTurnException`), which is answered to the sender alone. Wire contract, additive: `trick.constraint|requiredRank|requiredCount|chain` (public), `completion` (the holder's DTO only, omitted otherwise), action `COMPLETE`, event `COMPLETION`. Bots read the same `completion` in their view; the coordinator schedules a completion task for a bot that is not the current seat and drops it when the game refuses it.

V2 (trick variants, #256). `PlayEffects.clearReason` (pure, `president.domain.play`) says whether a play closes the trick and why: `CUT` (n twos on n cards, any n, or n-1 twos on a pair or a triple with the option `reducedCut`; never under the equal-play binding unless 2 is the bound rank; 2s led, one to three, also close; including when it is the last play of the hand (a forbidden finish: no place, ranked last, but the 2s still close the trick, owner 2026-10-18 reversing #262); owner feedback 2026-10-18 replacing "a lone 2 always cuts") or `QUAD` (a quad played at once). The cuts are fixed rules; only `reducedCut` is an option. `BeatRule` and `LegalPlays` take the `reducedCut` flag and list the 2s that cut. Forbidden finish (a fixed rule): a last play containing the top rank takes no place (`PlayerOut(seat, 0, true)`); the player is placed last when the round ends, the later forbidden finisher lower, the seat still holding cards just above them. A forbidden finisher never closes the trick, whatever the play (a lone 2 or a quad of 2s that empties the hand is no `CUT` or `QUAD`; the trick ends by the normal passes, `ALL_PASSED`); only the player taking 1st place does (`FIRST_OUT`). `mustPlay` makes `Round.pass` raise `MustPlayException` while a legal play exists (a completion is never compulsory) and `availableActions` omits `PASS`. Wire, additive: `players[].forbiddenFinish` (out at once, `place` null until the round ends), `lastRound.players[].forbiddenFinish`, event data `outForbidden` and `trickEndReason = CUT`. The heuristic bot keeps a cutting 2 and avoids a forbidden finish; when the pass is refused it plays its weakest legal play.

## Mangola

Spec: `docs/specs/2026-10-15-mangola-design.md`, plan: `docs/plans/2026-10-15-mangola-plan.md`. A game-rules context (`org.kevinkib.cardgames.mangola`), two players, plugged into the session core through a `MangolaFactory` bean (type `mangola`, lobby option `firstPlayer` = `host|guest|random`, default `random`; label `options.mangola.firstPlayer`; there is no set-up option, the set-up is always free placement: owner decision 2026-10-15) collected by `GameFactories`; nothing changes in `sessionmanagement`. The domain (N1, N2) holds the sowing engine and the `Mangola` aggregate with its `PLACEMENT` → `PLAY` → `FINISHED` phases.

- **Endpoints**: `MangolaRestController` (`/api/mangola/game/{id}`, `/join`, `/play-again`, over `SeatGameRest`) and `MangolaWebSocketController` (`/app/mangola/start` with the game-type check, then `arrange` (`counts`: 16 integers in the order of the seat's loop), `confirmLayout`, `editLayout`, `sow` (`pit`: 0..31), over `SeatCommands`). A refused command answers the sender alone with an `ErrorResponse` carrying its own projection.
- **Broadcasters** (beans of `config/MangolaConfig`): `SeatStateBroadcaster<Mangola, MangolaDto>` (given `sessionService::botSeats`: a bot seat gets no message), `MangolaGameActions` (call, touch, one broadcast under the game monitor) and a `SeatLifecycleBroadcaster<Mangola>` for presence and forfeit. Events (`MangolaEventType`): `START`, `LAYOUT_CHANGED`, `LAYOUT_CONFIRMED`, `LAYOUT_WITHDRAWN`, `LAYOUTS_REVEALED`, `SOWN` (data: `seat`, `pit`, `trace`, `captured`), `GAME_OVER` (the command that ended the game, or a forfeit).
- **Hidden placement**: `MangolaDto.forViewer` is the only projection. During the placement a seat sees its own draft in `board` and `null` for each of the opponent's 16 pits, plus `ready` for both seats; the opponent's `seeds` is `null` too. Both halves become public when both seats are ready (the reveal, one version bump). A game that ends during the placement (forfeit) never reveals the drafts. `LAYOUT_CHANGED` carries only the seat: the shared `SeatStateBroadcaster` sends every event to every human seat, so the other seat receives the event and a state in which nothing of the draft shows (it learns that a draft changed, never what it is). `draftOnlyEndless` (viewer's own draft, placement only) warns that every move of the draft is endless when played alone.
- **DTO fields**: `started`, `id`, `gameType`, `version`, `phase`, `currentSeat`, `seatA` (the seat owning rows 1 and 2, who starts), `board` (32 pits, `row * 8 + column`), `players[]` (`seat`, `name`, `current`, `side`, `seeds`, `ready`), `legalPits` (viewer, in play), `availableActions` (`ARRANGE`, `CONFIRM_LAYOUT`, `EDIT_LAYOUT`, `SOW`), `lastMove` (`seat`, `pit`, `trace` of `PICK`, `DROP`, `SKIP_EMPTY`, `CAPTURE`, `RELAY`, `RESUME_AFTER` steps), `repetitions` (from 2), `outcome` (`ONGOING` / `FINISHED{winner, reason}`).
- **Random seam**: the `mangolaRandom` generator is a `ThreadLocalRandomGenerator` outside the `test` profile and constant (every draw 0: the host starts) under it. `firstPlayer=host` makes the starter deterministic without it; a game always starts in the placement phase, and tests that need a fixed position build it with `Mangola.fromLayout`.
- **Frontend**: entry PR N7 (registry with `supportsBots: false`, `mangola-room`, `MangolaGameScreen`: shared lobby with the two host options through the generic options mechanism, placement editor, then the board with the sowing animation and its Skip button, the end screen held until the last sowing is shown, a draw shown as `draw`).
- **Not covered yet**: the test-position seam of the end-to-end PR (the Cypress specs play from two seeds per pit instead). The frontend offers bots (Add bot, seat option `level`: Easy, Medium, Expert; the rules say Expert is strong but not perfect). Bots: see "Mangola bots" below. The architecture rules cover the package (`ArchitectureRules.GAMES`).

### Mangola bots

Same pattern as the Three Men's Morris bots (a flagged seat of the session core, the strategy in the domain behind a port, the glue in the presentation layer), with two differences that come from the game: the placement is hidden and simultaneous, and a search can take a few hundred milliseconds. `MangolaFactory.supportsBots()` is true and declares the seat option `level` = `random|greedy|expert` (default `greedy`, label `options.mangola.level`).

- **Domain (`mangola.domain.bot`, pure)**: `MangolaBot` (port: `decideLayout(rules, side, rejected)` returns 16 counts summing to 32, `decideMove(position, side)` returns a pit), `RandomMangolaBot`, `GreedyMangolaBot` (one ply: the pit that keeps the most seeds on its half, that is the biggest capture, then the most pits left to sow), `ExpertMangolaBot` (negamax with alpha-beta and iterative deepening under a time budget and a node cap, moves ordered by captures, the best move of the previous depth first; the evaluation is the seed difference, the pits that can be sown, and the captures on offer minus those exposed; a finished game is worth plus or minus a million minus the plies, a draw by an endless sowing 0), `MangolaBotLevel` (option value to strategy). It is strong but not perfect: a position is 32 pit counts, no table of solved positions exists. Every tie goes through an injected `RandomGenerator`; the first depth is always completed, so a bot always answers, whatever the budget. A bot never plays an endless sowing when the rules make it illegal (it only sees the legal moves of the domain).
- **Hidden placement**: the port has no parameter for the opponent's draft, so a bot cannot peek; it only receives the rules, its own side (side A starts) and the layouts the game already refused it. The expert scores its candidate layouts (a catalogue of patterns, random layouts, mutations of the best) with a shallow search against a few typical opponent layouts. A confirmation that completes the pair and leaves the starting seat without a move is refused with `StuckStartException`: the coordinator retries with another layout (the refused ones are passed back, and a deterministic catalogue keeps the choice varied even with a constant generator), up to 8 times, then waits; the memory of refusals is dropped as soon as the opponent takes its confirmation back.
- **Presentation (`mangola.presentation.bot`)**: `MangolaBotCoordinator` (a `GameStateListener` and a `GameEvictionListener`). After each broadcast it schedules, after the `ThinkingDelay`, one task per bot seat that must arrange and confirm (placement) or sow (its turn). Unlike Morris, **the decision is computed outside the monitor of the game**: the task takes a snapshot (position, side, level) under the monitor and drops itself if the version moved, thinks without any lock, then takes the monitor again, checks the version a second time and applies the decision through `MangolaGameActions`. A human action, a forfeit or a Leave therefore never waits for a search, and a stale answer is discarded. A failing or illegal decision falls back to the first legal pit (or two seeds per pit for a layout); nothing is scheduled once the game is over, no human is left or it was evicted, and Play again gets a fresh table.
- **Wiring (`config/MangolaConfig`)**: the broadcaster is given `sessionService::botSeats`; `mangola.bots.delay-min-ms` / `delay-max-ms` (700 to 1300 ms), `mangola.bots.think-ms` (300, the expert's search time for a move) and `mangola.bots.layout-ms` (1000, for a layout), all 0 under the `test` profile (the expert then completes only its first depth). A safety node cap of 5 million applies in production.
- **Strength** (measured by `ExpertStrengthBenchmark`, run with `-Dmangola.bench=true -Dtest=ExpertStrengthBenchmark`): the expert reaches depth 7 to 9 in 300 ms. Against the greedy bot, each arranging its own half, it wins 98 to 99 games out of 100 (alternating who starts). On boards imposed by the random set-up the starter wins about 80 % of games between two greedy bots (games last about 11 plies), so the expert wins nearly every game it starts and about a third of those it does not.

## Shared backend building blocks

Spec: `docs/specs/2026-10-12-backend-mutualization-design.md`. The presentation and wiring code that the games copied from one another now lives once; each game keeps its controllers (explicit mappings), its `*GameActions`, its DTO projection and its bots.

- **`SeatCommands`** (`presentation/command`, bean in `AppConfig`): authenticates a seat from the game id and token, runs the action, and answers a refused command to the sender alone with an `ErrorResponse(type, message, state)`. An unknown or malformed id, or an unknown token, is ignored (debug log). The sender's state is attached whenever a game runs. Used by the WebSocket controllers of all four games.
- **`SeatGameRest`** (`presentation/rest`): the state / join / play-again REST endpoints of a game, used by all four REST controllers.
- **`SeatStateBroadcaster<G, Dto>` and `SeatLifecycleBroadcaster<G>`** (`presentation/state`): one DTO per seat, and presence / forfeit through the `GameLifecycleBroadcaster` port; declared per game in its configuration class (Bullshit, Kobo, Three Men's Morris; Bataille Corse keeps its own pair). `LobbyBroadcaster` sends the per-viewer lobby views of every game.
- **Architecture test**: `ArchitectureTest` (test root `architecture/`, ArchUnit `archunit-junit5`, test scope, main classes only) locks five rules: `sessionmanagement` and the `game` kernel know no concrete game, presentation or config; each game's `domain` knows no Spring, session, presentation or config (one case per game); no game depends on another game; the shared `presentation` package knows no concrete game; no cycles between top-level packages. The per-game cases come from hand-written lists (`ArchitectureRules.GAMES` and two `@ValueSource` in `ArchitectureTest`): a new game must be added there, nothing fails otherwise.
- **`CardDto` and `CardLabels`** (`presentation.dto`): one null-safe card record (`rank`, `suit`, `name`) and one parser from the published rank and suit labels back to a `Card`, shared by Bataille Corse, Bullshit and Kobo.
- **One Spring configuration per game** (`config/BatailleCorseConfig`, `BullshitConfig`, `KoboConfig`, `ThreeMenMorrisConfig`): factory, broadcasters, actions and bots of that game. `AppConfig` keeps the shared beans, and `GameFactories` is built from every `GameFactory` bean.
- **`SessionService.startGame(GameId, String token, Class<T>)`** returns the typed game, so controllers no longer cast. The game-type check stays in each `start` handler, before the call (Bullshit included: "This room is not a Bullshit game").
- **`SessionToken(String)` is tolerant** (#155): a value that is not a UUID becomes a name-based (version 3) UUID that no seat holds, so a malformed token is an unknown token everywhere (REST 403, WebSocket commands ignored) instead of an exception.
- **Shared test kit** (`src/test/.../presentation/support`): `RecordingMessaging` (records seat, game and token messages), `StompTestClient` (STOMP client for the integration tests, with `Frames` that wait on received frames), `SessionTable` (in-memory repository, `SessionService` and `RecordingMessaging` over given factories).

## Relationship

`sessionmanagement` has a **Conformist** relationship to the shared kernel: it speaks the kernel's language (`Game`, `GameFactory`, `GameId`, `PlayerId`) without translation, and never a concrete game's. The game contexts implement the kernel and know nothing of sessions; their `presentation` layer is the only place where a game meets the session core.

# Presence/Forfeiture as a Session Hexagon — Design

**Date:** 2026-06-16
**Status:** Approved (brainstorming)

## Problem

The top-level `presentation` package conflates two unrelated things:

- **Bucket A — transport/presentation adapters** (no business rules): STOMP/REST config, controllers, the `SimpMessagingTemplate` wrapper, the SUBSCRIBE interceptor, and the shared wire contracts (`Response`/`EventData`/`api` payloads/DTOs). These are honest presentation/transport.
- **Bucket B — a hidden concern with real state and rules: player presence & forfeiture.** `DisconnectForfeitService` encodes policy (disconnect → 60s grace → auto-loss; a reconnect cancels it; resign is the same terminal path), and `StompSessionSeatRegistry` / `ForfeitReasonRegistry` are **mutable stores** — repositories in all but name — sitting next to controllers with no port between them. This is the "won't evolve" smell.

Because B lives in `presentation`, the package looks like a utility/helper bag and is misnamed as a hexagonal layer while actually hiding a domain.

## Goal

Extract Bucket B into `sessionmanagement` as a properly layered sub-module (`sessionmanagement.presence`), so:
- presence/forfeiture has domain / application / port / infrastructure like the rest of `sessionmanagement`;
- the two registries become repositories behind ports (no controller-adjacent state);
- the lifecycle-broadcast seam inverts so dependencies always point inward to `sessionmanagement`;
- `presentation` is left holding only genuine adapters and wire contracts (no hidden domain).

## Decisions

1. **B folds into `sessionmanagement`, not a new bounded context.** Presence reuses session's game storage/lookup (`getGame`/`touch`) and the seat vocabulary; a peer context would have to re-port those. It can graduate later (spectators, multiple connection kinds).
2. **B is its own sub-module `sessionmanagement.presence`, with its own layers** — justified by *reason to change*, not size: presence state lives in **no** `SessionGame` field (it's in separate stores), its ports differ entirely from session-core, and its policy changes on a different axis than seating/room rules.
3. **Full hexagonal shape** for the sub-module (domain / application / port / infrastructure).
4. **Lifecycle-seam inversion.** The `GameLifecycleBroadcaster` interface moves from `presentation` into `sessionmanagement.presence.port` (a session-owned **outbound** port). Per-game broadcasters become its adapters. Session owns the lifecycle vocabulary; each game owns how the event is rendered.
5. **`presentation` stays intact** beyond losing B and the relocated port/resolver. After B leaves, what remains genuinely *is* a presentation/transport layer.
6. **Names:** `DisconnectForfeitService` → `PresenceService`; `StompSessionSeatRegistry` → port `ConnectionRegistry` + adapter `InMemoryConnectionRegistry`; `ForfeitReasonRegistry` → port `ForfeitLog` + adapter `InMemoryForfeitLog`; new port `ForfeitScheduler` (+ `TaskScheduler`-backed adapter). The `GameLifecycleBroadcaster` port **keeps its name** (well-named already; only its package moves) — refining the tentative "Notifier" rename floated during brainstorming, to limit churn.

## Out of scope (explicit follow-ups, not now)

- Renaming the residual `presentation` package (e.g. → `web`/`transport`).
- Relocating the session-specific web-adapters (`SessionRestController`, session DTOs) into `sessionmanagement` as an inbound adapter.
- Any frontend change. Any change to game domains or the kernel.

## Target structure

```
sessionmanagement/
  domain/            (unchanged: SessionGame, SessionPlayer, SessionToken, GameMode,
                      RoomFullException, SeatUnavailableException)
  application/       (unchanged: SessionService, GameFactories, JoinResult, *Exception,
                      port/SessionRepository; GameCleanupService — see note)
  infrastructure/    (unchanged: InMemorySessionRepository)
  presence/
    domain/
      Seat               (moved from presentation)
      ForfeitReason      (moved from presentation)
    application/
      PresenceService            (was presentation.DisconnectForfeitService)
      GameLifecycleBroadcasters  (resolver; moved from presentation)
    port/
      ConnectionRegistry         (interface; was StompSessionSeatRegistry's surface)
      ForfeitLog                 (interface; was ForfeitReasonRegistry's surface)
      ForfeitScheduler           (interface; new — wraps scheduling)
      GameLifecycleBroadcaster   (interface; moved from presentation — outbound port)
    infrastructure/
      InMemoryConnectionRegistry      (was StompSessionSeatRegistry)
      InMemoryForfeitLog              (was ForfeitReasonRegistry)
      TaskSchedulerForfeitScheduler   (new; wraps Spring TaskScheduler + Clock)
```

Stays in `presentation` (inbound adapters / shared infra): `LifecycleController`, `WebSocketDisconnectListener`, `SeatSubscriptionInterceptor`, `WebSocketConfiguration`, `GameMessagingService`, all `api/*`, `dto/*`, `dto/event/*`.

Per-game `*.presentation`: `BatailleCorseLifecycleBroadcaster`, `BullshitLifecycleBroadcaster` now implement `sessionmanagement.presence.port.GameLifecycleBroadcaster`. The classes that read forfeit reasons — `BatailleCorseLifecycleBroadcaster` and `BatailleCorseRestController` — now read the `ForfeitLog` port instead of the concrete `ForfeitReasonRegistry`. (Bullshit's forfeit is a silent elimination, so it carries no reason and reads nothing.)

## Current → target mapping

| Current (`presentation`) | Target | Kind |
|---|---|---|
| `DisconnectForfeitService` | `presence.application.PresenceService` | application service |
| `StompSessionSeatRegistry` | port `presence.port.ConnectionRegistry` + `presence.infrastructure.InMemoryConnectionRegistry` | port + adapter |
| `ForfeitReasonRegistry` | port `presence.port.ForfeitLog` + `presence.infrastructure.InMemoryForfeitLog` | port + adapter |
| (scheduling via injected `TaskScheduler`/`Clock`) | port `presence.port.ForfeitScheduler` + `presence.infrastructure.TaskSchedulerForfeitScheduler` | port + adapter |
| `GameLifecycleBroadcaster` (interface) | `presence.port.GameLifecycleBroadcaster` | outbound port |
| `GameLifecycleBroadcasters` (resolver) | `presence.application.GameLifecycleBroadcasters` | application collaborator |
| `Seat`, `ForfeitReason` | `presence.domain.Seat`, `presence.domain.ForfeitReason` | domain |
| `LifecycleController`, `WebSocketDisconnectListener`, `SeatSubscriptionInterceptor` | **unchanged location** (imports update) | inbound adapter |
| `BatailleCorseLifecycleBroadcaster`, `BullshitLifecycleBroadcaster` | **unchanged location**; implement the relocated port, read `ForfeitLog` | per-game adapter |

## Ports (contracts)

- **`ConnectionRegistry`** — `bind(String connectionId, Seat)`, `Optional<Seat> seatOf(String connectionId)`, `Optional<Seat> unbind(String connectionId)`, `removeGame(GameId)`. `connectionId` is opaque; the STOMP session id stays in the adapter.
- **`ForfeitLog`** — `record(Seat, ForfeitReason)`, `Map<Integer, ForfeitReason> reasonsBySeat(GameId)`, `removeGame(GameId)`.
- **`ForfeitScheduler`** — `ScheduledForfeit schedule(Instant deadline, Runnable task)`; the returned handle exposes `cancel()`. No Spring types leak through the interface.
- **`GameLifecycleBroadcaster`** (outbound) — `gameType()`, `disconnected(Game, Seat, long deadlineEpochMs)`, `reconnected(Game, Seat)`, `forfeited(Game, Seat, ForfeitReason)` (existing shape; relocated).

## Application: `PresenceService`

Same behavior as today's `DisconnectForfeitService`, now orchestrating ports only (no `ConcurrentHashMap` of its own beyond the `Seat → ScheduledForfeit` handle map, which is its working state):

- `onPresence(connectionId, gameId, playerId)` — `connectionRegistry.bind`; if a pending forfeit handle exists for the seat, `cancel()` it and `broadcasters.broadcasterFor(game).reconnected(...)`.
- `onDisconnect(connectionId)` — `connectionRegistry.unbind`; if the game is live, `forfeitScheduler.schedule(deadline, () -> forfeit(seat, DISCONNECTED))`, keep the handle, and `broadcasters.broadcasterFor(game).disconnected(...)`.
- `forfeit(seat, reason)` — idempotent on a finished game: `game.forfeit(...)`, `forfeitLog.record(...)`, `sessionService.touch(...)`, `broadcasters.broadcasterFor(game).forfeited(...)`.

`FORFEIT_GRACE` (60s) stays a constant on `PresenceService`. `Clock` is injected directly (a JDK seam). Game lookup stays via `SessionService.getGame` (returns the live `Game` or throws → treated as absent).

**Note on `GameCleanupService`** (session-core application): on eviction it currently clears both registries. It now depends on the `ConnectionRegistry` and `ForfeitLog` ports' `removeGame` — a within-context dependency from session-core onto presence ports, which is acceptable.

## Dependency map (after)

- **`game` kernel** — depends on nothing; everyone depends on it.
- **`sessionmanagement`** — depends only on the `game` kernel. Defines all ports (`SessionRepository`, `ConnectionRegistry`, `ForfeitLog`, `ForfeitScheduler`, `GameLifecycleBroadcaster`). `infrastructure` implements the storage/scheduler ports.
- **per-game contexts** — their `presentation` depends on `sessionmanagement.application` (calls services) **and** implements `sessionmanagement.presence.port.GameLifecycleBroadcaster`.
- **shared `presentation`** — pure adapters: inbound controllers call the application services; `GameMessagingService` is the outbound STOMP adapter used by the broadcaster impls.

The only relationship that changes is the **lifecycle-seam inversion**: the broadcaster interface and `PresenceService` move into `sessionmanagement`, so no dependency points out of sessions anymore.

## No cycles

`sessionmanagement.presence.application` → its own ports + `game` kernel + `SessionService`. Per-game `presentation` → `sessionmanagement` (for services and to implement the port). `sessionmanagement` never imports `presentation` or a game context. Spring wires the `List<GameLifecycleBroadcaster>` adapter impls into the resolver via the port interface.

## Testing

- **Move existing tests** for the relocated classes, updating imports/names. `DisconnectForfeitServiceTest` (if present) → `PresenceServiceTest`, now driving fakes/in-memory adapters for the ports instead of the concrete registries/`TaskScheduler`.
- **Port adapters get focused tests:** `InMemoryConnectionRegistry`, `InMemoryForfeitLog` (bind/seatOf/unbind/removeGame; record/reasonsBySeat/removeGame). The scheduler adapter is exercised via the service with a fake scheduler.
- **`PresenceService`** unit tests use a fake `ForfeitScheduler` (synchronous/controllable) and a fake `GameLifecycleBroadcaster` to assert: grace schedule on disconnect, cancel + reconnected on presence, forfeit path idempotency on finished games, resign path.
- Full backend suite stays green (per-game broadcaster behavior unchanged; only wiring/imports move). `AppConfig` bean wiring updates to construct the new adapters and inject ports.

## Migration sequencing (high level; details in the plan)

Move in dependency order so the tree compiles at each step: domain types (`Seat`, `ForfeitReason`) → ports → in-memory adapters → `PresenceService` + resolver → relocate the `GameLifecycleBroadcaster` interface and update per-game impls → update inbound adapters' imports → update `AppConfig` wiring. Keep each step a green commit.

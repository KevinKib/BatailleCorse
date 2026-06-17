# Context-Map Decoupling — Design

**Date:** 2026-06-17
**Status:** Approved (brainstorming)

## Goal

Remove the two coupling smells on the backend context map:

1. **The `core ⇄ presence` cycle.** `presence` depends on `core` (fine — presence is downstream), but `core` *also* depends on `presence` because `GameCleanupService` clears presence's registries on eviction. Make `presence` purely downstream so `core` imports nothing from `presence`.
2. **The game bounded contexts reaching into `core.domain`.** `bullshit`/`bataillecorse` presentation import `SessionGame`, `SessionToken`, `SessionPlayer`, `GameMode`, and the `RoomFull`/`SeatUnavailable` domain exceptions. Make `core.application` a published API (Open Host Service) so the game contexts depend only on `core.application` (+ the `game` kernel + shared transport), never `core.domain`.

Both parts are pure refactors (no behavior change); the full test suite is the safety net. They are independent and ship as two PRs from one spec.

---

## Part 1 — Break the `core ⇄ presence` cycle

Two new **core-owned** ports in `core.application`:

### `GameDirectory` (replaces presence's use of `SessionService`)
```java
public interface GameDirectory {
    Optional<Game> findGame(GameId id);   // empty if unknown or not yet started
    void touch(GameId id);
}
```
- `SessionService` implements it (it already has `findGame`/`touch`).
- `PresenceService` depends on `GameDirectory` instead of `SessionService`. Its private `findGame` helper (today a `getGame` + try/catch on `InvalidGameIdException`) collapses to `gameDirectory.findGame(id)`.

### `GameEvictionListener` (inverts the cleanup edge)
```java
public interface GameEvictionListener {
    void onEvicted(GameId id);
}
```
- `GameCleanupService` holds a `List<GameEvictionListener>` and, on eviction, calls `listeners.forEach(l -> l.onEvicted(id))` instead of importing `ConnectionRegistry`/`ForfeitLog`.
- `presence` supplies one adapter, `PresenceEvictionCleanup` (in `presence.application` or `presence.infrastructure`), implementing `GameEvictionListener` to clear `ConnectionRegistry` + `ForfeitLog` for that game.

### Wiring (`AppConfig`)
- `gameDirectory()` bean → returns the `SessionService` (it now implements `GameDirectory`), or a thin adapter delegating to it.
- `presenceService(...)` takes `GameDirectory` instead of `SessionService`.
- `presenceEvictionCleanup(...)` bean (a `GameEvictionListener`) constructed with the connection/forfeit ports.
- `gameCleanupService(...)` takes `List<GameEvictionListener>` (Spring injects all beans of that type), dropping its `ConnectionRegistry`/`ForfeitLog` params.

### Result
`presence.application` → `core.application.GameDirectory` + `core.application.GameEvictionListener` + `game` kernel. `core` imports nothing from `presence`. `GameCleanupService` no longer knows presence exists. Cycle gone.

---

## Part 2 — Make `core.application` a published API

The game contexts must depend only on `core.application`. Each `core.domain` leak closes by having the facade speak in published terms:

| Leak (in `core.domain`) | Fix |
|---|---|
| `SessionToken` | facade takes/returns **`String`** tokens: `findPlayerIdByToken(GameId, String)`, `startGame(GameId, String)`; `JoinResult.token()` returns `String`. `SessionToken` stays a `core.domain` internal (facade wraps the string). |
| `SessionGame` (from `createRoom`, `getGameSession`, and the JOIN broadcast) | `createRoom` returns **`RoomCreated(String gameId, String hostToken)`**. The lobby projection moves to a published **`LobbyView`** record in `core.application` (today's `presentation.dto.LobbyDto` shape: `started`, `gameId`, `players[{seat,name,joined}]`, `hostSeat`, `mySeat`, `minPlayers`, `maxPlayers`, `canStart`), built by facade methods `lobbyView(GameId, String token)` (one viewer) and `lobbyViews(GameId): List<LobbyView>` (one per claimed seat, for broadcasting). The game-type 404 guard uses a new `gameType(GameId): String`. **`LobbyBroadcaster.broadcast` changes to take a `GameId`** (not `SessionGame`) and pulls per-seat `LobbyView`s from the facade — so neither the game controller nor the broadcaster touches the aggregate. |
| `SessionPlayer` (from `getSeats`) | facade exposes `seats(GameId): List<SeatView>` where **`SeatView(int seat, String name, boolean joined)`** is published in `core.application`. `SessionViewDto`/`JoinEventData` map from `SeatView`. |
| `GameMode` | **relocate** the enum to `core.application` (it is a create-time published concept, used by create payloads). |
| `RoomFullException` / `SeatUnavailableException` (domain) | **translate**: `SessionService` catches the domain exception and throws a `core.application` published exception. To avoid two identically-named classes, the **domain** ones are renamed to internal names — `NoFreeSeatException` (thrown by `SessionGame.claimNextFreeSeat`) and `SeatTakenException` (thrown by `SessionGame.claimSeat`) — and `core.application` keeps the published `RoomFullException` / `SeatUnavailableException` that games catch. The domain still throws its own exceptions (the "domain throws" principle holds); the application republishes them. |

`LobbyView` and `SeatView` are also consumed by the shared `LobbyBroadcaster` / `SessionViewDto`, so they are a genuine Open Host Service, not a one-off for the controllers.

### Result
`bullshit`/`bataillecorse` presentation import only `core.application` (+ `game` kernel + shared transport). No `core.domain` imports remain in either game context. `SessionGame`/`SessionPlayer`/`SessionToken` are fully encapsulated.

---

## Component boundaries (after)

- `core.domain` — `SessionGame`, `SessionPlayer`, `SessionToken`, internal exceptions (`NoFreeSeatException`, `SeatTakenException`). No outside context imports it.
- `core.application` — the published API: `SessionService` + ports (`SessionRepository`, `GameDirectory`, `GameEvictionListener`), published records (`JoinResult` w/ String token, `RoomCreated`, `LobbyView`, `SeatView`), published enum (`GameMode`), published exceptions (`RoomFull`, `SeatUnavailable`, `GameAlreadyStarted`, `NotHost`, `NotEnoughPlayers`, `InvalidGameId`, `InvalidToken`). This is what every other context talks to.
- `presence` — downstream of `core.application` ports only.
- game contexts — talk to `core.application` (published) + `game` kernel + shared transport.

## Testing

- Pure refactors → the full backend suite (currently 265) stays green throughout; behavior is unchanged.
- Part 1: a focused `PresenceService` test already exists (uses fakes); update it to a fake `GameDirectory` instead of `SessionService`. Add a small test that `GameCleanupService` notifies its `GameEvictionListener`s, and that `PresenceEvictionCleanup` clears both stores.
- Part 2: existing controller/service tests adapt to the published signatures (String tokens, `RoomCreated`/`LobbyView`/`SeatView`, application exceptions). Add a test that `SessionService` translates `NoFreeSeatException` → published `RoomFullException` and `SeatTakenException` → published `SeatUnavailableException`. A grep check confirms no `bullshit`/`bataillecorse` source imports `core.domain`.

## Out of scope

- The shared `presentation` layer building views from the aggregate where **it** (not a game BC) is the consumer — e.g. `SessionRestController`. That remains the separate, previously-deferred follow-up (relocating session web-adapters out of shared `presentation`). Part 2 only redirects the **game** contexts; `LobbyView`/`SeatView` make that later move easier but don't require it now.
- No frontend change. No game-rules/domain change. No umbrella rename.

## Sequencing

Part 1 first (small, self-contained, removes the cycle), then Part 2 (larger, the published-API surface). Two PRs from this one spec.

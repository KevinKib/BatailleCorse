# Bullshit Multiplayer Lobby (Open Room + Host Start) — Design

**Date:** 2026-06-16
**Status:** Approved (brainstorming)

## Goal

Let a Bullshit game be played by 2–6 players. The creator opens a **room**; players join via a shared link, each taking the next free seat (up to 6). The **host** clicks **Start** once at least 2 players are present, and the game deals to exactly those who joined. This replaces the foundation slice's fixed-2, deal-at-create flow.

## Core principle (from brainstorming)

The gathering phase is a **session/lobby concern, not a domain concern**. The `Bullshit` aggregate stays untouched — always dealt-and-live, created only at Start with exactly the K players who joined. The lobby is modeled as a first-class state distinct from the game (a `SessionGame` with no aggregate yet), so **no `null`/absent-aggregate carries business meaning** — lobby and game are distinct, surfaced via `Optional<Game>`.

The lobby mechanism is built **generically in the session layer** so BatailleCorse can adopt it later; this slice only wires Bullshit to it. BatailleCorse's existing create/join flow is left untouched.

Per-game player bounds (min/max) live on the `GameFactory` (the existing per-game knowledge seam), so the session never hardcodes them and there is no domain leak into the session layer.

## Decisions

1. **Open room, host starts.** No player-count picker at create. Players join up to the max; host starts at ≥ min. Game deals to whoever is present.
2. **Bounds from the factory.** `GameFactory.minPlayers()`/`maxPlayers()` — Bullshit `2`/`6`, BatailleCorse `2`/`2`.
3. **Aggregate created at Start**, never during gathering. Repository supports a `SessionGame` with no `Game` (`findGame` returns `Optional`).
4. **Generic session lobby**; only Bullshit wired in this slice.
5. **2-player is just the minimum case** — the foundation's auto-deal-at-create is replaced by create-room + start.

## Architecture

### A. Factory player bounds

`GameFactory` gains:
```java
int minPlayers();
int maxPlayers();
```
- `BullshitFactory`: `minPlayers() = 2`, `maxPlayers() = 6`.
- `BatailleCorseFactory`: `minPlayers() = 2`, `maxPlayers() = 2`.

`GameFactories` exposes lookups by gameType: `minPlayers(gameType)`, `maxPlayers(gameType)` (delegating to `factoryFor(gameType)`).

### B. Generic session lobby (`SessionService` + repository)

New session operations (game-agnostic):

- `createRoom(String gameType, String hostName) : SessionGame`
  - Creates a `SessionGame` with `maxPlayers(gameType)` seats (so tokens exist for any joiner up to the cap), claims seat 0 for the host, saves it as a **lobby** (no `Game`).
- `joinRoom(GameId id, String name) : JoinResult`
  - Rejects if the game has already started (`findGame` present) → a distinct `GameAlreadyStartedException` → 409.
  - Claims the next free (lowest-id unclaimed) seat. Rejects if full (all seats claimed) → `RoomFullException` → 409.
  - Returns the claimed `PlayerId` + its `SessionToken`.
- `startGame(GameId id, SessionToken hostToken) : Game`
  - Host-only: `hostToken` must resolve to seat 0 → else `NotHostException` → 403.
  - Requires claimed-seat count K ≥ `minPlayers(gameType)` → else `NotEnoughPlayersException` → 409.
  - Rejects if already started → `GameAlreadyStartedException` → 409.
  - Creates the aggregate `factory.create(id, K)` and attaches it (`repository.save(game, sessionGame)`).
  - Because joins fill seats 0..K-1 contiguously, the K aggregate seats line up with the K claimed `SessionGame` seats; the unclaimed seats (K..max-1) remain dormant and are ignored after start.

**Repository changes** (`SessionRepository` + `InMemorySessionRepository`):
- `saveLobby(SessionGame sessionGame)` — store a session with no game.
- `Optional<Game> findGame(GameId id)` — honest "is it started yet?" (lobby → empty, started → present). No null.
- `load(GameId)` keeps throwing for a genuinely unknown id; lobby vs started is distinguished by `findGame`.
- `touch`/`evictStale` must also cover lobbies (a lobby with no game still ages out): track activity for lobby session ids too, and evict stale lobbies using the `idleTtl` threshold.

New exceptions (application layer): `GameAlreadyStartedException`, `RoomFullException`, `NotHostException`, `NotEnoughPlayersException`. (Reuse `SeatUnavailableException` only where a specific seat is contended; `RoomFullException` is the "no free seat" case.)

`createGame(...)` (immediate deal) stays for BatailleCorse and is unchanged.

### C. Bullshit presentation (lobby-vs-game split)

- **`@MessageMapping("/bullshit/create")`** → `sessionService.createRoom("bullshit", name)`. Ack (`@SendTo("/topic/game")`) carries `CREATE` with `{ gameId, hostToken }` and `state: null` (the room view arrives after the host subscribes to their seat and the lobby view is fetched/broadcast).
- **`@MessageMapping("/bullshit/start")`** (new) → resolve seat from token, `sessionService.startGame(id, token)`, then broadcast the dealt per-seat `BullshitDto` to all seats with a `START` event. Errors (not host / not enough players / already started) reply to the acting seat only.
- **`POST /api/bullshit/game/{id}/join`** → `sessionService.joinRoom(id, name)`; returns `JoinResponseDto{playerId, token}`; broadcasts the **lobby view** per seat with a `JOIN` event. Maps `RoomFullException`/`GameAlreadyStartedException` → 409, unknown/wrong-type → 404.
- **Lobby view** — a generic `LobbyDto` (per viewer):
  ```
  { started: false, gameId, players: [{ seat, name, joined }],
    hostSeat: 0, mySeat, minPlayers, maxPlayers, canStart }
  ```
  `canStart` = (viewer is host) AND (claimed count ≥ minPlayers). Built from the `SessionGame` + factory bounds — no aggregate needed.
- **Discriminator (`started` boolean) on both views.** To let the client branch unambiguously, both per-seat views carry a top-level `started` flag: `LobbyDto.started = false`, and `BullshitDto` gains `started = true` (a constant, since the game DTO only ever exists once started). This is a single explicit discriminator — no wrapper envelope, no null. Adding the field to `BullshitDto` is backward compatible.
- **Per-seat dispatch**: a small branch picks the view via `findGame(id)`:
  - lobby (no game) → `LobbyDto.forViewer(sessionGame, bounds, viewer)` (started=false)
  - started → existing `BullshitDto.forViewer(game, viewer)` via `BullshitStateBroadcaster` (started=true).
  In-game broadcasts (`DISCARD`/`CALL_BULLSHIT`) keep carrying `BullshitDto`; lobby broadcasts (`JOIN`) carry `LobbyDto`; the `START` broadcast carries `BullshitDto`. The client always reads `state.started` first, then the matching shape.
- **REST rehydration**: `GET /api/bullshit/game/{id}?token=` returns the `BullshitDto` (started=true) if started, else the `LobbyDto` (started=false). 403 without a valid seat token; 404 unknown. The client branches on `started`.

### D. Frontend lobby

- **Lobby screen** (`BullshitLobby.vue` or a phase within the existing screen): joined players list (names + seat), share link, and a **Start** button rendered only for the host, enabled when `canStart` (driven by the `LobbyDto`, not hardcoded bounds).
- **Create** → create room → land on the lobby screen (host).
- **Join** via link → land on the lobby screen until the host starts.
- **On `START`** event → transition to the game board.
- **`BullshitSession`/store**: add `createRoom`/`startGame`; handle a `LobbyDto` state (`phase = 'lobby'`) distinct from the game state (`phase = 'playing'`/`'finished'`). The waiting flag from the foundation is replaced by the lobby phase derived from `started`.
- **Game screen**: already iterates opponents generically; ensure it renders cleanly for >1 opponent (functional layout only — visual polish is out of scope).
- **Rehydration** (`useBullshitBootstrap`): fetch `GET /api/bullshit/game/{id}?token=`; show lobby or board based on the returned view's `started`/`view` discriminator.

## Component boundaries

- `GameFactory` — owns per-game bounds + aggregate construction. No session/presentation knowledge.
- `SessionService` — generic lobby lifecycle (`createRoom`/`joinRoom`/`startGame`); reads bounds via `GameFactories`; never game-specific.
- `SessionRepository` — stores lobbies and games; `findGame` distinguishes them.
- `LobbyDto` — generic per-viewer lobby projection of a `SessionGame` + bounds. No secrets.
- Bullshit presentation — wires the WS/REST endpoints, picks lobby-view vs game-view, broadcasts. The only Bullshit-specific layer.
- Frontend lobby — render + host Start; reads `LobbyDto`, calls session actions.

## Testing

**Backend (TDD):**
- `GameFactory` bounds (Bullshit 2/6, BatailleCorse 2/2).
- `SessionService.createRoom` (max seats, host claims 0, no game), `joinRoom` (next seat; full → `RoomFullException`; started → `GameAlreadyStartedException`), `startGame` (not-host → `NotHostException`; < min → `NotEnoughPlayersException`; deals K; already-started → reject).
- Repository `saveLobby`/`findGame` (lobby empty, started present); stale lobby eviction.
- `LobbyDto.forViewer` (joined list, hostSeat, mySeat, canStart true only for host at ≥ min).
- Bullshit presentation: create-room ack; join broadcasts lobby view; start deals + broadcasts game view to all seats; errors to acting seat only; REST returns lobby-vs-game by started.
- Full suite stays green (BatailleCorse untouched; its `createGame` path unchanged).

**Frontend (Vitest + @vue/test-utils + happy-dom):**
- Session/store: `createRoom`/`join`/`startGame` publish/POST correctly; `LobbyDto` → `phase==='lobby'`; `START` → board; lobby vs game rehydration.
- Lobby screen: Start visible to host only, disabled below min, enabled at ≥ min.
- Game screen renders >1 opponent without error.
- `vite build` is the type gate.

## Out of scope (YAGNI)

Wiring BatailleCorse to the generic lobby (designed-for, not done); game-picker UI; solo mode; disconnect/reconnect & forfeit overlays during lobby or game; game-duration timer; rematch; reveal/discard animations; suit-variant selection; N-seat visual/table-layout polish (functional rendering only); spectators.

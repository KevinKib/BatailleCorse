# Player Returns — 2-Player Polish

Date: 2026-06-06

## Context

After merging the 2-player session work (#18), three player-facing issues remain:

1. **Waiting screen vanishes prematurely.** When a human-vs-human game is created, the
   "Waiting for opponent…" overlay flashes for an instant and then disappears even though
   no opponent has joined.
2. **No easy way to share the game.** The host needs a frictionless way to hand the game
   to another player.
3. **Opponent is anonymous.** The board shows a hardcoded "Opponent" tag instead of the
   other player's name.

## Findings

- The waiting overlay already exists in `GameScreen.vue` and already contains a **Copy**
  button that copies the full join URL (`${window.location.origin}/join/:id`). Pasting
  that URL lands the opponent on the Join page with the code pre-filled. This is exactly
  the sharing behaviour we want — it is simply hidden by bug #1.
- Root cause of bug #1: `GameScreen.onMounted` always calls `restoreSession(...)`, and the
  multiplayer (single-token) branch of `GameSession.restoreSession` **unconditionally sets
  `waiting = false`** (`GameSession.ts`). This runs on the initial navigation to
  `/room/:id` as well as on reload, so it wipes the `waiting = true` that `create()` set.
- Names are currently **browser-local only**: `create()` explicitly drops `playerName`,
  and `opponentLabel` is hardcoded to `'Opponent'`. The server stores no names.
- `SessionGame` already tracks per-seat data via two collections keyed by `PlayerId`
  (`tokensByPlayer`, `claimedSeats`). Adding a third name map would create a parallel-maps
  smell.

## Decisions

- **Feature #2 (copy code):** no new UI. The existing join-URL copy is sufficient; fixing
  bug #1 makes it reliably visible. No bare-code field is added.
- **Names are mutual and shown on both sides.** Each client shows the opponent's name on
  the top tag and its own name on the bottom tag.
- **Waiting state is server-aware.** Seat occupancy comes from the server, so reloading
  mid-wait still shows the overlay and reloading after a join does not.
- **Names default server-side.** When a player creates/joins without a name, the server
  assigns a seat-based default (`Player 1` for seat 0, `Player 2` for seat 1) so both
  clients agree on the value (single source of truth).
- **`SessionGame` gets a `SessionPlayer`.** The token + claimed flag + name for a seat are
  folded into one `SessionPlayer` object held in a single `Map<PlayerId, SessionPlayer>`.
  This is a cohesion/correctness change, not a performance one (these maps hold 2 entries):
  it keeps the per-seat fields from drifting out of sync and makes `claim` a single write.

## Architecture

### Backend — session layer

**`SessionPlayer` (new, mutable class in `sessionmanagement.domain`)**
- Fields: `final PlayerId id`, `final SessionToken token`, `boolean claimed`, `String name`.
- `claim(String name)` sets `claimed = true` and stores the name.
- Accessors: `id()`, `token()`, `isClaimed()`, `name()`.

**`SessionGame` (refactor)**
- Replace `Map<PlayerId, SessionToken> tokensByPlayer` and `Set<PlayerId> claimedSeats`
  with a single `Map<PlayerId, SessionPlayer> players`.
- `create(id, players)` builds one `SessionPlayer` per seat with a fresh token, unclaimed,
  null name.
- `claim(PlayerId, String name)` delegates to the seat's `SessionPlayer.claim(name)`.
- Preserve existing behaviour through the same method surface:
  - `isClaimed(PlayerId)` → seat's `claimed`.
  - `findTokenByPlayer(PlayerId)` → `Optional<SessionToken>`.
  - `findPlayerByToken(SessionToken)` → `Optional<PlayerId>`.
- New read accessor for the presentation layer: a way to list seats with
  `{ id, name, joined }` (e.g. expose the `SessionPlayer` collection or a derived view).

**`SessionService`**
- `createGame(nbPlayers, mode, String creatorName)`: claim seat 0 with the creator's name
  (defaulted if blank). For SOLO, also claim seat 1 (AI) with its default name.
- `joinGame(BatailleCorseId, String name)`: claim seat 1 (`JOINER_SEAT`) with the joiner's
  name (defaulted if blank). Returns the existing `JoinResult`.
- Default-name helper applied when the incoming name is null/blank: seat 0 → `Player 1`,
  seat 1 → `Player 2`.
- Existing overloads (`createGame(nbPlayers)`, `createGame(nbPlayers, mode)`) kept as
  thin delegations so current callers/tests compile, or updated — chosen during planning.

### Backend — presentation layer

**Create (WebSocket)**
- `CreateGamePayload` gains `String name`.
- `BatailleCorseWebSocketController.createGame` passes `payload.name()` to the service.

**Join (REST)**
- `POST /api/game/{id}/join` accepts a JSON body `{ "name": "..." }` (optional/nullable).
- Controller passes the name to `sessionService.joinGame(gameId, name)`.

**New session-view endpoint**
- `GET /api/game/{id}/session` → `SessionViewDto`:
  ```json
  { "players": [ { "id": 0, "name": "Alice", "joined": true },
                 { "id": 1, "name": null,    "joined": false } ] }
  ```
- Built from the `SessionGame` seats. Keeps `BatailleCorseDto` / `BatailleCorse.fromJSON`
  untouched. Returns 404 for unknown/malformed ids, consistent with `GET /api/game/{id}`.

**JOIN broadcast (enriched)**
- `JoinEventData` extends to carry the same `players` list (id/name/joined) so a host
  sitting on the waiting screen updates names + ends waiting live, without re-fetching.

### Frontend

**`GameSession`**
- `create(mode, playerName)`: stop dropping `playerName`; include it in the `/app/create`
  payload (`{ mode, name }`).
- `join(id, name)`: POST `{ name }` to the join endpoint.
- `restoreSession`: stop forcing `waiting = false` in the multiplayer branch. Waiting is
  resolved by the session view instead.
- New `applySessionView(players)`: sets `myName`, `opponentName`, and
  `waiting = mode === 'multiplayer' && !opponentSeat.joined`. Emits the corresponding
  events (`waiting-change`, plus new name events).
- JOIN handling in `processEvent`: read the enriched `players` from `JoinEventData`, apply
  via the same path (sets `opponentName`, clears `waiting`).
- New `GameEvent` variants for name changes (`my-name-change`, `opponent-name-change`).

**Store (`BatailleCorse.store.ts`)**
- Add `myName` and `opponentName` refs, populated from the new events. They persist across
  send/slap/grab state updates.

**`GameScreen.vue`**
- `onMounted`: after hydrate + restoreSession, fetch `GET /api/game/:id/session` and apply
  it (sets waiting + names correctly on first paint and on reload).
- Top tag: `Computer (difficulty)` in solo, else `opponentName`.
- Bottom tag: `myName` (fallback to current `'You'`).
- Waiting overlay and its existing Copy button are otherwise unchanged.

**`StartGame.vue` / join flow**
- Pass `playerName` through `create` (already collected) and `join`.

## Data flow

1. **Host creates (Human):** `create('multiplayer', name)` → `{ mode, name }` over WS →
   server claims seat 0 with name → CREATE response → host routes to `/room/:id`.
2. **Host lands on room:** `onMounted` fetches session view → seat 1 not joined → `waiting
   = true`, overlay shown with Copy button. (Fixes bug #1.)
3. **Opponent opens join URL:** Join page pre-filled → `join(id, name)` POSTs `{ name }` →
   server claims seat 1 → JOIN broadcast (with both names) → host's `waiting` clears, host
   sees opponent's name; joiner sees host's name from its own session-view fetch.
4. **Either player reloads:** session-view fetch reconstructs names + correct waiting.

## Error handling

- Blank/missing name → server default (`Player 1` / `Player 2`).
- `GET /api/game/:id/session` 404 on unknown/malformed id → frontend treats as no session
  (route back to `/`, matching existing `getGame` behaviour).
- Join still returns 409 when seat 1 is already claimed (unchanged).

## Testing

Backend (no Mockito on domain; Builders/Fixtures; `givenX_whenY_thenZ`):
- `SessionPlayer`: claim sets claimed + name.
- `SessionGame`: create yields unclaimed seats with distinct tokens; `claim` marks claimed
  and stores name; `findTokenByPlayer` / `findPlayerByToken` behave as before. Update
  `SessionGameTest` cases that poke `tokensByPlayer()` to use the accessor methods.
- `SessionService`: create stores creator name (and default when blank); join stores joiner
  name (and default when blank).
- Controllers / IT: session-view endpoint returns correct `{ id, name, joined }`; JOIN
  broadcast carries both names; create accepts a name; 404 paths preserved.

Frontend:
- `GameSession`: `applySessionView` sets waiting + names correctly for joined/not-joined;
  `restoreSession` no longer forces waiting; create/join send names.
- Cypress: creating a Human game keeps the waiting overlay visible until a join; opponent
  name appears after join.

## Out of scope

- Bare game-code display / separate copy-code button.
- Persisting names beyond the in-memory session.
- Renaming after the game has started.

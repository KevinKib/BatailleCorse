# Player Identity Design

**Date:** 2026-05-19
**Branch:** claude/player-identity
**Status:** Approved

## Context

Currently the backend trusts the `playerIndex` sent by the client in every action payload. Any client can claim to be any player. This spec introduces server-side player identity using `SessionToken` as the credential for both normal gameplay and reconnection.

The AI (player 1) currently runs on the frontend. It will move server-side at some point; this design accommodates that without requiring changes to the identity model.

## Goals

- Backend derives player identity from a `SessionToken` credential, not from a client-supplied index
- The same token works after a WebSocket reconnect — no special reconnect flow needed
- Action payloads carry `gameId` + `token` only — no `playerIndex`

## Out of Scope

- Join flow (human vs human) — separate spec
- Moving AI server-side — separate spec
- Authentication / login — not planned

---

## Design

### Token as the Single Identity Credential

Every player seat is assigned a `SessionToken` (UUID) at game creation. The token is:
- Returned to the frontend in the CREATE response
- Sent by the client in every SEND / SLAP / GRAB payload
- Validated by the backend on every action to resolve the acting player

On reconnect, the client simply sends its next action with the same token — no special reconnect endpoint needed. The token is self-sufficient.

### CREATE Response: Token Distribution

The CREATE response includes both player tokens so the frontend can store them.

For human vs AI (current state), both tokens go to the same browser — token 0 for the human, token 1 for the client-side AI. When the AI moves server-side, only token 0 will be returned; that change belongs in the AI migration spec.

**New `CreateEventData` shape:**
```json
{
  "game": { "id": "uuid" },
  "tokens": { "0": "uuid-a", "1": "uuid-b" }
}
```

### Action Payload

**Before:**
```json
{ "gameId": "uuid", "playerIndex": 0 }
```

**After:**
```json
{ "gameId": "uuid", "token": "uuid-a" }
```

---

## Component Changes

### `SessionGame` (Session Management — domain)

No structural changes. `findPlayerByToken(SessionToken) → Optional<PlayerId>` already exists and is all that is needed.

### `InvalidTokenException` (Session Management — application)

New exception class, mirrors `InvalidGameIdException`. Thrown when a token is not found for a given game.

### `SessionService` (Session Management — application)

One new method:
- `findPlayerIdByToken(BatailleCorseId, SessionToken) → Optional<PlayerId>` — resolves a token to a player seat for a given game

`loadTokenByPlayerId` is unchanged (still used to build the CREATE response).

### `SessionRepository` (Session Management — port)

One new method:
- `loadSessionGame(BatailleCorseId) → SessionGame` — needed so `SessionService` can call `findPlayerByToken` on the session. The implementation already holds `SessionGame` in memory; this formalises access through the port.

### `InMemorySessionRepository` (Session Management — infrastructure)

Implements the new `loadSessionGame` method.

### `GameActionPayload` (websocket — api)

Replaces `playerIndex: Integer` with `token: String`. `gameId` is unchanged.

### `BatailleCorseWebSocketController` (websocket — presentation)

SEND / SLAP / GRAB handlers replace:
```java
Player player = batailleCorse.getPlayerByIndex(payload.playerIndex());
```
with:
```java
PlayerId playerId = sessionService
    .findPlayerIdByToken(new BatailleCorseId(payload.gameId()), new SessionToken(payload.token()))
    .orElseThrow(InvalidTokenException::new);
Player player = batailleCorse.getPlayerByIndex(playerId.id());
```

`InvalidTokenException` is caught by the existing try/catch block and returns an `ErrorResponse` — no new error handling path needed.

CREATE handler adds: after creating the game, load both tokens via `sessionService.loadTokenByPlayerId()` and include them in `CreateEventData`.

### `CreateEventData` (websocket — dto)

Adds `Map<Integer, String> tokens` (player index → token UUID string).

### Frontend: `BatailleCorse.store.ts`

- On CREATE event: store the `tokens` map and persist it to `localStorage` keyed by `gameId` (e.g. `localStorage.setItem('tokens:${gameId}', JSON.stringify(tokens))`)
- `send()`, `slap()`, `grab()`: payload becomes `{ gameId, token }` — token looked up by player index from the stored map. Human is always player 0 in the current setup; AI (player 1) uses `tokens[1]`
- `playerIndex` removed from all published payloads

### Frontend: `GameScreen.vue` (`onMounted`)

After fetching game state and before calling `subscribeToGame`, restore tokens from `localStorage`:
```typescript
const stored = localStorage.getItem(`tokens:${gameId}`);
if (stored) batailleCorseStore.restoreTokens(gameId, JSON.parse(stored));
else router.replace('/'); // tokens lost, cannot play
```

`restoreTokens(gameId, tokens)` is a new store action alongside `hydrate`.

### Frontend: TypeScript model for action payload

Update or add a type reflecting `{ gameId: string, token: string }`.

---

## Error Handling

| Situation | Response |
|---|---|
| Action arrives with unknown token | `ErrorResponse` — caught by existing try/catch, message: "Invalid token" |

---

## Testing

**Backend — unit:**
- `SessionGame.findPlayerByToken` — valid token returns correct `PlayerId`, unknown token returns empty
- `SessionService.findPlayerIdByToken` — delegates correctly to repository and session game

**Backend — integration:**
- `BatailleCorseWebSocketController` — SEND/SLAP/GRAB with valid token succeeds; with invalid token returns `ErrorResponse`
- CREATE response includes `tokens` map with correct UUIDs for both players

**Frontend:**
- Store stores `tokens` on CREATE event and persists to `localStorage`
- `onMounted` in `GameScreen.vue` restores tokens from `localStorage`; redirects to `/` if missing
- `send()` / `slap()` / `grab()` publish `{ gameId, token }` with no `playerIndex`

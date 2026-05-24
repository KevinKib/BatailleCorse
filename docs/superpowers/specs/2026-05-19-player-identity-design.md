# Player Identity Design

**Date:** 2026-05-19
**Branch:** claude/player-identity
**Status:** Approved

## Context

Currently the backend trusts the `playerIndex` sent by the client in every action payload. Any client can claim to be any player. This spec introduces server-side player identity using STOMP session IDs for normal gameplay and `SessionToken` as a reconnect credential.

The AI (player 1) currently runs on the frontend. It will move server-side at some point; this design accommodates that without requiring changes to the identity model.

## Goals

- Backend derives player identity from the STOMP connection, not from client-supplied claims
- Players can reclaim their seat after a WebSocket disconnect using a token
- Action payloads carry no player identity at all — just `gameId`

## Out of Scope

- Join flow (human vs human) — separate spec
- Moving AI server-side — separate spec
- Authentication / login — not planned

---

## Design

### Normal Gameplay: STOMP Session Identity

Every WebSocket connection has a server-assigned STOMP session ID, accessible in Spring via `SimpMessageHeaderAccessor.getSessionId()`. At CREATE time, the backend records `(gameId, stompSessionId) → player 0`. On every SEND / SLAP / GRAB, the controller looks up the session ID to find which player is acting — no token or playerIndex in the payload.

**Action payload before:**
```json
{ "gameId": "uuid", "playerIndex": 0 }
```

**Action payload after:**
```json
{ "gameId": "uuid" }
```

### Reconnect: Token Credential

When a client reconnects (new STOMP session ID), it sends its stored token to `/app/reconnect`:

```json
{ "gameId": "uuid", "token": "uuid" }
```

The backend validates the token against `SessionGame`, finds the corresponding player seat, and registers the new session ID. Normal action resolution then works again.

### CREATE Response: Token Distribution

The CREATE response includes both player tokens so the frontend can store them for reconnection. For human vs AI (current state), both tokens go to the same browser — token 0 for the human, token 1 for the client-side AI.

When the AI moves server-side, only token 0 will be returned. That change belongs in the AI migration spec.

**New `CreateEventData` shape:**
```json
{
  "game": { "id": "uuid" },
  "tokens": { "0": "uuid-a", "1": "uuid-b" }
}
```

---

## Component Changes

### `SessionGame` (Session Management — domain)

Add active STOMP session tracking alongside the existing token map:

- `Map<PlayerId, String> sessionIdsByPlayer` — current STOMP session per seat
- `activateSession(PlayerId, String sessionId)` — called at CREATE and on reconnect
- `findPlayerBySessionId(String sessionId) → Optional<PlayerId>` — used by controllers on every action
- `findPlayerByToken(SessionToken) → Optional<PlayerId>` — already exists, used for reconnect validation

### `SessionService` (Session Management — application)

Three new methods:

- `activatePlayerSession(BatailleCorseId, PlayerId, String stompSessionId)` — called by the controller after CREATE
- `findPlayerIdBySessionId(BatailleCorseId, String stompSessionId) → Optional<PlayerId>` — called on every action
- `reconnect(BatailleCorseId, SessionToken, String newStompSessionId) → PlayerId` — validates token, remaps session; throws `InvalidTokenException` if token unknown

### `SessionRepository` port

One new method:
- `loadSessionGame(BatailleCorseId) → SessionGame` — needed to support session ID lookups and reconnect (already partially implied by existing `loadSessionToken`, now made explicit)

### `GameActionPayload` (websocket — api)

Drops `playerIndex` and `token`. Only field remaining: `gameId: String`.

### `BatailleCorseWebSocketController` (websocket — presentation)

SEND / SLAP / GRAB handlers:
1. Extract STOMP session ID from `SimpMessageHeaderAccessor`
2. Call `sessionService.findPlayerIdBySessionId(gameId, sessionId)` → `PlayerId`
3. If empty → return `ErrorResponse` (unknown session — client should reconnect)
4. Call `batailleCorse.getPlayerByIndex(playerId.id())` → proceed as before

New `/app/reconnect` handler:
1. Extract STOMP session ID from headers
2. Call `sessionService.reconnect(gameId, token, sessionId)`
3. Broadcast a `RECONNECT` success event to `/topic/game/{gameId}` (so other clients know the player is back)
4. On `InvalidTokenException` → return `ErrorResponse`

CREATE handler:
1. After creating the game, call `sessionService.activatePlayerSession(gameId, player0Id, sessionId)`
2. Load both tokens via `sessionService.loadTokenByPlayerId()` for each player
3. Include tokens in `CreateEventData`

### `CreateEventData` (websocket — dto)

Add `Map<Integer, String> tokens` (player index → token UUID string).

### Frontend: `BatailleCorse.store.ts`

- On CREATE event: store `tokens` map (e.g. `playerTokens.value = response.eventData.tokens`)
- `send()`, `slap()`, `grab()`: payload becomes `{ gameId }` only — remove `playerIndex`
- New `reconnect(gameId)` function: called by `WebSocketService` on reconnect; publishes `{ gameId, token: playerTokens.value[0] }` to `/app/reconnect` — in the current human vs AI setup the human is always player 0; this will be revisited when join flow is added

### Frontend: `WebSocketService.ts`

- On `onConnect`, if `currentGameId` is set (reconnect scenario): call `store.reconnect(currentGameId)` after re-subscribing to the per-game channel

---

## Error Handling

| Situation | Response |
|---|---|
| Action arrives, session ID not found | `ErrorResponse` with message "Unknown session — reconnect required" |
| Reconnect with invalid token | `ErrorResponse` with message "Invalid token" |
| Reconnect succeeds | `SuccessResponse` with `RECONNECT` event type broadcast to `/topic/game/{gameId}` |

---

## Testing

- Unit: `SessionGame` — `activateSession`, `findPlayerBySessionId`, token + session ID interaction
- Unit: `SessionService` — `findPlayerIdBySessionId`, `reconnect` (valid token, invalid token, already-active session)
- Integration: `BatailleCorseWebSocketController` — SEND with valid session, SEND with unknown session ID, reconnect flow
- Frontend: store handles `tokens` in CREATE event, payload no longer includes `playerIndex`

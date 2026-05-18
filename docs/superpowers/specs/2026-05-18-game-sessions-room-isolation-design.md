# Game Sessions — Per-Room Channel Isolation & Refresh-Safe State

**Date:** 2026-05-18  
**Status:** Approved  
**Scope:** Human vs. AI only. Foundation for future human vs. human support.

---

## Problem

All WebSocket game events currently broadcast to `/topic/game`, shared across every game instance. Any connected client receives every other game's moves. Additionally, refreshing `/room/:id` breaks the game because the Pinia store is wiped and `GameScreen` has no way to recover the current game state.

---

## Goals

1. Isolate game events per room: `/topic/game/{gameId}`
2. Make `GameScreen` refresh-safe by rehydrating state from the server on mount
3. Make the URL the source of truth for the game ID (not the store)

---

## Architecture

Two independent concerns:

**Channel isolation** — backend broadcasts to `/topic/game/{gameId}`; frontend subscribes dynamically using the game ID from the URL param.

**State rehydration** — a new REST endpoint returns the full current game state; `GameScreen` fetches it on mount before subscribing to the WebSocket channel.

---

## Backend Changes

### 1. `GameMessagingService`

Change the broadcast destination from `/topic/game/` to `/topic/game/{gameId}`.

The game ID is already present in every event payload — it just needs to be threaded into the destination string.

### 2. New REST endpoint

```
GET /api/game/{id}
→ 200: full game state (same DTO shape used in WebSocket events)
→ 404: game not found
```

Implemented as a thin wrapper over the existing `SessionService.getGame(id)`. No new DTOs required — the response reuses the same state structure already produced by WebSocket event handlers.

---

## Frontend Changes

### 1. `WebSocketService.ts`

The subscribe method accepts a `gameId` parameter and subscribes to `/topic/game/{gameId}` instead of the hardcoded `/topic/game`.

### 2. `GameScreen.vue`

On mount:
1. Read game ID from `useRoute().params.id`
2. Call `GET /api/game/{id}` to populate the store with current game state
3. Pass the game ID to the WebSocket subscription

On 404 (unknown room): redirect to `/`.

### 3. `BatailleCorseStore`

The store must be populatable from the REST response, not only from WebSocket `CREATE` events. The game state shape is the same in both cases — the existing state-setting logic just needs to be accessible from `GameScreen` on mount.

The store stops owning the game ID. The URL is the source of truth; the store mirrors it.

---

## Data Flow

### Create (player starts a new game)

```
Player clicks "Deal" → POST /app/create
→ Backend creates game, returns UUID
→ Frontend navigates to /room/{uuid}
→ GameScreen mounts, reads {uuid} from URL
→ GET /api/game/{uuid} → hydrates store
→ Subscribe to /topic/game/{uuid}
```

### Normal play (after mount)

```
Player action → /app/{action}
→ Backend processes, broadcasts to /topic/game/{gameId}
→ GameScreen receives event, updates store
```

### Refresh

```
Browser navigates to /room/{uuid} (URL survives refresh)
→ GameScreen mounts, reads {uuid} from URL
→ GET /api/game/{uuid} → hydrates store with current state
→ Subscribe to /topic/game/{uuid} for live updates going forward
```

### Future: Player 2 joins via shared link

```
Player 2 navigates to /room/{uuid}
→ Same flow as refresh: fetch state, subscribe to channel
→ (Join endpoint + seat assignment added separately)
```

---

## Error Handling

| Scenario | Behaviour |
|---|---|
| `GET /api/game/{id}` returns 404 | Redirect to `/` |
| WebSocket disconnects mid-game | Standard STOMP reconnect; re-fetch state on reconnect |
| Game already finished | 200 with finished state; UI shows end screen |

---

## Out of Scope

- Human vs. human join flow (separate spec)
- Player authentication via session tokens
- Persistent storage (games still in-memory)
- Room listing / lobby UI

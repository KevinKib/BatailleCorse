# Rematch ("Play Again") — Design

**Date:** 2026-06-12
**Status:** Approved, pending implementation plan

## Summary

Add a "Play Again" button to the end-of-game overlay that starts a rematch
without returning to the home screen. Works for both solo (vs computer) and
multiplayer (vs human) games through a single backend mechanism.

A rematch resets the game **in place**: the backend deals a fresh deck into the
**same game id**, keeping every seat's token and name. Players never leave the
`/room/{id}` URL.

## Decisions (settled during brainstorming)

- **"Replay" means rematch / play again** — start a fresh game, not re-watch a recording.
- **Multiplayer requires both players to accept** — one proposes, the other accepts; the new game starts only when both agree.
- **Same room, reset in place** — the rematch reuses the existing game id, seats, tokens, and names. No new link, no navigation.
- **Approach A (unified backend)** — one mechanism governs both modes. There is no solo-vs-multiplayer branch in the rematch logic.

## Core idea

The rematch is governed by a single rule: **reset the game in place once every
seat has requested it.**

- **Multiplayer**: each browser requests a rematch for its own seat. Unanimity
  is reached when both humans have requested.
- **Solo**: the client already holds *both* seat tokens (seats 0 and 1 from
  `CreateEventData.tokens`). "Play Again" requests for both seats, so the
  unanimity rule fires immediately. The AI is a client-side puppet and needs no
  consent.

Because solo trivially satisfies "every seat requested," the backend never
branches on game mode. `GameMode` is **not** consulted by the rematch logic.

## Architecture

### 1. Backend domain — the unanimity rule

Coordination state lives in the session aggregate (logic in the model).

- **`SessionPlayer`** gains a mutable `rematchRequested` flag (mirrors the
  existing `claim` mechanism), with `requestRematch()` and `clearRematch()`.
- **`SessionGame`** gains:
  - `requestRematch(PlayerId)` — sets that seat's flag.
  - `isRematchUnanimous()` — true when **every** seat has requested.
  - `clearRematch()` — resets all seats' flags.

### 2. Backend application — reset in place

`SessionService.rematch(BatailleCorseId id)`:

```java
public BatailleCorse rematch(BatailleCorseId id) {
    SessionGame session = repository.loadSessionGame(id);   // keeps tokens + names
    BatailleCorse fresh = new BatailleCorse(id, NB_PLAYERS); // same id, new deck
    session.clearRematch();
    repository.save(fresh, session);
    return fresh;
}
```

Same id, same seats/tokens/names, brand-new dealt game. `GET /api/game/{id}`
then returns the fresh game, so rehydration stays correct with no extra work.

### 3. Backend transport — the `/app/rematch` endpoint

- New `EventType.REMATCH`.
- New `RematchEventData(Status status, PlayerIdDto requestedBy)` where
  `Status ∈ {PENDING, STARTED}`.
- New `@MessageMapping("/rematch")` handler (same token-resolution pattern as
  send/slap/grab):
  1. Resolve seat from token.
  2. `sessionGame.requestRematch(seat)`.
  3. If `isRematchUnanimous()` → `sessionService.rematch(gameId)`; broadcast
     `SuccessResponse(REMATCH, status=STARTED, freshBatailleCorseDto)`.
  4. Else → broadcast
     `SuccessResponse(REMATCH, status=PENDING, requestedBy=seat, currentBatailleCorseDto)`.

Both broadcast on the existing `/topic/game/{id}` channel.

### 4. Frontend — `GameSession` + store

- `GameSession.rematch()`:
  - **Solo** (`mode === 'solo'`): publish `/app/rematch` for **both** tokens
    (seats 0 and 1) → unanimous immediately.
  - **Multiplayer**: publish for the local seat only.
- `processEvent` handles `REMATCH`:
  - `PENDING` → emit a `rematch-pending` event carrying `requestedBy`, so the UI
    can show "Opponent wants a rematch."
  - `STARTED` → `cancelAll()` (clear auto-grab + pending AI action), re-init the
    AI (`this.ai = this.aiFactory()`) so solo difficulty is fresh, then let the
    accompanying state-update flow through the normal path.
- The store mirrors a `rematchState` (`idle | requested-by-me |
  requested-by-opponent`) from these events.

### 5. Frontend — end overlay UI (`GameScreen.vue`)

Add a **"Play Again"** button beside "Back to home". Label/behaviour driven by
`rematchState`:

| State | Button |
|---|---|
| idle | **Play Again** |
| requested-by-me (MP) | **Waiting for opponent…** (disabled) |
| requested-by-opponent (MP) | **Accept Rematch** (highlighted) |
| solo | **Play Again** → fires instantly |

**Required fix:** `useEndScreen` currently only ever sets `showEndOverlay` to
`true`. It must reset to `false` when a fresh (not-over) state arrives, so the
overlay dismisses on `REMATCH(started)`. Add this to the existing `watch`.

## Edge cases

- **Opponent left / forfeited**: their seat can't request, so the proposal stays
  pending and the proposer sees "Waiting for opponent…". v1 builds no timeout; if
  the opponent reconnects they can still accept. Documented, not built.
- **Both click simultaneously**: set/flag-based and idempotent — resolves once
  both flags are set, regardless of order.
- **Stale animation refs**: `lastSend` / `lastGrab` / `lastSlap` must not replay a
  prior move when the fresh state arrives. Verify the reset doesn't re-trigger a
  stale animation; the fresh state-update flows through the normal path.

## Testing

Following project conventions (no Mockito on domain classes; builders/fixtures;
`givenX_thenY` naming; test types per layer).

- **Domain** — `SessionGameTest`: `requestRematch` / `isRematchUnanimous` /
  `clearRematch` (builders/fixtures, no Mockito).
- **Application** — `SessionServiceTest`: `rematch()` preserves seats/tokens/names
  and deals a new game under the same id.
- **Transport** — controller/integration test for the pending → unanimous → reset
  flow.
- **Frontend** — `GameSession.test.ts`: solo (both-token) and multiplayer
  (single-seat) rematch + `REMATCH` handling; `useEndScreen.test.ts`: the
  dismiss-on-reset behaviour.
- **E2E** — Cypress spec for the solo "Play Again" loop (multiplayer handshake
  optional).

## Out of scope (v1)

- Re-watching a move-by-move recording of a finished game.
- A timeout/expiry on a pending rematch proposal.
- Swapping seats or who-deals-first on rematch (seats are kept as-is).

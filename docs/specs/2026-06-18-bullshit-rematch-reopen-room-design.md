# Bullshit Rematch via Reopen-the-Room — Design

**Date:** 2026-06-18
**Status:** Approved (brainstorming)

## Goal

Replace Bullshit's bespoke rematch mechanism with **reopening the room back into its lobby**, so a rematch is just the normal join→start flow. This fixes a whole class of seat/count bugs (showing 2/6, restarting a 3-player game with 6 players, broken seat↔index mapping when a middle player leaves) by removing the special-cased rematch path entirely rather than patching each symptom.

### Why

The earlier rematch added a parallel "tally" path (`joinRematch`/`leaveRematch`/`RematchOutcome`/`isRematchReady`/staying-counts) that re-derived "who is playing" and "how many seats" independently of the lobby. Those re-derivations kept disagreeing with reality because a room pre-allocates `maxPlayers` seats and a left player's seat is still "claimed." Modeling rematch as "play the room again" means reusing `joinRoom`/`startGame`, which already seat joiners contiguously and deal to the joined count — so the bugs cannot recur.

## Behavior

End-of-game overlay shows the result plus two actions:

- **Play Again** → re-join the room. The first player to click reopens the room (the finished game is cleared and the lobby reset to empty) and claims **seat 0**, becoming host; later clickers claim the next seats. Each player lands on the lobby screen and learns its fresh seat + token the same way a normal join does.
- **Back to Home** → navigate home. No server signal needed: a player who does not re-join simply is not in the reopened lobby. Because reopening rebuilds the lobby from empty, returning players get **fresh contiguous seats 0..K-1** — no gaps, no "claimed but gone" seats.

The host then presses **Start** from the lobby when ready (≥ minPlayers), which deals a fresh game to exactly the players who re-joined. This gives the host flexibility to wait for stragglers.

**Host after reopen:** the first player to click Play Again is seat 0 / host (the original host role does not carry over).

## Backend

### Reopen + re-join (one atomic operation)

New facade method on `SessionService`:

```
JoinResult playAgain(GameId id, String name)
```

- If the session currently holds a game (finished or not), **reopen**: replace it with a fresh empty lobby for the same `gameId`/`gameType` and `maxPlayers` capacity (i.e. `SessionGame.create(id, maxPlayers, gameType)`), dropping the `Game`.
- Then `claimNextFreeSeat(name)` and return `JoinResult(playerId, tokenString)` — reusing the exact seat-claim used by `joinRoom`.
- **Concurrency:** the reopen is a check-then-act, and STOMP/REST handlers run on a thread pool, so two simultaneous Play-Again calls could both reopen and clobber each other. Guard the whole reopen+claim in one per-room critical section (`synchronized` on a per-`gameId` lock, or on the repository) so the first call fully completes (reopen + claim seat 0) before the second observes the state (no reopen + claim seat 1). The repo's `ConcurrentHashMap` does **not** cover this compound operation.

### REST endpoint

`POST /api/bullshit/game/{id}/play-again` → `playAgain(gameId, name)` → `JoinResponseDto(playerId, token)`, mirroring the existing `POST /api/bullshit/game/{id}/join`. The client stores the new token/seat and navigates to the lobby. The existing `join` endpoint keeps its current semantics (used for share-link joins of a not-yet-started room); `play-again` is the finished-room path.

### Reuse, unchanged

`joinRoom` (seat claiming), `startGame` (deals `claimedCount()` players, seats contiguous), `LobbyView`/`LobbyBroadcaster` (lobby updates as players re-join). No seat↔index decoupling needed: claimed seats remain contiguous 0..K-1 by construction.

### Removed (Bullshit-only)

- `SessionService.joinRematch`, `leaveRematch`; `RematchOutcome`.
- `SessionGame.isRematchReady`, `rematchStayingCount`, `rematchReadyCount`, `leaveRematch`, the `staying()` helper.
- `SessionPlayer.leftRematch` / `leaveRematch()` / `hasLeftRematch()`.
- `BullshitRematchEventData`; the `/bullshit/rematch` and `/bullshit/leaveRematch` WS endpoints.
- `RematchStatus` usage in Bullshit (the shared enum stays for BatailleCorse).

### Untouched

BatailleCorse keeps its instant 2-player rematch: `SessionService.requestRematch(GameId, PlayerId): boolean`, `SessionGame.requestRematch`/`isRematchUnanimous`/`clearRematch`, `SessionPlayer.requestRematch`/`hasRequestedRematch`/`clearRematch`, and the `/rematch` BC endpoint. `SessionService.rematch(GameId)` (fresh-game-from-session) is also kept for BC.

## Frontend

- **`BullshitSession`**: remove `rematch()` / `leaveRematch()`. Add `playAgain(name?)` → `POST .../play-again`, store the returned `{playerId, token}` in `localStorage` (same shape as `join`), bind/subscribe to the new seat, and resolve so the caller can route to the lobby.
- **`Bullshit.store`**: remove the rematch progress state (`rematchRequested`/`rematchReady`/`rematchEligible`/`rematchButton`) and the `rematch`/`leaveRematch` actions. Add a `playAgain()` action.
- **`BullshitGameScreen`**: the `finished` phase still renders `EndGameOverlay`. "Play Again" → `store.playAgain()` then `router.push` to the Bullshit lobby route for the game; "Back to Home" → home (existing RouterLink). Remove the `:rematch-button` / `@leave` wiring.
- **`EndGameOverlay`** (shared with BatailleCorse): keep the `rematchButton` prop and the `playAgain` emit — **both games use them**. Only remove the `leave` emit + its `@click` that were added for the old Bullshit tally (BC never used them). Bullshit now passes a **static** rematch button (`{ label: 'Play Again', disabled: false }`) instead of the progress one, and wires `@play-again` to `store.playAgain()`. BatailleCorse's usage (its `RematchButton`/`useEndScreen`) is unchanged.

## Testing

- **Backend (JUnit 5 + Hamcrest):**
  - `playAgain` on a finished 3-player room (6-seat capacity) → first call reopens and claims seat 0; second/third claim 1/2; `startGame` deals a **3**-player game. Assert the fresh game has 3 players and seats are contiguous.
  - `playAgain` when no game is present yet behaves like a join (claims next seat).
  - A player who does not re-join is absent from the next game (size reflects only re-joiners).
  - Concurrency guard: two `playAgain` calls reopen exactly once (deterministic test of the critical section, e.g. asserting only one reset occurs / final claimed set is {0,1}).
  - Full suite stays green; the deleted bespoke-rematch tests are removed.
- **Frontend (Vitest):**
  - `BullshitSession.playAgain` posts to `.../play-again` and stores the new token/seat.
  - `BullshitGameScreen` finished phase: "Play Again" calls `store.playAgain()` and routes to the lobby; "Back to home" routes home.

## Out of scope

- BatailleCorse rematch (unchanged).
- The broader "never pre-allocate seats / persistent Room aggregate" refactor — reopening rebuilds the lobby cleanly, so the pre-allocation no longer causes bugs here; a deeper Room model remains a possible future change, not required now.
- Detecting a player who hard-closes the tab without clicking either button (they just don't re-join — same as not clicking Play Again).

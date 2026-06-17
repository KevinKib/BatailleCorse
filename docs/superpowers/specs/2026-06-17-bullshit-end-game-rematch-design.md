# Bullshit End-of-Game + Rematch — Design

**Date:** 2026-06-17
**Status:** Approved (brainstorming)

## Goal

Close the Bullshit game loop. Today the `finished` phase renders one line of text ("You win!/You lose") and there is **no rematch path at all** — not on the backend, not on the frontend (only BatailleCorse has rematch). This slice adds:

1. A proper end-of-game overlay (reusing the existing game-agnostic `EndGameOverlay`).
2. A Bullshit rematch flow with **unanimous-among-connected-seats** acceptance for 2–6 players.

This is the first slice of the broader "Bullshit screen polish" effort (others — disconnect/forfeit UX, reveal animation, N-seat layout, rules panel, timer, hand sorting — are separate, later slices).

## Acceptance model

A rematch starts when **every eligible seat has requested it**, where **eligible = connected and not forfeited**. A player who closed their tab (disconnected) or forfeited/was eliminated is excluded from the count, so they cannot block the rematch.

Eligibility is computed from the presence side, which already holds both signals:
`eligible = ConnectionRegistry.seatsFor(gameId) − ForfeitLog.reasonsBySeat(gameId).keySet()`.

## Architecture — controller-coordinated; `core` stays presence-ignorant

The recently-completed context-map decoupling (#63/#64/#65) made `core` import nothing from `presence`. This design preserves that. The Bullshit WS controller is the orchestrator (it legitimately depends on both `core.application` and the presence side); it asks presence for the eligible set and passes it into `core`. `core` never learns presence exists.

**Rejected alternatives:** (a) a rematch coordinator living in `presence` that calls back into `core` to build the fresh game — blurs responsibility and re-tangles the contexts; (b) eligibility derived from game-elimination only (no live presence) — fails the requirement, because at the end screen a disconnect is not turned into a forfeit (the disconnect→forfeit timer only runs for live games), so a player who left would block forever.

## Backend

### Presence: expose the eligible set
- **`ConnectionRegistry.seatsFor(GameId): Set<PlayerId>`** — new query; `InMemoryConnectionRegistry` filters its bindings by game id. (`Seat` stays internal to presence; the method returns kernel `PlayerId`s, or `Set<Seat>` mapped by the caller — implementation detail, kept inside presence.)
- **`PresenceService.activeSeats(GameId): Set<PlayerId>`** — `seatsFor(gameId)` minus `forfeitLog.reasonsBySeat(gameId).keySet()`. Lives in `presence.application`; both ports are already injected.

### Core: unanimity among an eligible subset
- **`SessionGame.isRematchUnanimousAmong(Set<PlayerId> eligible): boolean`** — true when the game is finished and every seat in `eligible` has `hasRequestedRematch()`. (An empty eligible set returns false — never auto-start with nobody connected.)
- **`SessionService.requestRematch(GameId, PlayerId, Set<PlayerId> eligibleSeats): boolean`** — records the seat's request, returns `isRematchUnanimousAmong(eligibleSeats)`. The existing all-seats `requestRematch(GameId, PlayerId)` is **unchanged** and still used by BatailleCorse.
- `SessionService.rematch(GameId)` is reused as-is to build the fresh game (same gameId; clears rematch flags as it does for BatailleCorse).

### Bullshit WS endpoint
- **`@MessageMapping("/bullshit/rematch")`** on `BullshitWebSocketController`, payload `GameActionPayload(gameId, token)`:
  1. Resolve `PlayerId` from the token (`findPlayerIdByToken`); on failure, log and return (consistent with the other Bullshit handlers).
  2. `Set<PlayerId> eligible = presenceService.activeSeats(gameId)` (controller gains a `PresenceService` dependency — `presentation → presence.application` is allowed).
  3. `boolean unanimous = sessionService.requestRematch(gameId, playerId, eligible)`.
  4. **If unanimous:** `Bullshit fresh = (Bullshit) sessionService.rematch(gameId)`; `sessionService.touch(gameId)`; broadcast **STARTED** with fresh per-seat state via the existing `BullshitStateBroadcaster.broadcast(fresh, REMATCH, data, msg)` (fans `BullshitDto.forViewer` per seat; same gameId, so clients re-render in place).
  5. **Else (pending):** broadcast **PENDING** per seat carrying the ready/eligible counts so each overlay can show "Waiting… n/m ready". The state payload is the still-finished game per viewer (re-broadcast through `BullshitStateBroadcaster`).

### Event data
- Reuse the shared **`RematchStatus`** enum (`STARTED` / `PENDING`).
- New **`BullshitRematchEventData(RematchStatus status, int ready, int eligible)`** in `bullshit.presentation.dto.event` (BatailleCorse's `RematchEventData` carries no counts, so Bullshit gets its own rather than overloading BC's). `ready` = count of eligible seats that have requested; `eligible` = size of the eligible set at decision time.

## Frontend (mostly reuse)

- **`BullshitSession.rematch()`** — publishes `/bullshit/rematch` with the stored token; surfaces the `REMATCH` event (status + counts) through the existing `onEvent` channel.
- **`Bullshit.store`** — handle the `REMATCH` event: track `{ ready, eligible, status }`. On `STARTED`, the accompanying `state-update` flips `phase` back to `playing` automatically (fresh game state arrives per seat). Expose a `rematchButton` derived state and a `rematch()` action. Reuse the existing **`RematchButton`** model / `useEndScreen` pattern where it fits Bullshit; otherwise a small Bullshit-local equivalent.
- **`BullshitGameScreen.vue`** — replace the bare `finished` block (lines 61–63) with the existing game-agnostic **`EndGameOverlay`** component, wired:
  - `didIWin` ← `store.iWon`
  - `subtitle` ← winner line (e.g. "Player N wins" / "You ran out of cards" — exact copy decided in implementation)
  - `rematchButton` ← derived state (idle → "Play again"; pending → "Waiting… n/m ready", disabled)
  - `@playAgain` → `store.rematch()`

## Testing

- **Backend (JUnit 5 + Hamcrest, no Mockito on domain):**
  - `SessionGame.isRematchUnanimousAmong` — unanimous over a subset; not unanimous when an eligible seat hasn't requested; false for empty eligible set; false when not finished.
  - `PresenceService.activeSeats` — connected minus forfeited; excludes a disconnected (unbound) seat; excludes a forfeited seat.
  - `ConnectionRegistry.seatsFor` (InMemory) — returns only this game's seats.
  - `BullshitWebSocketController` rematch — pending broadcast carries the right counts to each seat; unanimous (last connected seat clicks) triggers a fresh game broadcast per seat; a forfeited/disconnected seat does not block.
  - Full suite stays green (currently 265 → grows with the new tests).
- **Frontend (Vitest):**
  - `BullshitSession.rematch` publishes the right frame; event surfaces status/counts.
  - Store transitions: PENDING updates counts; STARTED returns `phase` to `playing`.
  - `BullshitGameScreen` renders `EndGameOverlay` on finish, button label reflects idle/pending, `playAgain` calls `store.rematch()`.

## Out of scope

- The other polish slices (disconnect/forfeit overlay, reveal animation, N-seat table layout, rules panel, turn timer, hand sorting #60).
- Auto-re-evaluating unanimity when an awaited player disconnects *after* others have clicked: **v1 evaluates only on a click**, so the rematch fires on the next click rather than instantly. Noted, accepted.
- No change to BatailleCorse's existing rematch.
- Suit-variant selection at create time.

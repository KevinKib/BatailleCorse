# Bullshit Frontend Foundation — Design

**Date:** 2026-06-16
**Status:** Approved (brainstorming)

## Goal

Stand up the **foundation** of the Bullshit game frontend: a real, playable-but-minimal vertical slice that proves the hidden-information transport works end-to-end. A player can create a 2-player Bullshit game, share a join link, the opponent joins, both see only their own hand over a per-seat topic, take turns discarding cards (1–4 as the server-dictated claim), call bullshit to trigger a reveal, and reach a win/lose end state.

This is deliberately scoped to the **foundation + YAGNI**: build the session/store/WebSocket seams and a minimal real screen; defer all polish.

## Decisions (from brainstorming)

1. **Architecture: parallel Bullshit stack mirroring BatailleCorse.** New `Bullshit.store.ts` + `BullshitSession.ts` + `model/Bullshit*.ts` + `BullshitGameScreen.vue` + Bullshit routes, plus a per-seat subscribe method added to the shared `WebSocketService`. BatailleCorse code is untouched. No generic game-core extraction in this slice (revisit later).
2. **Create mode: multiplayer share-link only.** No solo hot-seat.
3. **Player count: fixed at 2 this slice.** Bullshit's domain supports 2–6, but the session `joinGame` currently seats only `PlayerId(1)` (a 2-player assumption). 3–6 player join is a separate later slice.
4. **One backend change only:** a thin `POST /api/bullshit/game/{id}/join` reusing the existing seat-1 join.
5. **Outcome/turn driven off `state`, never off per-action events** (project rule: optimistic events precede the winner-bearing state-update).

## Architecture

### A. Routing & entry (`frontend/src/main.ts`)

Add Bullshit routes mirroring BatailleCorse, with `BASE = '/games/bullshit'`:

- `/games/bullshit/create` → `BullshitStartGame.vue` (player-name field + Create button; **no player-count selector** — fixed at 2)
- `/games/bullshit/join/:id?` → `BullshitStartGame.vue` (join form)
- `/games/bullshit/room/:id` → `BullshitGameScreen.vue`

Reachability: add a small **"Play Bullshit"** link on the existing `LobbyView.vue`. This is a link, not a game-picker (the picker is deferred). Direct URL navigation also works.

### B. Session + store + WebSocket seam (the real foundation)

**`WebSocketService.ts` (shared, modified):**
- Add `subscribeToSeat(gameId, seat, token, onMessage)` →
  `client.subscribe(`/topic/game/${gameId}/seat/${seat}`, cb, { token })`.
  The `token` native header is required by the backend `SeatSubscriptionInterceptor`.
- Persist the seat-subscription intent (`{gameId, seat, token, cb}`) so the existing `onConnect` **replays it after a reconnect**. This generalizes the current BatailleCorse-only resubscribe; BatailleCorse behavior is preserved.
- Actions continue to use the existing `publish('/app/...', body)`.

**`model/Bullshit*.ts` (new):** TypeScript mirrors of the backend per-seat DTOs:
- `BullshitState` (mirror of `BullshitDto`): `id`, `gameType`, `myHand: Card[]`, `availableActions: string[]`, `players: { id: string; handCount: number; isCurrentPlayer: boolean }[]`, `currentTarget: { label: string }`, `discardPileSize: number`, `table` (sealed: `{ state: 'NO_CLAIM' }` | `{ state: 'CLAIM'; claimantId; claimedTargetLabel; count }`), `pendingWinner` (`{ state: 'NONE' }` | `{ state: 'PENDING'; playerId }`), `outcome` (`{ status: 'ONGOING' }` | `{ status: 'FINISHED'; winnerId }`).
- `DiscardEventData` `{ claimantSeat; claimedTargetLabel; count }`, `CallBullshitEventData` `{ callerSeat; claimantSeat; truthful; pickerSeat; revealedCards: Card[] }`.
- `Response<BullshitState>` (`success`, `eventType`, `eventData`, `message`, `state`).
- Reuse the existing generic `Card` type (`{ rank, suit, name }`).

**`BullshitSession.ts` (new):** orchestrates actions and incoming messages.
- `create(name)`: subscribe to the lobby topic `/topic/game` for the `CREATE` ack; publish `/app/bullshit/create` with `{ nbPlayers: 2, mode: 'MULTIPLAYER', name }`; on `CREATE`, read seat-0 token from `eventData.tokens["0"]`, store `gameId` + token, persist to `localStorage`, `subscribeToSeat(gameId, 0, token)`, then `GET /api/bullshit/game/{id}?token=` to hydrate the initial state (the `CREATE` ack carries `state: null`, so the creator's own hand is fetched via REST so it is visible during the waiting phase).
- `join(gameId, name)`: REST `POST /api/bullshit/game/{id}/join` → `{ playerId, token }`; persist; `subscribeToSeat(gameId, playerId, token)`; then `GET /api/bullshit/game/{id}?token=` to hydrate initial state.
- Actions: `discard(cards: Card[])` → publish `/app/discard` `{ gameId, token, cards }`; `callBullshit()` → publish `/app/callBullshit` `{ gameId, token }`.
- Incoming per-seat `Response`: set store `state` from `response.state`; record `lastEvent` (`eventType` + `eventData`) for the reveal panel and status messages.

**`Bullshit.store.ts` (new, Pinia setup store):**
- State: `state: BullshitState | null`, `gameId`, `mySeat: number`, `myToken: string`, `lastEvent`, `selectedCards: Card[]`.
- Derived: `phase` = `waiting` (opponent not yet joined) | `playing` | `finished` (from `state.outcome.status`); `isMyTurn` (my seat's `isCurrentPlayer`); `canDiscard` / `canCallBullshit` (from `availableActions`).
- Tokens persisted to `localStorage["bullshit:tokens:" + gameId]` = `{ [seat]: token }`.

**Rehydration on reload:** read the stored token for the room's `gameId`, `GET /api/bullshit/game/{id}?token=`, set state, re-subscribe to the seat. Reuse the bootstrap-composable pattern with Bullshit-specific REST paths.

### C. Minimal game screen (`BullshitGameScreen.vue`, new)

Reuses the felt shell, `PlayingCard`, and `CardCounter`.

- **Opponent**: seat label + `CardCounter` (their `handCount`) + a turn highlight when their seat `isCurrentPlayer`.
- **Table**: current claim target ("Claim: ACE"); if `table.state === 'CLAIM'`, show the claimant and N face-down cards; show `discardPileSize`.
- **My hand**: `PlayingCard` list with multi-select (1–4). A **"Discard as {target}"** button enabled when it is my turn and 1–4 cards are selected. A **"Call Bullshit"** button enabled when `availableActions` includes `CALL_BULLSHIT`.
- **Reveal**: on a `CALL_BULLSHIT` event, a transient panel — "Player X called bullshit on Player Y — claim was TRUE/FALSE — Player Z takes the pile" + the `revealedCards` face-up. Dismissed on the next action. No animation.
- **Waiting**: before the opponent joins, show the share link + "waiting for opponent"; clears when a `JOIN` event arrives.
- **End**: when `outcome.status === 'FINISHED'`, a simple "You win" / "You lose" panel (compare `winnerId` to `mySeat`). No rematch.

### D. Backend — one thin change

Add `POST /api/bullshit/game/{id}/join` to `BullshitRestController`:
- Calls existing `sessionService.joinGame(gameId, name)` (seats `PlayerId(1)`), then `sessionService.touch(gameId)`.
- Broadcasts the per-seat state to all seats via `BullshitStateBroadcaster.broadcast(game, LifecycleEventType.JOIN.toString(), new EmptyEventData(), "Player 1 joined.")` so seat 0 learns the opponent arrived and leaves the waiting state.
- Returns `JoinResponseDto{ playerId, token }` (reusing the existing generic DTO).
- Maps a second join (seat already claimed) → the existing `SeatUnavailableException` → 409/appropriate error; unknown/malformed game → 404. Mirrors `BatailleCorseRestController.joinGame`.

This is the only backend change in the slice.

## Component boundaries

- `WebSocketService` — transport only; knows destinations and headers, not game semantics. The seat-subscription replay is generic.
- `BullshitSession` — translates user intents to STOMP/REST and incoming `Response`s to store mutations. No Vue/DOM.
- `Bullshit.store.ts` — reactive state + derived view flags. No transport.
- `BullshitGameScreen.vue` — render + user input; reads store, calls session actions. No transport, no business logic beyond presentation.
- `BullshitRestController` — gains one join mapping; delegates to `SessionService` + `BullshitStateBroadcaster`.

## Testing

**Backend (TDD):** `BullshitRestController` join tests — valid join returns seat-1 token + 200; broadcasts per-seat state to both seats; second join rejected; unknown game → 404. Run with the IntelliJ-bundled Maven + JBR; the full suite must stay green.

**Frontend (Vitest + happy-dom):**
- `BullshitSession`: `create`/`join`/`discard`/`callBullshit` publish to the correct destinations with the correct payloads; `subscribeToSeat` passes the `token` native header; `Response` → store state mapping is correct.
- `Bullshit.store.ts`: `phase`, `isMyTurn`, `canDiscard`/`canCallBullshit`, and card-selection logic.
- A light component check of the screen's button enable/disable wiring is optional.
- `vite build` is the real type-check/regression gate (bare `vue-tsc` gives false passes in worktrees without `node_modules`; regenerate the lockfile inside `node:20-alpine` if it changes).

## Explicitly out of scope (YAGNI)

Solo hot-seat; 3–6 player join (and multi-seat `joinGame`); game-picker UI; disconnect/reconnect & forfeit overlays; game-duration timer; rematch; reveal/discard animations; suit-variant (`CyclingSuitClaimMode`) selection; a Bullshit rules panel / i18n; turn-glow polish.

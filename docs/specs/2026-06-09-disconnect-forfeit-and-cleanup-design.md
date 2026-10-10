# Disconnect detection, forfeit, exit confirmation, and repository cleanup

Date: 2026-06-09

## Overview

Three related features for multiplayer BatailleCorse, plus a supporting
repository change:

1. **Disconnect auto-loss** — when a player's WebSocket connection drops
   mid-game, the opponent sees a 60-second countdown. If the dropped player
   reconnects in time, play resumes. Otherwise the server awards the win to the
   present player automatically.
2. **Exit confirmation** — leaving an in-progress game (in-app navigation or
   browser close/refresh) requires confirmation. In multiplayer, confirming
   forfeits immediately so the opponent wins without waiting the full minute.
3. **Repository cleanup** — finished and abandoned games are evicted from the
   in-memory repository so they don't accumulate, and lookups stop being O(n).

### Scope decisions

- **Disconnect detection and auto-loss are MULTIPLAYER-only.** SOLO is a single
  browser driving both seats (the AI is a frontend puppet in
  `frontend/src/model/ai/AI.ts`), so there is no opponent to inform and no
  remote connection to lose.
- **Exit confirmation applies to any in-progress game.** In solo it guards
  against accidentally abandoning progress (no forfeit message); in multiplayer
  it also fires a forfeit.
- **Leave trigger is connection-drop only.** Tab-switching, minimizing, or
  backgrounding the app does **not** start the timer as long as the socket
  stays alive. A page reload briefly drops and re-establishes the socket, which
  is comfortably absorbed by the 60s window.

## Current architecture (relevant pieces)

- **Repository:** `InMemorySessionRepository` stores games and session metadata
  in two `ArrayList`s. Games are never removed. Every `load`/action does a
  linear `stream().filter().findFirst()` scan.
  (`backend/.../sessionmanagement/infrastructure/InMemorySessionRepository.java`)
- **Domain:** `BatailleCorse` holds a `Result` computed from card counts via
  `Result.update(...)`. `isFinished()` is true once `result.winningPlayer != null`.
  There is no concession/forfeit path.
- **Real-time transport:** STOMP over WebSocket (`EnableWebSocketMessageBroker`).
  Action messages (`/app/send`, `/app/slap`, `/app/grab`, `/app/create`) carry
  `gameId` + `token`. Responses broadcast to `/topic/game/{gameId}`. Nothing
  binds a STOMP session to a `(gameId, playerId)` seat.
- **Frontend:** `GameScreen.vue` renders state and an end-game overlay driven by
  game state (not per-action events). `GameSession.ts` orchestrates events and
  knows `mode` (`solo`/`multiplayer`), `myPlayerIndex`. `WebSocketService.ts`
  resubscribes to the per-game topic ~3s after a reconnect. No router guards
  exist today.

## Design

### 1. Domain: terminal-by-forfeit

`Result` is currently derived from card counts only. Add an explicit concession
path so a game can become terminal regardless of cards:

- `BatailleCorse.concede(PlayerId loser)` sets `result` to a `Result` whose
  winner is the **other** player.
- Guard: **no-op if `isFinished()` already** — handles the race between a
  natural win and a firing disconnect timer.
- This is the single terminal path shared by explicit forfeit (feature 2) and
  timer auto-loss (feature 1). The "who wins on forfeit" logic lives in the
  model, not in the websocket layer.

### 2. Backend: presence, disconnect, timer

Detection uses an **explicit presence message + presence registry** (chosen over
inferring presence from SUBSCRIBE-frame headers or from the last action seen):
presence is something the client asserts, not a property derived from
transport-layer events that fire for their own reasons (reconnect resubscribes,
framework-managed subscriptions, etc.).

- **`PresenceRegistry`** (new, multiplayer only): maps
  `stompSessionId → (BatailleCorseId, PlayerId)` and the reverse, and holds the
  scheduled forfeit-task handle per seat.
- **`/app/presence`** message (`gameId` + `token`): resolve the seat via the
  existing token mechanism, record `sessionId → seat`. If a pending forfeit
  timer exists for that seat, **cancel it** and broadcast `OPPONENT_RECONNECTED`
  to `/topic/game/{id}`.
- **`@EventListener(SessionDisconnectEvent)`**: look up the seat for the dropped
  `sessionId`. If the game is multiplayer and not finished, schedule a 60s task
  (Spring `TaskScheduler`) and broadcast `OPPONENT_DISCONNECTED` with an absolute
  `deadline` (epoch-ms) so the present client can render an accurate countdown.
- **Timer fires** → `game.concede(loser)` → broadcast the normal winner-bearing
  `state-update` → schedule eviction grace (see §4).
- **`/app/forfeit`** message (feature 2 immediate forfeit): resolve seat from
  token → `game.concede(self)` → broadcast `state-update`. No timer involved.
- **New `EventType` values:** `OPPONENT_DISCONNECTED`, `OPPONENT_RECONNECTED`,
  `FORFEIT`.

**Edge cases**

- Both players drop: the first-dropped seat's timer fires first and ends the
  game; the second timer is a no-op against an already-finished game.
- Natural win during the countdown: `concede` no-ops; pending timers for a
  now-terminal game are also cancelled.
- SockJS reconnect (~3s): client re-sends presence, which cancels the timer.

### 3. Frontend: presence, countdown UI, exit confirmation

**Presence wiring** (`GameSession` / `WebSocketService`, multiplayer only):

- Send `/app/presence` on entering a multiplayer room (create / join / hydrate).
- Re-send `/app/presence` after every reconnect, hooked into the existing
  `WebSocketService` reconnect/resubscribe path. This single call is what keeps
  the presence approach robust across drops.

**Opponent-disconnected countdown** (`GameScreen.vue` + store):

- On `OPPONENT_DISCONNECTED`, store the server's absolute `deadline` and show an
  overlay banner counting down from `deadline - now`. The server clock is
  authoritative; the client only renders.
- On `OPPONENT_RECONNECTED`, clear the banner and resume play.
- On the winner-bearing `state-update`, the existing end-game overlay shows
  VICTORY — no special-casing required.
- Countdown duration/labels live in a single constant (e.g.
  `DISCONNECT_GRACE_MS`), matching the backend's 60s in meaning.

**Exit confirmation** (feature 2):

- A router guard (`onBeforeRouteLeave` on `/room/:id`): if the game is in
  progress, show a PrimeVue confirm dialog matching existing UI
  ("Leave the game? You'll forfeit.").
  - Confirm in **multiplayer**: publish `/app/forfeit`, then navigate.
  - Confirm in **solo**: just navigate (no forfeit; wording omits "forfeit").
- A `beforeunload` handler shows the native browser prompt while in an active
  game. Closing the tab cannot reliably send a forfeit message, so the actual
  forfeit for a hard close falls back to the disconnect timer (feature 1).
- No confirmation once the game is finished; existing back-to-home flows are
  untouched.

### 4. Repository cleanup (grace-delay + idle sweep)

- **Storage swap:** replace the two `ArrayList`s in `InMemorySessionRepository`
  with `Map`s keyed by `BatailleCorseId`, eliminating the O(n) scan on every
  action. Track a `lastActivityAt` timestamp per game, bumped on each action.
- **Grace eviction:** when a game reaches terminal state (natural or forfeit),
  schedule removal after a short grace (e.g. 2 min) so a reconnecting loser can
  still re-fetch the final state.
- **Idle sweep:** a `@Scheduled` job periodically removes any game whose
  `lastActivityAt` exceeds a longer idle TTL (e.g. 30 min) — covering games
  abandoned before finishing (e.g. a multiplayer game whose second seat never
  filled).
- Grace and TTL durations are named constants (single source of truth).
- Eviction also clears any `PresenceRegistry` entries for the game.

## Testing

Per project testing rules (no Mockito on domain classes; builders/fixtures;
`givenX_thenY` naming):

- **Domain (`BatailleCorse.concede`)**: `given` an ongoing game, `concede(loser)`
  sets the winner to the other seat; `given` an already-finished game, `concede`
  is a no-op. Use builders/fixtures, no mocks.
- **PresenceRegistry**: register/lookup/remove by session id and by seat;
  timer-handle storage and cancellation.
- **Disconnect/forfeit controller + listener**: seat resolution from token;
  `concede` invoked on timer fire and on `/app/forfeit`; broadcasts emitted with
  the right `EventType` and `deadline`.
- **Repository**: map-based load; grace eviction after terminal state; idle
  sweep removes stale games and clears presence entries.
- **Frontend**: countdown renders from a server `deadline` and clears on
  reconnect; router guard shows the dialog only for in-progress games and
  forfeits in multiplayer / navigates in solo; verify via the established
  frontend gate (`vite build`), not bare `vue-tsc`.

## Out of scope

- Reconnection grace tuning beyond the fixed 60s / 2-min grace / 30-min TTL
  constants.
- Persistence of games beyond process memory.
- Any change to SOLO gameplay other than the exit-confirmation dialog wording.

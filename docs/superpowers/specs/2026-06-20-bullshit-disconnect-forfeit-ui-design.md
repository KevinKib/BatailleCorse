# Bullshit disconnect / forfeit UI — design

**Date:** 2026-06-20
**Scope:** Frontend only (`frontend/`). No backend, no DTO changes.
**Branch:** `bullshit-disconnect-forfeit-ui` (base `main`).

## Problem

The Bullshit backend already broadcasts opponent-presence lifecycle events on the
per-seat topic `/topic/game/{id}/seat/{token}`, and the frontend transport layer
(`WebSocketService` → `BullshitSession.onResponse`) already forwards every one of
them into the store as `{ type: 'event', eventType, eventData, message }`. But the
store's `applyEvent` `'event'` case only handles `CALL_BULLSHIT` today — every
presence event is silently dropped. Players whose opponent disconnects or is
forfeited **see nothing**: the seat just freezes, then later vanishes, with no
explanation.

This change wires those events into the UI.

## Grounded backend facts (do NOT change — backend is done)

`BullshitLifecycleBroadcaster` emits three event types (stringified from
`LifecycleEventType`) on the per-seat topic:

| eventType                | eventData                                              |
|--------------------------|-------------------------------------------------------|
| `OPPONENT_DISCONNECTED`  | `{ disconnectedSeat: int, deadlineEpochMs: long }`    |
| `OPPONENT_RECONNECTED`   | `{ reconnectedSeat: int }`                            |
| `FORFEIT`                | `{ loserSeat: Integer }`                              |

These are generic `LifecycleEventType` values, **not** Bullshit-specific — any game
built on the shared session/presence layer emits the same three.

A 60s grace timer lives in `PresenceService`; on expiry the disconnected player is
forfeited. `Bullshit.forfeit(playerId)` is a **clean server-side elimination**
(`backend/.../bullshit/domain/Bullshit.java`):

- The player is removed from the game (`players.remove(index)`); their hand leaves
  with them — **cards are not dumped onto the pile**, and no one ever waits on them.
- If the forfeiter was the current player, the turn advances to whoever now occupies
  that slot (wrapping if needed); otherwise the turn stays put.
- The game continues coherently with the remaining players (ending if only one
  remains).

Consequence for the frontend: a forfeited seat simply **disappears on the next
state-update**, and the turn has already moved on. The UI's only job for forfeit is
to explain *why* a seat vanished.

## Decisions

1. **Disconnect indicator = per-seat badge** (not a full-screen overlay). Bullshit
   is N-player (2–6) seated around the table; a per-seat badge scales to multiple
   simultaneous disconnects and points at *who* dropped.
2. **Forfeit notice = transient banner.** A brief auto-dismissing
   `Player N forfeited`, mirroring the existing `reveal` timed-hold pattern; then the
   seat is gone via the next state-update. (A persistent badge has nothing to attach
   to once the seat is removed.)
3. **Build game-agnostic, reusable bricks.** Future games use the same shared
   session/presence layer and emit these same three events, so the new pieces are
   designed as game-neutral building blocks (live in the shared `composables/` and
   `components/` roots, not under `bullshit/`). Bullshit is the first consumer; the
   N-seat model is strictly more general than a 2-player one (a 2-player game is a
   map with ≤1 disconnected seat).
4. **Reuse BatailleCorse's shape, leave its code untouched.** The new countdown brick
   is modeled on BatailleCorse's `useDisconnectCountdown` (250ms ticker,
   server-provided absolute deadline, `ceil` seconds, `cancel()`), generalized from a
   single connection to a map of seats. The badge reuses `DisconnectOverlay`'s visual
   language (`--accent-negative-rgb`, copy). BatailleCorse's own composable/overlay
   are **left as-is** this PR; a focused follow-up can migrate BatailleCorse onto the
   bricks (its single-opponent case is a 1-entry map).

## Architecture

Transient connection state lives in the store, separate from authoritative game
state — exactly like `reveal`. `state-update` never touches it. The logic that
*reduces* lifecycle events into that state, and the clock that renders countdowns,
are extracted into two game-agnostic composable bricks; the badge and banner are two
game-agnostic component bricks.

### Brick A — `frontend/src/composables/useSeatPresence.ts` (new, generic)

Owns the transient presence state and the event-reduction logic (honors
logic-in-models: the store just forwards events to it). Single source of truth for
the lifecycle event-type strings and the forfeit hold duration.

```ts
export const SEAT_LIFECYCLE_EVENT = {
  OPPONENT_DISCONNECTED: 'OPPONENT_DISCONNECTED',
  OPPONENT_RECONNECTED:  'OPPONENT_RECONNECTED',
  FORFEIT:               'FORFEIT',
} as const;
export const FORFEIT_NOTICE_HOLD_MS = 4000;

useSeatPresence(options?: { presentSeats?: () => number[]; forfeitNoticeHoldMs?: number }) => {
  disconnections:     Ref<Record<number, number>>,        // seat → deadlineEpochMs
  forfeitNotice:      Ref<{ seat: number } | null>,        // transient, timed-hold
  liveDisconnections: ComputedRef<Record<number, number>>, // filtered to present seats
  applyPresenceEvent(eventType: string, eventData: unknown): void,
  reset(): void,                                            // clears state + timer
}
```

- `applyPresenceEvent` switches on `SEAT_LIFECYCLE_EVENT`:
  - `OPPONENT_DISCONNECTED` → `disconnections.value = { ...disconnections.value, [disconnectedSeat]: deadlineEpochMs }`
  - `OPPONENT_RECONNECTED` → remove `reconnectedSeat` (new object, no mutation)
  - `FORFEIT` → remove `loserSeat` from the map **and** set `forfeitNotice = { seat: loserSeat }`
    with an auto-clear timer (own timer ref, cleared before re-arming)
  - unknown eventType → no-op
- `liveDisconnections` filters `disconnections` to seats still in `presentSeats()`
  (defensive against a missed reconnect, or the forfeited seat in the gap between
  event and state-update). When no `presentSeats` getter is supplied it returns the
  raw map.
- Typed event-data shapes go in a **new shared, game-agnostic** model file
  `frontend/src/model/SeatLifecycleEvents.ts` (mirroring the backend, where these
  records live in the shared `presentation.dto.event` package — *not* under
  `bullshit`). Keeping them out of `model/bullshit/BullshitEvents.ts` is what makes
  the brick reusable by the next game.
  ```ts
  export interface OpponentDisconnectedEventData { disconnectedSeat: number; deadlineEpochMs: number; }
  export interface OpponentReconnectedEventData  { reconnectedSeat: number; }
  export interface ForfeitEventData              { loserSeat: number; }
  ```

### Brick B — `frontend/src/composables/useSeatDisconnectCountdown.ts` (new, generic)

The multi-seat generalization of BatailleCorse's `useDisconnectCountdown`.

```ts
useSeatDisconnectCountdown(options: {
  disconnections: () => Record<number, number>,
  isGameOver: () => boolean,
}) => { secondsRemainingFor(deadlineEpochMs: number): number, cancel(): void }
```

- One `now = ref(Date.now())` and one 250ms `setInterval`.
- A `watch` runs the ticker only while
  `Object.keys(disconnections()).length > 0 && !isGameOver()`; cleared otherwise.
- `secondsRemainingFor(deadline)` = `Math.max(0, Math.ceil((deadline - now.value) / 1000))`.
- `cancel()` stops the watcher and clears the interval; the view wires it into
  `onBeforeUnmount`.

### Brick C — `frontend/src/components/SeatDisconnectBadge.vue` (new, generic)

A small badge overlaid on a seat, reusing `DisconnectOverlay`'s visual language.

```ts
defineProps<{ secondsRemaining: number | null }>();
```

Renders `Reconnecting… {{ secondsRemaining }}s` (drops the `Ns` when null), styled off
`--accent-negative-rgb`. `prefers-reduced-motion` honored for any pulse.

### Brick D — `frontend/src/components/ForfeitBanner.vue` (new, generic)

A transient top banner. Game-neutral: takes the text to show.

```ts
defineProps<{ label: string }>();   // e.g. "Player 3 forfeited"
```

Styled consistently with the disconnect visual language; the caller wraps it in the
`<Transition>` and supplies the copy.

### Store — `frontend/src/state/Bullshit.store.ts`

Thin consumer of Brick A:

```ts
const presence = useSeatPresence({ presentSeats: () => (game.value?.players ?? []).map(p => Number(p.id)) });
```

`applyEvent`'s `'event'` case keeps the `CALL_BULLSHIT` branch and adds a single
delegation for the rest: `presence.applyPresenceEvent(event.eventType, event.eventData)`.
Re-export `disconnections`, `liveDisconnections`, `forfeitNotice` (and
`FORFEIT_NOTICE_HOLD_MS` from the brick) for the view and tests.

**Resetting stale presence (the store is a singleton reused across reopen-room
rematches).** A disconnect/forfeit from the previous game must not bleed into the
fresh one. Call `presence.reset()` in the `playAgain` action (before/at re-bind).
Defensively, also reset when `phase` transitions back to `'lobby'` (a `watch(phase)`
in the store) — covers any reopen path that lands in the lobby without `playAgain`.
`reset()` clears `disconnections`, `forfeitNotice`, and the forfeit timer.

### View — `frontend/src/view/bullshit/BullshitGameScreen.vue`

- Instantiate Brick B from `store.liveDisconnections` and `() => store.phase === 'finished'`.
- For each opponent, compute `disconnected = seat in liveDisconnections` and
  `secondsRemaining = disconnected ? secondsRemainingFor(deadline) : null`; pass both
  to `OpponentSeat`.
- `OpponentSeat.vue` gets two new optional props (`disconnected?`, `secondsRemaining?`),
  existing props/behavior unchanged; when `disconnected` it dims the seat card and
  mounts `SeatDisconnectBadge`.
- Render `ForfeitBanner` near the top of the table inside a `<Transition>`, driven by
  `store.forfeitNotice`, with `Player {{ forfeitNotice.seat + 1 }} forfeited`
  (does not obstruct the pile/reveal).
- `onBeforeUnmount(() => countdown.cancel())`.

Labels follow the established inline `Player ${id + 1}` convention (the playing
screen is 1-based; consistent with the reveal/last-play copy).

## Testing

- **Brick A** (`useSeatPresence.spec.ts`), fake timers: disconnect adds
  `{ seat: deadline }`; reconnect removes it; forfeit removes the seat, sets
  `forfeitNotice`, and auto-clears after `FORFEIT_NOTICE_HOLD_MS`;
  `liveDisconnections` drops a seat absent from `presentSeats()`; unknown eventType is
  a no-op.
- **Brick B** (`useSeatDisconnectCountdown.spec.ts`), fake timers: ticker advances
  `secondsRemainingFor`; stops when the map empties and when `isGameOver` is true;
  `cancel()` clears it.
- **Store tests** (`Bullshit.store.spec.ts`): the `'event'` cases delegate correctly
  (disconnect/reconnect/forfeit reflected in the exposed refs) and `CALL_BULLSHIT`
  still works; `playAgain` and a `phase`→`'lobby'` transition clear stale
  `disconnections`/`forfeitNotice`.
- **Component bricks**: `SeatDisconnectBadge` renders countdown text; `ForfeitBanner`
  renders its label.
- **Screen tests** (`BullshitGameScreen.spec.ts`): a disconnected opponent renders
  the badge with countdown; a forfeit renders the banner with the right player number.
- **Gate:** `npm run build` (worktrees lack `node_modules` → `npm ci` first; a bare
  `vue-tsc` gives a false pass) and `npm run test`.

## Reuse summary

| Need | Reuse / new |
|------|-------------|
| Countdown ticker logic | New generic Brick B, **modeled on** BatailleCorse's `useDisconnectCountdown` (generalized to N seats) |
| Disconnect visual language | Reuse `DisconnectOverlay`'s tokens/copy in Brick C |
| Transient timed-hold pattern | Reuse the existing `reveal` pattern in Brick A's forfeit notice |
| Per-seat anchor | Reuse existing `OpponentSeat.vue` (two additive props) |
| Felt/accent tokens | Reuse shared `--accent-negative-rgb` / `--accent-active-rgb` |

## Out of scope

- Any backend / DTO / domain change (backend is done; `bullshit/domain` is owned by
  a parallel session for issue #59).
- "Me disconnected" UX — the backend only sends `OPPONENT_*` to other seats, so the
  local player never appears as disconnected; the view renders only opponents anyway.
- **Migrating BatailleCorse onto the new bricks** — its working
  `useDisconnectCountdown` / `DisconnectOverlay` are left untouched; a focused
  follow-up PR can adopt the bricks (BC's single-opponent case is a 1-entry map).

## Addendum (2026-06-21): presence wiring + leave guard

Manual testing revealed the rendered UI never appeared, because the events it
listens for were **never emitted for Bullshit**. Root cause (traced end-to-end):
the backend attributes a socket drop to a seat only if that STOMP session was
bound via `/app/presence` (`PresenceService.onDisconnect` returns early when
`ConnectionRegistry.unbind` finds nothing). The frontend sends `/app/presence`
only through `WebSocketService.setPresence()`, whose sole caller was BatailleCorse's
`GameSession` — `BullshitSession` never registered presence. So Bullshit sessions
were absent from the registry and a tab-close no-op'd the whole chain.

Fix (frontend-only, still in scope):
- `BullshitSession.bind()` now calls `setPresence(JSON.stringify({ gameId, token }))`
  (added to `BullshitWebSocketPort`); `WebSocketService` already re-asserts presence
  on reconnect, so the `OPPONENT_RECONNECTED` path comes for free.
- Explicit leave path mirroring BatailleCorse **exactly, reusing the same code**:
  the shared `useLeaveGuard` composable (navigate away → `window.confirm` → forfeit)
  + the same `RouterLink` Back button. `BullshitSession.forfeit()` publishes
  `/app/forfeit`, surfaced as the store's `forfeit()` action. A hard tab-close still
  falls back to the 60s disconnect-grace timer.

This establishes the **reusable presence/leave framework** for future games: a new
game gets the full disconnect/forfeit experience by (1) calling `setPresence` on
bind, (2) exposing a `forfeit()` that publishes `/app/forfeit`, (3) reusing
`useLeaveGuard` + the per-seat presence bricks. No backend or per-game UI rebuild.

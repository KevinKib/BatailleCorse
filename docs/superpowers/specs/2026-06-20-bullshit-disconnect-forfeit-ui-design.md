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
   simultaneous disconnects and points at *who* dropped. BatailleCorse's
   full-screen `DisconnectOverlay` (2-player) stays untouched and is **not** reused.
2. **Forfeit notice = transient banner.** A brief auto-dismissing
   `Player N forfeited`, mirroring the existing `reveal` timed-hold pattern; then the
   seat is gone via the next state-update. (A persistent badge has nothing to attach
   to once the seat is removed.)
3. **New Bullshit-specific countdown composable** rather than reusing
   `useDisconnectCountdown`. The BatailleCorse composable models a *single* opponent
   connection; Bullshit needs a *map* of disconnected seats. Reuse the **shape**
   (250ms ticker, server-provided absolute deadline, `ceil` seconds, `cancel()`),
   not the code. BatailleCorse's composable is left untouched.

## Architecture

Three layers, mirroring the existing `reveal` transient-state pattern. Transient
connection state lives in the store, separate from authoritative game state;
`state-update` never touches it.

### Store — `frontend/src/state/Bullshit.store.ts`

New transient state:

```ts
const disconnections = ref<Record<number, number>>({}); // seat → deadlineEpochMs
const forfeitNotice  = ref<{ seat: number } | null>(null);
export const FORFEIT_NOTICE_HOLD_MS = 4000; // mirrors REVEAL_HOLD_MS
let forfeitTimer: ReturnType<typeof setTimeout> | null = null;
```

New `applyEvent` `'event'` cases (added alongside `CALL_BULLSHIT`):

- `OPPONENT_DISCONNECTED` → `disconnections.value = { ...disconnections.value, [disconnectedSeat]: deadlineEpochMs }`
- `OPPONENT_RECONNECTED` → remove `reconnectedSeat` from the map (new object, no mutation)
- `FORFEIT` → remove `loserSeat` from the map **and** set `forfeitNotice = { seat: loserSeat }`
  with an auto-clear timer (own `forfeitTimer`, cleared before re-arming, exactly like
  `revealTimer`)

Event-data types added to `frontend/src/model/bullshit/BullshitEvents.ts`:

```ts
export interface OpponentDisconnectedEventData { disconnectedSeat: number; deadlineEpochMs: number; }
export interface OpponentReconnectedEventData  { reconnectedSeat: number; }
export interface ForfeitEventData              { loserSeat: number; }
```

Derived getter exposed for the view (defensive against stale entries — a missed
reconnect, or the forfeited seat in the gap between event and state-update):

```ts
const liveDisconnections = computed<Record<number, number>>(() => {
  const present = new Set((game.value?.players ?? []).map(p => Number(p.id)));
  return Object.fromEntries(
    Object.entries(disconnections.value).filter(([seat]) => present.has(Number(seat)))
  );
});
```

Store exports add: `disconnections`, `liveDisconnections`, `forfeitNotice`.

### Composable — `frontend/src/composables/useBullshitSeatCountdown.ts` (new)

```ts
useBullshitSeatCountdown({
  disconnections: () => Record<number, number>,
  isGameOver: () => boolean,
}) => { secondsRemainingFor(deadlineEpochMs: number): number, cancel(): void }
```

- A single `now = ref(Date.now())` and one 250ms `setInterval`.
- The ticker runs only while `Object.keys(disconnections()).length > 0 && !isGameOver()`
  (driven by a `watch`, same as `useDisconnectCountdown`); cleared otherwise.
- `secondsRemainingFor(deadline)` returns `Math.max(0, Math.ceil((deadline - now.value) / 1000))`.
- `cancel()` stops the watcher and clears the interval; the view wires it into
  `onBeforeUnmount`.

### Components

**`frontend/src/components/bullshit/OpponentSeat.vue`** — two new optional props,
existing props/behavior unchanged:

```ts
defineProps<{
  label: string; handCount: number; active: boolean;
  disconnected?: boolean; secondsRemaining?: number | null;
}>();
```

When `disconnected`: dim/desaturate the seat card and overlay a small badge styled
off `--accent-negative-rgb` (consistent with `DisconnectOverlay`) reading
`Reconnecting… {{ secondsRemaining }}s`. `prefers-reduced-motion` is honored for any
animation (the component already duplicates the glow keyframes).

**`frontend/src/view/bullshit/BullshitGameScreen.vue`**:

- Instantiate `useBullshitSeatCountdown` from `store.liveDisconnections` and
  `() => store.phase === 'finished'`.
- For each opponent, compute `disconnected = seat in liveDisconnections` and
  `secondsRemaining = disconnected ? secondsRemainingFor(deadline) : null`; pass both
  to `OpponentSeat`.
- Add a `<Transition>` forfeit banner driven by `store.forfeitNotice`, reading
  `Player {{ forfeitNotice.seat + 1 }} forfeited`, positioned near the top of the
  table (does not obstruct the pile/reveal).
- `onBeforeUnmount(() => countdown.cancel())`.

Labels follow the established inline `Player ${id + 1}` convention (the playing
screen is 1-based; consistent with the reveal/last-play copy).

## Testing

- **Store tests** (`Bullshit.store.spec.ts`), fake timers:
  - `OPPONENT_DISCONNECTED` adds `{ seat: deadline }` to `disconnections`.
  - `OPPONENT_RECONNECTED` removes that seat.
  - `FORFEIT` removes the seat from `disconnections`, sets `forfeitNotice`, and
    auto-clears it after `FORFEIT_NOTICE_HOLD_MS`.
  - `liveDisconnections` drops a seat no longer present in `game.players`.
- **Screen tests** (`BullshitGameScreen.spec.ts`):
  - A disconnected opponent renders the badge with countdown text.
  - A forfeit renders the banner with the right player number.
- **Gate:** `npm run build` (worktrees lack `node_modules` → `npm ci` first; a bare
  `vue-tsc` gives a false pass) and `npm run test`.

## Out of scope

- Any backend / DTO / domain change (backend is done; `bullshit/domain` is owned by
  a parallel session for issue #59).
- "Me disconnected" UX — the backend only sends `OPPONENT_*` to other seats, so the
  local player never appears as disconnected; the view renders only opponents anyway.
- BatailleCorse's `DisconnectOverlay` / `useDisconnectCountdown` — left untouched.

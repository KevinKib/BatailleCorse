# Issue #71 — Collapse BatailleCorse's bespoke disconnect UI onto the shared presence bricks

**Date:** 2026-06-23
**Scope:** BatailleCorse frontend only. No Bullshit files, no shared-brick behavior changes, no backend.
**Closes:** #71

## Goal

Replace BatailleCorse's bespoke single-opponent disconnect plumbing with the
game-agnostic presence bricks introduced in PR #72, paying down the duplication
that #72 deliberately created. This is a **pure refactor with zero UX change**:
BC keeps its full-screen `DisconnectOverlay` ("X disconnected / waiting / you win
in Ns") — same component, same props, same text, same countdown behaviour.

**Forfeit is explicitly out of scope.** BC has no forfeit-banner UX today; the
bricks' `forfeitNotice`/`ForfeitBanner` are left unused. Adopting them would ADD
new UX to BC, contradicting the unchanged-UX constraint. File forfeit-banner
adoption as a follow-up if desired.

## The shared bricks being reused (as-is)

- `model/SeatLifecycleEvents.ts` — `OpponentDisconnectedEventData {disconnectedSeat, deadlineEpochMs}`, `OpponentReconnectedEventData {reconnectedSeat}`.
- `composables/useSeatPresence.ts` — `applyPresenceEvent(type, data)` reduces events into `disconnections` (seat→deadlineEpochMs) and `liveDisconnections` (filtered by `presentSeats()`); `reset()`; `SEAT_LIFECYCLE_EVENT` constant strings.
- `composables/useSeatDisconnectCountdown.ts` — one 250ms ticker over the map; inputs `{ disconnections(), isGameOver() }`; output `secondsRemainingFor(deadline)`, `cancel()`.

No brick is modified. If a tweak had been needed it would be a backward-compatible
addition; none is.

## Data flow: before → after

**Before:** `GameSession` maps `OPPONENT_DISCONNECTED`/`OPPONENT_RECONNECTED` into a
bespoke `{type:'opponent-connection', status, seat, deadlineEpochMs}` store event →
store `opponentConnection` ref → `useDisconnectCountdown` (holds all guards) →
`DisconnectOverlay`.

**After:** `GameSession` forwards the raw lifecycle event generically as
`{type:'presence-event', eventType, eventData}` → store delegates to
`useSeatPresence.applyPresenceEvent` → `liveDisconnections` map → GameScreen drives
`useSeatDisconnectCountdown` off that map and derives the single opponent's
`secondsRemaining` → `DisconnectOverlay` (unchanged).

## The four guards, re-homed (none lost)

The old `useDisconnectCountdown` gated the overlay on **is-it-me**,
**is-it-multiplayer**, **is-it-game-over**, and rendered the **countdown**. New homes:

### Non-obvious move: fold is-me + is-multiplayer into `presentSeats()`

The store wires:

```ts
const presence = useSeatPresence({
  presentSeats: () => mode.value === 'multiplayer' ? [1 - myPlayerIndex.value] : [],
});
```

`liveDisconnections` keeps a disconnect entry only when its seat is in
`presentSeats()`. By returning **just the opponent seat in multiplayer, and an
empty array in solo**, this single hook absorbs two old guards at once:

- **is-multiplayer** — solo returns `[]`, so no disconnect ever survives the filter.
- **is-me** — only `1 - myPlayerIndex` (the opponent) is "present", so a disconnect
  event carrying my own seat is filtered out.

This is the one piece of cleverness in the migration and the reason BC's
single-opponent semantics survive on a generic N-seat brick. It is called out here
because the brick name (`presentSeats`) reads as "everyone at the table"; in BC we
deliberately narrow it to "the opponent, if this is a real multiplayer game."

### is-game-over

`useSeatDisconnectCountdown({ isGameOver: () => isGameOver.value })` already stops
the ticker when the game is over. GameScreen's `opponentDisconnected` computed also
checks `!isGameOver.value` so the overlay hides at game end.

### countdown

`secondsRemainingFor(deadline)` over the 1-entry map; server-provided absolute
deadline, local clock only renders remaining seconds (unchanged semantics).

## Per-file changes (BC frontend only)

1. **`application/GameEvent.ts`** — replace the two `opponent-connection` variants
   with one `{ type: 'presence-event'; eventType: string; eventData: unknown }`.

2. **`application/GameSession.ts` (~350-365)** — replace the bespoke mapping block.
   Forward `OPPONENT_DISCONNECTED`/`OPPONENT_RECONNECTED` as
   `{type:'presence-event', eventType: response.eventType, eventData: response.eventData}`.
   Use the `SEAT_LIFECYCLE_EVENT` constants (imported from `useSeatPresence`) for the
   eventType comparison — single source of truth. The raw server `eventData` already
   matches the brick's payload shapes, so no reshaping is needed.

   **Do NOT re-add the old `Number(...)` coercion** that the bespoke mapping applied
   to `disconnectedSeat`/`deadlineEpochMs`. Forward `response.eventData` verbatim, the
   same way Bullshit forwards its events on this shared session/presence layer. It is
   safe because the brick is coercion-tolerant: `liveDisconnections` filters with
   `present.has(Number(seat))` (string-or-number keys both match), and
   `secondsRemainingFor` does numeric arithmetic on the deadline. Re-introducing
   coercion here would mean reshaping the payload, which the brick's contract makes
   unnecessary.

3. **`state/BatailleCorse.store.ts`** —
   - Add `const presence = useSeatPresence({ presentSeats })` (the narrowing above).
   - Remove the `opponentConnection` ref and the `opponent-connection` event case.
   - Add a `presence-event` case → `presence.applyPresenceEvent(event.eventType, event.eventData)`.
   - Expose `liveDisconnections: presence.liveDisconnections` (drop `opponentConnection` from the returned object).
   - Call `presence.reset()` on rematch-`started` and in `create()` so a stale
     disconnect can't linger across games.

4. **`view/alpha/GameScreen.vue`** —
   - Swap the `useDisconnectCountdown` import/usage for `useSeatDisconnectCountdown`,
     driven by `() => liveDisconnections.value`.
   - Derive: `disconnectedSeat` (first key of `liveDisconnections`, or null);
     `opponentDisconnected = disconnectedSeat !== null && !isGameOver`;
     `secondsRemaining = disconnectedSeat === null ? 0 : secondsRemainingFor(liveDisconnections[disconnectedSeat])`.
   - Feed `DisconnectOverlay` unchanged (`:opponent-label="opponentLabel" :seconds-remaining="secondsRemaining"`).
   - Replace `opponentConnection` in `storeToRefs` with `liveDisconnections`.
   - Keep `cancel()` in `onBeforeUnmount`.

5. **Delete `composables/useDisconnectCountdown.ts` and its test** — no other importer
   exists (grep-confirmed: only GameScreen + the test referenced it).

## Tests

- **Delete** `useDisconnectCountdown.test.ts`.
- **`BatailleCorse.store.test.ts`** — feed `presence-event` with `OPPONENT_DISCONNECTED`/
  `OPPONENT_RECONNECTED`; assert `liveDisconnections` reflects them; assert solo mode
  and a self-seat disconnect are both filtered out (the `presentSeats` narrowing);
  assert `reset()` clears on rematch/create.
- **`GameSession.test.ts`** — assert the two lifecycle events now emit `presence-event`
  (with forwarded eventType/eventData), not `opponent-connection`.
- **`GameScreen.test.ts`** — assert the overlay still renders with the same opponent
  label and `You win in Ns` countdown when the opponent disconnects, and hides on
  reconnect and at game over.
- **Gate:** `npm run build`.

## Out of scope / do not touch

Any Bullshit file, `BullshitGameScreen.vue`, the shared bricks' behaviour (additive
only — and none needed here), the backend.
